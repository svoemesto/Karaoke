# Design: fix-prod-runtime-errors-2026-08-09

## Context

См. proposal.md (мотивация) и specs/runtime-errors/spec.md (требования). Здесь — технические решения по каждому из 3 исправлений.

Текущее состояние, которое нужно знать для принятия решений:

- `karaoke-web` и `karaoke-app` деплоятся отдельно (AGENTS.md, секция «Архитектура»). `karaoke-web` ходит к MinIO через nginx-проксю `http://minio-proxy/`, которая сейчас НЕ маршрутизирует `/yookassa/`.
- Spring Boot 3.5.6 + Java 22 (см. баннер в логах). `Date.parse(String)` deprecated, `Timestamp.valueOf(String)` работает только для формата `yyyy-MM-dd HH:mm:ss[.fffffffff]`.
- Шаблон `templates/main.html` (Thymeleaf) — единственное место со старым Thymeleaf-рендерингом; основной UI живёт в `karaoke-public` (Vue 3), который этими ошибками не затронут.
- `KaraokeProperties.kt:30-63` уже имеет try/catch вокруг `readText()` (строка 48-49), но НЕ вокруг `savePropertiesMap()` (строка 61). Это асимметрия, которую и нужно исправить.

## Goals / Non-Goals

**Goals:**
- Минимальный diff (3 файла кода + 1 nginx-конфиг на проде) с максимальным покрытием 3-х ошибок.
- Каждое исправление диагностируемо в логах с первого повторения (без перезапуска контейнера).
- Все фиксы — backward-compatible; никаких новых API или изменений контрактов.

**Non-Goals:**
- Рефакторинг `KaraokeProperties` в две отдельные реализации (для `karaoke-app` и `karaoke-web`) — это большая задача, делаем минимальный try/catch.
- Полный переход с Thymeleaf на Vue (см. основной UI уже в Vue 3 в `karaoke-public`). Только точечный фикс строки 200 в `main.html`.
- Аудит nginx-конфигурации `minio-proxy` целиком — фиксируем только `/yookassa/`.
- Anti-fraud для ЮKassa recurring (отдельный эпик, см. AGENTS.md, Q&A про trial).

## Decisions

### Fix 1: ЮKassa — логирование statusCode в `chargeRecurring`

**Решение**: расширить `catch (e: Exception)` в `PaymentService.chargeRecurring` (`karaoke-web/src/main/kotlin/.../services/PaymentService.kt:265-268`) отдельной веткой `catch (e: WebClientResponseException)`, в которой логировать `statusCode`, `e.responseBodyAsString` (с ограничением по длине), `sub.id`, `payment_method_id` (для диагностики).

**Альтернативы рассмотренные**:
- **A) Полный retry с fallback (как в `createPayment`)** — отклонено. `createPayment` создаёт новый платёж с `save_payment_method=false`, что для рекуррентного не подходит (нужен именно `payment_method_id`). Retry без изменения body ничего не даст.
- **B) Перехват на уровне nginx** — отклонено. nginx не знает контекста `sub_id`, и тело ответа ЮKassa не пробрасывается в лог контейнера по умолчанию.
- **C) Только логирование без изменений в коде** — отклонено. Нужно явно ловить `WebClientResponseException`, чтобы добраться до `statusCode` и `responseBodyAsString`; `e.message` обрезается до общего описания.

**Параметры логирования**:
- Длина тела: `min(500, responseBodyAsString.length)` — чтобы не раздувать логи на огромных JSONах.
- Уровень: `println(...)` (как существующий код в `PaymentService`), чтобы не тащить SLF4J-зависимость только ради одной строчки.
- Формат: `PaymentService.chargeRecurring: ошибка автосписания для sub=<id> (pm=<paymentMethodId>): statusCode=<code> body=<500 chars>`.

### Fix 2: Thymeleaf `n.publishAt` — парсинг строки

