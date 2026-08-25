# Research: 238 — Импорт из папки: родители только у того же автора + автообложка альбома

**Дата**: 2026-08-25
**Спека**: [spec.md](spec.md)
**План**: [plan.md](plan.md)

## Цель

Подтвердить технический подход для двух изменений в общей функции импорта из папки (`Song.createFromPath`):

1. **Поиск «родителя» только у того же автора** — убрать fallback `findDuplicateOriginal()` на песни других авторов.
2. **Автообложка нового альбома из графического файла в `rootFolder`** — обрезка 1:1, масштабирование 400×400, сохранение `LogoAlbum.png` + превью + запись в `Pictures` (локальное и удалённое хранилище).

Никаких внешних сервисов / новых технологий — обе фичи реализуются в существующих модулях через переиспользование уже существующих утилит.

## Резолюция NEEDS CLARIFICATION

В спеке нет маркеров `[NEEDS CLARIFICATION]`. Все три спорных момента были зафиксированы через `/speckit.clarify` (Q1-Q3) и записаны в `## Clarifications` спеки.

## Исследованные области

### R1 — Текущая логика поиска «родителя» (`findDuplicateOriginal`)

**Decision**: Точечное изменение `findDuplicateOriginal()` в `karaoke-app/.../Utils.kt:4256` — оставить **один** вызов `findId(sameAuthorOnly = true)`, удалить `?: findId(sameAuthorOnly = false)`. Логика поиска «того же автора» уже параметризована флагом `sameAuthorOnly` внутри функции `findId` — нужно просто перестать делать второй запрос с `false`.

**Rationale**: Минимальное, точечное изменение в одном файле (10 строк). Вся остальная логика (нормализация имени через `normalizeSongNameForSearch`, выбор по `id ASC`, фильтр `TRIM(source_text) <> ''`, загрузка `Song` через `loadFromDbById`) **сохраняется**. Альтернативный вариант — менять вызывающий код (`Song.createFromPath`) и вызывать только «того же автора» — даёт тот же результат, но сложнее для отката и менее явно.

**Alternatives considered**:
- Пометить функцию `findDuplicateOriginal` как legacy и ввести новую `findParentSameAuthor` — overkill: функция уже имеет параметризацию, нужно просто использовать её.
- Полностью удалить функцию — нарушает существующие вызовы (`customFunction` тоже использует похожую логику, но через `findParentCandidateId`, не `findDuplicateOriginal`).

**Подтверждение в коде** (см. [livedocs/architecture-notes-archive.md:282-294](../../livedocs/architecture-notes-archive.md)): текущее поведение `findDuplicateOriginal()` — сначала `sameAuthorOnly=true`, при ненахождении — `sameAuthorOnly=false`. Эту вторую попытку удаляем.

**Затрагиваемые места**: только `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt:4256-4298`.

### R2 — Утилита обхода `rootFolder` для поиска графики

**Decision**: использовать существующую `getListFiles(pathToFolder: String, extensions: List<String>)` (`karaoke-app/.../Utils.kt:2493`). Сигнатура уже принимает список расширений, рекурсивный обход идёт потоково (`Files.walk(...).use { stream -> ... }`), возврат — `List<String>` путей, отсортированных лексикографически. Для подсчёта **только в `rootFolder` (не в подпапках) — нерекурсивно**: использовать `File(rootFolder).listFiles { _, name -> ... }` или `Files.list(Path(rootFolder))` (одноуровневый обход, не `Files.walk`).

**Rationale**: `getListFiles` — рекурсивная (для импорта аудио это нужно), но для автообложки нужен **плоский** обход только в `rootFolder`. Реализация в одну строку через `File(rootFolder).listFiles()`. Не добавляем новых утилит — стандартный JDK API.

**Filter по расширению** (без учёта регистра, см. A-001): `name.substringAfterLast('.', "").lowercase() in listOf("jpg", "jpeg", "png", "webp", "bmp", "tiff")`.

**Исключение скрытых файлов** (см. A-008): `!name.startsWith(".")`.

**Alternatives considered**:
- Использовать `getListFiles(rootFolder, listOf("jpg", ...))` — даст рекурсивный обход (включая `CD1/subfolder/cover.jpg`); для нашей задачи это лишнее, а для много-дискового альбома привело бы к тому, что в `CD1/` будет видна графика из `CD1/subfolder/` и из других дисков (если обход пойдёт глубже). Решено: только плоский обход в каждой `rootFolder` отдельно (см. Q1 — каждый диск имеет свою обложку).

