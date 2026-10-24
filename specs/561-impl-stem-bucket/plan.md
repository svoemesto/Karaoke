# Implementation Plan: 561 — Приватный бакет `stemjobs` и закрытый канал отдачи стемов (BR-258)

**Branch**: `561-impl-stem-bucket` | **Date**: 2026-10-24 | **Spec**: [../556-close-stem-bucket-anonymous/spec.md](../556-close-stem-bucket-anonymous/spec.md)
**OpenProject**: #361 (аудит #336, BR-258)

**Input**: Feature specification from `specs/556-close-stem-bucket-anonymous/spec.md`
(утверждена владельцем; решения владельца — обязательны к исполнению).

## Summary

Результаты премиум-задания «Создать минусовку» (стемы) и загруженный пользователем исходный
файл сегодня кладутся в **публичный** бакет `karaoke` (`StemJobProcessing.kt:193-196`),
у которого bucket policy разрешает анонимный `s3:GetObject` на весь префикс, а прод-nginx
проксирует `/minio/<bucket>/<object>` без авторизации. Итог: `stemjobs/<id>/<stem>.mp3`
читается анонимным GET по угадываемому URL — обход премиум-гейта и утечка чужого аудио
(BR-258, S1).

Правка состоит из трёх частей:

1. **Физическое разделение.** Приватные объекты переезжают в отдельный бакет `stemjobs`
   (без публичной политики). Публичный бакет `karaoke` не меняется — картинки/превью/mp3
   песен продолжают раздаваться анонимно (FR-002).
2. **Программный инвариант.** При старте `karaoke-app` приложение само приводит хранилище к
   безопасному состоянию: `stemjobs` → private, `karaoke` → public (FR-006, решение
   владельца №2).
3. **Закрытый канал отдачи.** `karaoke-web` не умеет читать MinIO с учётными данными
   (`WebKaraokeStorageServiceImpl` — заглушка, все методы бросают
   `UnsupportedOperationException`), а presigned-URL через path-proxy ломается
   переписыванием пути (SigV4). Поэтому приватный объект отдаёт приложение, имеющее
   доступ (`karaoke-app`), через server-to-server канал с уже настроенным общим секретом
   `X-Internal-Secret` — по образцу `InternalStemJobController` (FR-004/FR-005, решение
   владельца №3).

Формат публичного API не меняется (FR-011): `GET /api/public/account/stemjobs/{id}/download`
отдаёт тот же файл с теми же заголовками — меняется только источник байтов.

## Technical Context

**Language/Version**: Kotlin 2.2 / Spring Boot 3.5.6 (`karaoke-app`, `karaoke-web`), MinIO SDK 8.6.0

**Primary Dependencies**: Spring Web (`@RestController`), `io.minio.*` (`MinioClient`),
`java.net.HttpURLConnection` (передача объекта между приложениями), SLF4J (`infra.*` категории)

**Storage**: MinIO, remote-прод (`storage.remote-endpoint`) + локальный MinIO admin-машины;
PostgreSQL — сырой JDBC, схема не меняется (Constitution II)

**Testing**: JUnit 5 + Mockito (`karaoke-app/src/test`, `karaoke-web/src/test`) — регрессионные
тесты «до/после» без БД и без MinIO

**Target Platform**: admin-машина `nsa-i9` (`karaoke-app`) ↔ прод-хост (`karaoke-web`)

**Project Type**: web-service (backend + object storage)

**Performance Goals**: анонимный GET приватного объекта не отдаёт содержимое; легитимное
скачивание владельцем работает как раньше (один HTTP-запрос внутри приложения + чтение из
MinIO приложением)

**Constraints**: секрет не попадает в репозиторий (Constitution VIII); прод-деплой — только
с согласия владельца; публичное поведение сайта не меняется (FR-002, FR-011)

