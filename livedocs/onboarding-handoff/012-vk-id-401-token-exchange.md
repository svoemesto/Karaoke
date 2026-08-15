---
status: Active
slug: 012-vk-id-401-token-exchange
type: runbook
related:
  - ../onboarding.md
  - ../features/151-vk-id-personal-token.md
---

> **Как использовать**: скопируйте **весь блок «ПРОМТ НИЖЕ»** в первую реплику
> новому AI-агенту (Claude Code, Cursor, Aider, Cody, другой opencode сессии).
> Агент прочитает указанные файлы и подхватит работу с того же места.

---

## ПРОМТ НИЖЕ (скопировать)

```
Контекст: продолжаю работу над спекой #151 «VK ID personal token (миграция с
oauth.vk.ru на id.vk.ru)» в репо Karaoke/svoemesto. Реализация и hotfix'ы
уже в feature-ветке `151-vk-id-personal-token` (НЕ слиты в master).
Застряли на ошибке `invalid_client` при обмене code → token — VK возвращает
401 на `https://oauth.vk.ru/access_token` с ЛЮБЫМ типом ключа
(защищённый или сервисный). Нужно доделать оставшиеся пункты + разобраться
с 401.

## Что уже сделано (в feature-ветке `151-vk-id-personal-token`, НЕ в master)

Всего 5 коммитов на ветке относительно master (c1965780):

1. **238e5610** `spec(vk-id): миграция с oauth.vk.ru на id.vk.ru (specs/151)` —
   спек #151 (8 файлов, 2892 строки): spec.md, plan.md, tasks.md, data-model.md,
   quickstart.md, research.md, contracts/, checklists/.
2. **d6cb599c** `feat(vk-id): реализация VK ID OAuth flow + PKCE + auto-refresh (specs/151)` —
   основная реализация (11 файлов, +1324/−336 строк):
   - `karaoke-web/util/VkIdPkceUtils.kt` — PKCE утилиты (RFC 7636, S256,
     code_verifier 64 символа).
   - `karaoke-web/controllers/PublicVkIdAuthController.kt` — endpoint'ы
     `/api/public/utils/vkIdOAuthUrl` и `/api/public/utils/vkIdOAuthCallback`.
   - `karaoke-app/services/VkApiClient.kt` — `refreshVkIdAccessToken()` +
     `VkIdTokenRefreshResult` + `VkIdRefreshFailedException`.
   - `karaoke-app/services/VkIdTokenRefreshScheduler.kt` —
     `@Scheduled cron='0 0 * * * *'` (каждый час, порог 30 мин до истечения).
   - `karaoke-app/KaraokeProperties.kt` — 9 новых ключей `vkId*`.
   - `karaoke-app/controllers/ApiController.kt` — 3 endpoint'а
     `vkIdSaveTokens`/`vkIdTokenStatus`/`vkIdRefreshNow` (admin API).
   - `docs/features/vk-id-auth.md`, `docs/features/README.md` (обновлён),
     `docs/features/vk-news-auto-publish.md` (обновлён).
3. **8aa5fded** `fix(vk-id): hotfix — поле clientId из Long → String для безопасного резолва @Value` —
   `@Value("${vk.id.client-id}")` не резолвился в Long; сделали String +
   `clientIdLong()` для безопасной конвертации.
4. **819461c2** `fix(vk-id): token endpoint — VK ID использует oauth.vk.ru/access_token,
   не id.vk.ru/oauth2/token` — id.vk.ru/oauth2/token возвращает 404;
   правильный endpoint для token-фазы — `oauth.vk.ru/access_token` (гибрид
   VK ID: authorize на id.vk.ru, token на oauth.vk.ru). Endpoint для
   authorize правильный (`id.vk.ru/authorize`).
5. **48d2cf71** `fix(vk-id): TTL для PKCE pendingAuths увеличен с 10 до 30 минут` —
   `pendingTtlSeconds` в `PublicVkIdAuthController.kt` увеличен с 600L до
   1800L, чтобы пользователь успел авторизоваться и вернуться на callback.

### Текущий прогресс по Фазе 6 (T-6.x)

