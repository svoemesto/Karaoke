# Отчёт по #141 — Синхронизация аудио-потомков

> **Спека**: [spec.md](spec.md) | **Ветка**: `413-sync-audio-descendants` |
> **PR**: [svoemesto/Karaoke#516](https://github.com/svoemesto/Karaoke/pull/516) (MERGED, `789a272d`) |
> **Дата**: 2026-09-19.

## Что сделано

Реализован механизм «Синхронизация аудио-потомков» по OpenProject #141.

- **Backend** (`karaoke-app`):
  - новый `SyncAudioDescendants.kt` — гейт по diff, очередь с дедупликацией,
    single-flight воркер, перенос контента (`syncAudioDescendantFromParent`),
    массовый `syncAll()`;
  - хук в `Song.saveToDb()` (после UPDATE) и `Song.saveToDbLocked()` (после
    `commit()`, с подавлением каскада через ThreadLocal `suppressTriggers`);
  - эндпоинт `POST /api/utils/syncaudioparents`.
- **Frontend** (`webvue3`): кнопка «Синхронизировать аудио-потомков (Custom Function)»
  + модалка на `HomeView.vue`, Vuex action `syncAudioParentsPromise`.
- **Knowledge/docs**: новый компонент
  `knowledge/domains/catalog/components/audio-descendant-sync.md`; обновлены
  `domain.md`, `dictionaries.md`, `song-lifecycle.md`, `song-entity.md`;
  per-feature `docs/features/sync-audio-descendants.md`.
- **Тесты**: `SyncAudioDescendantsTest.kt` — 9 unit-тестов гейта/очереди.

## Принятые решения (wayfinder, 28 узлов)

- Ворота: родитель `idStatus ≥ 5`; при `<5` любые правки потомков не трогают.
- Триггер: content-diff **или** переход `<5 → 5/6`. Переход 5→6 повторно не гоняет.
- Точка перехвата: общий save-путь `Song` с гейтом по diff (иначе ~80 фоновых
  `saveToDb` дали бы ложные сверки).
- Для типа «Песня» маркеры обязательны; `instrumental`/`poetry` — пустые допустимы.
- Перенос набора `applyAudioParentMarkers` + свежие метрики/history + `.srt`.
- Повторная аудио-сверка, порог 95 %; ниже — пропуск с логом.
- Статус потомка → 5; только прямые дети, без рекурсии; активный `KaraokeProcess` — пропуск.
- Очередь с дедупликацией + общий single-flight; только LOCAL-БД.
- Admin: перебор потомков с группировкой по родителю, фоновый поток, SSE-сводка.

## Проверки

| Проверка | Результат |
|---|---|
| `:karaoke-app:compileKotlin`, `:karaoke-web:compileKotlin` | OK |
| `:karaoke-app:ktlintCheck` | OK |
| `:karaoke-app:test --tests '*SyncAudioDescendantsTest*'` | 9/9 PASS |
| `webvue3 npm run lint` / `npm run build` | OK |
| `:karaoke-app:bootJar`, `:karaoke-web:bootJar` | OK |
| `check-feature-doc.sh` | OK |
| `lint-knowledge.py --baseline` | PASS (new violations отсутствуют) |
| `check-knowledge-structure.sh` | 9/9 OK |
| `check-ssot-impact.py` | OK |
| `check-no-jpa-imports.sh`, `check-docker-image-tags.sh` | OK |
| CI PR #516 | 12/12 pass |

Дополнительно: baseline R-11 (`check-no-mp4-mentions.baseline`) пересчитан —
добавление эндпоинта сдвинуло line-номера legacy-упоминаний; новых нет.

## Известные ограничения / ручная валидация

- Интеграционные сценарии `quickstart.md` (1-8) требуют admin-машины с реальными
  данными (пара родитель↔потомок) — выполняет владелец.
- `karaoke-app` контейнер перезапускает владелец (машинное правило nsa-i9).
- Выравнивание прода — существующим two-DB sync (механизм пишет только LOCAL).

## Файлы

- `karaoke-app/.../SyncAudioDescendants.kt`, `.../model/Song.kt`,
  `.../controllers/ApiController.kt`, `.../test/.../SyncAudioDescendantsTest.kt`
- `webvue3/src/views/HomeView.vue`, `webvue3/src/components/Songs/store.js`
- `knowledge/domains/catalog/{domain.md,components/*}`
- `docs/features/sync-audio-descendants.md`
- `specs/413-sync-audio-descendants/*`
