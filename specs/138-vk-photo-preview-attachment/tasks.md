# Tasks: Надёжное превью публикации ВК через прикрепление обложки фото

**Input**: Design documents from `/specs/132-vk-photo-preview-attachment/`
**Prerequisites**: plan.md (required), spec.md (required), data-model.md, contracts/vk-photo-upload.md, quickstart.md, research.md

**Tests**: Тестов нет (см. `AGENTS.md` «Тесты»: существующие тесты — `@Disabled`, полагаться на них нельзя). Валидация — ручная через `quickstart.md`.

**Branch**: `138-vk-photo-preview-attachment` (зарезервировано через `./tools/reserve-branch-number.sh vk-photo-preview-attachment` — следующий свободный номер оказался 138, т.к. номера 132-137 заняты параллельными сессиями на других машинах)
**MVP scope**: Phase 1 + Phase 2 + Phase 3 (User Story 1) — это уже даёт рабочее превью через `photos.*`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно запустить параллельно (разные файлы, нет зависимостей).
- **[Story]**: к какой user story относится задача (US1, US2, US3, US4, US5).
- В описании — точный путь к файлу.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: резервирование имени ветки и создание feature-ветки.

- [x] T001 Зарезервировать номер ветки и создать ветку `138-vk-photo-preview-attachment` через `./tools/reserve-branch-number.sh vk-photo-preview-attachment` в `/home/nsa/Karaoke` (получили номер 138)
- [x] T002 [P] Прочитать существующий код `VkApiClient.kt` и `VkAutoPublishService.kt` в `/home/nsa/Karaoke/karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/` для понимания структуры HTTP-вызовов и потока публикации (через `codegraph_explore`)

**Checkpoint**: ветка создана, контекст существующего кода загружен — можно начинать Foundational.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: блокирующие задачи, которые ДОЛЖНЫ быть готовы до любой user story. Без них ни одна US не сможет работать: новые ключи конфигурации, общие data-классы, расширение PNG endpoint, multipart HTTP helper.

**⚠️ CRITICAL**: ни одна user story не может начаться, пока эта фаза не завершена.

- [X] T003 Добавить 4 новых ключа в `/home/nsa/Karaoke/karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt`: `vkPhotoAttachEnabled=true` (Boolean), `vkDocAttachEnabled=true` (Boolean), `vkPreviewImageWidth=1200` (Integer), `vkPreviewImageHeight=630` (Integer) — по образцу существующих `vkPreviewWarmup*` ключей (specs/130). Геттеры — тоже по образцу. KDoc на каждый ключ со ссылкой на `docs/features/vk-news-auto-publish.md`.
- [X] T004 [P] Создать `/home/nsa/Karaoke/karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkPhotoUploadClient.kt` (заготовка): enum `PhotoUploadMethod { PHOTOS, DOCS, NONE }`, data class `PhotoUploadResult(method: PhotoUploadMethod, attachment: String?, errorCode: Int?, errorMessage: String?)`, suspend-метод `uploadCover(songId: Long, pngBytes: ByteArray): PhotoUploadResult` (пока что пустая реализация, возвращает `PhotoUploadResult(NONE, null, null, null)`). KDoc на класс со ссылкой на `docs/features/vk-news-auto-publish.md` и перекрёстной ссылкой на `contracts/vk-photo-upload.md`.
- [X] T005 [P] Расширить endpoint `/api/public/song-vk-image/{id}` в `/home/nsa/Karaoke/karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt` — заменить параметры `frameW=537, frameH=240` на `frameW=KaraokeProperties.getInt("vkPreviewImageWidth", 1200), frameH=KaraokeProperties.getInt("vkPreviewImageHeight", 630)`. Использовать существующий геттер `KaraokeProperties.getInt(...)` (по образцу других параметров в этом же файле). Никаких других изменений в endpoint (логика прогрева, atomic write, проверка PNG magic-signature остаются как в specs/130).
- [X] T006 Добавить общий HTTP helper `multipartPost(uploadUrl: String, fieldName: String, fileName: String, contentType: String, bytes: ByteArray): Map<String, String>` в `/home/nsa/Karaoke/karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkApiClient.kt` (для шагов POST multipart на `upload_url` от VK — используется как в `photos.*`, так и в `docs.*`). Реализация: использует существующий `java.net.http.HttpClient` (см. `sendRequest` в этом же файле), формирует `multipart/form-data` с одним полем (filename=`<songId>.png`, content-type=`image/png`), парсит JSON-ответ в `Map<String, String>`. KDoc со ссылкой на `contracts/vk-photo-upload.md`.

