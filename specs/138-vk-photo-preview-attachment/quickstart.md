# Quickstart: Проверка работы превью через прикрепление обложки фото

**Branch**: `132-vk-photo-preview-attachment` | **Date**: 2026-08-04
**Spec**: [spec.md](./spec.md) | **Contract**: [contracts/vk-photo-upload.md](./contracts/vk-photo-upload.md)

## Назначение

5 ручных сценариев, которые подтверждают, что фича работает end-to-end
на admin-машине. Сценарии покрывают: успешную публикацию с превью,
обработку ошибок `photos.*`, fallback на `docs.*`, деградацию при полном
сбое, сохранение идемпотентности.

## Предусловия

Перед запуском любого сценария убедиться:

- [ ] `karaoke-app` собран и запущен на admin-машине.
- [ ] `karaoke-web` собран и запущен на проде (или локально для теста).
- [ ] В `Karaoke.properties` (admin-машина, `/sm-karaoke/system/Karaoke.properties`) настроено:
  - [ ] `vkAutoPublishEnabled = true`
  - [ ] `vkGroupId = <ID группы без минуса>`
  - [ ] `vkAccessToken = <Community access token>` (права `wall`, `video`, `photos`)
  - [ ] `vkUserAccessToken = <User access token>` (scopes `video,photos,wall,offline`) — **см. `docs/features/vk-news-auto-publish.md`, раздел «User-token через Implicit Flow»**
  - [ ] `vkProxyUrl` — если admin-машина за firewall (опционально)
  - [ ] `vkPreviewWarmupEnabled = true`
  - [ ] `vkPhotoAttachEnabled = true` (новый, default `true`)
  - [ ] `vkDocAttachEnabled = true` (новый, default `true`)
  - [ ] `vkPreviewImageWidth = 1200` (новый, default `1200`)
  - [ ] `vkPreviewImageHeight = 630` (новый, default `630`)
- [ ] В MinIO есть обложка альбома (`{author}/{year} - {album}/{author-year-album}.album.png`) и обложка автора (`{author}/{author}.author.png`) для тестовой песни.
- [ ] Тестовая песня имеет статус ≥ 6 и `Song.isContentReady = true`.
- [ ] Тестовая песня имеет **пустое** `Song.idVk` (не была опубликована раньше).

## Сценарий 1 — Успешная публикация с превью (FR-001, SC-001)

**Цель**: проверить, что пост в группе ВКонтакте содержит графическое превью.

**Шаги**:

1. Выбрать тестовую песню с пустым `Song.idVk` (например, `id=23217`).
2. Убедиться, что endpoint `/api/public/song-vk-image/23217` возвращает PNG 1200×630:
   ```bash
   curl -sI "https://sm-karaoke.ru/api/public/song-vk-image/23217"
   # Ожидаем: HTTP/2 200, Content-Type: image/png, Content-Length: ~100-300 КБ
   # Проверить размер:
   curl -s "https://sm-karaoke.ru/api/public/song-vk-image/23217" | file -
   # Ожидаем: PNG image data, 1200 x 630
   ```
3. Запустить публикацию:
   ```bash
   curl -s -X POST "http://localhost:8898/api/song/publishToVkNow" -d "id=23217&type=air"
   # Ожидаем JSON с success=true, state=PUBLISHED, postId="-<groupId>_<postId>"
   ```
4. Проверить логи `karaoke-app`:
   ```
   VkAutoPublishService.publishToVk: ...
   VkPreviewWarmupClient.warmup: status=SUCCESS, bytes=~200000
   VkPhotoUploadClient.uploadCover: photos.getWallUploadServer OK
   VkPhotoUploadClient.uploadCover: POST upload_url OK
   VkPhotoUploadClient.uploadCover: photos.saveWallPhoto OK, photo=photo-<groupId>_<photoId>
   VkApiClient.sendPostWithVideo: video.save OK
   VkApiClient.wallPost: success, post_id=-<groupId>_<postId>
   ```
