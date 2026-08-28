# Quickstart: Переименование `sett`/`settings` → `song` (260-rename-sett-vars)

**Input**: [`spec.md`](./spec.md), [`research.md`](./research.md), [`data-model.md`](./data-model.md), [`contracts/contracts.md`](./contracts/contracts.md)
**Дата**: 2026-08-28

> Это **валидационный сценарий**, не руководство по implementation. Каждый сценарий проверяет один аспект спеки. Прохождение всех сценариев означает готовность к деплою.

## Предусловия

1. **Окружение**: машина, на которой разрешено пересобирать/перезапускать `karaoke-app` (см. AGENTS.md «Ограничения и доступы агента»: обычно `dev-pc` под пользователем `dev`; иначе — запросить согласия пользователя на `karaoke-app` rebuild).
2. **Локальные Postgres/MinIO**: в рабочем состоянии (стандартный `deploy/do.sh start_*` для `karaoke-web`, `karaoke-public`, MinIO).
3. **Ветка**: `260-rename-sett-vars` (создана через `tools/specify-bootstrap.sh`).
4. **Baseline grep-проход** выполнен (`tasks.md` T002) — зафиксированы baseline-числа.
5. **Baseline исключений** подтверждены (`tasks.md` T003) — `KaraokePlatform.settingsField*`, `LS_SETTINGS_KEY`, `@KaraokeDbTableField(name = "settings_id")`, `SyncTarget.key = "settings"`, `SubsEdit.vue:183`, `tbl_public_settings`.

## Сценарий 1 — Все Kotlin-файлы собираются (FR-009, SC-003)

```bash
# Из корня репозитория
cd /home/nsa/Karaoke

./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel 2>&1 | tee /tmp/compile.log
```

**Ожидаемый результат**: `BUILD SUCCESSFUL`, 0 ошибок компиляции. Если есть ошибки — вероятнее всего пропущено переименование `sett`/`settings` в каком-то использовании в теле функции. Исправить точечно и повторить.

**Дополнительно** (полная сборка bootJar):
```bash
./gradlew clean :karaoke-app:bootJar :karaoke-web:bootJar --parallel 2>&1 | tee /tmp/bootJar.log
```

## Сценарий 2 — ktlint и ESLint без новых нарушений (FR-010, SC-004)

```bash
# Backend lint (есть baseline, не должно быть новых строк)
./gradlew :karaoke-app:ktlintCheck :karaoke-web:ktlintCheck 2>&1 | tee /tmp/ktlint.log

# Frontend lint (есть baseline, не должно быть новых строк в .eslint-baseline.json)
tools/check-eslint-baseline.sh karaoke-public 2>&1 | tee /tmp/eslint.log
cd karaoke-public && npm run lint 2>&1 | tee /tmp/npm-lint.log && cd ..
```

**Ожидаемый результат**:
- `ktlintCheck` — `BUILD SUCCESSFUL`, `baseline-karaoke-app.xml` и `baseline-karaoke-web.xml` НЕ изменились (`git diff -- config/ktlint/` пуст).
- `npm run lint` в `karaoke-public` — `0 errors`, `0 warnings`. (Переименование итератора не должно ничего сломать.)
- Если `check-eslint-baseline.sh` показывает «N new violations» — это нарушения, не относящиеся к переименованию, расследовать отдельно.

**Аварийный откат**: если новые нарушения появились И они связаны с переименованием (например, `max-line-length` где строка стала длиннее из-за `targetSong`/`renderSong`) — обновить baseline-файл через `./tools/update-eslint-baseline.sh` ИЛИ сократить имя.

## Сценарий 3 — Grep-проход для SC-001

```bash
cd /home/nsa/Karaoke

# Должно вернуть 0 совпадений вне исключений (FR-007)
grep -rn '\bsett\b' \
  --include='*.kt' --include='*.html' --include='*.js' --include='*.vue' --include='*.ts' --include='*.sql' \
  --exclude-dir=build --exclude-dir=node_modules --exclude-dir=.git --exclude-dir=dist \
  karaoke-app karaoke-web karaoke-public

# То же без word-boundary — поймать class/id/data-* с подстрокой «sett»
grep -rn 'sett' \
  --include='*.kt' --include='*.html' --include='*.js' --include='*.vue' --include='*.ts' --include='*.sql' \
  --exclude-dir=build --exclude-dir=node_modules --exclude-dir=.git --exclude-dir=dist \
  karaoke-app karaoke-web karaoke-public
```

