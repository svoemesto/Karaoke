# Tasks: Предварительная подготовка превью перед публикацией в ВК

**Input**: Design documents из `specs/130-vk-preview-generation/`
**Branch**: `130-vk-preview-generation`
**Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Research**: [research.md](./research.md) | **Contract**: [contracts/vk-preview-warmup.md](./contracts/vk-preview-warmup.md)

## Format: `[ID] [P?] [Story] Описание с путём`

- **[P]**: можно выполнять параллельно (разные файлы, нет зависимостей).
- **[Story]**: метка пользовательской истории (US1/US2/US3).
- Каждое задание содержит точный путь к файлу.

## Phase 1: Setup (общая инфраструктура)

**Цель**: подтвердить, что рабочее дерево и ветка соответствуют фиче.

- [x] T001 Подтвердить, что ветка `130-vk-preview-generation` активна и `git status` в корне репозитория показывает только `specs/130-vk-preview-generation/*` и `.specify/feature.json` (нерелевантные изменения отсутствуют)

---

## Phase 2: Foundational (блокирующие prerequisites)

**Цель**: инфраструктура, без которой нельзя начинать ни одну пользовательскую историю.

**⚠️ CRITICAL**: ни одна пользовательская история не может стартовать, пока эта фаза не завершена.

- [x] T002 Добавить свойства `vkPreviewWarmupEnabled=true`, `vkPreviewWarmupUrl=https://sm-karaoke.ru/api/public/song-vk-image/`, `vkPreviewWarmupTimeoutMs=30000`, `vkPreviewWarmupMaxAttempts=2` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt`
- [x] T003 Создать скелет `VkPreviewWarmupClient` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkPreviewWarmupClient.kt` с KDoc и `@see docs/features/vk-news-auto-publish.md`; конструктор принимает `KaraokeProperties`; метод `warmup(songId: Long): VkPreviewWarmupResult` строит URL, выполняет GET через `java.net.http.HttpClient` с `Redirect.NEVER` и настроенным timeout, повторяет transient-ошибки до `maxAttempts`; возвращает внутренний результат со `status`/`songId`/`attempts`/`httpStatus`/`bytes`/`durationMs`/`error`
- [x] T004 Создать скелет `VkPreviewWarmupClientTest` в `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/VkPreviewWarmupClientTest.kt` с JUnit 5 `@BeforeEach`/`@AfterEach`, поднимающим локальный `com.sun.net.httpserver.HttpServer` на случайном порту; helper `enqueue(status, contentType, body)` для ответов

**Checkpoint**: Foundation готов — пользовательские истории могут стартовать.

---

## Phase 3: User Story 1 — Публикация песни сразу с готовым превью (Priority: P1) 🎯 MVP

**Цель**: новый пост ВКонтакте содержит превью песни; VK-бот при первом обращении к публичному URL изображения получает готовый PNG без ожидания генерации.

**Independent Test**: запустить quickstart сценарий 2 на песне с пустым `idVk` и отсутствующим `/tmp/vk_<id>.png`; убедиться, что пост содержит превью и первый GET `song-vk-image/{id}` отдаёт HTTP 200 + `image/png` + ненулевой PNG (сценарий 1).

### Реализация User Story 1

