# Целостность данных и устойчивость фоновых задач karaoke-web

> **Status**: active
> **Feature Key**: web-data-integrity
> **Last Updated**: 2026-10-24
> **Spec**: [specs/378-pkg-s2-15/report.md](../../specs/378-pkg-s2-15/report.md)
> Per-feature документ по [Constitution VI FR-009](../../.specify/memory/constitution.md).

## Что делает

Убирает частичные записи и гонки в путях, которые меняют данные или деньги: батч
синхронизации применяется одной транзакцией, номер акции «каждая N-я бесплатно» выделяется
атомарно, сохранение черновика редактора не теряет правку, цикл автопродления не
останавливается на первом сбое, а карта rate-limit claim'ов ограничена по памяти.

## Зачем

Аудит #336 (BR-005, BR-012, BR-023, BR-515, BR-516, порции P-35/P-36/P-37/P-38). До правки:

- `POST /changerecords` применял батч statement за statement на autocommit-соединении:
  исключение в середине оставляло БД частично изменённой.
- `PriceService` считал `countPaid + 1` без блокировки: залп параллельных
  `POST /account/subscription/create` на границе акции давал всем 100% скидку
  (ветка нулевой цены ставит `PAID` без платежа).
- `POST /account/editor/tasks/{id}/save` игнорировал результат `createDbInstance`:
  при сбое/конкурентной вставке отвечал `200 {"ok":true}`, правка терялась.
- `users.forEach { tryRenew(user) }` без `try/catch`: сбой первого пользователя
  прерывал автопродление всех остальных на сутки.
- Карта `claimRateBuckets` росла на каждую пару «IP + минута» и не чистилась (OOM-риск).

## Как работает

1. **Транзакция батча** (`MainController.handleChangeRecords`): `autoCommit = false`,
   `commit()` после успешного применения всех действий, `rollback()` при любой ошибке,
   восстановление autocommit в `finally` (соединение thread-local). Детекция новости
   «песня появилась» — после коммита.
2. **Атомарный номер акции** (`PriceService.withOrdinalLock`): расчёт цены, создание
   подписки и запись `PAID` для бесплатной ветки выполняются под блокировкой по ключу
   `userId:scope` (фиксированные 64 полосы). Cart-checkout использует ту же блокировку.
3. **Черновик редактора** (`persistDraftOrExisting`): успешная вставка → созданный объект;
   `null` → перечитываем существующий и обновляем (upsert); иначе `500 save_failed`.
4. **Автопродление** (`renewEach`): сбой изолируется по пользователю, подробный `warn`
   с `siteUserId` — не чаще раза в 6 часов, в конце — сводка по тику.
5. **Rate-limit claim'ов** (`SongShareLinkService`): ключ — IP, окно в значении; уборка
   закрытых окон по времени (раз в минуту); `MAX_TRACKED_CLAIM_IPS = 50 000`, при
   превышении новые IP получают `RateLimited` (fail-closed).

## Инварианты

- Батч sync либо применён целиком, либо не применён; текст ошибки БД наружу не уходит.
- Атомарность номера акции действует в пределах одного процесса `karaoke-web`.
- Черновик редактора не отвечает `ok`, если не сохранён.
- Один сбойный пользователь не блокирует автопродление остальных в тике.
- Память под rate-limit claim'ов ограничена константой, а не числом IP за всё время.

## Известные ловушки

- Блокировка `withOrdinalLock` внутрипроцессная. При горизонтальном масштабировании нужен
  уникальный индекс в БД `(site_user_id, scope, ordinal)` либо строка-счётчик с
  `SELECT … FOR UPDATE` — это меняет схему БД и остаётся **за владельцем**.
- `PriceService` в тестах проверяет механизм блокировки; end-to-end сценарий требует
  активной `TYPE_NTH_FREE`-акции в БД.
- `handleChangeRecords` использует соединение из `KaraokeConnection` (thread-local);
  вложенная транзакция в этом же потоке невозможна — вызывающий код не должен открывать
  свою транзакцию вокруг.
- `FAILURE_LOG_INTERVAL_MS = 6 ч` подавляет и повторные РАЗНЫЕ ошибки того же пользователя
  в этом окне; подробности повторного сбоя остаются на `debug`.

## Ссылки

- [Отчёт пакета PKG-S2-15](../../specs/378-pkg-s2-15/report.md)
- [Карточки аудита P-35](../../specs/336-code-audit/findings/P-35.md), [P-36](../../specs/336-code-audit/findings/P-36.md), [P-37](../../specs/336-code-audit/findings/P-37.md), [P-38](../../specs/336-code-audit/findings/P-38.md)
- [MainController.kt](../../karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/MainController.kt)
- [PriceService.kt](../../karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/PriceService.kt)
- [PublicSongEditorController.kt](../../karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicSongEditorController.kt)
- [SubscriptionRenewalScheduler.kt](../../karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/SubscriptionRenewalScheduler.kt)
- [SongShareLinkService.kt](../../karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/SongShareLinkService.kt)
- [services-overview.md](../../knowledge/domains/karaoke-web/components/services-overview.md)
- [song-share-link-service.md](../../knowledge/domains/karaoke-web/components/song-share-link-service.md)

## Changelog

- **2026-10-24** (BR-005/BR-012/BR-023/BR-515/BR-516, OpenProject #378): транзакция батча sync,
  атомарный номер акции, upsert черновика редактора, изоляция сбоев автопродления, TTL-очистка
  и предел карты rate-limit claim'ов.