**Scale/Scope**: 6 файлов кода в `karaoke-app`, 4 в `karaoke-web`, 1 конфиг, 4 теста,
5 документов `knowledge/` + 1 per-feature документ

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Требование | Как выполняется | Статус |
|---|---|---|---|
| I. Self-contained автопайплайн | правка не тянет внешних SaaS | передача объектов — между своими приложениями, без внешних сервисов | ✅ |
| II. Сырой JDBC | никаких JPA/Hibernate | правка не трогает слой доступа к данным; схема не меняется | ✅ |
| III. Двух-БД синхронизация | не ломаем `SyncRegistry` | `tbl_stem_jobs` не участвует в синхронизации (см. `StemJob.kt`), правок нет | ✅ |
| IV. Async-очередь задач | R-43 `redirectErrorStream`, структурированное логирование | финализация задания остаётся функциональным шагом `KaraokeProcess`; логи — через `infra.*` категории | ✅ |
| V. Два фронтенда | admin и public — разные приложения | фронтенды не трогаются | ✅ |
| VI. Code Standards (FR-006/FR-009) | KDoc + `@see` на per-feature документ; ktlint; FR-009 — обновление `docs/features/<slug>.md` | новые компоненты с KDoc; per-feature документ обновляется в задачах | ✅ (в задачах) |
| VII. Тесты | регрессионный тест «до/после» | FR-010: тесты на раскладку бакетов, на fail-safe секрета, на отдачу/отказ | ✅ (в задачах) |
| VIII. Секреты и git-гигиена | секрет не в репозитории | в коде — только имена параметров (`stemjobs.internal-secret`, `stemJobsInternalSecret`); значение — только в env/Properties | ✅ |
| IX. Knowledge-first + изоляция | pre-flight, своя ветка/worktree | pre-flight — `research.md` (шаги 1-5); работа в worktree `.worktrees/impl361`, ветка `561-impl-stem-bucket` | ✅ |
| IX.3 Subagent isolation | отдельный worktree | worktree `/home/nsa/Karaoke/.worktrees/impl361`, главная копия не тронута | ✅ |

## Architecture

```
                       admin-машина (nsa-i9)                      прод-хост
  ┌──────────────────────────────────────────────┐        ┌─────────────────────────────┐
  │ karaoke-app                                   │        │ karaoke-web                 │
  │  StemJobProcessing.executeFinalizeStemJob    │        │  InternalStemJobController  │
  │   1) SAC_APP.uploadFile(bucket="stemjobs")    │        │   PUT /api/internal/        │
  │        → MinIO (приватный бакет, без policy)  │        │      stemjobs/{id}/files/…  │
  │   2) StemJobResultPublisher.publish(...)      │───────▶│   POST /api/internal/       │
  │        читает объект (учётные данные)          │  X-    │      stemjobs/{id}/purge     │
  │        и передаёт по HTTP с X-Internal-Secret  │  Internal-                              │
  │                                              │  Secret │  PublicStemJobController   │
  │  StemJobStoragePolicyInitializer             │        │   GET /api/public/account/  │
  │   при старте: stemjobs→private, karaoke→public│        │      stemjobs/{id}/download  │
  │                                              │        │   → отдаёт файл владельцу   │
  │  StemJobCleanup: удаляет объекты (новые +     │        │  из result-dir/<id>/<file>   │
  │  legacy в публичном бакете) + purge           │───────▶│                             │
  └──────────────────────────────────────────────┘        └─────────────────────────────┘
             │                                            ▲
             │ StorageApiClient (учётные данные)          │ /minio/ — только публичные
             ▼                                            │ объекты бакета karaoke
   ┌─────────────────────────┐                            │
   │ MinIO (remote, прод)    │  bucket karaoke — public    │
   │ 188.127.240.124:9000    │  bucket stemjobs — private  │
   └─────────────────────────┘
```

**Направление канала — admin → prod** (обоснование: research D3/Ф6): прод-`karaoke-web`
не достигает admin-машины (firewall), поэтому канал server-to-server для приватных объектов
может идти только в этом направлении — как уже работают `/raw`, `/ack` и `/changerecords`.

## Структура изменений

### `karaoke-app` (admin)