**Затрагиваемые места**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt` (новый private/companion-object метод или inline в `findOrCreateForSongImport`).

### R3 — Кадрирование/масштабирование обложки

**Decision**: переиспользовать `cropCenterSquareAndResize(bytes: ByteArray, targetSize: Int = 400)` (`karaoke-app/.../AlbumCoverFinder.kt:264`). Возвращает `BufferedImage?` (null при ошибке — что и нужно для FR-010).

**Rationale**: Уже реализует exactly то, что просит FR-006:
- Если уже 400×400 — возвращает as-is.
- Иначе вырезает центральный квадрат по короткой стороне.
- Масштабирует до `targetSize` (по умолчанию 400×400).
- При любом исключении — возвращает null + логирует в stdout.

Никаких изменений в `AlbumCoverFinder.kt` не требуется — функция уже реализует ровно нужную семантику.

**Alternatives considered**:
- Использовать `resizeBufferedImage(square, 400, 400)` напрямую — придётся дублировать логику центрального вырезания. Отвергнуто.

**Затрагиваемые места**: только вызов из новой функции (см. R4).

### R4 — Сохранение обложки: файл + `Pictures` + MinIO

**Decision**: переиспользовать существующую логику из `ApiController.saveAlbumCover` (`karaoke-app/.../controllers/ApiController.kt:3357-3417`). Полный пайплайн уже отработан:
1. `cropCenterSquareAndResize(decodedBytes, 400)` → `BufferedImage?`
2. `ImageIO.write(finalImage, "png", File("$rootFolder/LogoAlbum.png"))` + `chmod 666`
3. `song.pictureAlbum` (`Song.kt:732`) — getter, который:
   - Ищет запись `Pictures` по `pictureNameAlbum` (= `"$author - $year - $album"`)
   - Если есть — обновляет `full` (base64 PNG) и сохраняет
   - Если нет — создаёт новую запись через `Pictures.createNewPicture(...)`, которая и пишет в MinIO
   - Этот путь **переиспользуется без изменений** — `saveAlbumCover` именно так и работает.

**Rationale**: уже существующий фронт-энд путь (модалка `AlbumCoverModal` → `POST /api/song/savealbumcover`) идёт **точно так же**. Делаем по аналогии, но **из бэкенда автоматически** при импорте. Никаких изменений в `Pictures.kt`, `MinIO`-логике или `song.pictureAlbum` — всё переиспользуется.

**Извлечение существующего кода в новый helper**: в `karaoke-app/.../model/Album.kt` (companion-object) добавить `internal fun applyAutoAlbumCoverFromFolder(rootFolder: String, author: String, year: Int, album: String, database, storageService, storageApiClient): Boolean`. Эта функция:
1. Плоский обход `rootFolder` (`File.listFiles`).
2. Фильтрация по расширению (`jpg|jpeg|png|webp|bmp|tiff`), не скрытые.
3. Если ровно один кандидат — прочитать байты, `cropCenterSquareAndResize`, записать `LogoAlbum.png` + `chmod 666`.
4. Создать запись `Pictures` (через тот же подход, что и `saveAlbumCover`).
5. Возвращает `true` если обложка создана, иначе `false`.

**Вызов** — из `Album.findOrCreateForSongImport` сразу после создания нового альбома (т.е. только в ветке, где `createNewAlbum(...)` вызвался, а не `getAlbumByAuthorYearName(...)` вернул существующий). Это даёт нам автоматически выполнение FR-009 (только для **новых** альбомов).

**Подпись для FR-005**: нужно знать `rootFolder` каждой песни. `findOrCreateForSongImport` сейчас НЕ принимает `rootFolder`.

**Альтернатива (отвергнута)**: добавить параметр `rootFolder: String?` в существующую `findOrCreateForSongImport` (опциональный — для существующих вызовов остаётся `null`, новая логика автообложки активируется только если он задан). **Отвергнуто**: меняет публичную сигнатуру функции, которую использует `AlbumBackfill` (без автообложки), и размывает контракт «найти или создать альбом для песни» — добавление побочной логики автообложки в существующую функцию нарушает SRP.

**Принятое решение**: ввести новую перегрузку `findOrCreateForSongImportRaw(...)` (возвращает `Pair<Album?, Boolean>` с `isJustCreated`) и публичный `findOrCreateForSongImportWithAutoCover(...)` (оркестратор, который вызывает `Raw` и при `isJustCreated=true` — `applyAutoAlbumCoverFromFolder`). Существующая `findOrCreateForSongImport` остаётся **без изменений** — её использует `AlbumBackfill` (см. `AlbumBackfill.kt:114`), который не должен запускать автообложку для backfill-операций. Вызов из `Song.createFromPath:8064` — передаём `file.parent` (уже вычислен в `createFromPath` как `rootFolder`) в новый `findOrCreateForSongImportWithAutoCover`.

**Alternatives considered**:
- Сохранять `rootFolder` в `Album` — нарушает Constitution § II (сырой JDBC + изоляция схемы) и не нужно (папка вычисляется из песни).
- Делать отдельный эндпоинт `/api/utils/autoalbumcover` для запуска вручную — избыточно, пользователь явно просил «при создании альбома».
- Сохранять обложку отдельно от `Pictures` (только файл на диске) — нарушает FR-007 («загружена в локальное и удалённое хранилище тем же способом, что и обложки, добавленные вручную»), плюс потребует синхронизации `pictureAlbumReady`-флага.

**Затрагиваемые места**:
- `karaoke-app/.../model/Album.kt:484` (`findOrCreateForSongImport`) — добавить опциональный `rootFolder: String?` параметр и вызов `applyAutoAlbumCoverFromFolder`.
- `karaoke-app/.../model/Album.kt` (новый helper `applyAutoAlbumCoverFromFolder` в companion).
- `karaoke-app/.../model/Song.kt:8064` (`createFromPath`) — передать `rootFolder = rootFolder` (file.parent) в `findOrCreateForSongImport`.

### R5 — Где хранится `LogoAlbum.png` для много-дискового альбома

**Decision**: `rootFolder` каждого файла песни (см. Q1). Существующее свойство `Song.pathToFileLogoAlbum` (`Song.kt:575`) уже вычисляет:
- `$rootFolder/$fileName [album].png` — если файл существует
- `$rootFolder/LogoAlbum.png` — иначе
- `${File(rootFolder).parentFile.absolutePath}/LogoAlbum.png` — иначе

Для нашего пути (много-дисковый альбом) используется **второй** вариант: `$rootFolder/LogoAlbum.png` (т.к. в `rootFolder` ещё нет `fileName [album].png` при первом импорте). Это согласуется с Q1: каждый диск получает свой `LogoAlbum.png` в своей `rootFolder`.

**Преview**: `Song.pathToFileLogoAlbumPreview` (`Song.kt:597`) — `pathToFileLogoAlbum.replace(".png", ".preview.png")`. Используем тот же подход.

**Источник `song.pictureAlbum`** (`Song.kt:732`): ключ поиска/создания в `Pictures` — `pictureNameAlbum` (= `"$author - $year - $album"`). Это **один** ключ для всего альбома, независимо от числа дисков. То есть **для много-дискового альбома с разными обложками дисков текущая модель Pictures НЕ поддерживает несколько обложек одного альбома** — `pictureAlbum` будет указывать на последнюю созданную. Это known limitation, не блокер для FR — FR говорит о создании обложки **нового** альбома, а много-дисковый альбом с разными обложками — это edge case, который сейчас явно **не** покрывается. Поведение: для много-дискового альбома обложка создаётся **только для первой обработанной `rootFolder`** (или для всех, но перезатирает общую запись в `Pictures`). Для простоты и предсказуемости — обложка создаётся один раз на альбом, при первом `findOrCreateForSongImport` с заданным `rootFolder`. Это согласуется с FR-009 (не затирать существующие) и логикой `Pictures.getPictureByName`.

**Реализация-упрощение**: в `applyAutoAlbumCoverFromFolder` — после успешного сохранения `LogoAlbum.png` обновить/создать `Pictures` через `song.pictureAlbum` (поскольку `song` уже создан и `saveToDb()` уже вызван в `createFromPath` ДО вызова `findOrCreateForSongImport` — но `song.albumId` ещё не установлен в этой точке). 

**Пересмотр порядка в `Song.createFromPath:8064`**: 
  - Сейчас: `song.albumId = Album.findOrCreateForSongImport(...).id; song.saveToDb()`
  - Нужно: **сначала** вызвать `findOrCreateForSongImport` (он вернёт `Album?`), **потом** сохранить `LogoAlbum.png` (с уже выставленным `albumId`/author/year/album), **потом** `song.saveToDb()`.  
  - Лучше: вызвать `findOrCreateForSongImport` БЕЗ обложки, получить `Album?`, проставить `song.albumId`, **сохранить `song.saveToDb()`** (это нужно для `pictureNameAlbum` через поля `author`/`year`/`album`), **затем** запустить `applyAutoAlbumCoverFromFolder(rootFolder, song.author, song.year, song.album, ...)` — оно прочитает файлы в `rootFolder` и через ту же логику, что и `saveAlbumCover`, создаст `Pictures`.

**Подробный порядок** (новый `Song.createFromPath`):
```
val album = Album.findOrCreateForSongImport(...)  // без обложки
song.albumId = album?.id
song.saveToDb()
// НОВОЕ: если альбом только что создан (album != null и "свежий") — пробуем автообложку
if (album != null && isAlbumJustCreated) {
    Album.applyAutoAlbumCoverFromFolder(rootFolder, song.author, song.year, song.album, database, storageService, storageApiClient)
}
```

Для определения «свежести» альбома: `findOrCreateForSongImport` возвращает `Album?`. Можно сделать вторую перегрузку, возвращающую `Pair<Album?, Boolean>` (был ли создан). Альтернатива — флаг через временную метку или через `album.id > someThreshold` (хак). Самое чистое — изменить сигнатуру `findOrCreateForSongImport` так, чтобы возвращать `Pair<Album?, Boolean>` или data class.

**Decision (детализация)**: изменить сигнатуру `findOrCreateForSongImport` так, чтобы возвращать `Album?` (текущее) + дополнительный boolean через out-параметр или через data class-результат. Для минимизации изменений — ввести новую перегрузку `findOrCreateForSongImportWithAutoCover(rootFolder, ...)`, которая внутри вызывает существующую логику и после возврата Album делает автообложку при необходимости. 

Финальное решение: **новая перегрузка** `findOrCreateForSongImportWithAutoCover` в `Album.kt`, которая принимает `rootFolder` и использует ту же логику, что и `findOrCreateForSongImport`, плюс в конце (если альбом был только что создан) вызывает `applyAutoAlbumCoverFromFolder`. Существующая `findOrCreateForSongImport` остаётся без изменений — её использует `AlbumBackfill` (см. `AlbumBackfill.kt:114`), который не должен запускать автообложку для backfill-операций.

### R6 — Существующие тесты / моки

**Decision**: ручная проверка по `specs/082-fix-import-folder-oom/quickstart.md` (паттерн уже отработан). Существующие юнит-тесты в `karaoke-app/src/test` — `@Disabled` (см. Constitution § «Тесты»), полагаться на них не нужно.

**Rationale**: Constitutional principle — проверка делается пользователем вручную или в production-like окружении. Сценарии 1-6 из спеки покрываются ручным прогоном по `quickstart.md`.

## Сводка решений

| # | Решение | Файл(ы) |
|---|---------|---------|
| R1 | Убрать второй `findId(sameAuthorOnly = false)` в `findDuplicateOriginal` | `karaoke-app/.../Utils.kt` |
| R2 | Плоский обход `rootFolder` через `File.listFiles` (не рекурсивный `getListFiles`) | новый helper `Album.applyAutoAlbumCoverFromFolder` |
| R3 | Переиспользовать `cropCenterSquareAndResize(bytes, 400)` | `Album.applyAutoAlbumCoverFromFolder` |
| R4 | Переиспользовать путь `ApiController.saveAlbumCover` для записи файла + `Pictures` | новый helper в `Album.kt` |
| R5 | Обложка в `$rootFolder/LogoAlbum.png` (согласуется с `Song.pathToFileLogoAlbum`) | `Album.applyAutoAlbumCoverFromFolder` |
| R6 | Ручная проверка по `quickstart.md` (паттерн 082) | новый `quickstart.md` |

## Зависимости / риски

- **Изменение сигнатуры `findOrCreateForSongImport`** — нет, существующая сигнатура сохраняется, новая логика в новой перегрузке `findOrCreateForSongImportWithAutoCover`. Это страхует `AlbumBackfill` и любых других существующих caller'ов.
- **Производительность** — `applyAutoAlbumCoverFromFolder` вызывается **только при создании нового альбома** (не для каждой песни). Один `File.listFiles` + одно чтение одного файла + одна запись PNG + один `Pictures.save()` (через существующий путь) — лёгкая операция, не влияет на общую скорость импорта 10k файлов.
- **Concurrency** — `applyAutoAlbumCoverFromFolder` идёт последовательно в рамках одной `findOrCreateForSongImportWithAutoCover`; параллельные импорты из разных папок работают на разные альбомы (разные ключи `pictureNameAlbum`), конфликтов нет. Внутри одного импорта песни идут последовательно (`createFromPath` — `forEach` цикл), так что race condition'ов тоже нет.
- **Secret-leak** — нет: фича работает с файлами и `Pictures`, не с секретами. Constitution § VIII не затрагивается.

## Что НЕ входит в scope

- Изменение `findParentCandidateId` (`Utils.kt:4314`) — другая функция, используется в `customFunction` (пакетный поиск родителей для песен с `root_id=0`); пользователь явно просил ограничить поиск только в импорте из папки, не в `customFunction`.
- Изменение `markDublicatesPromise` / ручной кнопки «Найти и обработать дубликаты песен автора» — FR-004 запрещает.
- Изменение API/HTTP-контрактов эндпоинтов — фича прозрачна для UI, никаких новых эндпоинтов.
- Изменение Vue/UI компонентов — фича прозрачна для оператора, никаких новых кнопок/модалок.

## Готово к Phase 1

Все NEEDS CLARIFICATION резолвлены (через `/speckit.clarify`). Технический подход определён и подтверждён через анализ существующего кода. Можно переходить к `data-model.md`, `contracts/`, `quickstart.md`.