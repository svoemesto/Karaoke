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
#
# BR-607 (аудит #336, пакет PKG-S3-30): `OUT=$(docker exec … 2>&1 | tail -1)`
# под `set -euo pipefail` при падении rails-runner УБИВАЛ скрипт прямо на
# этой строке — объявленная диагностика «Не удалось создать/обновить доску: …»
# не печаталась никогда. Теперь код возврата docker exec проверяется, а
# в сообщение идёт ПОЛНЫЙ вывод (раньше `tail -1` отрезал причину ошибки).
#
# BR-608 (аудит #336, пакет PKG-S3-30): ID статусов в правилах переходов были
# ЗАШИТЫ ЧИСЛАМИ ([[1, 9], [7, 9], …]). Эти числа — дефолтные ID статусов
# OpenProject, они зависят от порядка статусов в конкретном экземпляре.
# Теперь статусы резолвятся по ИМЕНИ (`Status.find_by(name: …)`), а переходы,
# для которых нужного статуса нет, пропускаются и ПЕРЕЧИСЛЯЮТСЯ в выводе.
# Шаг «workflow-transitions» больше не рапортует успех при неудаче.
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

# BR-608: переходы задаются ИМЕНАМИ статусов, а не числами. Числовые ID
# (1 = New, 9 = In review, …) — это дефолт OpenProject, они зависят от
# порядка статусов в конкретном экземпляре и в другой инсталляции указывают
# на другие статусы.
TRANSITIONS = [
  ["New", "In review"],
  ["In progress", "In review"],
  ["Specified", "In review"],
  ["In review", "In progress"],
  ["In review", "Closed"],
  ["Closed", "In review"]
].freeze

# Роли: сначала по имени (переносимо), числовые ID — только как запасной
# вариант, если имена в этом экземпляре другие.
ROLE_NAMES = ["Work package editor", "Member", "Project admin"].freeze
roles = Role.where(name: ROLE_NAMES)
roles = Role.where(id: [1, 6, 8]) if roles.empty?

added = 0
skipped = 0
missing_statuses = []

resolved = TRANSITIONS.map do |(old_name, new_name)|
  old_status = Status.find_by(name: old_name)
  new_status = Status.find_by(name: new_name)
  if old_status.nil? || new_status.nil?
    missing_statuses << "#{old_name} -> #{new_name}"
    nil
  else
    [old_status.id, new_status.id]
  end
end
resolved = resolved.compact

Type.find_each do |type|
  roles.each do |role|
    resolved.each do |(old_id, new_id)|
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

puts JSON.dump(status: :ok, added: added, skipped: skipped,
               roles: roles.map(&:name),
               missing_statuses: missing_statuses)
'

info "Шаг 1/2: создание/обновление доски 'AI Pipeline'..."
# BR-607: код возврата docker exec проверяется — при падении rails-runner
# печатается диагностика с ПОЛНЫМ выводом, а не тихий выход по errexit.
OUT=""
if ! OUT=$(docker exec openproject bash -lc "cd /app && bundle exec rails runner -e production '$RUBY_BOARD_SCRIPT'" 2>&1); then
    err "Не удалось создать/обновить доску (rails runner упал):"
    printf '%s\n' "$OUT" | tail -20 >&2
    exit 1
fi
OUT_LAST="$(printf '%s\n' "$OUT" | tail -1)"
BOARD_ID=$(printf '%s\n' "$OUT_LAST" | jq -r '.board_id // empty' 2>/dev/null)
BOARD_URL=$(printf '%s\n' "$OUT_LAST" | jq -r '.board_url // empty' 2>/dev/null)

if [ -z "$BOARD_ID" ]; then
    err "Не удалось создать/обновить доску:"
    printf '%s\n' "$OUT" | tail -20 >&2
    exit 1
fi

info "  ✓ Доска ##${BOARD_ID} готова (URL: ${BOARD_URL})"

info "Шаг 2/2: добавление workflow-transitions для статуса 'In review'..."
# BR-608/BR-607: тот же принцип — падение rails-runner больше не превращается
# в «✓ Workflow transitions добавлено: 0 (или уже существовали)».
OUT2=""
if ! OUT2=$(docker exec openproject bash -lc "cd /app && bundle exec rails runner -e production '$RUBY_WORKFLOW_SCRIPT'" 2>&1); then
    err "Не удалось добавить workflow-transitions (rails runner упал):"
    printf '%s\n' "$OUT2" | tail -20 >&2
    exit 1
fi
OUT2_LAST="$(printf '%s\n' "$OUT2" | tail -1)"
ADDED=$(printf '%s\n' "$OUT2_LAST" | jq -r '.added // 0' 2>/dev/null)
SKIPPED=$(printf '%s\n' "$OUT2_LAST" | jq -r '.skipped // 0' 2>/dev/null)
MISSING=$(printf '%s\n' "$OUT2_LAST" | jq -r '(.missing_statuses // []) | join(", ")' 2>/dev/null)
ROLES_USED=$(printf '%s\n' "$OUT2_LAST" | jq -r '(.roles // []) | join(", ")' 2>/dev/null)
info "  ✓ Workflow transitions добавлено: ${ADDED} (уже существовали: ${SKIPPED})"
[ -n "${ROLES_USED}" ] && info "    роли: ${ROLES_USED}"
if [ -n "${MISSING}" ]; then
    warn "  ⚠ Не найдены статусы для переходов: ${MISSING}"
fi

echo ""
echo "${C_GREEN}═══════════════════════════════════════════════════════════════════${C_RESET}"
echo "${C_GREEN}  Kanban-доска «AI Pipeline» готова${C_RESET}"
echo "${C_GREEN}  Открыть в UI:    http://localhost:8080${BOARD_URL}${C_RESET}"
echo "${C_GREEN}  Колонки:         New / In specification / Specified /${C_RESET}"
echo "${C_GREEN}                    In progress / In review / Closed${C_RESET}"
echo "${C_GREEN}  Фильтр колонок:  assignee=ai-agent, status=<колонка>${C_RESET}"
echo "${C_GREEN}═══════════════════════════════════════════════════════════════════${C_RESET}"
