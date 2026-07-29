# Implementation Plan: Расширенный жизненный цикл статусов готовности песни

**Branch**: `022-song-status-lifecycle` | **Date**: 2026-07-29 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/022-song-status-lifecycle/spec.md`

## Summary

Поле `tbl_songs.id_status` (0..3 по факту сейчас, "готова" = >=3) расширяется
до 7 значений (0..6) с новым смыслом: 0 новая → 1 текст найден → 2 текст
проверен (орфография/пунктуация) → 3 текст проверен (слова соответствуют
песне) → 4 маркеры расставлены → 5 маркеры проверены → 6 готова. Существующие
песни `id_status>=3` мигрируют на 6 (одна SQL-миграция, LOCAL+SERVER). Порог
«готова» везде на публичной поверхности (karaoke-web, karaoke-public,
общий read-путь `PublicPlayerController.stemsReady`/`StatBySong`) меняется с
`>=3` на `>=6`. В SongEdit (webvue3) кнопки выбора статуса (сейчас 0,1,2,3,4,6,
без 5) получают новые подписи вместо унаследованных от старого,
неиспользуемого workflow "легаси MLT-рендера" (`group-button-legacy` на 4 и
6), и добавляется недостающая кнопка 5. Каждое из ~20 мест в
`karaoke-app`/`karaoke-web`, где код меняет `id_status` программно
(успешный поиск текста, forced-align маркеры, апрув crowd-редактора и т.д.),
аудируется и приводится к новой семантике; автоматические переходы обязаны
двигать статус строго на 1 шаг вперёд, никогда не понижая его (FR-010/011
spec.md).

**Ключевая находка Phase 0**: значения 4 и 6 уже заняты в коде другим,
явно помеченным «легаси» смыслом — старым MLT-пайплайном полного рендера
видео для соцплатформ (`Song.createKaraoke()`, `KaraokeProcess.
updateStatusProcessSettings`, `Song.state`/`SettingState.EXCLUSIVE*`). Эта
логика никогда не пересекалась с публичной готовностью сайта (используется
только в админских инструментах — цвет строки в таблице, дайджест-шаблоны),
поэтому переиспользование чисел 4/6 под новый смысл безопасно, но требует
явно **обезвредить** автоматический побочный эффект `idStatus==4L → "6"` в
`KaraokeProcess.kt` (иначе он будет незаконно перепрыгивать через этап 5
«маркеры проверены» в нарушение FR-011) и убрать побочный эффект выставления
статуса из `Song.createKaraoke()` (Song.kt:5417) — рендер видео для
соцплатформ более не должен трогать статус готовности контента.

## Technical Context

**Language/Version**: Kotlin 2.x / JDK 17 (Spring Boot 3.x, модули
`karaoke-app` и `karaoke-web`); Vue 3 + Vite (JS, без TypeScript) в `webvue3`.

**Primary Dependencies**: без новых зависимостей. Переиспользуется
существующий generic-фильтр `id_status` в `Song.getWhereList`
(поддерживает `>=`/`<` и т.п.), существующий JSON-based
`playerReadinessFlags` механизм (`deploy/karaoke-db/26_player_readiness_flags.sql`,
`Song.kt` readinessFlag/setReadinessFlag) и существующий `recordhash`-триггер
(id_status уже входит в хэш — новая колонка не добавляется, миграция
триггеров не требуется).

**Storage**: PostgreSQL через сырой JDBC (`KaraokeConnection`). Существующая
колонка `tbl_songs.id_status integer DEFAULT 0` (индексирована,
`tbl_songs_id_status_index`); новая SQL-миграция
`deploy/karaoke-db/32_song_status_lifecycle.sql` — один `UPDATE ... WHERE
id_status >= 3`, без DDL.

**Testing**: в CI автотестов для этого слоя нет (см. Конституцию — тесты
`karaoke-app/src/test` в основном `@Disabled`). Проверка — вручную по
`quickstart.md`, включая ручной прогон миграции на sandbox-копии БД.

**Target Platform**: Linux Docker — `karaoke-app` (только admin-машина),
`karaoke-web` (прод-сервер), `webvue3` (admin SPA), `karaoke-public` (не
меняется — рендерит уже отфильтрованные бэкендом данные).

**Project Type**: web-service (backend Kotlin + admin SPA); миграция БД —
общая для LOCAL и SERVER (двухбазовая синхронизация).

**Performance Goals**: без деградации — `id_status` уже индексирована той же
колонкой, что использовалась при пороге `>=3`; смена константы на `>=6` не
меняет план выполнения запроса.

**Constraints**: миграция обязана быть идемпотентной (повторный запуск не
должен ничего портить — `UPDATE ... SET id_status=6 WHERE id_status>=3`
идемпотентен по построению) и не должна требовать пересоздания
`recordhash`-триггеров (колонка не добавляется/не удаляется). Автоматические
переходы статуса не должны регрессировать/перепрыгивать этапы (FR-010,
FR-011 spec.md).

**Scale/Scope**: ~20 мест программной установки `id_status` (`karaoke-app`),
~15 мест чтения `id_status>=3`/`<3` как признака готовности (`karaoke-app`,
`karaoke-web`, `webvue3`), 1 SQL-миграция, обновление статус-лейблов и кнопок
в `SongEdit.vue`, обновление 2-3 per-feature документов (FR-009
Конституции).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип | Статус | Комментарий |
|---|---|---|
| I. Self-contained автопайплайн | ✅ N/A | Фича не добавляет внешних API в горячий путь обработки медиа — только меняет числовую семантику существующего поля и то, как её читают/пишут уже существующие пайплайны (Whisper forced-align, поиск текста и т.п.). |
| II. Сырой JDBC + дифф по хэшам | ✅ PASS | Миграция — обычный SQL `UPDATE` через `deploy/karaoke-db/*.sql` (как `27_author_special_order.sql`), не ORM. Никаких новых O(n²) сравнений — используется тот же `id_status`-фильтр, что уже есть в `Song.getWhereList`. |
| III. Двух-БД синхронизация через SyncRegistry | ✅ PASS (ключевой gate) | `id_status` уже часть `tbl_songs`, уже в `SyncRegistry`/`recordhash`. Новых колонок нет → пересоздавать `recordhash`-триггер не требуется. Миграционный `UPDATE` обязан быть применён **отдельно на LOCAL и на SERVER** (FR-003 spec.md) — так же, как `27_author_special_order.sql` (см. его шапку «Один раз на LOCAL и PROD отдельно»). |
| IV. Async-очередь задач | ✅ N/A | Изменение статуса — синхронная запись поля БД внутри существующих обработчиков (`saveToDb()`), не новая долгая операция. Существующие `KaraokeProcess`-побочные эффекты (auto-align, MELT-рендер) не меняют модель очереди, только условие простановки статуса. |
| V. Двух-фронтенд: админка и публичный сайт — разные приложения | ✅ PASS | Порог готовности `>=6` вводится **только** в публичных read-путях `karaoke-web`/`karaoke-public`-backing endpoints и в admin-инструментах, которые сами показывают тот же публичный плеер (`SongsTable.vue` иконка «Открыть онлайн-плеер» — намеренно синхронизируется с публичным порогом, иначе админ увидит неверный статус доступности). Правки статус-кнопок — только в `webvue3` (SongEdit), `karaoke-public` не трогается. |
| VI. Code Standards | ⚠️ ACTION REQUIRED | Изменённые публичные функции (`Song.status`, `Song.createKaraoke`, `KaraokeProcess.updateStatusProcessSettings`, `StatBySong.CONTENT_READY_FILTER`, компоненты `SongEdit.vue`) уже задокументированы KDoc/JSDoc — обновить текст на новую семантику, не добавлять новые public API без документации. `docs/features/*.md` обновляются per FR-009 (список — см. Project Structure). |
| VII. Cross-Machine Setup | ✅ N/A | Фича не трогает AI-конфиги/git-атрибуты. |

**Нарушений, требующих секции «Complexity Tracking», нет** — расширение
enum-подобной семантики существующего поля не добавляет новых
модулей/паттернов доступа к данным.

**Post-Phase 1 re-check**: `data-model.md` подтверждает, что новых сущностей
и колонок нет (Principle III — `recordhash` не трогается, только данные);
`contracts/song-status-lifecycle.md` фиксирует, что меняется поведение уже
существующих endpoint'ов (принимаемый диапазон `idStatus`, применяемый
порог), а не их форма; правки строго разделены по модулям (Principle V).
Все статусы Constitution Check остаются прежними — gate пройден.

## Project Structure

### Documentation (this feature)

```text
specs/022-song-status-lifecycle/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md         # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
│   └── song-status-lifecycle.md
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
deploy/karaoke-db/
└── 32_song_status_lifecycle.sql       # UPDATE tbl_songs SET id_status=6
                                        # WHERE id_status>=3 — LOCAL и SERVER
                                        # отдельно (Constitution Principle III)

karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── model/Song.kt                      # status (лейблы 0-6 под новый смысл);
│                                       # createKaraoke() — убрать побочный
│                                       # эффект idStatus<3→3 (FR-009); при
│                                       # необходимости readinessFlag-связанный
│                                       # код без изменений сигнатур
├── KaraokeProcess.kt                  # updateStatusProcessSettings —
│                                       # убрать/нейтрализовать побочный
│                                       # эффект idStatus==4L→"6" (нарушает
│                                       # FR-011: перепрыгивает этап 5)
├── Utils.kt                           # executeForcedAlignMarkers (idStatus
│                                       # >=3 return false → >=6; idStatus<2→
│                                       # "2" — свериться с FR-011),
│                                       # applyDuplicateOriginal/
│                                       # applyAudioParentMarkers/
│                                       # applyFamilySongSelection (аудит
│                                       # целевых значений "1"/"3" под FR-009),
│                                       # findAudioParentByWaveform-соседний
│                                       # код (root_id=0 AND id_status<3 —
│                                       # НЕ менять бездумно, см. data-model.md)
├── UtilsAI.kt                         # applyFoundLyricsIfMissing — без
│                                       # изменений (0→1 остаётся верным)
├── controllers/ApiController.kt       # doProcessForcedAlignMarkers/
│                                       # getSongsCreateForcedAlignMarkersAll
│                                       # (idStatus<3 → <6 гейт постановки в
│                                       # очередь), selectFamilySong-соседний
│                                       # applyAudioParentMarkers-вызов,
│                                       # generic songs2Update (idStatus?.let)
│                                       # без изменений — уже принимает любое
│                                       # значение
├── controllers/SongEditorController.kt # editSave (апрув crowd-задания) —
│                                       # idStatus<3→"3" меняется на целевое
│                                       # значение под новую семантику (см.
│                                       # research.md Decision 6)
├── controllers/MainController.kt      # legacy Thymeleaf createkaraoke/
│                                       # searchtext — синхронизировать с
│                                       # Utils.kt/ApiController.kt правками
├── model/HealthReport.kt              # deleteSearchResultsForReadySongs —
│                                       # id_status>=3 → >=6
├── ExportAlignmentDataset.kt          # id_status>=3 → >=6 (более строгий и
│                                       # более правильный фильтр для ML-
│                                       # датасета — только реально
│                                       # финализированные маркеры)
└── model/Publication.kt               # idStatus<3L/<4L в date-range
                                        # хелперах — аудит, не автоматическая
                                        # замена (см. research.md Decision 7)

karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/
├── StatBySong.kt                      # CONTENT_READY_FILTER: id_status>=3 → >=6
├── controllers/PublicPlayerController.kt # stemsReady(): idStatus>=3 → >=6
├── controllers/PublicApiController.kt # onlyPublishedFor(...): ">=3" → ">=6"
├── controllers/MainController.kt      # attr["id_status"]=">=3" → ">=6"
└── services/StatsCacheScheduler.kt    # комментарии, без изменения кода

karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Zakroma.kt
                                        # onlyPublished: args["id_status"]=">=3" → ">=6"

webvue3/src/components/Songs/
├── edit/SongEdit.vue                  # блок "Статус" (шаблон ~1937-1998):
│                                       # новые подписи для кнопок 0,1,2,3,4,6;
│                                       # + кнопка 5; убрать
│                                       # group-button-legacy/"Легаси"-тултипы;
│                                       # setStatus() — новые текстовые лейблы;
│                                       # кнопка "Точные маркеры"
│                                       # (:disabled="idStatus>=3") → >=4 (не
│                                       # порог публичной готовности, а гейт
│                                       # "маркеры уже расставлены" — см.
│                                       # research.md Decision 6)
├── edit/FamilySongsModal.vue          # fsm-row-low-status: idStatus<3 → <6
└── SongsTable.vue                     # player/playerDemo идStatus>=3 → >=6;
                                        # assign idStatus<3 → <6 (crowd-
                                        # редактирование доступно, пока не
                                        # "готова")

docs/features/
├── llm-lyrics-search.md               # обновляется (FR-009): applyFound
│                                       # LyricsIfMissing/execute* не меняют
│                                       # смысл 0→1, зафиксировать новую
│                                       # 7-значную шкалу целиком
├── mlt-generator.md                   # обновляется (FR-009): фиксирует,
│                                       # что createKaraoke() больше не
│                                       # трогает id_status
└── stats.md (или dual-db-sync.md)     # обновляется (FR-009): новое
                                        # определение «готова» (>=6) вместо
                                        # (>=3)
```

**Structure Decision**: Backend-изменения — только в существующих модулях
`karaoke-app` (model/controllers/Utils) и `karaoke-web` (controllers/
services); одна новая SQL-миграция в `deploy/karaoke-db/`. Фронтенд-правки —
только `webvue3` (админка); `karaoke-public` не меняется (потребляет уже
отфильтрованные бэкендом данные). Новых модулей/директорий не создаётся.

## Complexity Tracking

*Нарушений Constitution Check нет — секция не заполняется.*