**Решение**: заменить в `karaoke-web/src/main/resources/templates/main.html:200` вызов `#dates.format(n.publishAt, ...)` на inline-парсинг через Thymeleaf-utility-method `T(java.sql.Timestamp).valueOf(...)` ИЛИ (если это сломает другие форматы) на простой `String` slice.

**Альтернативы рассмотренные**:
- **A) Менять `NewsDTO` — поле `publishAt` сделать `java.util.Date`/`Timestamp`** — отклонено. Это каскадное изменение через несколько слоёв (контроллер → DTO → шаблон), затрагивает существующие формы в `webvue3`. Пропорция изменений не оправдана ради одной строки Thymeleaf.
- **B) Использовать `T(java.time.LocalDateTime).parse(...).format(...)`** — частично принято. Подходит ТОЛЬКО если формат ISO `yyyy-MM-ddTHH:mm:ss`. В нашем случае формат `yyyy-MM-dd HH:mm:ss.SSS` (с пробелом и миллисекундами) — нужно конвертировать в `T` и обрезать `.SSS`. Громоздко.
- **C) Inline Thymeleaf `T(java.sql.Timestamp).valueOf(${n.publishAt})`** — принято как основной вариант. `Timestamp.valueOf` принимает формат `yyyy-MM-dd HH:mm:ss[.fffffffff]`, что точно совпадает с тем, что приходит из БД (`Timestamp.toString()`). Сценарий с `null` обрабатывается через `th:if`.
- **D) Хранить дату как preformatted-строку в DTO** — fallback на случай, если C не сработает из-за пустой строки.

**Финальный вариант (C+D)**:
```html
<td th:if="${n.publishAt != null and n.publishAt != ''}"
    th:text="${#dates.format(T(java.sql.Timestamp).valueOf(n.publishAt.toString().replace('T', ' ')), 'dd.MM.yyyy HH:mm')}">
  ...
</td>
```

Это безопасно потому что:
- `n.publishAt` приходит как nullable String? в DTO (по умолчанию Kotlin String? = JSON null или "").
- `replace('T', ' ')` страховка от admin-формы, где datetime-local может прийти с `T`.
- `Timestamp.valueOf(...)` бросает `IllegalArgumentException` на некорректной строке — это попадёт в логи как 500 с понятным message (вместо тихого `null` после Java 22 `Date.parse`).

### Fix 3: KaraokeProperties — двухчастный фикс

**Решение**: **(a) убрать все обращения к `KaraokeProperties` из `karaoke-web/`** (на проде файлов `Karaoke.properties` физически нет, и публичный веб не должен зависеть от admin-only state) **+ (b) try/catch вокруг `savePropertiesMap()`** как defensive programming на случай будущих использований.

#### Часть (a): убираем обращения с веба

Аудит (на момент создания этого change): `grep -r "KaraokeProperties\." karaoke-web/src/main/kotlin --include="*.kt"` показал **ровно 2 реальных обращения**, оба в `PublicApiController.kt:467-468` в методе `songVkImage`:
- `KaraokeProperties.getInt("vkPreviewImageWidth")` → fallback на 1200
- `KaraokeProperties.getInt("vkPreviewImageHeight")` → fallback на 630

В `CaptchaConfigService.kt:7` — комментарий, явно говорящий «не используем `KaraokeProperties` для SmartCaptcha» (там используется `tbl_public_settings` из Postgres). Это правильный паттерн: admin-only `KaraokeProperties` → только для admin-only кода, а в публичном вебе конфиги хранить в Postgres.

**Действие**:
- Захардкодить `val frameW = 1200` и `val frameH = 630` в `songVkImage`.
- Удалить `import com.svoemesto.karaokeapp.KaraokeProperties` из `PublicApiController.kt`, если других обращений нет (проверить grep после правки).
- Это **убирает зависимость** публичного веба от `/sm-karaoke/system/Karaoke.properties` — даже если файл не существует и прав нет, код не пытается туда лезть.