**Ожидаемый результат**:
- Первая команда — `0` строк (всё переименовано в `song`).
- Вторая команда — только строки, явно разрешённые FR-007:
  - `webvue3/src/components/Songs/edit/SubsEdit.vue` (вне scope, не нашлась в этом grep — `webvue3` отдельный модуль).
  - `karaoke-public/src/player/KaraokePlayer.js` (настройки плеера).
  - `webvue3/src/player/KaraokePlayer.js` (настройки плеера).
  - `karaoke-web/src/main/resources/templates/testpage.html` — нужно проверить в baseline, что именно там (может быть CSS-class или комментарий; в расчёте `sett`-baseline не учитывалось — нужно проверить).
  - Любые `tbl_public_settings` ссылки (не Song).
  - Любые `settingsField*` в `KaraokePlatform.kt` (не Song).

**Если найдено что-то новое** — это баг реализации, исправить и повторить.

## Сценарий 4 — Grep-проход для SC-002

```bash
cd /home/nsa/Karaoke

# Должно вернуть 0 совпадений для Kotlin-полей типа Song
grep -rn '\bsettings\b\|: settings\|^[[:space:]]*\(val\|var\) settings\b' \
  --include='*.kt' \
  --exclude-dir=build --exclude-dir=node_modules --exclude-dir=.git --exclude-dir=dist \
  karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp \
  karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb
```

**Ожидаемый результат**: только строки из списка исключений FR-007 (см. baseline).

## Сценарий 5 — KDoc/JSDoc обновлены (SC-005, FR-008)

```bash
cd /home/nsa/Karaoke

# 0 совпадений устаревшего tbl_settings в KDoc/JSDoc
grep -rn 'tbl_settings' \
  --include='*.kt' --include='*.js' --include='*.vue' \
  --exclude-dir=build --exclude-dir=node_modules --exclude-dir=.git --exclude-dir=dist \
  karaoke-app karaoke-web karaoke-public webvue3 2>/dev/null | head -20
```

**Ожидаемый результат**: 
- В исходниках `karaoke-app`/`karaoke-web`/`karaoke-public` — 0 совпадений вне `deploy/karaoke-db/` (SQL-миграции — отдельный мир).
- В `karaoke-web/.../dto/ZakromaPublicDto.kt:9, 19` и `karaoke-web/.../services/ShareLinkSweeper.kt:130` — строки заменены на `tbl_songs`.

## Сценарий 6 — Ручная проверка legacy админки `karaoke-app` (SC-006)

> Это **UI-проверка в браузере**, не автоматизированная (по правилу проекта «тестов в CI нет»).

### 6.1 Подготовка

```bash
# Пересобрать локально (если не на dev-pc — запросить согласия на rebuild)
cd /home/nsa/Karaoke
./gradlew clean :karaoke-app:bootJar --parallel
# На dev-pc или с согласия — перезапустить локальный контейнер
cd deploy && bash do.sh build_start_karaoke-app  # или эквивалент
```

Открыть legacy админку (URL зависит от конфигурации; обычно `http://localhost:8080` или `http://<deploy-host>`).

### 6.2 Сценарий

1. Открыть страницу `/songs` (legacy админка со списком песен).
2. **Ожидаемо**: таблица песен отрисовывается — каждая строка показывает `id`, `songName`, `author`, `year`, `album`, `track`, цвет фона (`color`).
3. Если некоторые поля показывают пустые значения или строки одинакового цвета — `model.addAttribute("song", ...)` не сработало или шаблон не обновлён. Проверить `git log -p` для релевантных файлов.

### 6.3 Дополнительно

Открыть страницу `/filter` или `/zakroma` если они есть. Та же логика: список песен отрисовывается без пустых полей.

## Сценарий 7 — Smoke-test `karaoke-public` после деплоя (SC-007)

> Тоже UI-проверка в браузере. Поскольку `karaoke-public` деплоится отдельно от backend (FR-014), нужно дождаться deploy `karaoke-public` после мержа.

