# Отчёт OpenProject #407 — `PKG-SEC-CHMOD` (права на файлы: `chmod 666/777`)

> **Пакет**: `PKG-SEC-CHMOD`, severity S3, волна E (короткий цикл).
> **Бэклог аудита**: [#336](https://openproject.local/work_packages/336) (`specs/336-code-audit/backlog.md`, блок `### PKG-SEC-CHMOD`).
> **Ветка**: `603-pkg-sec-chmod`, база — `origin/master` @ `b3d4455a`.
> **Worktree**: `/home/nsa/Karaoke/.worktrees/waveE-sec-chmod` (главная копия не изменялась).
> **Дата**: 2026-10-25.
> **Записи**: BR-115, BR-247.

## Что сделано (по записям)

| BR | Где было | Механизм до | Стало |
|---|---|---|---|
| **BR-115** | `karaoke-app/.../UtilsPictures.kt:566` + 12 мест `:63, 102, 213, 287, 354, 440, 506, 535, 552, 652, 851, 1015` (P-08-06) | каталог публикации картинок — `chmod 777`; каждый файл/превью — `chmod 666` через внешний процесс | каталог — `setPublishedDirectoryPermissions()` = `755`; файлы — `setPublishedFilePermissions()` = `644`; права ставит JVM (`Files.setPosixFilePermissions`), внешнего `chmod` нет |
| **BR-247** | `karaoke-app/.../KaraokeProperties.kt:99` (запись) и `:294–302` (резервная копия) (P-05-04) | `chmod 666` на `Karaoke.properties` (токены VK/Telegram/Whisper/internal-secret в Base64); `.bak` создавался `copyTo` с umask-овыми `644` | `setSecretFilePermissions()` = `600`; `600` ставится на временный файл **до** `ATOMIC_MOVE`, `.bak` тоже `600` и публикуется через временный файл |

Ключевой шов — три хелпера в `karaoke-app/.../Extentions.kt`
(`setPublishedFilePermissions`, `setPublishedDirectoryPermissions`,
`setSecretFilePermissions`) поверх общего `applyPosixPermissions()`. Ошибка
прав логируется и не прерывает вызывающий код (поведение прежнего
`setSongFilePermissions`, тикет #213). `setSongFilePermissions` сохранён и
делегирует в общий код (`644`), его тест `SetSongFilePermissionsTest` не менялся.

### BR-115 — почему это опасно и почему именно `644`/`755`

`APP_FOLDER_*` смонтированы одновременно в `karaoke-app` и `karaoke-webvue3`
(`deploy/docker-compose-app.yml:48–53`, `deploy/docker-compose-webvue3.yml:9–14`).
`666` даёт право **записи** любому пользователю хоста и любому процессу в любом
контейнере с этим томом: опубликованную картинку можно подменить/подложить, не
имея прав владельца, — снимается целостность артефакта, который отдаётся сайтом
и плеером. `777` на каталоге позволяет ещё и создавать/удалять в нём файлы.

`644` оставляет файл читаемым всем — это **объективно нужно**: статику отдаёт
nginx в другом контейнере (другой UID). Право записи при этом только у
владельца-процесса `karaoke-app`, который единственный эти файлы и создаёт.
`755` на каталоге оставляет чтение и обход (нужны nginx), убирая запись для всех.
Широких прав «по объективной причине» не осталось, поэтому ничего не расширял.

### BR-247 — почему `600`, а не `640`

В файле лежат `vkAccessToken`, `vkClientSecret`, `vkIdRefreshToken`,
`telegramBotToken`, `telegramVlessUuid`, `whisperApiKey`,
`stemJobsInternalSecret`, `requestIamToken`; Base64 — кодирование, не шифрование.
Читает и пишет файл только процесс `karaoke-app` (у `karaoke-web` своя
Spring-конфигурация, этот файл он не читает; host-инструменты правит админ).
Поэтому группа не нужна — `600`. Резервная копия `.bak` содержит те же секреты,
поэтому получает те же `600`; копия пишется во временный файл, получает права и
только потом публикуется под именем `.bak` — окна «`.bak` уже существует, но
читаем всем» больше нет.

## Guard «до/после»

Добавлен `tools/check-file-permissions.sh`: статически сканирует
`karaoke-app/src/main`, `karaoke-web/src/main`, `deploy`, `tools` и падает на
`chmod` с битом others-write (`666`, `777`, `667` и т.п.), а также на
символических `o+w`/`a+w`/`chmod +w`. Строки-комментарии и KDoc не считаются.
Baseline (`tools/check-file-permissions.baseline`, ключ = путь + sha256
содержимого строки) содержит только legacy-места **других** пакетов #336
(BR-271/#397 и записи, ещё не разложенные по пакетам); новые нарушения не
допускаются. Есть `--self-test` (плохой пример → exit 1, чистый → exit 0) и job
`file-permissions-guard` в `.github/workflows/lint.yml`.

### До правки (файлы из `HEAD^` в отдельном корне)

```
$ bash tools/check-file-permissions.sh --root "$tmp"
  ❌ karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt:99 [kotlin-chmod]
  ❌ karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/UtilsPictures.kt:63 [kotlin-chmod]
  ...
  ❌ karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/UtilsPictures.kt:566 [kotlin-chmod]
Найдено chmod с правом записи для всех: 14 (покрыто baseline: 0)
exit=1
```

14 = 13 мест в `UtilsPictures.kt` (12×`666` + 1×`777`) + 1 место в
`KaraokeProperties.kt` (`666`).

### После правки

```
$ bash tools/check-file-permissions.sh
OK: chmod с правом записи для всех нет (покрыто baseline: 101 совпадений)
exit=0
```

`101` — совпадения в 67 строках legacy-кода других пакетов (одна и та же строка
может встречаться в файле несколько раз). В файлах пакета `chmod` не осталось.

## Тесты

- `FilePermissionsTest` (4 теста, `karaoke-app/src/test/...`): опубликованный
  файл — `644`; каталог — `755`; файл с секретами — `600`; после двух записей
  `writePropertiesFileAtomic` и сам `Karaoke.properties`, и `.bak` — `600`.
- Полный `:karaoke-app:test`: **569 тестов, 0 падений** (headless).
- `:karaoke-web:test`: **173 теста, 0 падений**.

## Проверки

| Проверка | Команда | Результат |
|---|---|---|
| karaoke-app tests | `GRADLE_USER_HOME=… ./gradlew … :karaoke-app:test` | `BUILD SUCCESSFUL`, 569 тестов, 0 падений (с `DISPLAY=`) |
| karaoke-app tests (как в брифе, `DISPLAY=:0` из окружения) | то же без снятия `DISPLAY` | 4 падения только в `VkPreviewWarmupClientTest`: `java.awt.AWTError: Can't connect to X11 window server using ':0'` — sandbox без X-сервера, к пакету не относится (тот же env-эффект зафиксирован в `specs/383-pkg-sec-sqli/report.md`) |
| karaoke-web tests | `… :karaoke-web:test` | `BUILD SUCCESSFUL`, 173 теста, 0 падений |
| ktlint | `… ktlintCheck` | `BUILD SUCCESSFUL` |
| guard file permissions | `bash tools/check-file-permissions.sh` / `--self-test` | exit 0 / `OK: self-test пройден` |
| knowledge structure | `bash tools/check-knowledge-structure.sh` | `OK: knowledge/ structure valid (9/9 checks passed)` |
| knowledge lint | `python3 tools/lint-knowledge.py --baseline config/knowledge/baseline-knowledge-lint.txt` | `All documentation passes … checks` |
| doc references | `python3 tools/check-doc-references.py` | `OK: битых ссылок, несуществующих путей и эндпоинтов нет` |
| recordhash | `python3 tools/check-recordhash-consistency.py` | `OK recordhash-контракт` |
| no MP4 | `bash tools/check-no-mp4-mentions.sh` | `OK: all 139 MP4 mentions are in baseline` |
| feature doc | `bash tools/check-feature-doc.sh docs/features/*.md` | все 44 документа валидны |
| subagent isolation | `bash tools/check-subagent-isolation.sh` | `Все commits … уникальны` |

## Изменённые файлы

- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Extentions.kt` — хелперы
  прав (`644`/`755`/`600`), общий `applyPosixPermissions`.
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/UtilsPictures.kt` — BR-115:
  13 вызовов `chmod` → хелперы.
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt` —
  BR-247: `chmod 666` убран, `600` на временный файл и `.bak`.
- `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/FilePermissionsTest.kt` —
  регрессия прав.
- `tools/check-file-permissions.sh`, `tools/check-file-permissions.baseline` —
  guard и его baseline.
- `.github/workflows/lint.yml` — job `file-permissions-guard` (+ self-test).
- `knowledge/guidelines/architecture-conventions.md` — таблица прав, запрет
  `runCommand(chmod)`, границы миграции, enforcement guard'ом.
- `knowledge/domains/processing/components/karaoke-properties.md` — `600` для
  файла и `.bak` вместо `chmod 666`.
- `knowledge/domains/catalog/components/pictures.md` — `644`/`755` для картинок
  из `UtilsPictures.kt` (и пометка, что `Pictures.saveToDisk` — legacy другого BR).

Per-feature документ (FR-009) не заводился: пакет не меняет поведение фичи, а
приводит права к безопасным; SSoT обновлён в `knowledge/`.

## Границы

Не трогал: `specs/336-code-audit/registry.csv` (ведёт лид), чужие пакеты/BR,
деплой/контейнеры/прод/БД, главную копию `/home/nsa/Karaoke`. Публичные
контракты и формат `Karaoke.properties` не менялись — только режим файла и
способ его выставления.

## Что осталось за владельцем

1. **Остальные `chmod 666/777` (101 совпадение / 67 строк) в чужих зонах:**
   `KaraokeProcess.kt`, `Song.kt`, `Utils.kt`, `Functions.kt`,
   `CleanupJournal.kt`, `UtilsPictures`-соседи, `deploy/create_clear_db.sh`.
   Часть затрагивает рендеринг (Kdenlive/ffmpeg читают файлы), поэтому менять
   их «по коду» нельзя — это отдельные записи #336 (в т.ч. BR-271/#397).
   Guard фиксирует их в baseline: новые не появятся, старые уйдут вместе со
   своими пакетами (baseline можно только сокращать).
2. **BR-249 (save-swallows, #397):** `savePropertiesMap` по-прежнему глотает
   ошибку записи (`println`) и `set()` не возвращает результат; требование
   «5xx + SLF4J» — в том пакете. Здесь убран только ложный сбой «не удалось
   сохранить» из-за `chmod` (его больше нет).
3. **Секреты в файле, а не в env/secret-store** (инвариант integration #2):
   `600` закрывает локальный доступ, но Base64 не шифрует. Перенос токенов в
   env — отдельное решение владельца (меняет деплой).

## Хэши

- Коммит: `bed21c1e` — `fix(sec): права 644/755/600 вместо chmod 666/777 (BR-115/BR-247, #407)`.
- `Extentions.kt` — sha256 `68b091bab7c58bf3…`
- `UtilsPictures.kt` — sha256 `04e5c036b833d341…`
- `KaraokeProperties.kt` — sha256 `b839b22e11604c06…`
- `FilePermissionsTest.kt` — sha256 `721a3f22189f913e…`
- `tools/check-file-permissions.sh` — sha256 `1e8247cdc44297af…`
- `tools/check-file-permissions.baseline` — sha256 `0bc794c2eb6a831d…`
