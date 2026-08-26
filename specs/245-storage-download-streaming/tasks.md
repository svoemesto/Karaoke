---
description: "Task list для FR-104: streaming download из MinIO в StorageController.downloadFile"
---

# Tasks: Streaming download из MinIO в StorageController.downloadFile

**Input**: Design documents from `/specs/245-storage-download-streaming/`
- plan.md (required)
- spec.md (required for user stories)

**Tests**: Конституция § Тесты — автоматические тесты `@Disabled`. Тестирование — ручное через curl + admin UI. Tests-фазы НЕ включены.

**Organization**: Tasks сгруппированы по user story (US1 — OOM-free, US2 — Range-поддержка).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно делать параллельно
- **[Story]**: US1, US2
- В описаниях — точные file:line

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Preconditions проверка.

- [ ] T001 [P] Переключиться на ветку `245-storage-download-streaming`, убедиться что `git status` чистый.
- [ ] T002 [P] Прочитать `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/StorageController.kt:downloadFile` (строки 116-146) — текущая реализация с `readAllBytes()`.
- [ ] T003 [P] Проверить наличие `karaokeStorageService.getFileStat(bucketName, fileName): StatObjectResponse?` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/KaraokeStorageService.kt` (FR-002 spec.md — для Content-Length).
- [ ] T004 [P] Проверить, что MinIO Java SDK поддерживает Range-запросы через `GetObjectArgs` (для US2).
- [ ] T005 [P] Прочитать `karaoke-app/src/main/resources/application.yml` — найти секцию `server.tomcat` для Phase 4 (max-swallow-size).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Подготовка Resource wrapper.

**⚠️ CRITICAL**: US1 не может начаться, пока wrapper не готов.

- [ ] T006 В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/StorageController.kt` создать внутренний класс `LengthAwareInputStreamResource`:
  ```kotlin
  /**
   * InputStreamResource с явным Content-Length. Spring требует переопределить оба метода:
   * getContentLength() (legacy) и contentLength() (Spring 5.x).
   * @see specs/245-storage-download-streaming/spec.md FR-002
   */
  private class LengthAwareInputStreamResource(
      private val delegate: InputStream,
      private val contentLength: Long,
  ) : InputStreamResource(delegate) {
      override fun getContentLength(): Long = contentLength
      override fun contentLength(): Long = contentLength
  }
  ```

- [ ] T007 Запустить `./gradlew :karaoke-app:compileKotlin --parallel` — должен пройти.

**Checkpoint**: Wrapper готов — можно начинать US1.

---

## Phase 3: User Story 1 — Скачивание больших MP4 без OOM (Priority: P1) 🎯 MVP

**Goal**: MP4 100 MB → heap ≤ baseline + 50 MB. MP4 500 MB → без OOM.

**Independent Test**: Загрузить MP4 100 MB в MinIO → скачать через endpoint → замерить peak heap через `jconsole`/`VisualVM`.

### Implementation for User Story 1

