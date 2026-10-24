# PKG-S2-15 — karaoke-web: COMPAT, DATA, ERR, LEAK, PERF, RACE, SEC

- **Задача**: OpenProject #378, пакет `PKG-S2-15` (волна C, бэклог аудита #336)
- **Ветка**: `580-pkg-s2-15` (от `master` @ `534766da`), worktree `.worktrees/waveC-s2-15`
- **Дата**: 2026-10-24
- **Записи пакета (13)**: BR-001, BR-002, BR-004, BR-005, BR-012, BR-013, BR-015, BR-022,
  BR-023, BR-500, BR-504, BR-515, BR-516
- **Источник правды**: `specs/336-code-audit/findings/P-35…P-39.md`, `registry.csv`
  (порции P-35, P-36, P-37, P-38, P-39)

## Итог

| BR | Класс | Было | Стало | Файл | Тест | Статус |
|---|---|---|---|---|---|---|
| BR-001 | SEC | Оба VK ID-endpoint'а анонимны: authorize-URL получал любой, токен своего VK-аккаунта уходил на admin-машину и перезаписывал прод-credentials | Выдача authorize-URL — только с `X-Internal-Secret` (`vk.id.admin-secret`, fail-safe: пусто = закрыто); отказ в журнал `infra.vkid.security`; токен сохраняется только для `user_id` из `vk.id.allowed-user-ids` (пусто = прежнее поведение) | `controllers/PublicVkIdAuthController.kt`, `resources/application.yml` | `PublicVkIdAuthPerimeterTest` (10) | исправлено (часть — за владельцем) |
| BR-002 | SEC | Тело ответа VK ID (начинается с `access_token`) писало INFO-лог и вставлялось в HTML ошибки | Тело не логируется и не рендерится ни в одной ветке: только длина; `error_description` экранируется | `controllers/PublicVkIdAuthController.kt` | `PublicVkIdAuthPerimeterTest` | исправлено |
| BR-004 | ERR | Один singleton-бин `RateLimitInterceptor` мутировался дважды: оба endpoint'а картинок работали с лимитом vk-image и делили bucket | Два независимых экземпляра, `endpointName`/`limitPerMinute` — конструкторные (immutable), состояние раздельное | `config/WebMvcConfig.kt`, `services/RateLimitInterceptor.kt` | `WebMvcConfigRateLimitTest` (2) + обновлён `RateLimitInterceptorTest` | исправлено |
| BR-005 | RACE | `countPaid + 1` без блокировки: залп параллельных `POST /create` на границе акции давал всем 100% скидку | `PriceService.withOrdinalLock(userId, scope)` сериализует «расчёт цены → создание подписки → запись PAID» (64 полосы); используется и в cart-checkout | `services/PriceService.kt`, `controllers/PublicSubscriptionController.kt`, `controllers/PublicCartController.kt` | `PriceServiceOrdinalLockTest` (3) | исправлено в процессе; межпроцессная атомарность — за владельцем (БД) |
| BR-012 | DATA | Батч `/changerecords` применялся statement за statement на autocommit: ошибка в середине оставляла БД частично изменённой | Весь батч — одна транзакция: `autoCommit=false`, `commit()` после успеха, `rollback()` при ошибке, восстановление autocommit в `finally`; новость — после коммита | `controllers/MainController.kt` | `MainControllerChangeRecordsTransactionTest` (4) | исправлено |
| BR-013 | SEC | `/song/{id}`, `/song-picture/{id}`, `/song-vk-image/{id}` отдавали любую песню по id — без `id_status >= 6` и без `SKIP` | `isPubliclyVisible(song)` (`idStatus >= 6 && !SKIP`); не-редактору — 404 (как для несуществующей), редактору — видна | `controllers/PublicApiController.kt` | `PublicApiVisibilityTest` (4) | исправлено |
| BR-015 | PERF | `?size=100000` вытягивал всю `tbl_news` (3.5+ MB JSON); отрицательный `page` → отрицательный offset; батч метрик без предела | `normalizePaging`: `size` ∈ [1,100], `page >= 0`, offset в `Long`; батч метрик > 500 → 413 | `controllers/PublicNewsController.kt`, `controllers/PublicApiController.kt` | `PublicNewsPagingTest` (4), `PublicApiVisibilityTest` | исправлено (nginx `client_max_body_size` — за владельцем) |
| BR-022 | SEC | `access` не проверял `SKIP`: пока песня в эфире, полный стем уходил анониму/премиуму | SKIP блокирует плеер (`song_skipped`) и в `/access`, и в момент отдачи байт; исключения — `canWorkWithSkipped` и предпросмотр по своему заданию | `controllers/PublicPlayerController.kt`, `services/PlayerAccessService.kt`, `services/PlayerRightsProvider.kt` | `PlayerAccessSkipTest` (6) | NEEDS-VERDICT: максимум безопасного сделан, вердикт за владельцем |
| BR-023 | DATA | Результат `createDbInstance` игнорировался: при сбое/конкурентной вставке — `200 {"ok":true}`, правка терялась | Результат проверяется; конкурентная вставка доигрывается как upsert; иначе `500 save_failed` | `controllers/PublicSongEditorController.kt` | `PublicSongEditorDraftPersistenceTest` (3) | исправлено |
| BR-500 | COMPAT | `statbysong.html` обращался к `cntBoosty`, которого нет в `StatBySongDto`: SpEL ронял всю страницу в 500 | Колонка Boosty удалена (в DTO её заменили `cntMax`/`cntSponsr`); страница рендерится | `templates/statbysong.html` | `ThymeleafTemplatesTest` (3) | исправлено |
| BR-504 | SEC | `tbl_news.link` подставлялся в `href` «как есть»: `javascript:` исполнялся по клику | `SafeLink.isSafe` до модели (только `http(s)`); небезопасная ссылка обнуляется, шаблон рендерит заголовок текстом, `window.open` — под проверкой | `util/SafeLink.kt`, `controllers/MainController.kt`, `templates/main.html` | `SafeLinkTest` (6), `ThymeleafTemplatesTest` | исправлено (валидация на записи — за владельцем) |
| BR-515 | ERR | `users.forEach { tryRenew(it) }` без `try/catch`: сбой первого пользователя прерывал автопродление всех остальных на сутки | `renewEach`: сбой изолирован, `warn` с `siteUserId` не чаще раза в 6 ч, в конце — сводка | `services/SubscriptionRenewalScheduler.kt` | `SubscriptionRenewalSchedulerTest` (4) | исправлено |
| BR-516 | LEAK | `claimRateBuckets` с ключом `ip:минута` никогда не чистилась — рост памяти пропорционально числу IP за всё время | Ключ — IP (окно в значении), ленивая уборка закрытых окон раз в минуту, предел 50 000 IP (fail-closed) | `services/SongShareLinkService.kt` | `SongShareLinkClaimBucketsTest` (4) | исправлено |