```bash
cd /home/nsa/Karaoke
cd deploy && bash do.sh build_start_public  # или эквивалент
```

Открыть публичный сайт.

### 7.1 Сценарий

1. Открыть страницу `/search` и ввести в поиске имя любой песни (`Song.loadFromDbById(...)` существующей).
2. **Ожидаемо**: список песен отрисовывается с корректными `author`, `year`, `album`, `track`, `songName`, корректным цветом флага и кнопками корзины/избранного.
3. Открыть `/zakroma/<authorId>` — список песен в альбомах отрисовывается.
4. Если какой-то `v-for` рендерит пустые строки или ошибки в JS-консоли — Vue-итератор не был переименован в каком-то файле.

## Сценарий 8 — Smoke-test SQL в `StatBySong.kt`

```bash
cd /home/nsa/Karaoke

# Прогон через KDoc-style Kotlin REPL не нужен; достаточно визуальной проверки:
grep -n 'from\|join' karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/StatBySong.kt | head -20
```

**Ожидаемый результат**: строки `left join tbl_songs song on e.song_id = song.id` и `left join tbl_songs song on song.id = e.song_id` присутствуют. Алиас `sett` не встречается.

> При полном rebuild `karaoke-app` (Сценарий 1) тесты в этой области Kotlin-кода не запускаются (по правилу проекта). Реальная проверка — после деплоя в UI «Закрома» (сценарий 7.1): если альбомы/песни отрисовываются — SQL-фетч работает.

## Сценарий 9 — Финальная атомарность (SC-007)

> Подразумевает, что PR готов к мёрджу как **один атомарный шаг** (FR-013/FR-014):

```bash
cd /home/nsa/Karaoke

# Все изменения в одной ветке
git rev-parse --abbrev-ref HEAD  # → 260-rename-sett-vars

# Все Kotlin-файлы:
git diff --stat master -- '*.kt'  # показать только значимые изменения

# Все Thymeleaf-шаблоны + Vue-компоненты:
git diff --stat master -- '*.html' '*.vue' '*.js'

# Все SQL-строки (находятся в Kotlin-коде):
git diff --stat master -- '*.kt' | xargs -I {} grep -l 'from tbl_songs\|join tbl_songs' {} 2>/dev/null
```

**Ожидаемый результат**:
- В одной ветке все правки — frontend, backend, шаблоны, SQL.
- Diff показывает чистые переименования (строки `-sett`+`+song`), без смешения с другой работой.

## Сводка сценариев

| # | Проверка | Автоматизирован? | Что подтверждает |
|---|---|---|---|
| 1 | Gradle compile + bootJar | ✅ | FR-009, SC-003 |
| 2 | ktlint / ESLint baseline | ✅ | FR-010, SC-004 |
| 3 | Grep `sett` (нет за пределами исключений) | ✅ | SC-001, FR-015 |
| 4 | Grep `settings` Kotlin (нет за пределами исключений) | ✅ | SC-002, FR-015 |
| 5 | Grep `tbl_settings` в KDoc/JSDoc | ✅ | SC-005, FR-008 |
| 6 | UI legacy админки (`/songs`, `/filter`, `/zakroma`) | ❌ ручная | SC-006, FR-013 |
| 7 | UI `karaoke-public` (`/search`, `/zakroma`) | ❌ ручная | FR-004, FR-014 |
| 8 | SQL алиас в `StatBySong.kt` | ✅ (грепом) | FR-005 |
| 9 | Атомарность — одна ветка, один PR | ✅ (git diff) | SC-007, FR-013/014 |

Все ✅-сценарии проходят в CI/locally. ❌-сценарии — пользователь вручную (по правилу проекта «тестов в CI нет»).

## Done When

Все 9 сценариев помечены «PASS», плюс:
- [ ] `git commit` со всеми правками (или цепочка логических коммитов).
- [ ] PR создан по правилам AGENTS.md «CI-gate для master».
- [ ] CI `lint.yml` зелёный.
- [ ] После мержа: хэш коммита(ов) добавить в `.git-blame-ignore-revs` (Constitution VII.2), прецедент спеки 102 T046.
