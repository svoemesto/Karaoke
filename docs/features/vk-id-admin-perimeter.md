# VK ID: периметр выдачи токена и защита секрета

> **Status**: active
> **Feature Key**: vk-id-admin-perimeter
> **Last Updated**: 2026-10-24
> **Spec**: [specs/378-pkg-s2-15/report.md](../../specs/378-pkg-s2-15/report.md)
> Per-feature документ по [Constitution VI FR-009](../../.specify/memory/constitution.md).

## Что делает

Закрывает анонимный доступ к VK ID-flow в `karaoke-web`: выдача OAuth-ссылки требует
общего секрета администратора, токен сохраняется только для доверенного VK `user_id`,
а тело ответа VK ID на token-запрос не попадает ни в журнал, ни в HTML.

## Зачем

Аудит #336 (BR-001, BR-002, порция P-37). Оба endpoint'а (`/api/public/utils/vkIdOAuthUrl`
и `/api/public/utils/vkIdOAuthCallback`) были опубликованы без аутентификации: любой
анонимный клиент мог пройти flow СВОИМ VK-аккаунтом, и его токен уходил на admin-машину
в `POST /api/utils/vkIdSaveTokens`, перезаписывая прод-credentials публикации
(`vkIdAccessToken` / `vkIdRefreshToken`). Дополнительно тело ответа VK ID (начинается
с `access_token`) писалось в INFO-лог и вставлялось в HTML страницы ошибки — токен
попадал в stdout контейнера и централизованные логи.

## Как работает

1. `GET /api/public/utils/vkIdOAuthUrl` требует `X-Internal-Secret`
   (`vk.id.admin-secret` / env `VK_ID_ADMIN_SECRET`). Пустой настроенный секрет
   закрывает канал (`403 secret_not_configured`) — fail-safe, ловушка «пустой секрет +
   пустой заголовок» исключена. Отказ пишется в журнал `infra.vkid.security`
   (причина + `X-Real-IP`, без значения секрета).
2. Callback (`GET .../vkIdOAuthCallback`) — браузерный редирект VK, заголовок передать
   не может; его рубеж — одноразовый `state` с TTL 30 минут, который выдаёт только
   защищённый endpoint.
3. Токен сохраняется на admin-машину только для `user_id` из списка
   `vk.id.allowed-user-ids` (env `VK_ID_ALLOWED_USER_IDS`). Пустой список — прежнее
   поведение (решение владельца); заданный — любой другой `user_id` получает отказ.
4. `parseTokenResponse` — единая точка разбора ответа VK ID: в журнал уходит только
   длина (`debug`), на странице ошибки — длина и отсылка к журналу; `error_description`
   экранируется. Тело ответа не логируется и не рендерится ни в одной ветке.

## Инварианты

- Секрет администратора не появляется ни в теле ответа, ни в журнале.
- Пустой настроенный секрет = канал закрыт (не «открыт всем»).
- Ни одна ветка ошибки VK ID не содержит тела ответа (ни в HTML, ни в логе).
- `state` одноразовый: `pendingAuths.remove(state)` до обмена code → token.

## Известные ловушки

- `state` не привязан к cookie/сессии инициатора (классический OAuth login-CSRF);
  самостоятельного пути без доступа к выдаче authorize-URL не даёт, но привязка —
  отдельное решение владельца.
- `admin-api-url` по умолчанию `http://nsa-i9:8898` (plain HTTP) — перевод на HTTPS
  требует изменения deploy-конфигурации (за владельцем).
- Ограничение «только доверенный VK user_id» реализовано на стороне `karaoke-web`;
  приём токена на admin-стороне (`karaoke-app`) по-прежнему валидирует только
  валидность user-token.
- Если `VK_ID_ADMIN_SECRET` не задан на проде, endpoint выдачи OAuth-ссылки не работает —
  значение задаёт владелец в deploy.

## Ссылки

- [Отчёт пакета PKG-S2-15](../../specs/378-pkg-s2-15/report.md)
- [Карточка аудита P-37](../../specs/336-code-audit/findings/P-37.md)
- [PublicVkIdAuthController.kt](../../karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicVkIdAuthController.kt)
- [application.yml](../../karaoke-web/src/main/resources/application.yml) — параметры `vk.id.admin-secret`, `vk.id.allowed-user-ids`
- [public-controllers-2.md](../../knowledge/domains/karaoke-web/components/public-controllers-2.md) — контракт контроллера
- [specs/151-vk-id-personal-token/spec.md](../../specs/151-vk-id-personal-token/spec.md) — исходный дизайн flow

## Changelog

- **2026-10-24** (BR-001/BR-002, OpenProject #378): секрет администратора на выдаче
  authorize-URL, список доверенных `user_id`, тело ответа VK ID вне логов и HTML.