**Checkpoint**: foundation готов — keys доступны, VkPhotoUploadClient существует, PNG endpoint расширен, multipart helper готов. Можно начинать user stories.

---

## Phase 3: User Story 1 — Пост бота ВКонтакте содержит графическое превью (Priority: P1) 🎯 MVP

**Goal**: при публикации поста бот загружает PNG-обложку через `photos.*` методы VK API и прикрепляет её к посту через `attachments=photo<owner>_<id>` (плюс видео, если есть).

**Independent Test**: запустить `POST /api/song/publishToVkNow?id=<id>&type=air` для песни с пустым `Song.idVk`. Дождаться `state=PUBLISHED`. Открыть пост в группе ВКонтакте → он должен содержать большую картинку-превью (сниппет), а не «голый» текст.

### Implementation for User Story 1

- [X] T007 [US1] Добавить метод `getWallUploadServer(groupId: Long, userToken: String): String` в `/home/nsa/Karaoke/karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkApiClient.kt`. Вызывает `POST https://api.vk.ru/method/photos.getWallUploadServer` с параметрами `group_id`, `access_token`, `v=5.199`. Возвращает `response.upload_url`. KDoc со ссылкой на `contracts/vk-photo-upload.md#1-photosgetwalluploadserver`. Throw `VkApiException` при `error_code in 27/15/5/29` (non-retryable авторизация) или `5xx/timeout` (retry).
- [X] T008 [US1] Добавить метод `saveWallPhoto(server: Int, photoJson: String, hash: String, groupId: Long, userToken: String): PhotoAttachment` в `/home/nsa/Karaoke/karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkApiClient.kt`. Вызывает `POST https://api.vk.ru/method/photos.saveWallPhoto`. Возвращает `PhotoAttachment(id=<id>, ownerId=<owner_id>, attachment="photo<owner>_<id>", loadMethod=PHOTOS)`. KDoc со ссылкой на `contracts/vk-photo-upload.md#3-photossavewallphoto`.
- [X] T009 [US1] Реализовать PHOTOS-ветку в `VkPhotoUploadClient.uploadCover(...)` в `/home/nsa/Karaoke/karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkPhotoUploadClient.kt`: (1) проверить `KaraokeProperties.vkPhotoAttachEnabled`; (2) вызвать `VkApiClient.getWallUploadServer(groupId, userToken)`; (3) вызвать `VkApiClient.multipartPost(uploadUrl, "photo", "<songId>.png", "image/png", pngBytes)` → получить `Map<String, String>`; (4) вызвать `VkApiClient.saveWallPhoto(server, photo, hash, groupId, userToken)`; (5) вернуть `PhotoUploadResult(PHOTOS, attachment, null, null)`. Throw `VkPhotoUploadException` при любой ошибке (для retry/fallback логики в Фазе 4). KDoc со ссылкой на `contracts/vk-photo-upload.md`.
- [X] T010 [US1] Расширить существующий метод `wall.post` в `/home/nsa/Karaoke/karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkApiClient.kt` (или его wrapper) — добавить параметр `photoAttachment: String?` (по умолчанию `null`). Формировать `attachments` в порядке: сначала `photo<owner>_<id>` (если есть), потом `video<owner>_<video_id>` (если есть). Если оба есть — `"photo...,video..."`. Если только видео — `"video..."`. Если только фото — `"photo..."`. Без изменений retry/backoff (используется существующая обёртка).
- [X] T011 [US1] Интегрировать `VkPhotoUploadClient.uploadCover` в `/home/nsa/Karaoke/karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkAutoPublishService.kt` — вставить новый шаг между существующим `VkPreviewWarmupClient.warmup(songId)` (шаг 5) и существующим `VkApiClient.sendPostWithVideo(...)` (шаг 7). Передавать `pngBytes` из warmup в upload. При успехе — передавать `photoAttachment` в wall.post. Использовать существующий `songLocks[song.id]` (расширить lock на новый шаг). Логировать успех: `"фото для песни ${songId} загружено через ${method}: ${attachment}"`.
- [X] T012 [US1] Добавить логирование в `VkAutoPublishService` в `/home/nsa/Karaoke/karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkAutoPublishService.kt`: при ошибке `uploadCover` — записать в `vkAutoPublishLastError` префикс `photo upload failed:` + причина. Сохранять существующий переход в `SEND_FAILED` (используется существующий helper `markFailed`). НЕ вызывать fallback на этом шаге — fallback в Фазе 6 (US4).

