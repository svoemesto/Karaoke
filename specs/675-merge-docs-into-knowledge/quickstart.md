# Quickstart — проверка слияния `docs/` в `knowledge/`

**Feature**: `675-merge-docs-into-knowledge` | **Issue**: `KARAOKE-334` | **Дата**: 2026-10-29

> Руководство по **проверке** результата, а не по реализации. Порядок работ — в [plan.md](./plan.md),
> форматы — в [contracts/](./contracts/), решения — в [research.md](./research.md).

---

## 0. Предусловия

| Что | Как проверить |
|---|---|
| Ветка | `git branch --show-current` → `675-merge-docs-into-knowledge` |
| Рабочее дерево чистое | `git status --porcelain` → пусто |
| `master` не тронут | `git log master --oneline -1` → коммит до `3fb7c8bf` |
| Инструменты на месте | `bash --version`, `python3 --version`, `git --version`, `rg --version` |

```bash
cd /home/nsa/Karaoke
git branch --show-current
git status --porcelain
bash --version | head -1
python3 --version
```

> `lychee` (проверка ссылок) ставится отдельно: `cargo install lychee` — см. подсказку самого
> `tools/verify-doc-links.sh`. Без него сценарий 6 частично недоступен; остальные сценарии
> проверяются штатными инструментами проекта.

---

## 1. Сценарий «слой `docs/` исчез» (SC-001, FR-001)

```bash
cd /home/nsa/Karaoke

# 1.1. Ни одного отслеживаемого файла в docs/
git ls-files docs/ | wc -l          # ожидается: 0

# 1.2. Каталога docs/ нет на диске
test -d docs && echo "FAIL: docs/ существует" || echo "OK: docs/ отсутствует"

# 1.3. Guard зелёный
bash tools/check-no-docs-dir.sh     # ожидается: код 0
```

**Ожидаемый вывод**: `0`, `OK: docs/ отсутствует`, код возврата 0.

**Что это доказывает**: FR-001, SC-001 — параллельного второго слоя документации нет.

---

## 2. Сценарий «guard ловит возврат `docs/`» (SC-005, FR-012)

```bash
cd /home/nsa/Karaoke

# 2.1. Фикстура «каталог docs/ появился» — guard обязан упасть
bash tools/check-no-docs-dir.sh --root tools/fixtures/no-docs-dir/vulnerable
echo "код возврата: $?"          # ожидается: 1

# 2.2. Чистое дерево — guard обязан пройти
bash tools/check-no-docs-dir.sh --root tools/fixtures/no-docs-dir/clean
echo "код возврата: $?"          # ожидается: 0
```

**Ожидаемый вывод**: уязвимая фикстура → сообщение «Каталог docs/ в корне репозитория запрещён (ADR-0011)» и код 1; чистая фикстура → код 0.

**Что это доказывает**: SC-005 — 2 из 2 сценариев; guard рабочий, а не «зелёный всегда».

---

## 3. Сценарий «26 per-feature документов» (SC-004, FR-003)

```bash
cd /home/nsa/Karaoke

# 3.1. Количество
ls knowledge/product/features/*.md | wc -l        # ожидается: 26

# 3.2. Полный набор секций FR-009 для каждого
bash tools/check-feature-doc.sh knowledge/product/features/*.md archive/docs/features/*.md
echo "код возврата: $?"                            # ожидается: 0

# 3.3. Профиль B проверяется, профиль A — нет (линтер не смотрит в product/)
python3 tools/lint-knowledge.py --baseline config/knowledge/baseline-knowledge-lint.txt
```

**Что это доказывает**: FR-003 и SC-004 — все 26 документов соответствуют FR-009.

---

## 4. Сценарий «реестр аудита» (SC-002, FR-011, FR-015)

```bash
cd /home/nsa/Karaoke

# 4.1. Уникальных BR-кодов в реестре — 510
grep -ohE "\bBR-[0-9]+\b" knowledge/system/infra/audit-336-registry.md | sort -u | wc -l
# ожидается: 510

# 4.2. Guard «BR-код ↔ компонент»
python3 tools/check-audit-registry.py
echo "код возврата: $?"                            # ожидается: 0

# 4.3. Код, упомянутый в knowledge/, есть в реестре (точечная проверка)
for code in BR-258 BR-541 BR-601; do
  grep -q "$code" knowledge/system/infra/audit-336-registry.md \
    && echo "OK: $code в реестре" || echo "FAIL: $code потерян"
done
```

**Что это доказывает**: FR-011, FR-015, FR-018, SC-002 — 510 из 510 кодов на месте; коды мост-файлов не потеряны.

---

## 5. Сценарий «0 битых ссылок на перемещённые файлы» (SC-003, FR-014)

```bash
cd /home/nsa/Karaoke

# 5.1. Перекрёстные ссылки внутри knowledge/
bash tools/check-knowledge-cross-links.sh
echo "код возврата: $?"

# 5.2. Ссылки и упоминания файлов/эндпоинтов
python3 tools/check-doc-references.py
echo "код возврата: $?"

# 5.3. Структура Knowledge
bash tools/check-knowledge-structure.sh
echo "код возврата: $?"

# 5.4. Ссылки lychee (если lychee установлен)
bash tools/verify-doc-links.sh
```