5. Открыть группу ВКонтакте в браузере — найти новый пост. Должен содержать:
   - Текст поста.
   - URL на страницу песни.
   - **Большую картинку-превью** (обложка альбома + название песни).
   - Видео-демо (если MP4 был готов).
6. Дополнительно — через VK API проверить наличие прикреплённого фото:
   ```bash
   # В standalone-приложении VK (или через свой скрипт):
   # wall.getById(posts="-<groupId>_<postId>") → проверить attachments[0].type == "photo"
   ```

**Ожидаемый результат**: пост содержит фото-превью (SC-001).

## Сценарий 2 — Сбой `photos.*`, успешный fallback на `docs.*` (FR-006, SC-003)

**Цель**: проверить, что при потере user-scope `photos` бот использует
fallback через `docs.*` и пост создаётся с прикреплением-документом.

**Шаги**:

1. Имитировать потерю scope `photos` — подменить `vkUserAccessToken`
   на токен БЕЗ scope `photos` через Properties UI (или временно
   установить пустой user-token).
2. Запустить публикацию для песни с пустым `Song.idVk`.
3. Проверить логи `karaoke-app`:
   ```
   VkPhotoUploadClient.uploadCover: photos.getWallUploadServer error_code=27
   VkPhotoUploadClient.uploadCover: falling back to docs.*
   VkPhotoUploadClient.uploadCover: docs.getWallUploadServer OK
   VkPhotoUploadClient.uploadCover: POST upload_url OK
   VkPhotoUploadClient.uploadCover: docs.save OK, doc=doc-<groupId>_<docId>
   VkApiClient.wallPost: success, post_id=-<groupId>_<postId>
   ```
4. Открыть группу ВКонтакте — найти пост. Должен содержать:
   - Текст, URL, видео (если было).
   - Прикреплённый документ (иконка файла PNG, клик открывает картинку).
   - ⚠️ Большого сниппета-превью может не быть (документ отображается
     как файл, а не как сниппет) — это **ожидаемое** поведение fallback.

**Ожидаемый результат**: пост создан с прикреплением-документом (SC-003).

## Сценарий 3 — Полный сбой всех методов (FR-007, SC-007)

**Цель**: проверить, что при сбое `photos.*` И `docs.*` пост создаётся
в деградированном виде (без превью), но НЕ полностью проваливается.

**Шаги**:

1. Имитировать одновременный сбой — установить `vkPhotoAttachEnabled=false`
   И `vkDocAttachEnabled=false` через Properties UI.
2. Запустить публикацию для песни с пустым `Song.idVk`.
3. Проверить логи:
   ```
   VkPhotoUploadClient.uploadCover: skipped (vkPhotoAttachEnabled=false)
   VkPhotoUploadClient.uploadCover: skipped (vkDocAttachEnabled=false)
   VkApiClient.sendPostWithVideo: video.save OK
   VkApiClient.wallPost: success, post_id=-<groupId>_<postId>
   ```
   Или, если `vkPhotoAttachEnabled=true`, но user-token битый:
   ```
   VkPhotoUploadClient.uploadCover: photo upload failed: photos.getWallUploadServer error_code=5
   VkPhotoUploadClient.uploadCover: doc upload failed: docs.getWallUploadServer error_code=...
   VkAutoPublishService.publishFile: photo attach failed: photos.*=5 + docs.*=...
   VkApiClient.sendPostWithVideo: video.save OK
   VkApiClient.wallPost: success, post_id=...
   ```
4. Проверить `vkAutoPublishLastError`:
   ```bash
   # Через webvue3 или прямой SQL:
   SELECT id_vk, player_readiness_flags->>'vkAutoPublishLastError' AS err
   FROM tbl_songs WHERE id = 23217;
   # Ожидаем: post_id заполнен, err начинается с "photo attach failed:" или пусто
   ```
5. Открыть пост в группе ВКонтакте — должен быть без превью (только текст + URL + видео).

**Ожидаемый результат**: пост создан в деградированном виде, `vkAutoPublishLastError` содержит понятное описание (FR-007, SC-007).