**Checkpoint**: User Story 1 работает. Пост создаётся с прикреплённым фото. Если `photos.*` упадёт с ошибкой, пост НЕ создаётся (это исправляется в Phase 4/6).

---

## Phase 4: User Story 2 — Безопасная обработка ошибки загрузки фото (Priority: P1)

**Goal**: при сбое `photos.*` методов бот корректно обрабатывает ошибку: retry transient (5xx/timeout), non-retryable → переход к fallback (US4) или `SEND_FAILED` с понятным описанием.

**Independent Test**: подменить `vkUserAccessToken` на невалидный через Properties UI. Запустить публикацию. Проверить: `state=SEND_FAILED`, `vkAutoPublishLastError` начинается с `photo upload failed:`, `Song.idVk` остался пустым.

### Implementation for User Story 2

- [X] T013 [US2] Добавить retry-логику (3 попытки с backoff 30с→2мин→5мин) для transient-ошибок в `VkApiClient.getWallUploadServer`, `saveWallPhoto` и `multipartPost` в `/home/nsa/Karaoke/karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkApiClient.kt`. Использовать существующий retry-helper (если есть) или существующий паттерн (см. существующий `sendPostWithVideo` — там уже реализован retry). Non-retryable коды: `4, 5, 15, 27, 100, 29` (последний — rate limit, возвращается через backoff). KDoc со ссылкой на `specs/121-vk-news-auto-publish` (FR-009 — retry-политика).
- [X] T014 [US2] Добавить классификацию ошибок в `VkPhotoUploadClient.uploadCover` в `/home/nsa/Karaoke/karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkPhotoUploadClient.kt`: различать `VkAuthException` (code=27/15/5 — триггер fallback на US4) и `VkTransientException` (5xx/timeout/429 — retry внутри метода) и `VkInvalidParamsException` (code=100 — без retry, сразу FAILED). Throw соответствующего подкласса.
- [X] T015 [US2] Расширить обработку ошибок в `VkAutoPublishService.publishFile` в `/home/nsa/Karaoke/karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkAutoPublishService.kt`: при `VkAuthException` из uploadCover — НЕ ставить `SEND_FAILED` сразу, а позволить US4 (Phase 6) попробовать fallback. При `VkTransientException` после исчерпания retry — ставить `SEND_FAILED` с префиксом `photo upload failed: <тип>` (например, `photo upload failed: 5xx after 3 retries`). При `VkInvalidParamsException` — сразу `SEND_FAILED` с префиксом `photo upload failed: invalid params (code 100)`. Сохранять `Song.idVk` пустым.

**Checkpoint**: ошибки `photos.*` обрабатываются без ложных `SEND_FAILED` для recoverable ошибок.

---

## Phase 5: User Story 3 — Сохранение существующего поведения идемпотентности (Priority: P1)

**Goal**: все ранее реализованные инварианты автопубликации (идемпотентность по `Song.idVk`, process-local lock, rate-limit, retry wall.post, прогрев PNG) продолжают работать без регрессий.

**Independent Test**: запустить повторную публикацию песни, у которой `Song.idVk` заполнен → пост НЕ создаётся. Параллельно запустить два `publishToVkNow` для одной песни → ровно один пост.

### Implementation for User Story 3

