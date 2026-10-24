# OpenProject #365 — PKG-MONEY-IDEMPOTENCY (волна C): идемпотентность платежей ЮKassa и автопродления

- **Ветка**: `583-pkg-money-idempotency` (worktree `.worktrees/waveC-money`, от master `79b74b0a`)
- **Пакет**: `PKG-MONEY-IDEMPOTENCY`, порции P-35/P-36/P-38 аудита #336
- **Записи**: BR-014 (S2, DATA), BR-020 (S2, DATA), BR-021 (S2, RACE), BR-514 (S2, DATA) — короткий
  цикл, ТЗ = карточки аудита (отдельная спека не заводилась)
- **Дата**: 2026-10-24
- **Коммиты**: `2c72c520` — код + тесты + knowledge + per-feature документ; отчёт — отдельным
  коммитом (см. `git log`)

---

## 1. Итог по записям

| BR | Файл | Что было не так | Что сделано |
|---|---|---|---|
| **BR-020** | `karaoke-web/.../controllers/PublicPaymentController.kt:61` (`handleWebhook`), новый `karaoke-web/.../services/SubscriptionFulfillmentService.kt:54` (`fulfillPaid`), `:113` (`claimPaid`), `:147` (`applyFulfillment`) | `sub.status = PAID; sub.save()` коммитился ДО `applyFulfillment` и вне транзакции. Повторный вебхук видел «все PAID» и отвечал 200 no-op → оплаченный период терялся навсегда; два параллельных ретрая оба проходили `all { PAID }` и оба продлевали `sitePremiumUntil` | Статус и начисление — **одна транзакция**; захват подписки — условный `UPDATE tbl_subscriptions ... WHERE id = ? AND status <> 'PAID'` (решает число обновлённых строк), продление — `SELECT site_premium_until ... FOR UPDATE` + `UPDATE tbl_site_users` на том же соединении. Исключение → rollback и проброс наружу (вебхук 500 → ЮKassa повторяет доставку). Повторный вызов по уже PAID-строке получает 0 строк и **не трогает** `sitePremiumUntil` |
| **BR-021** | `karaoke-web/.../controllers/PublicSubscriptionController.kt:162` (`handleCreate`), `:293` (`reuseExistingOrder`), `:341` (`createPaymentForExisting`), `karaoke-app/.../model/Subscription.kt:255` (`findPendingSite`), `:318` (`findPendingSong`), `:345` (`pickUnfinished`), новый `karaoke-web/.../services/PaymentOrderReuse.kt` | Переиспользование заказа только при статусе платежа ровно `"pending"`. `succeeded` (вебхук задержался после возврата с оплаты) или `null` (ЮKassa недоступна) → создавался **второй** платёж и второй `confirmationUrl`; для `scope=SONG` дедупликации не было вовсе | Решение вынесено в `PaymentOrderReuse.decide`: `pending`/`waiting_for_capture` → тот же платёж; `succeeded` → `fulfillPaid` и ответ `PAID` (второй счёт не выставляется); `canceled` → строка FAILED, новый заказ; `null`/неизвестный статус → **503 fail-closed**. Поиск незавершённого заказа учитывает и `CREATED` (строка создаётся до платежа), `pickUnfinished` предпочитает строку с уже существующим платежом и самую новую. Строка `CREATED` без платежа **досоздаётся** (`sub-<id>-<createdAt>` — тот же ключ), а не дублируется. Защита распространена на `SONG` |
| **BR-014** | `karaoke-web/.../controllers/PublicCartController.kt:184` (`handleCheckout`), `:215` (`reuseCartOrder`), `:298` (`createNewCartOrder`), `:369` (`commitCartOrder`), `:408` (`rollbackOrder`), `:458` (`checkoutLock`), `karaoke-app/.../Subscription.kt:284` (`loadUnfinishedCartOrder`) | `checkout` создавал подписки и **сразу удалял корзину**, и только потом создавал платёж. `createCartPayment == null` → 503, корзина пуста, `CREATED`-подписки висят без платежа, повторить нельзя (`cart_empty`). `orderId` — новый `UUID` на каждый запрос, поэтому двойной submit = второй заказ и второй платёж | Порядок: строки заказа (`CREATED`, общий `orderId`) → `createCartPayment` → **только при успехе** `PENDING` и удаление позиций корзины. При отказе — `DELETE ... WHERE id = ANY(?)` откатывает строки, корзина цела. Незавершённый заказ переиспользуется по стабильному `orderId` (тот же `Idempotence-Key` `order-<orderId>`); `succeeded` → начисление без нового счёта; `canceled` → FAILED и новый заказ; `null` → 503. Два одновременных checkout сериализуются по `site_user_id` (полоса блокировок), второй видит заказ первого |
| **BR-514** | `karaoke-web/.../services/SubscriptionRenewalScheduler.kt:82` (`tryRenew`), `:144` (`recordPaid`), `:155` (`createRenewal`), `:192` (`isUnfinishedRenewal`), `:208` (`idempotenceKeyFor`) | `result.id` отбрасывался → `yookassa_payment_id` пуст → вебхук о списании не находил подписку (`unknown_subscription`), доступ не выдавался; списание до фиксации, без транзакции; `Idempotence-Key` = `renew-<userId>-<yyyy-MM-dd>` — сбой после списания → на следующем тике новая строка, новый ключ, **второе списание за тот же период** | Ключ привязан к **строке продления** (`renew-<userId>-sub<id>`), строка коммитится до списания → повтор тика возвращает тот же платёж ЮKassa (дедупликация). `result.id` сохраняется **до** начисления; статус PAID и продление — транзакцией `SubscriptionFulfillmentService`. Незавершённая строка прошлого тика переиспользуется (признак — непустой `yookassaPaymentMethodId`, чтобы не «продлить» ручную PENDING-покупку и не создать для неё второй платёж). Неопределённый результат списания (таймаут) **не** закрывает строку: повтор — тем же ключом. Отмена платежа (verify = `canceled`) закрывает строку, следующая попытка — новая строка и новый ключ |