**Что это доказывает**: FR-014, SC-003 — ни одна ссылка на перемещаемый файл не осталась устаревшей.

**Отдельно — попутная починка битых ссылок**: guard'ы выше не ловят ссылки на файлы, которых
нет **и** не было (например, `docs/onboarding.md`). Проверка вручную:

```bash
grep -rn "docs/onboarding\|docs/claude-code-setup\|docs/migration-prod-server" \
  knowledge/ AGENTS.md CLAUDE.md .specify/memory/constitution.md \
  --include='*.md' | grep -v "Бывшие пути" || echo "OK: таких ссылок нет"
```

---

## 6. Сценарий «правила чисты» (SC-007, FR-021)

```bash
cd /home/nsa/Karaoke

# 6.1. В корневых правилах и инструментах нет упоминаний docs/
grep -rn "docs/" AGENTS.md CLAUDE.md .specify/memory/constitution.md \
  .github/PULL_REQUEST_TEMPLATE.md tools/README.md PR_DESCRIPTION.md \
  | grep -v "Бывшие пути" | grep -v "таблиц.*соответств" || echo "OK: чисто"

# 6.2. В инструментах не осталось зашитых путей docs/
grep -rn "docs/features\|docs/api\|docs/ops\|docs/governance" tools/ \
  --include='*.sh' --include='*.py' || echo "OK: чисто"

# 6.3. Ни одного workflow-файла с рассинхроном
bash tools/check-ci-workflow-sync.sh
echo "код возврата: $?"                            # ожидается: 0
```

**Что это доказывает**: FR-021, SC-007, FR-013 — правила, инструменты и обе копии CI-конфигурации указывают на `knowledge/`.

---

## 7. Сценарий «история сохранена» (SC-011, FR-019)

```bash
cd /home/nsa/Karaoke

# 7.1. git log --follow показывает исходную историю
git log --follow --oneline knowledge/product/features/kortege-execution.md | tail -3
# ожидается: в конце — коммиты с путём docs/features/kortege-execution.md

# 7.2. То же для runbook'а и реестра
git log --follow --oneline knowledge/guidelines/runbooks/log-correlation.md | tail -3
git log --follow --oneline archive/docs/architecture-notes.md | tail -3
```

**Что это доказывает**: FR-019, SC-011 — перенос сделан через `git mv`, история не потеряна.

---

## 8. Сценарий «ADR принят, старый — помечен» (FR-017)

```bash
cd /home/nsa/Karaoke

# 8.1. Новый ADR
head -15 knowledge/adr/0011-merge-docs-into-knowledge.md
# ожидается: status: Accepted, supersedes: 0007-adopt-knowledge-as-ssot,
#            superseded_by: null, date: 2026-10-29,
#            блок «> **Supersedes**: ADR-0007 — в части п. 2, п. 4 и следствия 5…»

# 8.2. В ADR-0007 изменено ТОЛЬКО метаданное
git diff master -- knowledge/adr/0007-adopt-knowledge-as-ssot.md
# ожидается: одна изменённая строка — superseded_by: 0011-merge-docs-into-knowledge

# 8.3. Реестр ADR отражает частичную замену
grep -n "0011-merge-docs-into-knowledge" knowledge/adr/README.md
```

**Что это доказывает**: FR-017 — ADR-0011 Accepted, ADR-0007 не переписан (append-only), реестр обновлён.

---

## 9. Сценарий «справочник бывших путей покрыт» (FR-024)

```bash
cd /home/nsa/Karaoke

# 9.1. Раздел присутствует
grep -n "Бывшие пути" knowledge/README.md

# 9.2. Каждый старый путь из docs/ есть в справочнике
#     (список берём из git-истории ветки до переноса)
git ls-tree -r --name-only 3fb7c8bf docs/ | sed 's#^docs/##' \
  | while read -r p; do
      grep -q "$p" knowledge/README.md || echo "НЕ ПОКРЫТО: docs/$p"
    done
# ожидается: пустой вывод
```

**Что это доказывает**: FR-024 — справочник покрывает все 73 старых пути.

---

## 10. Сценарий «два копии workflow содержат одинаковый набор проверок» (FR-012, FR-013)

```bash
cd /home/nsa/Karaoke

# 10.1. Guard присутствует в обеих копиях
grep -c "check-no-docs-dir" .forgejo/workflows/lint.yml   # ожидается: ≥ 1
grep -c "check-no-docs-dir" .github/workflows/lint.yml     # ожидается: ≥ 1

# 10.2. Фикстуры рассинхрона
bash tools/check-ci-workflow-sync.sh --root tools/fixtures/ci-workflow-sync/divergent
echo "код возврата: $?"                                  # ожидается: 1
bash tools/check-ci-workflow-sync.sh --root tools/fixtures/ci-workflow-sync/synced
echo "код возврата: $?"                                  # ожидается: 0
```

---

