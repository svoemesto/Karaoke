#!/usr/bin/env bash
# =====================================================================
# tools/tracker-bootstrap-board.sh — создать/обновить Kanban-доску
# "AI Pipeline" в проекте Karaoke (фильтр assignee=ai-agent).
#
# Создаёт доску через rails-runner внутри openproject-контейнера.
# Идемпотентно — повторный вызов обновляет существующую доску.
#
# Использование:
#   bash tools/tracker-bootstrap-board.sh
# =====================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null && pwd)"

# Цвета
if [ -t 1 ]; then
    C_GREEN=$'\033[0;32m'
    C_YELLOW=$'\033[0;33m'
    C_RED=$'\033[0;31m'
    C_RESET=$'\033[0;32m'
else
    C_GREEN=""
    C_YELLOW=""
    C_RED=""
    C_RESET=""
fi

info() { echo "${C_GREEN}▸${C_RESET} $*"; }
warn() { echo "${C_YELLOW}⚠${C_RESET} $*" >&2; }
err()  { echo "${C_RED}✗${C_RESET} $*" >&2; }

if ! docker ps --format '{{.Names}}' | grep -q '^openproject$'; then
    err "Контейнер openproject не запущен. Запустите: bash tools/install-tracker.sh"
    exit 1
fi

# Записать Ruby-скрипты в файлы внутри openproject-контейнера,
# чтобы избежать проблем с bash quoting / экранированием.
RUBY_BOARD_SCRIPT='
require "json"
project = Project.find_by(identifier: "karaoke")
agent = User.find_by(login: "ai-agent")
unless project && agent
  puts JSON.dump(status: :error, message: "Не найден проект karaoke или пользователь ai-agent")
  exit 1
end

board = Boards::Grid.find_or_initialize_by(project: project, name: "AI Pipeline")
board.row_count = 1
board.column_count = 6
board.options ||= {}
board.save!

columns_to_create = [
  { name: "New", position: 1 },
  { name: "In specification", position: 2 },
  { name: "Specified", position: 3 },
  { name: "In progress", position: 4 },
  { name: "In review", position: 5 },
  { name: "Closed", position: 6 }
]

existing_widgets = board.widgets.where(identifier: "work_package_query").index_by(&:start_column)
results = []

columns_to_create.each do |col_def|
  status = Status.find_by(name: col_def[:name])
  unless status
    results << { name: col_def[:name], error: "status_not_found" }
    next
  end

  col = col_def[:position]
  query_name = "AI Pipeline — #{col_def[:name]}"

  query = Query.find_or_initialize_by(project: project, name: query_name)
  is_new = query.new_record?
  query.user = agent
  query.public = true
  query.show_hierarchies = false
  query.include_subprojects = false
  query.save! if is_new || query.filters.empty?

  needs_save = false
  unless query.filters.any? { |f| f.field.to_s == "status_id" && f.values == [status.id.to_s] }
    query.add_filter("status_id", "=", [status.id.to_s])
    needs_save = true
  end
  unless query.filters.any? { |f| f.field.to_s == "assigned_to_id" && f.values == [agent.id.to_s] }
    query.add_filter("assigned_to_id", "=", [agent.id.to_s])
    needs_save = true
  end
  query.save! if needs_save

  widget = existing_widgets[col]
  unless widget
    widget = board.widgets.new(identifier: "work_package_query")
  end
  widget.start_row = 1
  widget.start_column = col
  widget.end_row = 2
  widget.end_column = col + 1
  widget.options = {
    "queryId" => query.id,
    "filters" => query.filters.map(&:to_hash).map(&:deep_stringify_keys)
  }
  widget.save!

  results << {
    col: col,
    name: col_def[:name],
    status_id: status.id,
    query_id: query.id,
    widget_id: widget.id
  }
end

board.widgets.where(identifier: "work_package_query")
            .where("start_column > ?", columns_to_create.size).destroy_all

board.reload
puts JSON.dump(
  status: :ok,
  board_id: board.id,
  board_name: board.name,
  board_url: "/projects/#{project.identifier}/boards/#{board.id}",
  widget_count: board.widgets.count,
  columns: results
)
'

RUBY_WORKFLOW_SCRIPT='
require "json"
review_status = Status.find_by(name: "In review")
unless review_status
  puts JSON.dump(status: :error, message: "Статус \"In review\" не найден")
  exit 1
end

rules = [[1, 9], [7, 9], [3, 9], [9, 7], [9, 12], [12, 9]]
added = 0
skipped = 0

Type.find_each do |type|
  Role.where(id: [1, 6, 8]).each do |role|
    rules.each do |(old_id, new_id)|
      next unless [old_id, new_id].include?(review_status.id)
      next if Workflow.where(type_id: type.id, role_id: role.id,
                              old_status_id: old_id, new_status_id: new_id).exists?
      begin
        Workflow.create!(type_id: type.id, role_id: role.id,
                          old_status_id: old_id, new_status_id: new_id)
        added += 1
      rescue ActiveRecord::RecordNotUnique
        skipped += 1
      end
    end
  end
end

puts JSON.dump(status: :ok, added: added, skipped: skipped)
'

info "Шаг 1/2: создание/обновление доски 'AI Pipeline'..."
OUT=$(docker exec openproject bash -lc "cd /app && bundle exec rails runner -e production '$RUBY_BOARD_SCRIPT'" 2>&1 | tail -1)
BOARD_ID=$(echo "$OUT" | jq -r '.board_id // empty' 2>/dev/null)
BOARD_URL=$(echo "$OUT" | jq -r '.board_url // empty' 2>/dev/null)

if [ -z "$BOARD_ID" ]; then
    err "Не удалось создать/обновить доску:"
    err "  $OUT"
    exit 1
fi

info "  ✓ Доска ##${BOARD_ID} готова (URL: ${BOARD_URL})"

info "Шаг 2/2: добавление workflow-transitions для статуса 'In review'..."
OUT2=$(docker exec openproject bash -lc "cd /app && bundle exec rails runner -e production '$RUBY_WORKFLOW_SCRIPT'" 2>&1 | tail -1)
ADDED=$(echo "$OUT2" | jq -r '.added // 0' 2>/dev/null)
info "  ✓ Workflow transitions добавлено: ${ADDED} (или уже существовали)"

echo ""
echo "${C_GREEN}═══════════════════════════════════════════════════════════════════${C_RESET}"
echo "${C_GREEN}  Kanban-доска «AI Pipeline» готова${C_RESET}"
echo "${C_GREEN}  Открыть в UI:    http://localhost:8080${BOARD_URL}${C_RESET}"
echo "${C_GREEN}  Колонки:         New / In specification / Specified /${C_RESET}"
echo "${C_GREEN}                    In progress / In review / Closed${C_RESET}"
echo "${C_GREEN}  Фильтр колонок:  assignee=ai-agent, status=<колонка>${C_RESET}"
echo "${C_GREEN}═══════════════════════════════════════════════════════════════════${C_RESET}"