- [X] T016 [US3] Сохранить проверку `Song.idVk.isBlank()` в начале `VkAutoPublishService.publishToVk` в `/home/nsa/Karaoke/karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkAutoPublishService.kt` (БЕЗ ИЗМЕНЕНИЙ — инвариант из specs/121, FR-008). Добавить unit-комментарий в KDoc: «Если `idVk` не пуст — никакие шаги (включая новый uploadCover из US1) не выполняются». Никакого нового кода, только documentation assertion.
- [X] T017 [US3] Расширить существующий `songLocks: ConcurrentHashMap<Long, Any>` в `/home/nsa/Karaoke/karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkAutoPublishService.kt` — убедиться, что lock берётся на весь диапазон шагов: проверка `idVk` → прогрев PNG → загрузка фото (NEW) → `wall.post`. Если lock уже покрывает весь `publishFile` целиком — достаточно проверить и добавить unit-комментарий. Если lock берётся внутри отдельных шагов — добавить взятие lock на уровне `publishFile`. KDoc обновить со ссылкой на US3.
- [X] T018 [US3] Сохранить rate-limit (3 поста/час) в `/home/nsa/Karaoke/karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkAutoPublishService.kt`: убедиться, что счётчик rate-limit инкрементируется РОВНО ОДИН РАЗ на успешный `wall.post` (после загрузки фото ИЛИ без неё — не важно). Никакого нового кода; только unit-комментарий.
- [X] T019 [US3] Сохранить retry-логику `wall.post` (3 попытки с backoff 30с→2мин→5мин) в `/home/nsa/Karaoke/karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkApiClient.kt` — без изменений. Убедиться, что при неуспехе всех 3 попыток `wall.post` НЕ загружает фото повторно (фото уже в VK; повторять только `wall.post`). Никакого нового кода; только unit-комментарий.

**Checkpoint**: идемпотентность, lock, rate-limit и retry работают как раньше. Новый шаг загрузки фото не сломал ничего.

---

## Phase 6: User Story 4 — Fallback через документ-картинку (Priority: P2)

**Goal**: при `error_code=27/15/5` от `photos.*` методов бот пробует `docs.*` методы; если и они не сработали — пост создаётся без фото (деградация), но НЕ полный отказ.

**Independent Test**: подменить `vkUserAccessToken` на токен без scope `photos`. Запустить публикацию. Проверить логи — должна быть попытка `docs.*` методов. Если community-token валиден — пост создаётся с прикреплением-документом.

### Implementation for User Story 4

- [X] T020 [US4] Добавить метод `getDocWallUploadServer(communityToken: String): String` в `/home/nsa/Karaoke/karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkApiClient.kt`. Вызывает `POST https://api.vk.ru/method/docs.getWallUploadServer` с `access_token`, `v=5.199`. Возвращает `response.upload_url`. KDoc со ссылкой на `contracts/vk-photo-upload.md#4-docsgetwalluploadserver`. Throw `VkApiException` аналогично T007.
- [X] T021 [US4] Добавить метод `saveWallDoc(fileJson: String, title: String, communityToken: String): DocAttachment` в `/home/nsa/Karaoke/karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkApiClient.kt`. Вызывает `POST https://api.vk.ru/method/docs.save` с `file`, `title`, `access_token`, `v=5.199`. Возвращает `DocAttachment(id=<id>, ownerId=<owner_id>, attachment="doc<owner>_<id>", loadMethod=DOCS)`. KDoc со ссылкой на `contracts/vk-photo-upload.md#6-docssave`.
- [X] T022 [US4] Реализовать DOCS-ветку (fallback) в `VkPhotoUploadClient.uploadCover` в `/home/nsa/Karaoke/karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkPhotoUploadClient.kt`: добавить catch-блок для `VkAuthException` после PHOTOS-ветки. Внутри: (1) проверить `KaraokeProperties.vkDocAttachEnabled`; (2) если `false` — прокинуть `VkAuthException` дальше; (3) если `true` — вызвать `VkApiClient.getDocWallUploadServer(communityToken)`; (4) вызвать `VkApiClient.multipartPost(uploadUrl, "file", "<songId>.png", "image/png", pngBytes)` (тот же helper, другое имя поля); (5) вызвать `VkApiClient.saveWallDoc(file, "<songId>.png", communityToken)`; (6) вернуть `PhotoUploadResult(DOCS, attachment, null, null)`. Если DOCS-ветка тоже упала с `VkAuthException` — throw `VkBothAttachFailedException(photosError, docsError)`. KDoc со ссылкой на US4.
- [X] T023 [US4] Добавить обработку `VkBothAttachFailedException` в `VkAutoPublishService.publishFile` в `/home/nsa/Karaoke/karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkAutoPublishService.kt`: НЕ ставить `SEND_FAILED`; вместо этого — записать `vkAutoPublishLastError` с префиксом `photo attach failed: photos=27 docs=27` (пример), `attachment=null`, и продолжить `wall.post` БЕЗ прикреплённого фото (только видео, если есть). Это **деградация**, не отказ. Существующий код `wall.post` (из T010) уже умеет работать без `photoAttachment`.
- [X] T024 [US4] Обновить `wall.post` из T010 в `/home/nsa/Karaoke/karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkApiClient.kt`: добавить логирование для трёх сценариев attachments — `"both photo and video"`, `"photo only"`, `"video only (no photo, degradation)"`. Использовать существующий logger. Никаких изменений retry/backoff.