- [x] T005 [US1] Реализовать детекцию успеха в `VkPreviewWarmupClient.kt`: HTTP 200 + ненулевое тело + `Content-Type` содержит `image/png` ИЛИ `ImageIO.read` успешно декодирует PNG → `VkPreviewWarmupResult.SUCCESS` с заполненными `bytes`/`size`/`durationMs`/`attempts`; при `vkPreviewWarmupEnabled=false` возвращать диагностический bypass-результат, не вызывающий VK API
- [x] T006 [US1] Добавить тест "200 + валидный PNG → SUCCESS" в `VkPreviewWarmupClientTest.kt`: локальный сервер отдаёт 200 + корректные PNG-байты (PNG magic signature + ImageIO-валидный буфер); assert `result.status == SUCCESS`, `result.bytes > 0`, `result.durationMs >= 0`
- [x] T007 [US1] Встроить warmup в `VkAutoPublishService.publishTextOnly` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkAutoPublishService.kt`: после существующих проверок готовности песни и идемпотентности, перед `client.wallPost`; при `SUCCESS` продолжить текущий `wallPost` flow; при `FAILED` пропустить `wallPost` (финальная запись ошибки — в T011)
- [x] T008 [US1] Встроить warmup в `VkAutoPublishService.publishFile`: в той же точке для ветки `video.save` → upload → `wall.post`; при `SUCCESS` продолжить существующий video-flow; при `FAILED` пропустить и записать ошибку

**Checkpoint**: User Story 1 функционально завершена и тестируется независимо через quickstart сценарии 1 и 2.

---

## Phase 4: User Story 2 — Безопасная обработка ошибки подготовки изображения (Priority: P1)

**Цель**: при ошибке прогрева успешный пост ВКонтакте не создаётся; администратор отличает ошибку подготовки от ошибки VK API; повторная попытка после устранения причины успешна.

**Independent Test**: запустить quickstart сценарий 4 (недоступный `vkPreviewWarmupUrl`); убедиться, что состояние становится `SEND_FAILED`, `vkAutoPublishLastError` начинается с `preview prewarm failed:`, `wallPost` не вызывается, `idVk` остаётся пустым; после восстановления URL повторная публикация успешна (сценарий 5 отделяет ошибку VK от ошибки изображения).

### Реализация User Story 2

- [x] T009 [US2] Реализовать классификацию ошибок в `VkPreviewWarmupClient.kt`: 3xx без follow → FAILED, 4xx → FAILED, 5xx → FAILED после `maxAttempts`, тайм-аут/сетевая ошибка → FAILED после `maxAttempts`, 200 с пустым телом → FAILED, 200 с неверным MIME или повреждённым PNG → FAILED; retry только для transient-сетевых ошибок и 5xx; результат FAILED содержит `httpStatus`, `error`, `attempts`
- [x] T010 [US2] Добавить тесты failure-кейсов в `VkPreviewWarmupClientTest.kt`: 302 redirect без follow → FAILED, 404 → FAILED, 500 → FAILED после 2 попыток, тайм-аут → FAILED, 200 с пустым телом → FAILED, 200 с повреждённым PNG → FAILED, 500 затем 200 → SUCCESS на повторе
- [x] T011 [US2] Привязать warmup FAILED к существующему `writeFailure` в `VkAutoPublishService.publishTextOnly` и `publishFile`: записать `SEND_FAILED` через существующий механизм, `vkAutoPublishLastError` с префиксом `preview prewarm failed:`, `postId=null`; убедиться, что `client.wallPost`/`client.sendPostWithVideo` НЕ вызываются при FAILED; ошибка VK API после успешного warmup сохраняется отдельно без префикса

**Checkpoint**: User Stories 1 и 2 работают независимо (успех и ошибка покрыты quickstart сценариями 1-5).

---

## Phase 5: User Story 3 — Сохранение существующего поведения публикаций (Priority: P2)

**Цель**: идемпотентность сохранена, повторные и параллельные попытки не создают дублей и не повреждают файл превью.

**Independent Test**: запустить quickstart сценарий 3 (повторный `publishToVkNow` на песне с заполненным `idVk`); убедиться, что новый пост не создан и warmup не вызван; сценарий 5 подтверждает, что после ошибки VK повторный запуск не требует обязательной регенерации картинки.

### Реализация User Story 3

- [x] T012 [US3] Сделать запись PNG атомарной в `PublicApiController.songVkImage` в `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt`: сформировать PNG в памяти как сейчас, записать во временный файл в той же директории, затем `Files.move` с `StandardCopyOption.ATOMIC_MOVE` и fallback на обычную замену при отсутствии поддержки ОС; при чтении отклонять пустой/повреждённый кэш и генерировать заново
- [x] T013 [US3] Добавить process-local lock по `song.id` в `VkAutoPublishService.kt`: обернуть критическую секцию (идемпотентность + warmup + VK-вызов) в `publishTextOnly` и `publishFile`; в callback `onRenderCompleted` повторно проверить `idVk` под тем же lock и пропустить вторую публикацию, если `idVk` уже заполнен; lock снимать в `finally`; lock не хранить в БД

**Checkpoint**: все три пользовательские истории функционально независимы.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Цель**: документация, линтинг, компиляция, финальная проверка per-feature.

- [x] T014 [P] Обновить `docs/features/vk-news-auto-publish.md`: добавить раздел о prewarm (назначение, четыре новые настройки с дефолтами, поведение при успехе и ошибке, идемпотентность, атомарный web-кэш), поставить ссылки на `specs/130-vk-preview-generation/spec.md`, `research.md`, `contracts/vk-preview-warmup.md`
- [x] T015 Запустить `./gradlew ktlintCheck`, `./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin`, `./gradlew :karaoke-app:test` — все должны пройти SUCCESS (baseline-baseline ktlint разрешён)
- [x] T016 Запустить `bash tools/check-feature-doc.sh docs/features/*.md` — все документы должны остаться валидными

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: нет зависимостей — стартует немедленно.
- **Phase 2 (Foundational)**: зависит от Phase 1 — БЛОКИРУЕТ все пользовательские истории.
- **Phase 3 (US1)**: зависит от Phase 2.
- **Phase 4 (US2)**: зависит от Phase 3 (расширяет `VkPreviewWarmupClient`).
- **Phase 5 (US3)**: зависит от Phase 3 (использует интеграцию в `VkAutoPublishService`).
- **Phase 6 (Polish)**: зависит от завершения US1/US2/US3.

### Story Dependencies

- **US1 (P1)**: стартует после Foundational — независима.
- **US2 (P1)**: стартует после Foundational; логически опирается на `VkPreviewWarmupClient` из US1, но её тесты независимы.
- **US3 (P2)**: стартует после US1 (использует точку интеграции в `VkAutoPublishService`); тесты (quickstart сценарий 3) независимы.

### Внутри каждой Story

- Скелет/implementation пишется до тестов в той же story.
- Service-метод создаётся до интеграции в существующий flow.
- Implementation завершается до Polish.

### Parallel Opportunities

- Phase 2: T003 и T004 можно стартовать параллельно после T002.
- Phase 3: T005 и T008 в одном файле `VkAutoPublishService.kt` — последовательно; T007 и T008 в одном файле — последовательно.
- Phase 6: T014 (документация) можно стартовать параллельно с T015/T016.
- Разные пользовательские истории могут редактироваться параллельно разными исполнителями после Phase 2.

---

## Parallel Example: User Story 1

```bash
# Phase 2 после T002 — параллельно:
Task: "Скелет VkPreviewWarmupClient в services/VkPreviewWarmupClient.kt"
Task: "Скелет VkPreviewWarmupClientTest в test/.../VkPreviewWarmupClientTest.kt"
```

```bash
# Phase 3 — последовательно по файлам:
Task: "Успех PNG в VkPreviewWarmupClient.kt (T005)"
Task: "Тест 200 + PNG в VkPreviewWarmupClientTest.kt (T006)"
Task: "Интеграция в publishTextOnly (T007)"
Task: "Интеграция в publishFile (T008)"
```

---

## Implementation Strategy

### MVP First (только User Story 1)

1. Phase 1: Setup.
2. Phase 2: Foundational.
3. Phase 3: US1 (T005–T008).
4. **STOP и VALIDATE**: прогнать quickstart сценарии 1 и 2 на тестовой песне; убедиться, что пост содержит превью.
5. Deploy/demo при готовности.

### Incremental Delivery

1. Setup + Foundational → фундамент готов.
2. US1 → независимый тест → MVP.
3. US2 → независимый тест ошибки → регрессия не ломает US1.
4. US3 → независимый тест идемпотентности → регрессия не ломает US1/US2.
5. Каждая история добавляет ценность без поломки предыдущих.

### Parallel Team Strategy

С несколькими исполнителями после Phase 2:

- Исполнитель A: US1 (T005–T008) — `VkPreviewWarmupClient` + `publishTextOnly`/`publishFile` интеграция.
- Исполнитель B: US3 атомарный web-кэш (T012) — `karaoke-web`, отдельный модуль, можно стартовать параллельно с A.
- Исполнитель C: документация (T014) — `docs/features/vk-news-auto-publish.md`, параллельно.

Затем — US2 (T009–T011) после T005, и lock (T013) после T007.

---

## Notes

- [P] задачи = разные файлы, нет зависимостей.
- Метка `[Story]` привязывает задачу к пользовательской истории для трассировки.
- Каждая пользовательская история завершаема и тестируема независимо.
- Тесты пишутся ПОСЛЕ скелета класса, но до интеграции в существующий flow.
- Live VK проверяется только вручную (quickstart сценарии 1-5).
- Существующие интеграционные тесты требуют сети/браузера/credentials и НЕ заменяют quickstart.
- Все новые публичные symbols (например, `VkPreviewWarmupClient`) получают KDoc с `@see docs/features/vk-news-auto-publish.md`.
- Секреты VK-токенов в логи не попадают; `preview prewarm failed:` — единственный диагностический префикс.