**Публичный контракт API.** Форма ответов не менялась: `POST /cart/checkout` и
`POST /subscription/create` возвращают те же поля (`orderId`/`subscriptionId`, `status`,
`confirmationUrl`), коды ошибок прежние (`payment_unavailable` — 503, `cart_empty` — 400,
`already_subscribed` — 409). Изменилось **поведение при повторе**: он переиспользует
существующий заказ, а не создаёт второй (это и есть исправление). Фронтенд
(`karaoke-public/src/views/CartView.vue:148–167`) читает только `confirmationUrl` и
`error === 'cart_empty'`.

**Миграция БД не требуется.** Признак «уже обработано» — существующий `status`
(условный `UPDATE`), ключи идемпотентности — существующие `order_id` /
`yookassa_payment_id`; новых колонок нет. Файлов в `deploy/karaoke-db/` не добавлялось.

---

## 2. Корень (общий для четырёх записей)

Один класс дефекта: **деньги двигаются раньше, чем фиксируется результат**, а признак
«этот платёж уже обработан» вычисляется чтением (`if (status == PAID)`, `if (status ==
"pending")`) вместо атомарной записи.

| Аспект | Было | Стало |
|---|---|---|
| Признак «уже начислено» | чтение `subs.all { PAID }` перед обработкой | условный `UPDATE ... WHERE status <> 'PAID'` в той же транзакции, что начисление |
| Граница транзакции | `sub.save()` (autocommit), затем `user.save()` | `autoCommit=false` → захват → начисление → `commit`/`rollback` |
| Признак «этот заказ тот же» | новый `UUID`/новая строка на каждый запрос | стабильный `orderId` + ключ идемпотентности от сущности |
| Неизвестный статус платежа | «создадим ещё один» | fail-closed (503/5xx) |
| Побочный эффект заказа | корзина очищалась до платежа | только после успешного платежа |

---

## 3. Тесты «до/после»

Все четыре набора — в `karaoke-web/src/test/...`, стиль репозитория (Mockito + поддельное
`KaraokeConnection`, живая БД и реальный API ЮKassa не используются).

| Набор | Покрывает | Тестов |
|---|---|---|
| `services/SubscriptionFulfillmentServiceTest.kt` | BR-020: атомарный захват, отсутствие второго продления, rollback, сохранение хвоста срока, `autoCommit` | 7 |
| `services/PaymentOrderReuseTest.kt` | BR-021 + BR-014: решение по статусу платежа (`pending`/`waiting_for_capture`/`succeeded`/`canceled`/`null`/неизвестный) | 6 |
| `controllers/PublicCartCheckoutOrderTest.kt` | BR-014: порядок «платёж → фиксация заказа/очистка корзины», откат при отказе платежа, пустой `id` | 3 |
| `services/SubscriptionRenewalIdempotenceTest.kt` | BR-514: стабильность ключа для строки продления, различие ключей для новой строки, независимость от пользователя | 3 |