**Checkpoint**: fallback работает. Пост создаётся даже при полном сбое `photos.*` + `docs.*` (но в деградированном виде).

---

## Phase 7: User Story 5 — Размер и качество превью-картинки (Priority: P2)

**Goal**: PNG-обложка имеет размер 1200×630 px (стандарт Open Graph), генерируется endpoint `/api/public/song-vk-image/{id}` с обновлёнными параметрами.

**Independent Test**: открыть `https://sm-karaoke.ru/api/public/song-vk-image/<id>` → убедиться через `curl -s ... | file -` что PNG имеет размер 1200×630. Запустить публикацию → в ВК должна загрузиться картинка 1200×630.

### Implementation for User Story 5

- [X] T025 [US5] В `/home/nsa/Karaoke/karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt` (тот же endpoint, что и T005) — убедиться, что `KaraokeProperties.getInt("vkPreviewImageWidth", 1200)` и `KaraokeProperties.getInt("vkPreviewImageHeight", 630)` возвращают `1200` и `630` соответственно. Удалить старые hard-coded `frameW=537, frameH=240` (если остались). KDoc обновить: «размер берётся из `vkPreviewImageWidth/Height` (default 1200×630, Open Graph)».
- [X] T026 [P] [US5] В `/home/nsa/Karaoke/karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkPreviewWarmupClient.kt` — проверить, что warmup-клиент НЕ зашивает размер жёстко (использует `KaraokeProperties.getInt("vkPreviewImageWidth/Height")` или просто читает endpoint без проверки размера). Если зашивает — обновить на `1200×630` по умолчанию. Если уже использует properties — добавить unit-комментарий со ссылкой на US5.
- [X] T027 [P] [US5] В `/home/nsa/Karaoke/karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkPreviewWarmupClient.kt` — сохранить проверку PNG magic-signature (первые 8 байт = `\x89PNG\r\n\x1a\n`) после прогрева. Если файла нет или сигнатура неверная — throw `VkPreviewWarmupException`. Никаких изменений логики, только assertion в KDoc: «размер превью 1200×630 px контролируется в PublicApiController, прогрев только проверяет magic-signature».

**Checkpoint**: PNG 1200×630 генерируется endpoint'ом, warmup-клиент работает с новым размером, фото в VK загружается в правильном размере.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: документация, линтеры, ручная валидация.

- [X] T028 [P] Обновить `/home/nsa/Karaoke/docs/features/vk-news-auto-publish.md`: добавить новую секцию «Превью через photos.saveWallPhoto» с FR-001..FR-017 (перекрёстные ссылки на spec.md); скорректировать секцию «Как работает» — вставить шаг 6 (загрузка фото) между прогревом и `wall.post`; добавить секцию «Известные ловушки» — про `photos.*` user-token scope и fallback на `docs.*`. Ссылка на `specs/132-vk-photo-preview-attachment/`.
- [X] T029 [P] Обновить `/home/nsa/Karaoke/docs/features/README.md` — если меняется список фич (сейчас 11 + 1) или описание существующей записи для `vk-news-auto-publish.md` — добавить краткое упоминание о добавленном фото-превью. Если без изменений — пропустить.
- [ ] T030 [P] Прогнать вручную 5 сценариев из `/home/nsa/Karaoke/specs/132-vk-photo-preview-attachment/quickstart.md` (сценарии 1-5): успешная публикация с превью, fallback docs.*, полная деградация, идемпотентность, параллельная публикация. Задокументировать результат в лог-файле (или комментарии PR).  
  ⚠️ Не выполнено в этой сессии (требует admin-машины / gradle на dev-pc / реального VK API). Требует проверки пользователем.