| Файл | Что меняется |
|---|---|
| `model/StemJob.kt` | Константы `STORAGE_BUCKET = "stemjobs"`, `LEGACY_PUBLIC_BUCKET = "karaoke"`, `PUBLIC_STORAGE_BUCKET = "karaoke"`; хелперы `objectKey(jobId, stem, ext)`, `legacyObjectKey(jobId, stem, ext)`, `resultFileName(stem, originalExt)`, `validStemNames(mode)` |
| `StemJobProcessing.kt` | Загрузка в приватный бакет; передача объектов в `karaoke-web` по внутреннему каналу; удаление legacy-объектов при ошибке; логирование |
| `StemJobResultPublisher.kt` (новый) | Чтение объекта из приватного бакета (учётные данные) + передача в `karaoke-web` по `X-Internal-Secret`; повтор при сетевой ошибке |
| `StemJobCleanup.kt` | Удаление объектов из приватного бакета **и** legacy-объектов из публичного; вызов `purge` на `karaoke-web` |
| `StemJobStoragePolicyInitializer.kt` (новый) | Инвариант закрытости при старте: `stemjobs` → private, `karaoke` → public; fail-safe (ошибка не роняет старт) |

### `karaoke-web` (прод)

| Файл | Что меняется |
|---|---|
| `controllers/InternalStemJobController.kt` | Новые эндпоинты `PUT /{id}/files/{fileName}` и `POST /{id}/purge` с тем же fail-safe секретом; логирование отказов |
| `controllers/PublicStemJobController.kt` | Отдача приватного файла из `result-dir` (вместо анонимного чтения через `/minio/`); логирование отказов; формат ответа прежний |
| `services/StemJobResultFiles.kt` (новый) | Единая точка раскладки путей результатов, allow-list имён стемов, `purge` |
| `services/StemJobTempCleanupScheduler.kt` | Уборка каталогов результатов по TTL (24 ч + запас) |
| `src/main/resources/application.yml` | Новый параметр `stemjobs.result-dir` (env `STEMJOBS_RESULT_DIR`) |

### Публичное поведение — что НЕ меняется

- `/minio/karaoke/...` (картинки, превью, mp3 песен) — без изменений;
- `GET /api/public/account/stemjobs/{id}/download` — тот же URL, тот же query-параметр `stem`,
  тот же `Content-Type`/`Content-Disposition`, те же коды отказа (404 на чужое/истёкшее);
- публичный JSON API сайта — без изменений (FR-011).

## Проверки (Definition of Done)

```bash
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:compileKotlin :karaoke-app:compileKotlin --parallel
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:test :karaoke-app:test
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew ktlintCheck
bash tools/check-subagent-isolation.sh
bash tools/check-knowledge-structure.sh
python3 tools/check-doc-references.py
bash tools/check-feature-doc.sh docs/features/*.md
git diff | grep -iE 'secret\s*=\s*["'"'"'][0-9a-zA-Z]{16,}'   # должно быть пусто
```

Фронтенд не трогается (`webvue3`, `karaoke-public` — без изменений), nginx не меняется.

## Риски и смягчения

| Риск | Смягчение |
|---|---|
| Канал передачи объектов недоступен (сеть/секрет) | 3 попытки с паузой; при исчерпании — задание `ERROR` с понятным сообщением, объекты в бакете удаляются (не остаются «обещанными, но недоступными») |
| Копия результата потеряна при перезапуске контейнера `karaoke-web` | `STEMJOBS_RESULT_DIR` рекомендуется направить на постоянный том (quickstart, шаг 4) |
| Старые объекты в публичном бакете `karaoke` ещё до 24 ч читаются анонимно | Уборка удаляет legacy-объекты вместе с новыми (research D1/D7); в отчёте — отдельная строка для владельца |
| Ошибка связи с MinIO при старте `karaoke-app` | Fail-safe: ошибка логируется, старт приложения не блокируется |
| Случайное расширение публичной политики бакета `karaoke` | Политика `karaoke` сегодня — `s3:GetObject` на весь бакет; `setBucketPublic` записывает ровно её (без изменений, FR-002) |

## Сложность (Complexity Tracking)

- Новых абстракций — минимум: одна (`StemJobResultFiles` на web) и один компонент
  (`StemJobStoragePolicyInitializer` на app).
- Новых сетевых сущностей — ноль; используется существующий HTTP-канал admin→prod.
- Новых секретов — ноль (решение владельца №3).
- Новых зависимостей (библиотек) — ноль.
- Транзакционность применения SQL не затрагивается (BR-012 — вне задачи).