- [ ] T008 [US1] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/StorageController.kt:downloadFile` (строки 116-146) добавить параметр `rangeHeader`:
  ```kotlin
  fun downloadFile(
      @RequestParam("bucketName") bucketName: String,
      @RequestParam("fileName") fileName: String,
      @RequestHeader(value = "Range", required = false) rangeHeader: String?,
      request: HttpServletRequest,
  ): ResponseEntity<Any> {  // Any — для совместимости с Resource и ResourceRegion
  ```
  Spring автоматически обработает Range header и вернёт `ResourceRegion` (HTTP 206).

- [ ] T009 [US1] Заменить блок `val inputStream = ...; val bytes = inputStream.readAllBytes()` (строки 132-134) на streaming через `Resource`:
  ```kotlin
  // FR-002: Content-Length из fileStat
  val fileStat = karaokeStorageService.getFileStat(bucketName, fileName)
  val contentLength = fileStat?.size() ?: -1L

  // FR-001: streaming через Resource (НЕ readAllBytes!)
  val inputStream = karaokeStorageService.downloadFile(bucketName, fileName)
  val resource: Resource = LengthAwareInputStreamResource(inputStream, contentLength)

  // FR-003: Content-Disposition
  val headers = HttpHeaders().apply {
      add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$fileName\"")
      if (contentLength > 0) {
          setContentLength(contentLength)
      }
  }

  return ResponseEntity.ok().headers(headers).body(resource as Any)
  ```

- [ ] T010 [US1] Удалить старый return statement (строка ~138-141): `return ResponseEntity.ok()...body(bytes)` — больше не нужен.

- [ ] T011 [P] [US1] Добавить KDoc на обновлённый `downloadFile` (Constitution § VI FR-006): описание streaming-поведения, FR-001 (Resource), FR-002 (Content-Length), FR-003 (Content-Disposition), FR-004 (Range), FR-005 (broken pipe).

- [ ] T012 [US1] Добавить импорты:
  ```kotlin
  import org.springframework.core.io.InputStreamResource
  import org.springframework.core.io.Resource
  ```

- [ ] T013 [US1] Запустить `./gradlew :karaoke-app:compileKotlin --parallel` — должен пройти. Если есть ошибки типов — починить.

- [ ] T014 [US1] Запустить `./gradlew :karaoke-web:ktlintCheck` — никаких новых нарушений.

- [ ] T015 [US1] Код-ревью: убедиться что streaming через `Resource` корректно работает с Spring `ResourceHttpMessageConverter`. При сомнениях — проверить в локальном запуске через curl.

- [ ] T016 [US1] **OOM-test**: загрузить MP4 100 MB в MinIO (через существующий `POST /api/storage/upload`), скачать через `curl /api/storage/download?...` → замерить peak heap через `jconsole`. Ожидание: ≤ baseline + 50 MB (SC-001). **Если OOM или peak > baseline + 50 MB — откатить изменения и пересмотреть подход (см. Risks в plan.md).**

- [ ] T017 [US1] **OOM-stress-test**: MP4 500 MB → скачать → ожидание успеха без OOM (SC-002). **Критичный тест — если падает, фича не готова к merge.**

**Checkpoint**: US1 функциональна. MP4 500 MB без OOM (SC-002), TTFB ≤ 200 мс (SC-006).

---

## Phase 4: User Story 2 — Поддержка Range-запросов (Priority: P2)

**Goal**: Range-запросы возвращают HTTP 206 с правильным `Content-Range`.

**Independent Test**: `curl -H "Range: bytes=0-1023" -i` → HTTP 206 + `Content-Range`.

**Замечание**: Это требование может быть уже удовлетворено автоматически после US1 — Spring `ResourceHttpMessageConverter` обрабатывает Range автоматически. Только verify.

### Implementation for User Story 2

- [ ] T018 [US2] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/StorageController.kt:downloadFile` изменить signature возврата для явной поддержки `ResourceRegion`:
  ```kotlin
  fun downloadFile(...): ResponseEntity<Resource> {
      ...
      // Spring сам разберёт Range header через ResourceRegion
  }
  ```
  Убрать `Any` из сигнатуры (T008) и использовать типизированный `Resource`.

- [ ] T019 [P] [US2] Добавить `import org.springframework.core.io.support.ResourceRegion` (если нужен).

- [ ] T020 [US2] Запустить `./gradlew :karaoke-app:compileKotlin --parallel` — должен пройти.

- [ ] T021 [US2] **Range-test**: `curl -H "Range: bytes=0-1023" -i /api/storage/download?bucketName=...&fileName=mp4.mp4` → ожидание HTTP 206 + `Content-Range: bytes 0-1023/<total>` (SC-005).

- [ ] T022 [US2] **Range-boundary-test**: `curl -H "Range: bytes=100-199" -i` для файла 1000 байт → HTTP 206 + `Content-Range: bytes 100-199/1000`.

- [ ] T023 [US2] **Range-invalid-test**: `curl -H "Range: bytes=9999999-" -i` → HTTP 416 (Range Not Satisfiable).

- [ ] T024 [US2] Локальная проверка broken pipe (FR-005): запустить `curl` с `--max-time 1`, прервать download → проверить, что в docker logs нет `unhandled exception` или `ERROR` (только WARN через SLF4J logger).

**Checkpoint**: US2 функциональна. Range-запросы работают (SC-005), broken pipe обрабатывается (FR-005).

---

## Phase 5: Polish & Cross-Cutting Concerns

