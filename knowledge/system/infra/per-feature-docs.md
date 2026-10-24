# Per-feature docs (docs/features/)

> **Домен**: system (infrastructure)
> **Компонента**: каталог 7 per-feature документов в `docs/features/`.

## Назначение

`docs/features/<slug>.md` — **per-feature документы** для каждой
ключевой фичи (FR-009 из Constitution VI). Это **канонический
SSoT** для per-feature описания. **НЕ дублируется** в
`knowledge/` — Knowledge ссылается на них.

## Каталог (7 документов)

| Файл | Строк | Slug | OpenProject | FR |
|---|---|---|---|---|
| `idempotent-path-sanitize.md` | 167 | Санитайзер путей | #53 | FR-001..FR-014 |
| `pagination-filter-admin-tables.md` | 178 | Фильтры таблиц | — | — |
| `tracker-local-integration.md` | 177 | Локальная интеграция с трекером | — | — |
| `search-text-extract-btn.md` | 154 | Кнопка извлечения текста | — | — |
| `process-bulk-actions.md` | 118 | Bulk-операции с процессами | — | — |
| `song-edit-and-censored.md` | 110 | Редактирование + цензура | — | — |
| `zakroma-tiles-sort-order.md` | 93 | Порядок тайлов «Закрома» | — | — |

**NB**: 7 (не 12 как было сказано ранее). Все < 200 строк.

## Структура (FR-009 + Constitution VI)

Каждый per-feature документ имеет 6 обязательных секций
(проверяется `tools/check-feature-doc.sh`, см. [ci-tools.md](ci-tools.md)):

- `## Что делает` — краткое описание.
- `## Зачем` — обоснование.
- `## Как работает` — детальный алгоритм.
- `## Инварианты` — что MUST быть неизменным.
- `## Известные ловушки` — что может сломаться.
- `## Ссылки` — KDoc на код + OpenProject + спеку.

Plus (опционально):

- `## Где находится` — где код.
- `## Контракт (FR-001..FR-014)` — полный FR-список.
- `## Тесты` — какие тесты.

**Отсутствующий документ — ошибка.** `tools/check-feature-doc.sh` (guard
`check-feature-doc`, CI-джоб `docs-lint`) при недоступном файле печатает
`ERROR` **и завершается с кодом 1**: раньше код возврата терялся, счётчик
ошибок не рос, и гейт рапортовал успех, не проверив ни одного документа
(аудит #336, OpenProject #379, BR-572).

## Связь с Knowledge

Knowledge (`knowledge/domains/*/components/*.md`) **ссылается** на
per-feature docs, **не дублирует** их. Это соглашение (Pass 343+).

## Известные TODO

- [ ] **`docs/features/`** — может быть больше (12 в [governance-docs.md](governance-docs.md))?
- [ ] **FR-009** — compliance всех per-feature docs (Pass 343+)?

## Changelog

- **OpenProject #379** (2026-10-24, аудит #336, BR-572): отсутствующий
  per-feature документ учитывается как ошибка — `check-feature-doc.sh`
  завершается с кодом 1 (раньше — `ERROR` в выводе и exit 0).
- **Pass 380** (2026-09-09): Initial. Автор: agent (Karaoke).