# Implementation Plan: Предварительная подготовка превью перед публикацией в ВК

**Branch**: `130-vk-preview-generation` | **Date**: 2026-08-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/130-vk-preview-generation/spec.md`

## Summary

Перед финальным вызовом VK API для новой публикации песни `karaoke-app` синхронно прогревает существующий публичный endpoint изображения `karaoke-web` (`/api/public/song-vk-image/{id}`). Прогрев считается успешным только после HTTP 200 и проверки ненулевого корректного PNG без follow-redirect. После успеха сохраняется существующий `wall.post`/video flow; при ошибке пост не создаётся, а существующее состояние `SEND_FAILED` получает диагностическую ошибку с префиксом `preview prewarm failed:`.

Решение применяется к общему `VkAutoPublishService` для AIR и PREMIUM, не меняет текст, ссылку или VK attachments, не добавляет таблиц/колонок и сохраняет идемпотентность. На стороне `karaoke-web` запись PNG в `/tmp/vk_<id>.png` выполняется атомарно, чтобы параллельный запрос не прочитал частичный файл.

## Technical Context

**Language/Version**: Kotlin 1.x, JDK 17, Gradle multi-module; существующий Java HTTP Client и стандартные средства работы с PNG

**Primary Dependencies**: Spring Boot существующих модулей, `java.net.http.HttpClient`, `ImageIO`; новых внешних зависимостей не требуется

**Storage**: PostgreSQL через сырой JDBC для существующего `Song.saveToDb()`/`player_readiness_flags`; MinIO остаётся источником album/author картинок; эфемерный PNG-кэш находится на диске контейнера `karaoke-web` в `/tmp/vk_<id>.png`

**Testing**: JUnit 5 офлайн-тесты нового HTTP-помощника с локальным test server или подменяемым transport; ручная end-to-end проверка живого `karaoke-web` и VK-поста; обязательные Gradle compile/lint и per-feature documentation checks

**Target Platform**: Linux/Docker; `karaoke-app` на admin-машине, `karaoke-web` на production-like web-среде; агент не перезапускает `karaoke-app` по ограничениям проекта

**Project Type**: Многомодульный backend web-service с отдельным публичным web-модулем и существующей фоновой очередью автопубликации

**Performance Goals**: один prewarm-запрос не дольше 30 секунд, не более 2 попыток по умолчанию; после успешного прогрева повторное получение PNG должно идти по кэшированному пути и укладываться в целевой p95 менее 1 секунды на production-like окружении; публикация не должна начинаться до получения готового изображения

**Constraints**: не следовать HTTP-редиректам; при невалидном/пустом PNG, 3xx, 4xx/5xx после retry или сетевом тайм-ауте не вызывать `wall.post`; не добавлять секреты в код/логи; не вводить новые DB-колонки, миграции или sync targets; не менять формат существующего VK-поста; сохранять режим отката через `vkPreviewWarmupEnabled=false`

**Scale/Scope**: один admin-процесс и малый поток VK-публикаций с существующим лимитом порядка нескольких постов в час; AIR/PREMIUM-публикации песен с известным `songId`; один PNG на песню; исторические посты и legacy-новости без связанной песни не исправляются автоматически

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Pre-Phase 0

| Principle | Status | Проверка |
|---|---|---|
| I. Self-contained автопайплайн | PASS | Prewarm обращается к собственному `karaoke-web`, а не к SaaS для обработки медиа. VK API уже является существующей частью публикации; изменение проверяет явно предложенную пользователем последовательность. |
| II. Сырой JDBC + diff по хэшам | PASS | Состояние сохраняется существующим `Song.saveToDb()` в `player_readiness_flags`; новый HTTP-помощник не вводит другого DB-слоя. |
| III. SyncRegistry и две БД | PASS | Новых сущностей и колонок нет; существующие `Song.idVk`/JSON-флаги продолжают работать по текущему sync flow. |
| IV. Async-очередь задач | PASS с ограничением | Prewarm — короткий ограниченный сетевой precondition существующего VK flow, а не новый ffmpeg/MLT/MinIO subprocess. Render MP4 по-прежнему выполняется через `KaraokeProcess`; без unbounded wait. |
| V. Разделение admin/public frontend | PASS | Изменение backend-only; `webvue3` и `karaoke-public` не смешиваются. |
| VI. Code Standards | PASS при выполнении плана | Новые публичные symbols должны получить KDoc/JSDoc с `@see`; per-feature документ VK обновляется в том же PR. |
| VII. Cross-Machine Setup | PASS | Конфигурация добавляется в общий механизм `KaraokeProperties`; персональные AI-конфиги и ветка master не затрагиваются. |
| VIII. Секреты и git-гигиена | PASS | URL публичного веба не секрет; VK tokens не меняются и не логируются; новые настройки не содержат credentials. |

Все ворота проходят; нерешённых `NEEDS CLARIFICATION` после Phase 0 не осталось.

## Research Summary

Полные решения и альтернативы находятся в [research.md](./research.md). Ключевые выводы:

- прогревать нужно `/api/public/song-vk-image/{id}`, а не `/api/public/og/song`;
- успех определяется HTTP 200 и валидным PNG при отключённых redirect-follow;
- ошибка прогрева блокирует публикацию и записывается через существующий `SEND_FAILED`;
- отдельная БД-сущность для результата не нужна;
- атомарная замена web-кэша закрывает гонку чтения частично записанного PNG;
- офлайн-тесты покрывают HTTP-решения, живой VK остаётся ручной проверкой.

## Implementation Design

### 1. Настройки prewarm

В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt` добавить четыре обычных свойства:

- `vkPreviewWarmupEnabled=true`;
- `vkPreviewWarmupUrl=https://sm-karaoke.ru/api/public/song-vk-image/`;
- `vkPreviewWarmupTimeoutMs=30000`;
- `vkPreviewWarmupMaxAttempts=2`.

Свойства должны быть доступны существующему Properties UI/API, не содержать токенов и быть описаны в `docs/features/vk-news-auto-publish.md`.

### 2. HTTP-помощник прогрева

Создать внутренний сервис в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkPreviewWarmupClient.kt`.

Сервис должен:

1. Собрать URL из настроенного base URL и `songId` без ручного добавления секретов.
2. Выполнить GET с ограниченным connect/read timeout и `Redirect.NEVER`.
3. Повторить только временные сетевые/5xx ошибки не более настроенного числа попыток.
4. Прочитать тело, проверить ненулевой размер, MIME и корректность PNG через стандартный JDK-механизм.
5. Вернуть внутренний результат с успехом либо фазой/причиной ошибки, HTTP-кодом, попытками и длительностью.
6. Не писать в логи тело ответа, query-секреты или токены.

При `vkPreviewWarmupEnabled=false` сервис должен вернуть диагностический bypass-result, позволяющий сохранить аварийный откат к старому flow. В обычной конфигурации значение по умолчанию включено.

### 3. Встраивание в VK-публикацию

Изменить `VkAutoPublishService.kt` в финальных путях `publishTextOnly` и `publishFile`:

- оставить существующие проверки `idVk`, premium-флага, готовности песни и demo-MP4 до финального этапа;
- перед `client.wallPost`/`client.sendPostWithVideo` выполнить prewarm;
- при ошибке записать `SEND_FAILED` через существующий `writeFailure`, вернуть `VkAutoPublishResult` с `postId=null` и префиксом `preview prewarm failed:`;
- только после prewarm выполнять текущую VK-публикацию и существующее сохранение `idVk`/`newsPremiumVkSent`;
- при ошибке VK после успешного prewarm оставить текущую VK-ошибку отдельно от ошибки изображения.

Для `publishFile` прогрев выполняется непосредственно перед существующим комбинированным `video.save` → upload → `wall.post` flow. Это гарантирует готовность PNG к моменту `wall.post`, не меняя механизм вложения видео.

Редкий `publishNewsWithoutVideo` без связанного `songId` сохраняет legacy-поведение, поскольку для него нет корректного URL превью песни. Если scheduler уже может определить id песни из ссылки, общий helper может быть вызван до публикации без изменения формата сообщения.

### 4. Идемпотентность и конкурентность

В `VkAutoPublishService` добавить process-local lock keyed by `song.id`, общий для начального вызова и `onRenderCompleted`:

- повторный вызов после заполненного `idVk` сразу возвращает текущий `PUBLISHED`;
- вызов, пока уже поставлен `RENDERING`, не создаёт второй render process;
- только владелец критической секции может перейти к prewarm и VK-публикации;
- callback после рендера повторно проверяет `idVk` и premium-флаг перед публикацией;
- lock не хранится в БД и очищается после завершения операции.

Это покрывает одновременные вызовы в единственном процессе `karaoke-app`; межпроцессная распределённая блокировка не входит в scope первой версии.

### 5. Атомарный web-кэш

В `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt` сохранить существующие размеры/формат/имя кэша, но заменить прямую запись `writeBytes` на безопасную последовательность:

- сформировать PNG полностью в памяти как сейчас;
- записать во временный файл в той же директории;
- атомарно заменить целевой `/tmp/vk_<id>.png`, с безопасным fallback на обычную замену, если ОС не поддерживает `ATOMIC_MOVE`;
- при чтении существующего файла отклонять пустой/повреждённый кэш и генерировать заново.

`PublicOgSongController` и nginx rewrite не меняются: они уже указывают на тот же публичный URL картинки.

### 6. Тесты

Добавить офлайн-тесты для нового helper в `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/VkPreviewWarmupClientTest.kt` с локальным JDK HTTP server или подменяемым HTTP transport:

- корректный PNG и HTTP 200;
- редирект без follow и ошибка;
- 404/5xx и ограниченные retry;
- тайм-аут;
- пустое/повреждённое тело;
- успешный повторный вызов.

Финальное «пост не создан при ошибке prewarm» и наличие превью в VK проверяются ручным quickstart-сценарием, так как текущие VK-классы не покрыты CI-безопасными интеграционными тестами.

### 7. Документация

В том же изменении кода обновить `docs/features/vk-news-auto-publish.md`:

- описать prewarm как обязательный шаг перед `wall.post`;
- перечислить настройки и поведение при ошибке;
- зафиксировать 302/fallback, эфемерный `/tmp`-кэш и ограничение VK cache;
- добавить ссылку на `specs/130-vk-preview-generation/`.

`research.md`, `data-model.md`, `contracts/vk-preview-warmup.md` и `quickstart.md` являются design artifacts этой спеки; `tasks.md` создаётся отдельной фазой `/speckit.tasks`.

## Phase 1 Design Artifacts

- [Data model](./data-model.md) — временный результат prewarm, существующие state/flags, переходы и отсутствие DB migration.
- [HTTP contract](./contracts/vk-preview-warmup.md) — запрос, критерии готового PNG, failure matrix и ordering.
- [Quickstart](./quickstart.md) — ручные AIR/PREMIUM, failure, idempotency, VK separation и команды проверки.

## Project Structure

### Documentation (this feature)

```text
specs/130-vk-preview-generation/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── vk-preview-warmup.md
├── checklists/
│   └── requirements.md
└── tasks.md
```

### Source Code (repository root)

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── KaraokeProperties.kt
└── services/
    ├── VkAutoPublishService.kt
    ├── VkAutoPublishScheduler.kt
    ├── VkAutoPublishState.kt
    ├── VkAutoPublishResult.kt
    └── VkPreviewWarmupClient.kt

karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/
└── VkPreviewWarmupClientTest.kt

karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/
└── PublicApiController.kt

docs/features/
└── vk-news-auto-publish.md
```