- [ ] T025 [P] В `karaoke-app/src/main/resources/application.yml` добавить/обновить:
  ```yaml
  server:
    tomcat:
      max-swallow-size: -1  # для больших download'ов (Phase 4 plan.md)
      connection-timeout: 300s  # таймаут для streaming
  ```
  (если уже есть секция `server.tomcat` — добавить/обновить ключи; иначе создать).

- [ ] T026 [P] TTFB-test (SC-006): `curl -w '%{time_starttransfer}' -o /dev/null` для MP4 100 MB → ожидание ≤ 200 мс.

- [ ] T027 [P] Concurrent-test (SC-003): 10 одновременных download'ов MP4 100 MB → heap ≤ baseline + 200 MB.

- [ ] T028 [P] Small-file-test (SC-007): файл 50 KB → latency overhead ≤ 5 мс vs `readAllBytes()`.

- [ ] T029 [P] Обновить per-feature документ `archive/docs/features/storage-api.md` (если есть; иначе создать). Constitution § VI FR-009.

- [ ] T030 Code-review checklist (Constitution § VI FR-006, FR-009): KDoc обновлён, per-feature документ обновлён, baseline линтера не вырос.

- [ ] T031 Создать PR через `gh pr create --base master`. Title: `storage-download-streaming: streaming через Resource вместо readAllBytes()`.

- [ ] T032 Дождаться CI 8/8 PASS, merge через `gh pr merge --merge` (БЕЗ `--delete-branch`).

- [ ] T033 Deploy на admin-машину (НЕ на prod — это admin-only endpoint).

- [ ] T034 Post-deploy: OOM-test на 500 MB + TTFB + Range + Concurrent — все SC подтверждены.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1**: нет зависимостей.
- **Phase 2**: зависит от Phase 1 — БЛОКИРУЕТ US1.
- **Phase 3 (US1)**: зависит от Phase 2.
- **Phase 4 (US2)**: зависит от Phase 3.
- **Phase 5**: зависит от всех user stories.

### Within Each User Story

- Phase 1 параллельна (T001-T005).
- Phase 2 последовательна (T006-T007).
- Phase 3 последовательна (T008-T017) — критично: OOM-test (T016, T017) БЛОКИРУЕТ merge.
- Phase 4 последовательна (T018-T024).
- Phase 5 параллельна где возможно (T025-T034).

### Parallel Opportunities

- Все Setup preconditions параллельны.
- TTFB (T026), Concurrent (T027), Small-file (T028) — параллельные тесты.

---

## Implementation Strategy

### MVP First (US1 Only)

1. ✅ Phase 1: Setup preconditions.
2. ✅ Phase 2: Foundational (Resource wrapper).
3. ✅ Phase 3: US1 (streaming через Resource).
4. **STOP and VALIDATE**: OOM-test (T016, T017) — **критично**. Если падает, откатить.
5. Deploy на admin-машину.

### Incremental Delivery

- US1 = основной эффект (OOM-free, TTFB).
- US2 = дополнительный (Range для видеоплееров). Spring даёт «бесплатно», нужно только verify.

### Parallel Team Strategy

Фича средняя (~1.5-2 часа кодинга + 1 час тестирования). Параллельно с FR-101, FR-102, FR-103 — разные файлы, разные ветки.

---

## Notes

- Это **admin-only** фича — на проде (`karaoke-web`) этот endpoint НЕ вызывается (см. KDoc `WebKaraokeStorageServiceImpl`). Deploy только на admin-машину.
- **Критичный риск**: OOM-test на 500 MB (T017) может выявить проблему с streaming — если падает, см. Risks в plan.md (Phase 3) и откатить.
- `ResourceHttpMessageConverter` Spring сам обрабатывает Range header, но только если возвращается `Resource` (не `StreamingResponseBody`). Поэтому US1+US2 вместе.
- Tomcat config (T025) — рекомендуется, но не критично для маленьких файлов. На admin-машине heap обычно больше (1-2 GB), чем на проде karaoke-web.
- Broken pipe (FR-005, T024) — обычно Spring справляется сам, но explicit WARN logging — best practice.
- **Не блокирует** другие Tier-1 фичи — разные файлы, разные ветки.
- После успешного merge — feature-ветка НЕ удаляется.