Итого: 13/13 записей закрыты правкой; 2 требуют решения владельца в части, выходящей за
безопасный объём пакета (BR-022 — подтверждение модели угроз, BR-005 — схема БД).

## Регресс-контроль «до/после»

Полный прогон после правки:

```
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew --max-workers=2 \
  -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" :karaoke-app:test
# BUILD SUCCESSFUL (FROM-CACHE)

GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew --max-workers=2 \
  -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" :karaoke-web:test
# BUILD SUCCESSFUL; 29 классов, 151 тест, 0 failures, 0 skipped
```

Демонстрация «до» на старом коде (тот же тест, файл откачен на HEAD):

```
# BR-012: старый MainController.kt (без транзакции)
:karaoke-web:test --tests '*MainControllerChangeRecordsTransactionTest*'
→ MainControllerChangeRecordsTransactionTest > успешный батч фиксируется одной транзакцией() FAILED
→ ... > ошибка в середине батча откатывает всю транзакцию() FAILED
→ ... > autocommit соединения возвращается в исходное состояние() FAILED
4 tests completed, 3 failed

# BR-500/BR-504: старые statbysong.html и main.html
:karaoke-web:test --tests '*ThymeleafTemplatesTest*'
→ statbysong рендерится без поля cntBoosty() FAILED
    org.thymeleaf.exceptions.TemplateProcessingException (EL1008E: cntBoosty)
→ новость без безопасной ссылки рендерится текстом, без href и data-link() FAILED
3 tests completed, 2 failed
```

Для остальных записей старый код не компилируется с новыми тестами (изменились
конструктор `RateLimitInterceptor`, сигнатуры/видимость методов, добавлены новые
internal-функции) — «до» зафиксировано в KDoc тестов и в диффе.

## Проверки

