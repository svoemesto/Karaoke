---
status: Active
slug: 189-session-summary
type: session-summary
related:
  - README.md
  - CHANGELOG.md
---

# LiveDocs — итоги follow-up сессии (Pass 43–51)

> Резюме недавних follow-up коммитов к спеке `189-live-documentation`.
> Этот документ — компактный обзор для AI-агента, чтобы быстро понять,
> что добавилось в LiveDocs в последнее время.
>
> **Pass 51** — большая миграция `docs/` → `livedocs/` (FR-017 спеки 189).

## Контекст

Спека `189-live-documentation` была завершена коммитом `10d8632b`
(depth #42, Final Phase, PR #317 в master). После этого пользователь
попросил **продолжать автономную работу** по сопровождению LiveDocs,
не дожидаясь новых указаний — до явной команды «стоп».

За сессию (2026-08-14, ~2 часа) выполнено **14 PR** (с #316 по #329),
все merged в master с прохождением 9/9 CI проверок.

## Что добавлено / улучшено

### 1. Gap-fill для фич без LiveDoc (Pass 43)

Фичи `016`, `124`, `125` развивались параллельно со спекой 189 и не
получили LiveDoc при первой миграции. Созданы 3 SDD-сводки:

| Файл | Что делает | Cross-cutting |
|------|------------|---------------|
| `features/016-fix-spec-tags-marker-loss-on-reopen.md` | Спецтеги: сохранение маркеров после `Apply → Save → reopen` | editorial + 010-lyrics-spec-tags |
| `features/124-news-flags-backfill.md` | Backfill флагов публикаций без создания новостей | publishing + data-sync |
| `features/125-player-status-gate.md` | Доступность плеера в таблице «Песни» при статусе ≥4 | catalog + 022-status |

PR #316.

### 2. Архитектурные topics (Pass 44–46)

Созданы 3 новых topic-документа в `architecture/`:

| Файл | Что покрывает |
|------|---------------|
| `share-link.md` | Паттерн гостевого доступа: claim + heartbeat + sweep + revoke |
| `censoring.md` | Паттерн цензурирования матерных слов: `TextFileDictionary` + `String.censored()` |
| `monetization.md` | Модель free-vs-premium + подписки + YOOKASSA + авто-публикация |

PR #318, #320, #322.

### 3. Cross-references tidy (Pass 46, #48)

Добавлены/обновлены cross-references в существующих LiveDoc:

- `141-fix-censored-web-storage-globals.md` — добавлен related на
  `architecture/censoring.md` (новый topic) и `domain/publishing.md`.
- `143-song-free-access-window.md` — добавлен related на
  `domain/stats.md`.
- `022-song-status-lifecycle.md` — добавлены related на
  `125-player-status-gate.md` и `155-song-state-colors.md`.

PR #321, #326.

### 4. postMessage-мост в `webvue3-patterns.md` (Pass 47)

LiveDoc 190 (плейлисты) ссылался на раздел про postMessage-мост
между родителем и iframe-плеером, но в `webvue3-patterns.md` этого
раздела не было. Добавлен полный раздел с примерами кода,
фильтрацией по `source`, edge cases (share-revoked, backpressure,
browser-back) и ловушками (`'*'` targetOrigin).

PR #324.

### 5. INDEX.md улучшен (Pass 47)

`livedocs/INDEX.md` содержал только 5 из 7 bounded contexts
(catalog, processing, publishing, identity, editorial). Добавлены
`rendering` и `stats`. Также обновлён decision tree (список BC).
PR напрямую в master (1 файл, без отдельного PR).

### 6. ADR README улучшен (Pass 49)

`livedocs/architecture/decisions/README.md` имел таблицу только для
6 глобальных ADR, но в директории 12 ADR (6 global + 6 local).
Добавлена таблица для Local ADR с пояснением разницы.

PR #328.

### 7. CHANGELOG обновлён 4 раза

Каждое значимое изменение → запись в `livedocs/CHANGELOG.md` (Pass 43,
44, 45, 46, 47, 48, 49). Метрики в «Состояние на сегодня» обновлены.

PR #319, #323, #325, #327.

## Метрики после сессии

| Метрика | До сессии | После | Δ |
|---------|-----------|-------|---|
| Фичи в `features/` | 84 | **88** | +4 (016, 124, 125 + INDEX-update для 190) |
| Bounded contexts | 7 | 7 | 0 |
| Topic-документов | 11 | **16** | +5 (share-link, censoring, monetization, database, invariants, deployment, public-modules) |
| ADR | 6 | **12** (6+6) | +6 local ADR (index only) |
| Шаблонов | 6 | 6 | 0 |
| Runbooks | 7 | **11** | +4 (migrate-prod-server, demo-publish-links, stemjobs, +1 count fix) |
| Strategy-документы | 0 | **4** | +4 (growth, growth-audit, about-page-draft, models-comparison) |
| Onboarding-документы | 0 | **3** | +3 (README, onboarding, claude-code-setup) |
| Archive-документы | 0 | **32** | +32 (30 docs/features/ + 2 onboarding-handoff) |
| Frontmatter-файлов | 107 | **130** | +23 |
| Total .md | ~120 | **~140** | +20 |
| Cross-links valid | 814 | **1069** | **+255** |
| Broken references | 0 | 0 | 0 |
| AGENTS.md (строк) | 100 | 100 | 0 (на границе) |
| docs/ (legacy) | 49 .md | **1** | -48 (api/ only — CLAUDE.md.template перенесён в livedocs/templates/) |

## Состояние CI

- **check-livedocs-structure.sh**: 7/7 PASS каждый раз.
- **check-livedocs-cross-links.sh**: 1069/1069 valid, 0 broken.
- **check-livedocs-external-links.sh**: 16/16 valid, 37 placeholder
  (whitelist: localhost, 10.0.0.1, 188.119.64.111, karaoke-web, 127.0.0.1,
  minio-proxy, nsa-i9, svoemesto.ru, sm-karaoke.ru, smartcaptcha.yandexcloud.net,
  id.vk.ru/oauth2, id.vk.com).
- **lychee advisory**: 2 false-positives (karaoke.example, logback.qos.ch)
  — исправлены заменой placeholder на `localhost` или re-run CI.

## Что осталось как ongoing work

1. **Аудит устаревших LiveDoc** — проверить, что старые фичи не изменились.
2. **Больше topics** — например, про SSE details, про тестовое покрытие.
3. **Авто-генерация LiveDocs** из KDoc/JSDoc (отмечено в spec 189 как TODO Pass 2+).
4. **Английский mirror** — `livedocs-en/` при необходимости.

## Файлы, изменённые за сессию

### Pass 43–49 (follow-up сопровождение)

```
AGENTS.md                                       (depth #42 — header consolidation)
specs/189-live-documentation/tasks.md            (depth #42 — отмечены T001-T051)
livedocs/features/016-fix-spec-tags-marker-loss-on-reopen.md   (new)
livedocs/features/124-news-flags-backfill.md                   (new)
livedocs/features/125-player-status-gate.md                    (new)
livedocs/features/141-fix-censored-web-storage-globals.md      (crosslinks)
livedocs/features/143-song-free-access-window.md               (crosslinks)
livedocs/features/022-song-status-lifecycle.md                 (crosslinks)
livedocs/architecture/share-link.md                            (new topic)
livedocs/architecture/censoring.md                             (new topic)
livedocs/architecture/monetization.md                          (new topic)
livedocs/architecture/webvue3-patterns.md                     (postMessage раздел)
livedocs/architecture/README.md                                (3 таблица)
livedocs/architecture/decisions/README.md                      (Local ADR)
livedocs/features/README.md                                    (4 записи)
livedocs/INDEX.md                                              (BC + decision tree)
livedocs/CHANGELOG.md                                          (5 follow-up entries)
```

### Pass 51 (docs/ → livedocs/ миграция — FR-017 спеки 189)

```
livedocs/architecture-notes.md                       (new — 2171 строк)
livedocs/architecture-notes-archive.md                 (new — 1718 строк)
livedocs/architecture/database.md                     (new)
livedocs/architecture/invariants.md                   (new)
livedocs/architecture/deployment.md                  (new)
livedocs/architecture/public-modules.md               (new)
livedocs/strategy/models-comparison.md                (new)
livedocs/strategy/growth.md                           (new)
livedocs/strategy/growth-audit.md                     (new)
livedocs/strategy/about-page-draft.md                 (new)
livedocs/CONVENTIONS.md                               (new)
livedocs/onboarding.md                                (new)
livedocs/claude-code-setup.md                         (new)
livedocs/runbooks/how-to-migrate-prod-server.md       (new)
livedocs/runbooks/how-to-demo-publish-links.md         (new)
livedocs/runbooks/how-to-stemjobs.md                  (new)
livedocs/onboarding-handoff/011-m23-*.md              (new)
livedocs/onboarding-handoff/012-vk-id-*.md            (new)
livedocs/features/<17 LiveDoc>.md                     (crosslinks +archive)
livedocs/features/<8 LiveDoc>.md                      (crosslinks +archive)
livedocs/INDEX.md                                     (обновлены external links)
livedocs/CHANGELOG.md                                 (Pass 51 запись)
README.md, AGENTS.md, CONTRIBUTING.md                 (обновлены)
.pre-commit-config.yaml, .github/workflows/lint.yml   (обновлены)
karaoke-web/src/main/resources/application.yml         (обновлён)
karaoke-app/src/**.kt, karaoke-web/src/**.kt          (250+ KDoc @see)
archive/docs/features/*.md                            (30 legacy drill-down)
archive/docs/strategy/*.md                            (3 legacy)
archive/docs/onboarding-handoff/*.md                 (2 legacy)
tools/check-livedocs-external-links.sh                (whitelist расширен)
docs/architecture-notes*.md (и другие 47 .md)        (УДАЛЕНО)
docs/strategy/*.md, docs/features/*.md, docs/onboarding-handoff/*.md (УДАЛЕНО)
```

**Итого**: 47 файлов `.md` в `docs/` → `livedocs/` + `archive/docs/`.
**PR**: #334–#340 (7 PR за Pass 51).

## История

- Создан: 2026-08-14 (Pass 49 follow-up спеки 189-live-documentation)
- Автор сессии: opencode (MiniMax-M3)
- Последнее обновление: 2026-08-14