Итого 19 тестов.

### Команда

```bash
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew --max-workers=2 \
  -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" \
  :karaoke-web:test --tests '*SubscriptionFulfillmentServiceTest' \
                    --tests '*PaymentOrderReuseTest' \
                    --tests '*SubscriptionRenewalIdempotenceTest' \
                    --tests '*PublicCartCheckoutOrderTest'
```

### ДО (старая логика временно возвращена в тот же шов, тест не менялся)

**BR-020** — `claimPaid` снова безусловный (`return true`):

```
SubscriptionFulfillmentServiceTest > повторный вебхук не продлевает срок второй раз() FAILED
    org.opentest4j.AssertionFailedError: второй обработчик не должен считать себя первым
        ==> expected: <false> but was: <true>
7 tests completed, 1 failed
BUILD FAILED
```

**BR-021/BR-014** — `PaymentOrderReuse.decide` снова «только `pending`»:

```
PaymentOrderReuseTest > succeeded - подписка уже оплачена, второй платёж не создаётся() FAILED
PaymentOrderReuseTest > неизвестный статус - fail-closed, а не новый счёт() FAILED
PaymentOrderReuseTest > ЮKassa недоступна - fail-closed, новый платёж не создаётся() FAILED
PaymentOrderReuseTest > waiting_for_capture - переиспользуем тот же платёж() FAILED
6 tests completed, 4 failed
BUILD FAILED
```

**BR-014** — `commitCartOrder` снова фиксирует заказ/чистит корзину до проверки платежа:

```
PublicCartCheckoutOrderTest > платёж не создан - заказ откатывается, корзина не трогается() FAILED
PublicCartCheckoutOrderTest > пустой id платежа трактуется как отказ() FAILED
3 tests completed, 2 failed
BUILD FAILED
```

**BR-514** — ключ снова от даты запуска:

```
SubscriptionRenewalIdempotenceTest > ключ привязан к строке продления, а не к дате запуска() FAILED
3 tests completed, 1 failed
BUILD FAILED
```

### ПОСЛЕ

```
SubscriptionFulfillmentServiceTest:  tests=7  failures=0  errors=0
PaymentOrderReuseTest:               tests=6  failures=0  errors=0
PublicCartCheckoutOrderTest:         tests=3  failures=0  errors=0
SubscriptionRenewalIdempotenceTest:  tests=3  failures=0  errors=0
BUILD SUCCESSFUL
```

Сценарии из задания покрыты: **повторный вебхук** (тест «повторный вебхук не продлевает срок
второй раз»), **параллельные запросы** (условный `UPDATE`: 0 строк у второго обработчика;
сериализация checkout по пользователю), **повторная доставка** (`succeeded` → начисление без
второго счёта; повтор checkout → тот же `orderId`/`confirmationUrl`).

---

## 4. Проверки

| Проверка | Команда | Результат |
|---|---|---|
| Тесты `karaoke-app` + `karaoke-web` | `./gradlew --max-workers=2 ... :karaoke-app:test :karaoke-web:test` | `BUILD SUCCESSFUL` |
| Линтеры Kotlin | `./gradlew ... :karaoke-app:ktlintCheck :karaoke-web:ktlintCheck` | `BUILD SUCCESSFUL` |
| Структура knowledge | `bash tools/check-knowledge-structure.sh` | `OK: knowledge/ structure valid (9/9 checks passed)` |
| Ссылки в документах | `python3 tools/check-doc-references.py` | после добавления отчёта — без проблем (см. ниже) |
| Изоляция субагентов | `bash tools/check-subagent-isolation.sh` | ветка `583-pkg-money-idempotency`, чужих коммитов нет |
| Per-feature документ | `bash tools/check-feature-doc.sh docs/features/subscription-payment-idempotency.md` | `OK [...] — Все документы валидны` |

Замечание: Kotlin-демон в этом окружении падает на read-only `~/.local/share/kotlin/daemon` и
Gradle переходит на «Compile without Kotlin daemon» — сборка от этого успешна (в логе есть
`Daemon compilation failed`, итог — `BUILD SUCCESSFUL`).

Реальный API ЮKassa не вызывался, ключи не использовались: все внешние вызовы в тестах —
заглушки (`PaymentService` mock, `SubscriptionFulfillmentService` с поддельным JDBC).

