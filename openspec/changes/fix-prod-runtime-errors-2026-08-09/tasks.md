# Tasks: fix-prod-runtime-errors-2026-08-09

> Каждая задача — отдельный checkbox. Применяются последовательно по группам.
> Перед коммитом см. AGENTS.md, секция «CI-gate для master»: feature-ветка → push → PR → CI 7/7 → merge (без `--delete-branch`).

## 1. Fix 1: PaymentService — детальное логирование ЮKassa

- [x] 1.1 В `karaoke-web/src/main/kotlin/com/svoemesto/karaokeapp/services/PaymentService.kt` (метод `chargeRecurring`, ~236-269) добавить отдельный `catch (e: WebClientResponseException)` ПЕРЕД существующим `catch (e: Exception)`. Внутри — `println("PaymentService.chargeRecurring: ошибка автосписания для sub=${sub.id} (pm=${sub.yookassaPaymentMethodId}): statusCode=${e.statusCode} body=${e.responseBodyAsString?.take(500)}")`. Существующий общий `catch` оставить для не-WebClient ошибок.
- [x] 1.2 Скомпилировать: `./gradlew :karaoke-web:compileKotlin`. Убедиться, что нет warnings и `WebClientResponseException` импортируется (`org.springframework.web.reactive.function.client.WebClientResponseException`). Импорт уже был (строка 10), задача 1.1 только добавила catch. Компиляция прошла успешно (см. задачу 3.5).
- [ ] 1.3 Локально проверить (опционально, если есть `YOOKASSA_SHOP_ID/SECRET_KEY` в env): создать подписку с фейковым `yookassaPaymentMethodId`, дёрнуть `chargeRecurring`, убедиться что лог содержит `statusCode=400` и тело ответа. **Skip** — нет Yookassa creds на dev-машине. Проверка будет сделана автоматически при первом реальном автосписании на проде (задача 6.3).

## 2. Fix 2: Thymeleaf main.html — парсинг `n.publishAt`

- [x] 2.1 В `karaoke-web/src/main/resources/templates/main.html` (line 200) найти ячейку с `th:text="${#dates.format(n.publishAt, 'dd.MM.yyyy HH:mm')}"`.
- [x] 2.2 Заменить на (с сохранением оригинальных colspan/style и двумя ветками — для непустого и пустого publishAt):
```html
<td colspan="1" style="width: 110px; padding-left: 8px; padding-right: 8px; color: #cccccc; font-size: small; font-variant-numeric: tabular-nums; vertical-align: middle">
    <span th:if="${n.publishAt != null and n.publishAt != ''}"
          th:text="${#dates.format(T(java.sql.Timestamp).valueOf(n.publishAt.toString().replace('T',' ')), 'dd.MM.yyyy HH:mm')}">—</span>
    <span th:if="${n.publishAt == null or n.publishAt == ''}">—</span>
</td>
```
- [ ] 2.3 Проверить локально: поднять `karaoke-web` локально (`bash deploy/do.sh build_start_web`), создать новость с `publishAt = "2026-08-08 20:17:14.741"` через `POST /api/news/update`, открыть `/` — страница рендерится без 500. **Skip** — локальный контейнер не поднят, проверка перенесена на прод (задача 6.1).

## 3. Fix 3: KaraokeProperties — двухчастный фикс

> На проде файлов `Karaoke.properties` нет — в идеале обращений к `KaraokeProperties` из `karaoke-web` быть не должно. Поэтому сначала убираем реальные вызовы, потом добавляем try/catch как defensive programming.

- [x] 3.1 В `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt` (метод `songVkImage`, строки 467-468) заменить:
  ```kotlin
  val frameW = KaraokeProperties.getInt("vkPreviewImageWidth").let { if (it <= 0) 1200 else it }
  val frameH = KaraokeProperties.getInt("vkPreviewImageHeight").let { if (it <= 0) 630 else it }
  ```
  на:
  ```kotlin
  val frameW = 1200
  val frameH = 630
  ```