- [ ] T031 Запустить `./gradlew ktlintCheck` в `/home/nsa/Karaoke` — убедиться, что baseline не вырос (новый код в стиле `VkApiClient` / `VkAutoPublishService`). Если ktlint ругается — поправить.  
  ⚠️ Не выполнено в этой сессии (требует admin-машины / gradle на dev-pc / реального VK API). Требует проверки пользователем.
- [ ] T032 [P] Запустить `bash /home/nsa/Karaoke/tools/check-kdoc-coverage.sh` и `bash /home/nsa/Karaoke/tools/check-jsdoc-coverage.sh /home/nsa/Karaoke/webvue3` — оба должны быть зелёными (100%). Новые классы (`VkPhotoUploadClient`) и методы должны быть покрыты KDoc.  
  ⚠️ Не выполнено в этой сессии (требует admin-машины / gradle на dev-pc / реального VK API). Требует проверки пользователем.
- [ ] T033 Создать commit на ветке `132-vk-photo-preview-attachment` с сообщением по стилю проекта: `feat(vk): attach photo preview to wall.post via photos.saveWallPhoto (with docs.* fallback)`. **НЕ** пушить и **НЕ** создавать PR — это делает пользователь (см. `AGENTS.md` «CI-gate для master»).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: нет зависимостей — можно начать сразу.
- **Foundational (Phase 2)**: зависит от Setup — БЛОКИРУЕТ все user stories.
- **User Stories (Phase 3-7)**: все зависят от Foundational. Stories выполняются **последовательно** в порядке P1 → P1 → P1 → P2 → P2 (фазы 3, 4, 5, 6, 7), потому что:
  - Phase 3 (US1) — базовая загрузка через `photos.*`. Без неё Phase 4/6/7 не могут быть проверены.
  - Phase 4 (US2) — retry/классификация ошибок. Зависит от методов Phase 3.
  - Phase 5 (US3) — идемпотентность. Зависит от Phase 3 (нужен код, который должен быть идемпотентным).
  - Phase 6 (US4) — fallback на `docs.*`. Зависит от Phase 4 (классификация ошибок) и Phase 3 (методы VK).
  - Phase 7 (US5) — размер PNG. Зависит от Phase 2 (T005 уже сделал PNG endpoint).
- **Polish (Phase 8)**: зависит от всех story-фаз.

### User Story Dependencies

- **US1 (P1)**: можно начать после Foundational — нет зависимостей от других stories.
- **US2 (P1)**: можно начать после Foundational — нужны методы из US1 для классификации ошибок.
- **US3 (P1)**: можно начать после Foundational — не зависит от US1/US2 логически (только проверки, что уже существующий код работает).
- **US4 (P2)**: можно начать после US1 + US2 (нужны методы и классификация ошибок).
- **US5 (P2)**: можно начать после Foundational — независимая от других stories (только endpoint).

### Within Each User Story

- T007, T008, T010 (VkApiClient методы) → T009 (VkPhotoUploadClient PHOTOS-ветка) → T011 (интеграция) → T012 (логирование).
- T013 (retry) → T014 (классификация) → T015 (обработка в VkAutoPublishService).
- T016, T017, T018, T019 — независимые проверки (можно параллельно).
- T020, T021 (VkApiClient docs.* методы) → T022 (DOCS-ветка) → T023 (обработка в VkAutoPublishService) → T024 (логирование).
- T025 (endpoint) → T026, T027 (warmup-клиент — можно параллельно).

### Parallel Opportunities