---

## 5. Что за владельцем (продуктовые решения и остаточные риски)

1. **Хвосты от старой версии автопродления.** Строки, созданные до правки, несут ключ
   `renew-<userId>-<yyyy-MM-dd>` и могут остаться `PENDING` без `yookassa_payment_id` при
   сбое после списания. Код их переиспользует и **не** спишет второй раз (ключ теперь от
   строки), но факт возможного «списания без начисления» надо сверить вручную в кабинете
   ЮKassa:
   ```sql
   SELECT id, site_user_id, created_at, final_price
     FROM tbl_subscriptions
    WHERE scope = 'SITE' AND auto_renew = true
      AND status IN ('PENDING','CREATED')
      AND yookassa_payment_id = ''
      AND created_at < '2026-10-24'
    ORDER BY created_at DESC;
   ```
   Решение (начислить вручную / закрыть строки) — за владельцем.
2. **Ограничение числа попыток автопродления.** Неопределённый результат списания (таймаут)
   оставляет строку `PENDING` и повторяется ежедневно с тем же ключом: денег это не списывает
   (fail-closed), но если карта мертва и ЮKassa вообще не создаёт платёж, попытки будут
   повторяться без ограничения. Ограничение требует нового поля (например, `renew_attempts`) —
   это миграция, решение за владельцем.
3. **Блокировка checkout — в пределах процесса** (полосы по `site_user_id`). Это корректно для
   текущего прода (один контейнер `karaoke-web`, см. `DEVELOPMENT.md`). При переходе на
   несколько инстансов нужна блокировка в БД (`pg_advisory_xact_lock` по `site_user_id`) —
   решение по инфраструктуре за владельцем.
4. **UX при изменении корзины после создания заказа.** Повторный checkout переиспользует
   существующий заказ: песни, добавленные в корзину после его создания, в этот заказ не
   попадают (остаются в корзине на следующий). Это осознанный fail-safe против двойного
   списания; если нужен другой сценарий («доплатить разницу»), это продуктовое решение.
5. **`status = FAILED` при отказе карты** ставится только по подтверждённому `canceled` от
   ЮKassa; «сетевой» отказ оставляет строку открытой для повтора. Если владелец хочет иной
   порог (например, закрывать после N подряд неудач), нужен счётчик попыток (см. п. 2).

---

## 6. Изменённые файлы

```
M karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Subscription.kt
    (+findPendingSite/findPendingSong учитывают CREATED; pickUnfinished; loadUnfinishedCartOrder)
M karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicCartController.kt
    (BR-014: порядок «платёж → фиксация», откат заказа, переиспользование заказа, блокировка)
M karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicPaymentController.kt
    (BR-020: транзакционное начисление через сервис, шов handleWebhook)
M karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicSubscriptionController.kt
    (BR-021: решение по статусу, досоздание платежа для CREATED, fail-closed, SONG)
M karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/SubscriptionRenewalScheduler.kt
    (BR-514: ключ от строки, сохранение result.id, транзакционное начисление, повтор)
A karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/SubscriptionFulfillmentService.kt
A karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/PaymentOrderReuse.kt
M knowledge/domains/monetization/domain.md
    (инварианты 4–6, fulfillment, контракт вебхука, новые сервисы)
A docs/features/subscription-payment-idempotency.md            (FR-009, per-feature)
A karaoke-web/src/test/kotlin/.../services/SubscriptionFulfillmentServiceTest.kt
A karaoke-web/src/test/kotlin/.../services/PaymentOrderReuseTest.kt
A karaoke-web/src/test/kotlin/.../controllers/PublicCartCheckoutOrderTest.kt
A karaoke-web/src/test/kotlin/.../services/SubscriptionRenewalIdempotenceTest.kt
A specs/365-pkg-money-idempotency/report.md                     (этот отчёт)
```

`specs/336-code-audit/registry.csv`, чужие пакеты, `deploy/`, контейнеры и БД не менялись;
миграций нет; rebase не выполнялся; push/PR не создавались.

---

## 7. Хэши

| Что | Хэш |
|---|---|
| База (master) | `79b74b0a` |
| Код + тесты + knowledge + per-feature | `2c72c520` (`2c72c520114dbc8b3e68322499945cc92d10c799`) |
| Отчёт (этот файл) | см. `git log --oneline -2` ветки `583-pkg-money-idempotency` |