## 11. Сценарий «скиллы синхронизированы» (FR-020, KARAOKE-332 п. 11)

```bash
cd /home/nsa/Karaoke

# 11.1. Каноничная копия — проектная, под git
git ls-files .agents/skills/managing-living-docs/SKILL.md .agents/skills/audit-living-docs/SKILL.md

# 11.2. Рантайм-копия указывает на канон (симлинк), а не на копию
ls -l .dsh/skills/managing-living-docs .dsh/skills/audit-living-docs

# 11.3. Хеши совпадают
sha256sum .agents/skills/managing-living-docs/SKILL.md \
          .dsh/skills/managing-living-docs/SKILL.md

# 11.4. Единственная ссылка на Karaoke-путь docs/ исправлена
grep -n "architecture-notes" .agents/skills/audit-living-docs/SKILL.md
# ожидается: archive/docs/architecture-notes.md

# 11.5. Generic-зеркала команды НЕ тронуты
sha256sum ~/.dsh/skills/managing-living-docs/SKILL.md   # ожидается: 767b61dc…a63ae3
git -C /home/nsa/agents-team-srv status --porcelain .agents/skills/  # ожидается: пусто
```

**Что это доказывает**: FR-020 — правка git-версии действие, а рантайм-копия синхронизирована; generic-зеркала не тронуты.

---

## 12. Сценарий «72/73 — сводка по всем файлам» (SC-009)

```bash
cd /home/nsa/Karaoke

# 12.1. Сколько файлов было в docs/ до переноса
git ls-tree -r --name-only 3fb7c8bf docs/ | wc -l        # ожидается: 73

# 12.2. Сколько из них куда-то легло
#      — в продуктовую ветку
ls knowledge/product/features/*.md | wc -l                 # ожидается: 26
#      — в runbook'и
ls knowledge/guidelines/runbooks/*.md | grep -v README | wc -l   # ожидается: 5
#      — реестр содержит BR-коды всех пакетов
grep -c "BR-" knowledge/system/infra/audit-336-registry.md
```

**Полная сверка «до/после» по 73 файлам** — таблица в [contracts/migration-map.md § 1–2](./contracts/migration-map.md#1-поимённая-карта--docsfeatures-65-файлов): каждый файл имеет ровно одну строку решения. Проверка — сверка списка `git ls-tree -r --name-only 3fb7c8bf docs/` с таблицей (0 неучтённых).

---

## 13. Полный локальный прогон (аналог CI)

```bash
cd /home/nsa/Karaoke

bash tools/check-no-docs-dir.sh
bash tools/check-ci-workflow-sync.sh
python3 tools/check-audit-registry.py
bash tools/check-knowledge-structure.sh
bash tools/check-knowledge-cross-links.sh
python3 tools/lint-knowledge.py --baseline config/knowledge/baseline-knowledge-lint.txt
python3 tools/check-doc-references.py
bash tools/check-feature-doc.sh knowledge/product/features/*.md archive/docs/features/*.md
bash tools/verify-doc-links.sh        # требует lychee
```

Ожидается: **все** коды возврата 0.

---

## 14. Обязательные проверки проекта перед коммитом (AGENTS.md)

| Проверка | Команда |
|---|---|
| Секреты | `git ls-files \| grep -iE '\.env$\|do\.env$\|\.key$\|\.pem$\|\.p12$\|\.pfx$'` → пусто |
| Gradle | `GRADLE_USER_HOME="${GRADLE_USER_HOME:-$(dirname "$(git rev-parse --path-format=absolute --git-common-dir)")/.gradle}"` |
| Docker | `DOCKER_CONFIG="${DOCKER_CONFIG:-<корень>/.docker}"` |
| Контейнеры | только через `deploy/do.sh`; **не** перезапускать `karaoke-app` без согласия и без остановки очереди (Pass 380) |
| Фронтенд | `cd webvue3 && npm run ...` (в корне `package.json` нет) |
| Post-edit | скилл `kara-post-edit` — обязателен после правок кода |
| Pre-commit | скилл `kara-pre-commit` — 7 проверок CI перед коммитом |

> **Изменения рантайм-кода в этом усилии нет** — правится только содержимое docstring'ов
> (`@see`), поэтому полная пересборка образов не требуется; достаточно, чтобы
> `karaoke-app` скомпилировался (KDoc-аннотации не влияют на байткод, но контрактный
> тест читает исходники).

---

## 15. Что НЕ проверяется этим quickstart

| Вне области | Почему |
|---|---|
| Вторая волна (дочистка) | по решению карты — вне этой спеки |
| `archive/docs/**` (43 существующих файла) | вне скоупа; guard смотрит только корень |
| `livedocs/**` | каталога нет в репозитории; упоминания не чинятся |
| `~/.dsh/skills/**`, `/home/nsa/agents-team-srv/**` | read-only; проверяется только то, что они **не изменены** (сценарий 11.5) |
| Продакшен, контейнеры, база данных | миграция документации — прод не затрагивается |
| Сравнительные таблицы generic ↔ Karaoke в скиллах | по решению карты не трогаются |
