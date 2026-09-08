# Phase 1 Quickstart: Настраиваемый таймаут между поисковыми запросами (iter #316)

**Branch**: `316-search-timeout-configurable` | **Date**: 2026-09-08 (rev 3: UI fix + авто-подбор)
**Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

## Цель

Runnable validation scenarios — как доказать, что фича работает end-to-end (для **обоих** UI потоков: кнопка «Песни» + импорт папки + авто-подбор через лог и SSE).

## Prerequisites

1. **KaraokeProperties файл существует** на сервере (`/sm-karaoke/system/Karaoke.properties`, base64-encoded, НЕ БД-таблица — `KaraokeProperties.kt:25`, `:18` `PATH_TO_KARAOKE_PROPERTIES_FILE` + `:40-42` decode + `:70` encode; **Constants.kt:149 не существует** — это rev 1 `WEBVUE_PROPERTIES_FILE_PATH` (Кирилл Р-4)). Проверка:
   ```bash
   docker exec karaoke-app bash -c 'grep -a lyricsSearchTimeoutSeconds /sm-karaoke/system/Karaoke.properties || echo "нет ключа"'
   ```
2. **karaoke-app пересобран** с новым полем в `Karaoke.kt` + регистрацией в `KaraokeProperties.kt` + 2 точки внедрения паузы + endpoint + SSE notification:
   ```bash
   ./gradlew :karaoke-app:bootJar --parallel
   ```
3. **webvue3 пересобран** с новыми полями в `customConfirmParams.fields` (SongsTable.vue + HomeView.vue):
   ```bash
   cd webvue3 && npm run build
   ```
4. **Container restart** (только владелец).

## Governance (NON-NEGOTIABLE)

- Агенты НЕ перезапускают контейнер `karaoke-app`. Smoke-test только владелец.
- Smoke-test реального БД через curl — только владелец.
- Visual verify — только владелец.

## Validation Scenarios

### Scenario 1: Первый запуск — default 10 секунд (US1 AS-1)

**Шаги**:
1. Открыть admin-SPA → компонент «Песни».
2. Убедиться что `KaraokeProperties` НЕ содержит ключ `lyricsSearchTimeoutSeconds` (или содержит, но значение корректное):
   ```bash
   docker exec karaoke-app bash -c 'grep -a lyricsSearchTimeoutSeconds /sm-karaoke/system/Karaoke.properties || echo "нет ключа"'
   ```
3. Нажать кнопку массового поиска (например, «Найти тексты для всех песен» в `SongsTable.vue`).
4. **Expected**:
   - Открывается **существующая** модалка «Подтвердите поиск текста» (НЕ новая модалка!).
   - В модалке **два** поля: «Движок поиска» (select) и **«Таймаут (сек)»** (text input, default = `10`).
   - Ввести `12`, нажать «Да».
   - Backend `getSearchSongTextAll` запускает поиск, между `getLyricsSearch` запросами `Thread.sleep(12000)`.
   - В `KaraokeProperties` появляется ключ `lyricsSearchTimeoutSeconds` со значением `12` (через API write).

### Scenario 2: Повторный запуск — default = последнее введённое (US1 AS-2)

**Шаги**:
1. После завершения Scenario 1 (значение `12` сохранено в KaraokeProperties).
2. Открыть admin-SPA снова.
3. Нажать кнопку массового поиска в «Песни».
4. **Expected**: в поле «Таймаут (сек)» default = `12` (read из backend через `GET /api/lyrics-search-timeout`).

### Scenario 3: Validation — отвергает невалидный ввод (US1 AS-3)

**Шаги**:
1. Открыть диалог массового поиска.
2. В поле «Таймаут (сек)» ввести `0` → нажать «Да».
3. **Expected**: диалог показывает ошибку «Таймаут должен быть положительным целым числом ≥ 1», default остаётся `12`.
4. Ввести `-3` → нажать «Да» — то же поведение.
5. Ввести `abc` → нажать «Да» — то же поведение.
6. Ввести `12` → нажать «Да» — диалог закрывается, поиск запускается.

### Scenario 4: Импорт файлов из папки — таймаут применяется (US1 AS-5, FR-007 путь B)