- [x] 3.2 Удалить `import com.svoemesto.karaokeapp.KaraokeProperties` из `PublicApiController.kt` (строка 14), если других обращений к `KaraokeProperties` в файле не осталось.
- [x] 3.3 Прогнать аудит: `grep -rn "KaraokeProperties\." karaoke-web/src/main/kotlin --include="*.kt"` — **0 реальных вызовов**. `grep -rn "KaraokeProperties" karaoke-web --include="*.kt"` нашёл только 1 упоминание — комментарий в `CaptchaConfigService.kt:7` («Ключи Yandex SmartCaptcha живут в tbl_public_settings (Postgres), не в файловых KaraokeProperties»), это KDoc-пояснение правильного паттерна, не реальное обращение.
- [x] 3.4 В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt` (метод `savePropertiesMap`, ~65-82) обернуть ВЕСЬ метод в `try { ... } catch (e: Exception) { println("KaraokeProperties: не удалось сохранить файл ${pathToFile()}: ${e.message}") }` — это покрывает все вызовы: `loadPropertiesMap()` (строка 61) и `set()` (строка 151). Оборачивать только вызов на 61 нельзя, потому что `set()` тоже вызывает `savePropertiesMap()` и без защиты может залогировать stacktrace из `writeText` на проде.
- [x] 3.5 Скомпилировать: `./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin` (оба, потому что shared-класс). `BUILD SUCCESSFUL in 25s` — 4 actionable tasks: 3 executed, 1 up-to-date.
- [ ] 3.6 Проверить локально: удалить `/sm-karaoke/system/Karaoke.properties` (на dev-машине, если есть права), запустить `karaoke-web`, дёрнуть `GET /api/song-vk-image/159` — должно вернуть 200 OK с PNG, в логах — WARN `KaraokeProperties: не удалось сохранить файл ...`. **Skip** — на проде после Fix 3.1-3.2 обращений к `KaraokeProperties` из `karaoke-web` больше нет (см. 3.3), путь к `/sm-karaoke/` никогда не откроется из `songVkImage`. Try/catch из 3.4 покрывает только будущие случайные использования в shared-коде.

## 4. CI-gate: PR + проверки

- [ ] 4.1 Закоммитить: `git add karaoke-app/ karaoke-web/ openspec/changes/fix-prod-runtime-errors-2026-08-09/` и `git commit -m "fix: 3 production runtime errors — ЮKassa logging, Thymeleaf publishAt, KaraokeProperties on prod (#NNN)"`.
- [x] 4.2 Запустить локально все 7 проверок (см. AGENTS.md, Q&A «Как проверить, что CI пройдёт»): `./gradlew ktlintCheck`, `cd webvue3 && npm run lint:check && cd ..`, `cd karaoke-public && npm run lint:check && cd ..`, `bash tools/check-kdoc-coverage.sh`, `bash tools/check-jsdoc-coverage.sh webvue3`, `bash tools/check-jsdoc-coverage.sh karaoke-public`, `pre-commit run --all-files`. Все зелёные:
  - ktlintCheck: BUILD SUCCESSFUL
  - webvue3 lint:check: 0 errors
  - karaoke-public lint:check: 0 errors
  - KDoc coverage: 97.0% (целевой ≥ 50%)
  - JSDoc coverage webvue3: 100.0%
  - JSDoc coverage karaoke-public: 100.0%
  - pre-commit ktlint: Passed (остальные hooks — no files to check)
- [ ] 4.3 `git push -u origin 161-fix-prod-runtime-errors-2026-08-09`.
- [ ] 4.4 `gh pr create --base master --title "fix: 3 production runtime errors — ЮKassa logging, Thymeleaf publishAt, KaraokeProperties on prod" --body "..."`.
- [ ] 4.5 `gh pr checks` — дождаться CI 7/7 SUCCESS.
- [ ] 4.6 `gh pr merge --merge` (БЕЗ `--delete-branch` — см. AGENTS.md, секция «Жизненный цикл feature-ветки»).
- [ ] 4.7 В `docs/architecture-notes.md` добавить запись о PR (Pass 35+).

## 5. Деплой (выполняется пользователем)

> ⚠️ Агент НЕ делает эти шаги — см. AGENTS.md, секция «Ограничения агента».

- [ ] 5.1 Пересобрать `karaoke-web.jar`: `bash deploy/do.sh build_app`.
- [ ] 5.2 Перезапустить контейнер `karaoke-web`: `bash deploy/do.sh build_start_web`.
- [ ] 5.3 Добавить в nginx-конфиг `minio-proxy` блок (см. design.md, раздел «Связанное: nginx minio-proxy»):
```
location /yookassa/ {
    proxy_pass https://api.yookassa.ru/v3/;
    proxy_set_header Host api.yookassa.ru;
    proxy_set_header Authorization $http_authorization;
    proxy_pass_request_headers on;
    proxy_ssl_server_name on;
}
```
- [ ] 5.4 `nginx -t && systemctl reload nginx`.
- [ ] 5.5 Проверить через 24 часа: `docker logs karaoke-web --since "24h" | grep -E "PaymentService.chargeRecurring"` — должно быть детальное логирование.

## 6. Верификация на проде

- [ ] 6.1 `GET https://svoemesto.ru/` — главная рендерится, новости с заполненным `publishAt` отображают дату.
- [ ] 6.2 `GET https://svoemesto.ru/api/song-vk-image/159` — 200 OK с PNG (раньше падало 500 после рестарта). Проверить, что в HTML картинка 1200×630 (как было при fallback).
- [ ] 6.3 В течение 24ч — НЕТ `400 Bad Request from POST http://minio-proxy/yookassa/payments` в логах (или есть, но с понятным телом ответа ЮKassa).
- [ ] 6.4 Если 400 всё ещё есть — прочитать тело ответа из лога и эскалировать в ЮKassa или конкретному пользователю.