## Сценарий 4 — Идемпотентность при повторной публикации (FR-008, SC-004)

**Цель**: проверить, что повторная публикация песни, у которой `Song.idVk` заполнен, не создаёт второго поста.

**Шаги**:

1. Взять песню с уже заполненным `Song.idVk` (например, из сценария 1).
2. Очистить `Song.vkAutoPublishState` (но НЕ `idVk`).
3. Запустить публикацию повторно:
   ```bash
   curl -s -X POST "http://localhost:8898/api/song/publishToVkNow" -d "id=23217&type=air"
   ```
4. Проверить логи:
   ```
   VkAutoPublishService.publishToVk: song.idVk=... → skip (PUBLISHED)
   ```
5. Проверить `Song.idVk` — НЕ изменилось (тот же post_id).
6. В группе ВКонтакте — НЕТ нового поста.

**Ожидаемый результат**: повторная публикация пропущена (SC-004).

## Сценарий 5 — Параллельная публикация одной песни (FR-009, SC-004)

**Цель**: проверить, что одновременный запуск двух публикаций одной
песни создаёт ровно один пост (process-local lock).

**Шаги**:

1. Взять песню с пустым `Song.idVk`.
2. Параллельно (в двух shell-окнах одновременно):
   ```bash
   curl -s -X POST "http://localhost:8898/api/song/publishToVkNow" -d "id=23217&type=air" &
   curl -s -X POST "http://localhost:8898/api/song/publishToVkNow" -d "id=23217&type=air" &
   wait
   ```
3. Проверить логи:
   ```
   VkAutoPublishService.publishToVk: песня 23217 — lock acquired
   VkPhotoUploadClient.uploadCover: ...
   VkApiClient.wallPost: success, post_id=...
   VkAutoPublishService.publishToVk: песня 23217 — lock acquired (после release первого)
   VkAutoPublishService.publishToVk: song.idVk=... → skip (PUBLISHED)
   ```
4. Проверить `Song.idVk` — заполнено ОДНО значение.
5. В группе ВКонтакте — ОДИН пост.

**Ожидаемый результат**: ровно один пост (SC-004).

## Бонус: визуальная проверка качества превью

**Цель**: убедиться, что новая картинка 1200×630 выглядит качественно.

**Шаги**:

1. Открыть PNG в браузере:
   ```
   https://sm-karaoke.ru/api/public/song-vk-image/<id>
   ```
2. Проверить разрешение (через DevTools → Network → клик на запрос → preview).
3. Проверить визуально:
   - ✅ Обложка альбома отображается (не пустое место).
   - ✅ Обложка автора отображается.
   - ✅ Название песни читаемо (не обрезано, не выходит за рамки).
   - ✅ Нет артефактов от старого размера 537×240 (поля/отступы).

**Ожидаемый результат**: PNG 1200×630, обложка + название песни видны корректно.

## Cleanup после тестов

1. Вернуть `vkUserAccessToken` к валидному (если меняли).
2. Удалить тестовые посты из группы ВКонтакте вручную (UI).
3. Очистить `Song.idVk` для тестовых песен через webvue3 (если хотите повторить).
4. Проверить `karaoke-app` логи на отсутствие ошибок.

## Когда сценарий провалился

| Симптом | Вероятная причина | Где смотреть |
|---------|-------------------|--------------|
| Логи: `photos.getWallUploadServer error_code=27` | User-token потерял scope `photos` | Получить новый user-token через `/api/utils/vkOAuthUrl` |
| Логи: `photos.getWallUploadServer error_code=5` | User-token невалиден | То же |
| Логи: `preview prewarm failed: not a valid PNG` | PNG endpoint не возвращает корректный файл | `https://sm-karaoke.ru/api/public/song-vk-image/<id>` |
| В ВК нет превью, но логи показывают успех | VK API изменил поведение | Проверить attachments через `wall.getById` |
| `vkAutoPublishLastError` пуст, но пост без превью | Деградация — оба метода вернули ошибку | Проверить логи на префикс `photo attach failed:` |