- T003, T004, T005 — параллельно (разные файлы, нет зависимостей).
- T006 (multipart helper) → после T003, T004, T005 (но T003-T005 не зависят от T006).
- T016, T017, T018, T019 (Phase 5) — все параллельно (разные аспекты существующего кода, не пересекаются).
- T020, T021 (Phase 6) — параллельно (оба метода в VkApiClient, но в разных местах).
- T026, T027 (Phase 7) — параллельно (оба в VkPreviewWarmupClient, но разные методы).
- T028, T029, T030, T032 (Phase 8) — параллельно (разные файлы).

---

## Parallel Example: Phase 3 (User Story 1)

```bash
# Запустить параллельно (в разных файлах):
Task T007: "Добавить getWallUploadServer в VkApiClient.kt"
Task T008: "Добавить saveWallPhoto в VkApiClient.kt"
Task T010: "Расширить wall.post с photoAttachment в VkApiClient.kt"

# Затем последовательно:
Task T009: "Реализовать PHOTOS-ветку в VkPhotoUploadClient.kt"
Task T011: "Интегрировать uploadCover в VkAutoPublishService.kt"
Task T012: "Логирование ошибок с префиксом photo upload failed:"
```

## Parallel Example: Phase 5 (User Story 3)

```bash
# Все 4 задачи — независимые проверки существующего кода:
Task T016: "Проверить проверку Song.idVk.isBlank() в VkAutoPublishService"
Task T017: "Проверить songLocks покрывает весь диапазон"
Task T018: "Проверить rate-limit 3 поста/час"
Task T019: "Проверить retry wall.post 30с→2м→5м"
```

---

## Implementation Strategy

### MVP First (Phases 1-3)

1. Завершить Phase 1: Setup (T001-T002).
2. Завершить Phase 2: Foundational (T003-T006).
3. Завершить Phase 3: User Story 1 (T007-T012).
4. **STOP и VALIDATE**: запустить публикацию → проверить, что в ВК появился пост с большим превью-фото (сценарий 1 из quickstart.md).
5. Деплой/демо, если готово.

**MVP scope** = Phases 1 + 2 + 3 (12 задач). Это уже даёт рабочее превью через `photos.*`. Остальные фазы — robustness (retry, fallback, размер PNG).

### Incremental Delivery

1. Setup + Foundational → Foundation ready.
2. Добавить User Story 1 (Phase 3) → протестировать → **это MVP**.
3. Добавить User Story 2 (Phase 4) → retry/классификация ошибок. Тестировать отдельно: подменить токен → убедиться, что `SEND_FAILED` с правильным префиксом.
4. Добавить User Story 3 (Phase 5) → проверка идемпотентности. Тестировать: повторная публикация / параллельная.
5. Добавить User Story 4 (Phase 6) → fallback docs.*. Тестировать: подменить токен → убедиться, что fallback сработал.
6. Добавить User Story 5 (Phase 7) → размер PNG. Тестировать: открыть endpoint → убедиться, что 1200×630.
7. Polish (Phase 8) → документация, линтеры, ручная валидация.

Каждая фаза добавляет ценность, не ломая предыдущие.

### Parallel Team Strategy

С одним разработчиком (или одним AI-агентом) — последовательно по фазам. С двумя:
- Dev A: Phases 1 + 2 + 3 + 5 (базовая функциональность + идемпотентность).
- Dev B: Phases 4 + 6 + 7 (обработка ошибок + fallback + размер PNG).
- Sync на Phase 8 (документация, валидация).

---

## Notes

- [P] задачи = разные файлы, нет зависимостей.
- [Story] метка маппит задачу на конкретную user story для трассировки.
- Каждая user story должна быть независимо завершаемой и тестируемой (но Phase 4/6 требуют код из Phase 3 — это естественная зависимость).
- Никаких тестов — проект не использует unit-тесты (см. `AGENTS.md` «Тесты»). Валидация — ручная через `quickstart.md`.
- Коммит после каждой задачи или логической группы.
- Остановиться на любой фазе для валидации story независимо.
- Избегать: расплывчатых задач, конфликтов в одном файле, кросс-story зависимостей, ломающих независимость.
- **НЕ** пушить и **НЕ** создавать PR — пользователь делает это сам после CI 7/7 SUCCESS (см. `AGENTS.md` «CI-gate для master»).
- Все упоминания файлов — абсолютные пути в `/home/nsa/Karaoke/`.