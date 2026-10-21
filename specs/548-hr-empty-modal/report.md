# Report: Форма HR при нуле записей в репорте (OpenProject #325)

**Issue**: OpenProject #325
**Branch**: `548-hr-empty-modal`
**Дата**: 2026-10-21

## Проблема

Если у песни 0 записей в Health Report (HR), то:

- в таблице песен клик по ячейке HR «ничего не делает» — модалка открывается и
  мгновенно закрывается;
- в `SongEdit` кнопка HR недоступна (`disabled`);
- даже в открытой модалке кнопка «Сбросить кеш» недоступна, потому что
  `songId` брался из первой записи списка.

Сбросить кеш хранилища для такой песни из UI было нельзя, хотя сброс работает
по id песни и от записей HR не зависит.

## Причина

1. `HealthReportTable.vue` закрывал модалку вотчером, как только
   `healthReportList.length === 0`. А `/api/song/healthReportList` отдаёт только
   ошибки (`errorsOnly()`, `ApiController.kt:7774-7786`) — у здоровой песни
   список пуст.
2. `SongEdit.vue` — `:disabled="songHealthReports.length === 0"` на кнопке HR.
3. `HealthReportTableHeader.vue` — `songId` вычислялся как
   `healthReportList[0].songId`, а кнопка «Сбросить кеш» была `disabled` при
   пустом списке.

## Решение

Фронтенд-only, без изменений бэкенда (`POST /api/song/resetStorageCache`
работает по `ids`, `ApiController.kt:7904-7935`):

- **`HealthReportTable.vue`** — вотчер автозакрытия удалён; id песни передаётся
  в шапку пропом `songId`; список предыдущей песни очищается при открытии,
  чтобы не показывать чужие строки; `id` принимает `String | Number`
  (родители передают число).
- **`HealthReportTableHeader.vue`** — новый проп `songId` (фолбэк на список),
  `effectiveSongId`; кнопка «Сбросить кеш» больше не `disabled` при пустом
  списке. `Repair All` при нуле по-прежнему недоступна (нечего исправлять).
- **`HealthReportTableBody.vue`** — состояния «Загрузка…» и «Записей Health
  Report нет — нарушений не найдено» вместо пустого тела.
- **`store.js`** — геттер `getHealthReportListIsLoading`; флаг загрузки
  сбрасывается в `finally` (иначе при ошибке навсегда оставался бы «Загрузка…»).
- **`SongEdit.vue`** — убрано `:disabled` у кнопки HR.

### Документация

- `docs/features/storage-metadata-cache.md` — заметка «Ноль записей HR».
- `knowledge/domains/storage/domain.md` — то же в разделе «Ручной сброс кеша».
- `python3 tools/lint-knowledge.py` — OK.

## Проверки

| Проверка | Результат |
|---|---|
| `:karaoke-app:compileKotlin :karaoke-web:compileKotlin` | OK |
| `:karaoke-web:ktlintCheck` | OK |
| `:karaoke-web:bootJar`, `:karaoke-app:bootJar` | OK |
| `webvue3`: `npm run lint` | OK (exit 0) |
| `webvue3`: `npm run build` | OK (vite, 7.9 s) |
| `webvue3`: `npm run format:check` | OK |
| `karaoke-public`: `npm run lint` / `build` / `format:check` | OK |
| `tools/check-jsdoc-coverage.sh` | 98.1% (≥50%) |
| `tools/lint-knowledge.py` | All checks pass |
| `deploy/do.sh build_webvue3` | OK (образ `karaoke-webvue3:1`) |

## Что проверено в живом UI

Не проверено на запущенном контейнере: изменение фронта попадёт в
`localhost:7906` только после пересборки образа и перезапуска
`karaoke-webvue3` (согласие владельца по матрице AGENTS.md). Сценарий проверки:

1. Открыть песню с 0 записей HR (колонка HR = `0`) в таблице песен → клик по
   ячейке: модалка остаётся открытой, показывает «Записей Health Report нет»,
   кнопка «Сбросить кеш» активна.
2. В `SongEdit` кнопка «Health Report (0)» активна, модалка ведёт себя так же.
3. Нажать «Сбросить кеш» → запрос `POST /api/song/resetStorageCache` уходит с
   id песни, список перезапрашивается.

## Ограничения

- Автотестов на компоненты HR в `webvue3` нет (нет unit-инфраструктуры для
  админ-компонентов; e2e Playwright покрывает редактор). Регрессия закрыта
  ручным сценарием выше.