- **T-6.1** `karaoke-web/.../deploy/web-server-deploy/deploy/docker-compose-web.yml`
  обновлён: добавлены `VK_ID_CLIENT_ID`, `VK_ID_CLIENT_SECRET`,
  `VK_ID_REDIRECT_URI`, `VK_ID_ADMIN_API_URL`, а также `DB_REMOTE_HOST`,
  `DB_REMOTE_PORT`, `STEMJOBS_INTERNAL_SECRET`, `VK_CLIENT_SECRET`,
  `VK_APP_ID`, `VK_REDIRECT_URI`. Исправлен комментарий `CLAUDE.md` →
  `DEVELOPMENT.md`. (Удалена дублирующая строка `STEMJOBS_INTERNAL_SECRET`.)
- **T-6.2** Пользователь сгенерировал новые ключи в кабинете VK ID и
  обновил `deploy/do.env` на сервере 188.119.64.111.
- **T-6.3** Контейнер `karaoke-web` перезапущен с новыми переменными.
- **T-6.4** `curl https://sm-karaoke.ru/api/public/utils/vkIdOAuthUrl`
  возвращает валидный URL (проблема CSRF истёкшего state была решена
  через hotfix #5 выше).
- **T-6.5** Открытие URL в браузере → VK показывает форму авторизации →
  пользователь разрешает → callback приходит в
  `https://sm-karaoke.ru/api/public/utils/vkIdOAuthCallback`.
- **T-6.6** В логах `karaoke-web`:
  ```
  VK ID OAuth callback: exchanging code (length=220) for token...
  VK ID token POST failed
  java.io.IOException: Server returned HTTP response code: 401 for URL: https://oauth.vk.ru/access_token
  ```
- **T-6.7** ❌ Заблокировано: токен не получен.

### Главная гипотеза (НЕ подтверждена)

**VK ID Web OAuth 2.0 для приложения 54704234 (тип Web) требует другой
ключ**, который отсутствует в кабинете VK ID по адресу
`https://id.vk.com/about/business/go/`. Возможные варианты:

1. **«Защищённый ключ»** (secure_key) → 401 `invalid_client`.
2. **«Сервисный ключ доступа»** (service_key) → 401 `invalid_client`.
3. Возможно, нужен **«Клиентский ключ»** или **«Секрет приложения»**,
   который генерируется в **«Подключение авторизации»** → «Ключи»
   (отдельная секция кабинета). В текущем UI кабинета мы эту секцию не
   видели — она могла появиться после активации OAuth 2.0 или быть
   скрыта за «галочкой» в настройках приложения.

### Эксперименты, которые были сделаны (показали результат)

Прямой curl на `https://oauth.vk.ru/access_token`:

| Параметры | Ответ VK |
|-----------|----------|
| `oauth.vk.ru/access_token` + `client_id=54704234` + `client_secret=INVALID` + `code=INVALID` | `{"error":"invalid_client","error_description":"client_secret is incorrect"}` |
| `oauth.vk.ru/access_token` + `client_id=54704234` + `client_secret=INVALID` + `code=INVALID` + `grant_type=authorization_code` (явно) | `{"error":"invalid_client","error_description":"client_secret is incorrect"}` |
| `oauth.vk.ru/access_token` + `client_id=54704234` + code (без client_secret, только PKCE) | `{"error":"invalid_client","error_description":"client_secret is undefined"}` |
| `oauth.vk.ru/access_token` + HTTP Basic Auth (`54704234:INVALID`) | `{"error":"invalid_client","error_description":"client_id is undefined"}` |
| `oauth.vk.com/access_token` (вместо `.ru`) | `{"error":"invalid_client","error_description":"client_secret is incorrect"}` |

**Вывод**: VK принимает запросы и валидирует параметры → проблема в
**значении ключа**, не в формате запроса.

## Чего НЕ сделано (твой backlog)

### Блокер 1: 401 invalid_client

Разобраться, какой именно ключ требует VK для приложения `54704234`
(Web OAuth 2.0). Возможные шаги:

1. **Проверить кабинет VK ID ещё раз**. Возможно, есть отдельная секция
   «Подключение авторизации» → «Ключи OAuth» с client_secret, отличным
   от «Защищённого ключа» и «Сервисного ключа». Скриншот кабинета:
   `~/.config/opencode/journal/2026-08-05-vk-id-cabinet.png` (если есть).
2. **Проверить email от VK ID** после регистрации приложения — там мог
   быть client_secret для OAuth (отдельный от Standalone Implicit Flow).
3. **Создать новое приложение** через `https://id.vk.com/about/business/go/`
   с типом «Web» и **включить OAuth 2.0** (есть отдельная галочка в
   настройках), затем проверить, появляется ли новый тип ключа.
4. **Спросить в поддержке VK ID** через `https://id.vk.com/support/`
   с описанием проблемы: «Web OAuth 2.0 возвращает invalid_client для
   client_secret из кабинета».
5. **Попробовать `https://id.vk.ru/oauth2/auth`** (вместо `id.vk.ru/authorize`)
   — может быть, нужен другой authorize endpoint для VK ID v2.

### Блокер 2: после решения 401

1. T-6.7: проверить сохранение токена через
   `curl http://nsa-i9:8898/api/utils/vkIdTokenStatus`.
2. T-7.1-T-7.3: разблокировка спеки #138 (загрузка превью-фото).

## Обязательно прочитай перед началом (порядок важен)

1. `.specify/memory/constitution.md` — NON-NEGOTIABLE принципы.
2. `AGENTS.md` — общие правила (Q&A, особенно про «feature-ветка НЕ
   удаляется после мёрджа»).
3. `docs/onboarding.md` — setup машины.
4. `DEVELOPMENT.md` — архитектура, команды.
5. `CONTRIBUTING.md` — стиль кода (Kotlin/Vue/SQL/Docker).
6. `docs/features/vk-id-auth.md` — per-feature документация по VK ID OAuth.
7. `docs/features/vk-news-auto-publish.md` — обновлён в рамках спеки #151.
8. `specs/151-vk-id-personal-token/spec.md` — спецификация (8 файлов).
9. `specs/151-vk-id-personal-token/quickstart.md` — сценарии проверки.

Также прочти:
- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicVkIdAuthController.kt`
  — основной OAuth controller.
- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/util/VkIdPkceUtils.kt` — PKCE.
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkApiClient.kt` —
  `refreshVkIdAccessToken()` + `VkIdTokenRefreshResult` + `VkIdRefreshFailedException`.
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkIdTokenRefreshScheduler.kt`.
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt` —
  9 ключей `vkId*`.
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt` —
  3 endpoint'а admin API.
- `karaoke-web/src/main/resources/application.yml` — секция `vk.id.*`
  (client-id, client-secret, redirect-uri, admin-api-url).
- `deploy/web-server-deploy/deploy/docker-compose-web.yml` — переменные окружения.

## Главный принцип (повторять не забывать)

**TTL для PKCE pendingAuths = 1800 сек (30 мин)**, не 600. Это защищает
от CSRF истечения при медленном вводе пароля / 2FA в VK. См. hotfix
`48d2cf71`.

**Endpoint для token-фазы VK ID = `https://oauth.vk.ru/access_token`**
(НЕ `https://id.vk.ru/oauth2/token` — возвращает 404). Endpoint для
authorize-фазы = `https://id.vk.ru/authorize` (это правильно).
См. hotfix `819461c2`.

**PKCE ОБЯЗАТЕЛЕН** для Web OAuth 2.0 в VK ID. Без `code_verifier` VK
вернёт `invalid_client` с `code_verifier is undefined`. Используется
S256 (`code_challenge` = base64url(SHA256(verifier))).

## Порядок работы (предлагаю)

1. **Прочитай всё** из секции «Обязательно прочитай».
2. **Зафиксируй статус** `151-vk-id-personal-token` через
   `git status` и `git log master..HEAD` (должно быть 5 коммитов).
3. **НЕ создавай новую ветку** — работай в `151-vk-id-personal-token`
   (см. AGENTS.md → «Жизненный цикл feature-ветки»). Если правка меняет
   поведение — это нормальный follow-up PR из той же ветки.
4. **Разберись с 401**: проверь кабинет VK ID на наличие дополнительных
   ключей; если не найдёшь — спроси у пользователя, какие ещё варианты
   ключей есть в кабинете, или попроси создать новое приложение с
   включённым OAuth 2.0.
5. **После решения 401** — доделай T-6.7 и T-7.1-T-7.3.
6. **Перед коммитом**: `./gradlew ktlintCheck`,
   `cd webvue3 && npm run lint:check && cd ..`,
   `cd karaoke-public && npm run lint:check && cd ..`,
   `bash tools/check-kdoc-coverage.sh`,
   `bash tools/check-jsdoc-coverage.sh webvue3`,
   `bash tools/check-jsdoc-coverage.sh karaoke-public`.
7. **Запушь** и **создай PR** (если не было) через
   `gh pr create --base master --title "..." --body "Closes #151"`.
8. **Дождись CI 7/7 SUCCESS** через `gh pr checks` или `gh run watch`.
9. **Смерджи** через `gh pr merge --merge` (**БЕЗ** `--delete-branch`).

## Чего НЕ делать в этом раунде

- Не мёржить ничего без зелёного CI 7/7 (см. AGENTS.md → «CI-gate для master»).
- Не применять никакие миграции автоматически (только по прямому запросу).
- Не редактировать `deploy/.env`, `deploy/do.env` (секреты) — только
  пользователь.
- Не ломать обратной совместимости (всё аддитивное, opt-in).
- Не удалять ветку `151-vk-id-personal-token` после мёрджа
  (см. AGENTS.md → «Жизненный цикл feature-ветки»).
- Не коммитить `deploy/ollama_data/`, `dist/`, `node_modules/`,
  `deploy/.env`, `deploy/do.env`.

## Как со мной общаться

- Абсолютный язык — **русский** (см. AGENTS.md → «АБСОЛЮТНОЕ ПРАВИЛО:
  язык общения»).
- Комментарии и KDoc/JSDoc — на русском.
- Прежде чем удалять/переписывать большие куски — спроси.
- Перед коммитом — `pre-commit` (или все 7 проверок вручную).
- Если не уверен — уточни через `question` с `<options>`.

Начни с чтения всех 10 пунктов и спроси меня: «Какой именно раздел
кабинета VK ID показывать тебе на скриншоте?» (для поиска недостающего
ключа).
```

---

## Почему промт такой

1. **Контекст** — что в feature-ветке, что нет → агенту не нужно догадываться.
2. **Точные гипотезы** — что уже проверено (curl-тесты на 5 разных форматов
   запросов) и какой результат → агенту не нужно повторять.
3. **Главный блокер** — 401 invalid_client + 4 возможных направления
   расследования (найти другой ключ, новый кабинет, поддержка VK, попробовать
   другое endpoint).
4. **Файлы** — агенту сразу понятно, куда смотреть.
5. **Главный принцип поверх файлов** — TTL=30 мин, token endpoint =
   `oauth.vk.ru/access_token`, PKCE обязателен → повторяется даже если
   агент не прочитает спек/AGENTS.md.
6. **Порядок работы** — сначала разобраться с 401, потом T-6.7/T-7.x.
7. **Ограничения** — что НЕ делать (особенно «не мёржить без CI 7/7,
   не удалять ветку, не применять миграции»).
8. **Язык** — обязательно русский (NON-NEGOTIABLE в AGENTS.md).

## Нюансы

- Если новый агент — Claude Code, ему нужен локальный `CLAUDE.md`
  (см. `docs/claude-code-setup.md`). Скопировать шаблон
  `livedocs/templates/CLAUDE.md.template` в `CLAUDE.md` (локально).
- Если Cursor — настроить `.cursorrules` (см. `docs/onboarding.md`).
- Если Aider/Cody — у них свои форматы конфигов.
- Если новый opencode на той же машине, но без истории сессии — этот же
  промт работает, конфиг уже на месте.
- Журнал `~/.config/opencode/journal/2026-08-05-session-vk-id.md`
  (если есть) содержит полное обсуждение и причины решений — полезен,
  но **не обязателен** (промт выше самодостаточен).