```
bash tools/check-knowledge-structure.sh        → OK: knowledge/ structure valid (9/9 checks passed)
python3 tools/check-doc-references.py           → НАЙДЕНО: 0 проблем (после добавления отчёта)
bash tools/check-subagent-isolation.sh         → OK (ветка 580-pkg-s2-15 в отдельном worktree)
python3 tools/check-ssot-impact.py             → OK: all matched SSoT-impact rules satisfied
:karaoke-web:ktlintCheck                       → BUILD SUCCESSFUL
```

## За владельцем

1. **BR-022 (NEEDS-VERDICT)**: подтвердить, что снятие контента (`SKIP`) всегда сопровождается
   снятием публикации и удалением стемов. Правка сделана fail-closed (SKIP блокирует плеер и
   публичные выборки) и остаётся defense-in-depth при любом ответе.
2. **BR-005**: окончательная атомарность при горизонтальном масштабировании требует
   уникального индекса `(site_user_id, scope, ordinal)` или строки-счётчика с
   `SELECT … FOR UPDATE` — это меняет схему БД. Сейчас `karaoke-web` — один инстанс,
   внутрипроцессной блокировки достаточно.
3. **BR-001**: привязка `state` к cookie/сессии инициатора и HTTPS для `admin-api-url`
   (дефолт `http://nsa-i9:8898`), а также приём токена на admin-стороне только от известного
   VK-пользователя (сейчас ограничение вынесено в список на стороне `karaoke-web`).
   Без `VK_ID_ADMIN_SECRET` на проде endpoint выдачи OAuth-ссылки не работает — значение
   задаёт владелец в deploy.
4. **BR-015**: отдельный `client_max_body_size` на location метрик в nginx — ops-конфиг,
   вне пакета (приложение уже отклоняет батч > 500 с 413).
5. **BR-504**: валидация схемы ссылки на стороне записи (`karaoke-app` `NewsController`)
   не сделана — вне модуля пакета; защита стоит на рендере.

## Вне порции

- **BR-517** (P-38-04, `SongShareLinkService.kt:537`) числится в `registry.csv` за #378, но в
  списке 13 записей пакета его нет (в карточке — кандидат на объединение с BR-008). Не трогал;
  `log.error` на штатном пути и `println`/`printStackTrace` в `tryClaim` остаются.
- `specs/336-code-audit/registry.csv` не изменялся (ведёт лид).

## Изменённые файлы

Код (18): `PublicVkIdAuthController.kt`, `WebMvcConfig.kt`, `RateLimitInterceptor.kt`,
`MainController.kt`, `PublicApiController.kt`, `PublicNewsController.kt`,
`PublicPlayerController.kt`, `PublicSongEditorController.kt`, `PublicSubscriptionController.kt`,
`PublicCartController.kt`, `PlayerAccessService.kt`, `PlayerRightsProvider.kt`, `PriceService.kt`,
`SongShareLinkService.kt`, `SubscriptionRenewalScheduler.kt`, `util/SafeLink.kt` (новый),
`application.yml`, `templates/main.html`, `templates/statbysong.html`.

Тесты (12): `PublicVkIdAuthPerimeterTest`, `WebMvcConfigRateLimitTest`,
`MainControllerChangeRecordsTransactionTest`, `PublicApiVisibilityTest`, `PublicNewsPagingTest`,
`PublicSongEditorDraftPersistenceTest`, `PlayerAccessSkipTest`, `PriceServiceOrdinalLockTest`,
`SongShareLinkClaimBucketsTest`, `SubscriptionRenewalSchedulerTest`, `SafeLinkTest`,
`ThymeleafTemplatesTest`; обновлён `RateLimitInterceptorTest`.

Документация: `knowledge/domains/karaoke-web/components/{config, main-controller,
public-controllers-2..6, services-overview, song-share-link-service, thymeleaf-templates}.md`;
`docs/features/{vk-id-admin-perimeter, public-api-exposure, web-data-integrity,
web-render-safety, song-air-access}.md`.

## Коммиты

| Хэш | Что |
|---|---|
| `5cbf602a` | BR-001/BR-002 — периметр VK ID-flow |
| `cfacfc16` | BR-004 — независимые rate-limit интерцепторы |
| `2a99dceb` | BR-013/BR-015 — видимость песен и пределы публичной выдачи |
| `ddceaf0c` | BR-022 — SKIP-контент не отдаётся плеером |
| `092c4e5f` | BR-005/BR-012 — транзакция sync-батча и атомарный номер акции |
| `87b37096` | BR-023/BR-515/BR-516 — черновик редактора, автопродление, карта claim |
| `b9a93585` | BR-500/BR-504 — рендер ссылки новости и страница статистики |
