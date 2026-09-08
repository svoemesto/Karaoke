# Karaoke — Living Documentation v2 (Single Source of Truth)

> **Статус**: в разработке. Структура каркаса развёрнута в спецификации
> `320-living-docs-v2-bootstrap` (ветка `320-living-docs-v2-bootstrap`).
> Наполнение доменов/фич/epic'ов ведётся в следующих спецификациях.

Эта директория — **Single Source of Truth (SSoT)** для архитектуры проекта
Karaoke. Описывает текущее состояние системы, а не историю изменений.

## Структура

| Раздел | Назначение |
| --- | --- |
| [`system/`](system/) | C4 L1 (System Context) и L2 (Containers). Общий взгляд сверху. |
| [`domains/`](domains/) | C4 L3 — Bounded Contexts и компоненты (DDD). |
| [`adr/`](adr/) | Architecture Decision Records — почему мы сделали именно так. |
| [`epics/`](epics/) | Стратегические эпики — крупные переходы, влияющие на несколько доменов. |
| [`guidelines/`](guidelines/) | Стандартные паттерны и ограничения (KDoc/JSDoc, конвенции и т.п.). |
| [`public/`](public/) | Человеко-читаемые проекции внутренней документации. |
| [`templates/`](templates/) | Шаблоны для новых доменов, компонентов, ADR и эпиков. |

## Жизненный цикл документации

1. **Ephemeral work** — черновики, задачи, заметки живут **вне** `docs/`
   (в `specs/NNN-.../`, в личном scratch-каталоге, в письмах peer-mail).
2. **Mutation** — после реализации состояние `docs/` обновляется так,
   чтобы оно отражало **текущий** state системы, а не diff.
3. **Purge** — эфемерные артефакты удаляются. Merge ≠ Done, пока
   Living Documentation не приведён в соответствие с кодом.

## Linking Protocol (L3 → L2 → L1)

Атомарная операция при любом изменении:

- **Новый компонент (L3)** → обязан быть явно перечислен в `domain.md` (L2).
- **Новый домен (L2)** → обязан быть явно перечислен в `domains/README.md` (L1).
- **Удаление** файла на L3 → обязательно удалить ссылку на L2.

Нарушение этого протокола — критический архитектурный регресс.

## Шаблоны и линтер

- Новые файлы **обязаны** использовать шаблоны из `docs/templates/`.
- Перед merge запускается линтер: `python3 tools/lint-docs.py`.
- Линтер проверяет: эмодзи, обязательные секции, broken-линки L1↔L2↔L3.

## Для AI-агентов: протокол работы

Префикс `[SSoT-Verified]` в начале ответа — обязателен, если анализ
основан на этой документации.

1. **Initialize**: прочитай `docs/README.md` → `domains/README.md` →
   определи домен/эпик/ADR, к которому относится задача.
2. **Analyze**: прочитай `domain.md` целевого домена, чтобы понять
   Ubiquitous Language, и связанные ADR.
3. **Work in isolation**: инструменты/скрипты/временные файлы — вне `docs/`.
4. **Mutate state**: обнови документы `docs/` через Linking Protocol.
5. **Verify**: сверься с шаблонами и прогони линтер.
6. **Purge**: удали эфемерные артефакты.

## Karaoke-overrides (явные отклонения от generic-bootstrap)

Эти правила **специфичны для Karaoke** и перекрывают generic-фрагменты
из `bootstrap-living-docs/payload/fragments/`:

1. **CLAUDE.md ≠ AGENTS.md.** В Karaoke `AGENTS.md` — runtime governance
   (изменяется только владельцем через спецпроцедуру, см. AGENTS.md
   «Как обновлять этот файл»), `CLAUDE.md` — рекомендации для Claude Code.
   Они намеренно расходятся. **Ассиметрию не «чинить».**
2. **Агент делает git-цикл.** В Karaoke AI-агент создаёт feature-ветку
   `NNN-slug`, открывает PR и мержит через CI. Запрет на коммиты
   из generic-bootstrap к Karaoke не применяется — см. AGENTS.md
   «Git — CI-gate для master».
3. **ADR-нумерация.** Существующие ADR в `docs-old/livedocs/architecture/decisions/`
   (`0001-raw-jdbc.md` … `0008-…`, `local-0001-…` … `local-0006-…`)
   сохраняют свои ID и при переносе в `docs/adr/` **не переименовываются**.
4. **Эпики и спецификации.** Karaoke уже использует `specs/NNN-<slug>/`
   как полный жизненный цикл фичи (spec → plan → tasks → implementation).
   `docs/epics/` заводится только для **стратегических** переходов,
   затрагивающих несколько specs; связь `EPIC-XXX → specs/N, M, K`
   указывается явно в секции «Roadmap & Steps».
5. **`livedocs/` и `docs/` сосуществуют** до полной миграции. Старая
   структура `livedocs/` обслуживается существующими
   `tools/check-livedocs-*.sh` и CI; новая `docs/` — SSoT по этому
   README. Архивирование старой структуры произойдёт отдельным PR
   после того, как новые `docs/domains/`, `docs/adr/`, `docs/guidelines/`
   будут полностью наполнены и пройдут audit.

## Историческая преемственность

| Что | Где сейчас | Куда мигрирует |
| --- | --- | --- |
| Bounded Contexts | `livedocs/domain/<name>.md` (1 файл → 1 домен) | `docs/domains/<name>/domain.md` + `components/*.md` |
| C4 L1/L2/L3 | `livedocs/architecture/L{1,2,3}-*.md` | `docs/system/01-context.md` (L1), `docs/system/02-containers.md` (L2), `docs/domains/<name>/components/*.md` (L3) |
| ADR | `livedocs/architecture/decisions/NNNN-*.md` | `docs/adr/NNNN-*.md` (ID сохраняется) |
| Features | `livedocs/features/<spec-id>-*.md` | `specs/<NNN>/spec.md` (источник) + `docs/domains/<name>/domain.md` (влияние на домен) |
| Templates | `livedocs/templates/` | `docs/templates/` (уже здесь) |
| Runbooks | `livedocs/runbooks/` | переезжают в `docs/guidelines/runbooks/` в отдельной спецификации |
| Architecture notes | `docs-old/architecture-notes.md` (дневник) | остаётся в `docs-old/` как append-only changelog |