**Structure Decision**: Изменение остаётся в существующих `karaoke-app` service flow и `karaoke-web` public image controller. Новый HTTP helper локализован в `karaoke-app/services`, состояние переиспользует `Song.player_readiness_flags`, а контракт и тестовые материалы живут в каталоге спеки. `webvue3`, `karaoke-public`, SQL-миграции и новый VK API endpoint не требуются.

## Complexity Tracking

Нарушений Конституции нет; отдельные строки для оправдания нарушений не применяются. Process-local lock и четыре runtime-настройки — минимальное расширение для требований конкурентности, диагностируемости и отката.

## Constitution Check (Post-Phase 1 Design)

| Principle | Status | Итоговая проверка дизайна |
|---|---|---|
| I. Self-contained автопайплайн | PASS | Используется внутренний вызов между текущими `karaoke-app` и `karaoke-web`; новые SaaS-зависимости и новый внешний media API отсутствуют. VK API flow остаётся существующим и явно входит в пользовательскую задачу. |
| II. Сырой JDBC + diff по хэшам | PASS | Нет нового DB access layer; `Song.saveToDb()` обновляет только существующие поля/JSON-блоб через текущий механизм. |
| III. Двух-БД синхронизация | PASS | Нет новых сущностей, колонок, миграций, `SyncRegistry` targets или recordhash-триггеров. |
| IV. Async-очередь | PASS | MP4-render не изменяется и остаётся в `KaraokeProcess`; prewarm имеет bounded timeout/retry и является коротким сетевым precondition финального вызова, а не новой media-processing задачей. |
| V. Два фронтенда | PASS | Frontend не меняется; backend flow не смешивает admin/public SPA. |
| VI. Code Standards | PASS при реализации | Новый публичный API не планируется; новые public symbols, если появятся, получат KDoc с `@see docs/features/vk-news-auto-publish.md`; per-feature документ входит в deliverables. |
| VII. Cross-Machine Setup | PASS | Связь admin→prod настраивается существующим Properties-механизмом; персональные конфиги не добавляются в git. |
| VIII. Секреты и git-гигиена | PASS | Новые параметры — публичный URL, timeout, retry и boolean; токены не меняются, не выводятся и не попадают в документацию/код. |

Post-design gate проходит. Реализация может переходить к `/speckit.tasks`.