#### Часть (b): try/catch в savePropertiesMap

После части (a) **непосредственной** причины для 5xx на проде уже нет. Но класс `KaraokeProperties` остаётся в shared-коде и может быть случайно использован в будущем (например, в новом публичном endpoint). Defensive programming:

**Действие**: обернуть `savePropertiesMap()` в `loadPropertiesMap()` (строка 61) в try/catch и НЕ пробрасывать `Exception` дальше. Логировать WARN с путём и сообщением.

**Альтернативы рассмотренные**:
- **A) Условный путь на основе `WEB_WORK_ON_SERVER`/`WEB_WORK_IN_CONTAINER`** — отклонено. Эти переменные уже используются (см. `KaraokeWebApplicationKt`), но менять константу `PATH_TO_KARAOKE_PROPERTIES_FILE` рискованно (это глобал, читаемый в нескольких местах, включая `karaoke-app`, который работает только локально).
- **B) Полная очистка `WebvueProperties`** — отклонено для этого PR. Та же история: используется в `webvue3` (админка); менять путь там — отдельный рискованный PR.
- **C) `try { ... } catch (e: Exception) { logger.warn(...) }` в `savePropertiesMap()`** — принято как дополнение к части (a). Минимальный и безопасный фикс: даже если кто-то в будущем случайно вызовет `KaraokeProperties.get*()` из публичного кода, попытка записи падает с понятным WARN, метод возвращается, `loadPropertiesMap` спокойно работает с дефолтами.

**Параметры для части (b)**:
- Ловить `Exception` (не только `IOException`): на проде может быть и `AccessDeniedException`, и `FileSystemException` — все симптомы «нет доступа к пути».
- Уровень: `println(...)` (как в существующем коде в `KaraokeProperties.savePropertiesMap` — `runCommand("chmod 666 ...")` тоже не обёрнут в try/catch и молча падает в `runCommand`).
- Сообщение: `KaraokeProperties: не удалось сохранить файл <path>: <e.message>`.

**Альтернатива для будущего**: для публичного веба, если потребуется конфигурируемое значение (вместо захардкоженного), использовать `tbl_public_settings` из Postgres — паттерн уже есть в `CaptchaConfigService.kt`.

### Связанное: nginx `minio-proxy`

**Решение**: добавить в nginx-конфиг `minio-proxy` (файл `deploy/minio-proxy.conf` или аналогичный) блок:
```
location /yookassa/ {
    proxy_pass https://api.yookassa.ru/v3/;
    proxy_set_header Host api.yookassa.ru;
    proxy_set_header Authorization $http_authorization;
    proxy_pass_request_headers on;
    proxy_ssl_server_name on;
}
```

**Альтернативы рассмотренные**:
- **A) Использовать отдельный hostname `yookassa-proxy`** — отклонено. Не хочется плодить контейнеры; можно добавить location в существующий nginx.
- **B) WebClient идёт напрямую на `api.yookassa.ru`** — отклонено. nginx-прокся даёт единую точку для HTTPS-терминации и TLS-сертификата (иначе придётся хранить сертификат внутри `karaoke-web` jar). Также устраняет MTU=1500 mismatch (см. комментарий в `PublicApiController.kt:55-58`).

**ВНИМАНИЕ**: правка nginx-конфига на проде делается пользователем вручную (см. AGENTS.md, раздел «Ограничения агента»). Агент НЕ делает `ssh root@188.119.64.111` и НЕ правит `/etc/nginx/sites-enabled/`. Проверка конфига и reload — тоже пользователь.

## Risks / Trade-offs