**Шаги**:
1. Перейти на `HomeView.vue`, кнопка «Добавить файлы из папки».
2. Открывается **существующая** модалка «Добавление файлов из папки» с информацией о формате.
3. В модалке теперь есть **поле «Таймаут (сек)»** (text input, default = `12` из KaraokeProperties).
4. Ввести `7`, нажать «Да».
5. **Expected**:
   - Backend `doCreateFromFolder` запускает пул `lyricsSearchExecutor` (4 потока), но ПЕРЕД каждым `submit` (внутри `if (!textResolved)`) делает `Thread.sleep(7000)`.
   - Между submit'ами проходит ≥ 7 секунд (проверяется через логи KaraokeApp).
   - После завершения — `KaraokeProperties` содержит `lyricsSearchTimeoutSeconds = 7`.

### Scenario 5: Две точки входа — единое значение (FR-007, SC-006)

**Шаги**:
1. Ввести таймаут `15` через диалог из `SongsTable.vue` (Путь A — searchsongtextall).
2. Завершить поиск.
3. Перейти на `HomeView.vue`, кнопка «Добавить файлы из папки».
4. **Expected**: в поле «Таймаут (сек)» default = `15` (то же значение, single-user, KaraokeProperties).
5. Наоборот: ввести `20` через HomeView.vue → SongsTable.vue показывает `20`.

### Scenario 6: Properties UI без перезапуска (SC-007)

**Шаги**:
1. Открыть Karaoke Properties UI (стандартный, ~150 параметров), найти `lyricsSearchTimeoutSeconds`.
2. Изменить значение с `15` на `30`, сохранить.
3. Без перезапуска karaoke-app — открыть admin-SPA, нажать кнопку массового поиска.
4. **Expected**: в поле «Таймаут (сек)» default = `30` (read в момент запуска, FR-008).

### Scenario 7: Авто-подбор — `minIntervalMs` в логе и SSE (US1 AS-6, FR-009/FR-010, SC-008)

**Шаги**:
1. Открыть список песен без текста (≥ 5 шт).
2. Запустить массовый поиск с таймаутом `5` (5 секунд).
3. **Expected** (после завершения цикла):
   - В `docker logs karaoke-app | grep lyrics-search-summary` есть строка формата:
     ```
     [lyrics-search-summary] path=A count=5 successful=5 minIntervalMs=5001 totalDurationMs=25005
     ```
     (или похожие — `minIntervalMs` ≥ `timeout * 1000`).
   - В admin-SPA через SSE приходит событие `MASS_SEARCH_SUMMARY` (enum value `"massSearchSummary"`) с тем же payload (если реализовано `case SseNotificationType.MASS_SEARCH_SUMMARY` в `App.vue`).
4. Если все запросы упали (0 успешных): `successful=0`, `minIntervalMs=null`.
5. Если только 1 успешный: `successful=1`, `minIntervalMs=null` (нет пары).
6. Если ≥ 2 успешных: `minIntervalMs` — реальное минимальное время между двумя успехами.

## 5-step Verification (NON-NEGOTIABLE, канон brief.md:59-63)

После каждого изменения кода (агент):

```bash
# 1. Backend compile: app+web
./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel

# 2. Линтеры: ktlint + npm lint
./gradlew :karaoke-web:ktlintCheck
cd webvue3 && npm run lint && cd ..

# 3. Backend bootJar
./gradlew :karaoke-web:bootJar --parallel
# На nsa-i9 под nsa: также :karaoke-app:bootJar

# 4. Frontend Vite: build + format:check
cd webvue3 && npm run build && npm run format:check && cd ..

# 5. Docker-образы: build_webvue3 (+ build_public если менялся)
cd deploy && bash do.sh build_webvue3
```

> На nsa-i9 под nsa допустим 3-step subset (compile + lint + bootJar) для правок только в `karaoke-app`/`karaoke-web`. **Шаги 2/4/5 для `karaoke-public` — no-op**, пока `karaoke-public` не менялся (FR-005 в spec: scope только admin bundle).

## Git workflow (FR-028)

- Вся работа в working tree до самого финала.
- 0 коммитов/push'ей.
- Финальный commit + push + merge — ТОЛЬКО по прямому указанию владельца.

— Илья (boss)