| Risk | Mitigation |
|------|-----------|
| ЮKassa может реально отвечать 400 из-за того, что `payment_method_id` невалиден (карта истекла). Фикс nginx не поможет. | Детальное логирование покажет конкретный `code` ответа (`payment_method_not_found`, `invalid_request` и т.п.). Это и есть задача фикса. |
| `T(java.sql.Timestamp).valueOf(...)` в Thymeleaf-шаблоне — экзотика, может пугать ревьюера. | Альтернатива (хранить `Timestamp` в DTO) требует изменений в 3 слоях. Inline-парсинг — 1 строка. Комментарий в коде объяснит. |
| Try/catch в `KaraokeProperties.savePropertiesMap` маскирует другие ошибки записи (например, на admin-машине, где права есть, но кончилось место). | На admin-машине путь `/sm-karaoke/` доступен, и при реальной проблеме с местом WARN будет виден. Лучше видеть WARN, чем 500 на проде. |
| После части (a) Fix 3 (`PublicApiController` больше не зовёт `KaraokeProperties`) `grep` может показать пустой результат, и ревьюер спросит: «а что вообще делает `KaraokeProperties` и зачем там try/catch?» | Ответ: это shared-класс, потенциально используется в `karaoke-app` (админка) и в любом будущем публичном коде. Try/catch — defensive programming, не выпиливаемый код. См. `CaptchaConfigService.kt:7` как пример осознанного выбора: для публичного веба конфиги — в Postgres, для admin — в `KaraokeProperties`. |
| nginx `proxy_ssl_server_name on` — SNI обязателен для `api.yookassa.ru` (SAN в сертификате). | Без SNI nginx получит сертификат дефолтного хоста и SSL handshake провалится. SNI нужен. |
| Правка nginx требует ручного деплоя пользователем. | В tasks.md явно выделена отдельная task с пометкой «выполняется пользователем». Без неё фикс кода не имеет смысла — повторные 400 в логах никуда не денутся. |
| Минимальные фиксы (3 файла) могут раздражать ревьюера: «почему не один большой рефакторинг?» | Принцип проекта (AGENTS.md, Pass 33+) — bugfix максимально узким диффом, чтобы diff легко ревьюился и откатывался. Рефакторинг — отдельный PR. |

## Migration Plan

### Порядок деплоя

1. **Сначала код** — feature-ветка → PR → CI 7/7 → merge → пересборка `karaoke-web.jar` → деплой.
2. **Потом nginx** — пользователь добавляет `location /yookassa/` в `minio-proxy`, проверяет `nginx -t`, делает `systemctl reload nginx`.
3. **Проверка на проде** — в течение 24 часов смотрим логи `karaoke-web`:
   - `PaymentService.chargeRecurring: statusCode=...` — появились детальные логи (фикс кода работает);
   - В идеале — 200 от ЮKassa и продлённые подписки (фикс nginx работает);
   - Если всё ещё 400 — смотрим тело ответа и эскалируем в ЮKassa.
4. **Thymeleaf** — после деплоя кода проверить `GET /` в инкогнито: должны рендериться все новости с заполненным `publishAt`.
5. **KaraokeProperties** — перезапустить `karaoke-web` и проверить, что `GET /api/song-vk-image/159` (или любой id) возвращает 200 OK с PNG, а не 500.

### Rollback

- **Код**: revert merge commit, пересобрать `karaoke-web`, деплоить старую версию (3-5 минут).
- **nginx**: `rm` (или закомментировать) `location /yookassa/`, `nginx -t && systemctl reload nginx` (1 минута).
- Откат одного из трёх фиксов без других — только через revert отдельного коммита (если будут отдельные коммиты в одном PR) или через revert всего PR (если один коммит).

### Что НЕ делается при деплое

- Никаких миграций БД.
- Никаких изменений переменных окружения (env).
- Никаких изменений в `karaoke-app` (хотя `KaraokeProperties.kt` живёт там — это shared-класс; фикс работает и для admin-машины тоже, не ломая её).

## Open Questions

Нет открытых вопросов, которые могут изменить specs, design или tasks. Все технические решения приняты в этом документе.
