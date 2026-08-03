# Quickstart: Валидация backfill флагов публикаций

**Branch**: `124-news-flags-backfill` | **Date**: 2026-08-03 | **Spec**: [spec.md](./spec.md) | [Plan](./plan.md) | [API](./contracts/api.md) | [Data Model](./data-model.md)

## Обзор

Гайд для end-to-end валидации backfill флагов публикаций готовых песен + kill-switch + sync + проверки отсутствия лавины новостей. Все шаги выполняются вручную администратором.

## Предварительные условия

- `karaoke-app` контейнер запущен на LOCAL (админ-машина).
- `karaoke-web` контейнер запущен на PROD (сервер `188.119.64.111`).
- webvue3 собран и доступен (открывается админский UI).
- SSH-доступ к PROD (для проверки `tbl_news` напрямую, опционально).
- На LOCAL и PROD есть готовые песни с неконсистентными флагами (для чистой валидации — выбрать тестовую песню с `newsAvailableAnnounced=false`).

## Шаг 0: Проверка «до» (baseline)

**Цель**: Зафиксировать состояние до backfill — сколько готовых песен имеют неконсистентные флаги.

### 0.1. Dry-run backfill на LOCAL

В webvue3 (или через curl) вызвать:

```bash
curl -s -X POST "http://localhost:8898/api/utils/backfillpublishflags" \
  -d "target=local&dryRun=true"
```

**Ожидаемый результат**: SSE-тост с отчётом:
```json
{
  "totalCandidates": <N>,
  "fixedNewsAvailableAnnounced": <A>,
  "fixedPremiumComplete": <B>,
  "alreadyOk": <C>,
  "skippedActivePublishing": <D>,
  "skippedNoMarkers": <E>,
  "durationMs": 0,
  "dryRun": true
}
```

Записать числа `A`, `B`, `C`, `D`, `E` — они должны совпасть с реальным backfill (SC-010).

### 0.2. Проверка `tbl_news` на PROD «до»

```bash
# На PROD (через docker exec на сервере, или через любой psql-клиент):
docker exec karaoke-db psql -U postgres -d karaoke -c \
  "SELECT COUNT(*) FROM tbl_news WHERE created_at >= NOW() - INTERVAL '1 hour';"
```

**Ожидаемый результат**: число (записать — это baseline, после backfill+sync новых записей за окно kill-switch не должно появиться).

## Шаг 1: Backfill на LOCAL

**Цель**: Привести флаги всех готовых песен на LOCAL в complete-состояние (FR-001, FR-002).

### 1.1. Запуск backfill (real, не dry-run)

```bash
curl -s -X POST "http://localhost:8898/api/utils/backfillpublishflags" \
  -d "target=local&dryRun=false"
```

**Ожидаемый результат**:
- Немедленный ответ `true`.
- SSE-прогресс-тосты каждые ~500 песен: «Обработано 500/N...», «Обработано 1000/N...» и т.д.
- Финальный SSE-тост с отчётом (см. формат в [API](./contracts/api.md)).
- Числа `fixedNewsAvailableAnnounced`/`fixedPremiumComplete` в финальном отчёте совпадают с dry-run из Шага 0.1 (SC-010).
- `durationMs` — ≤ 15 минут (SC-007).

### 1.2. Проверка идемпотентности

```bash
# Повторный dry-run — должен показать 0 расхождений
curl -s -X POST "http://localhost:8898/api/utils/backfillpublishflags" \
  -d "target=local&dryRun=true"
```

**Ожидаемый результат**: `fixedNewsAvailableAnnounced=0`, `fixedPremiumComplete=0`, `alreadyOk=N` (SC-008).

### 1.3. Проверка тестовой песни на LOCAL

Выбрать готовую песню (например, id=X) и проверить флаги:

```bash
docker exec karaoke-db psql -U postgres -d karaoke -c \
  "SELECT id, id_status, player_readiness_flags FROM tbl_songs WHERE id = X;"
```

**Ожидаемый результат**: в JSON есть все ключи complete-набора (см. [Data Model](./data-model.md) → «После backfill»):
- `newsAvailableAnnounced: true`
- `newsPremiumPublishPending: false`
- `newsPremiumTelegramSent: true`
- `newsPremiumVkSent: true`
- `premiumAutoPublishState: "COMPLETE"`

### 1.4. Проверка `tbl_news` на LOCAL

```bash
docker exec karaoke-db psql -U postgres -d karaoke -c \
  "SELECT COUNT(*) FROM tbl_news WHERE created_at >= NOW() - INTERVAL '30 minutes' AND source='auto';"
```

**Ожидаемый результат**: `0` (на LOCAL backfill не создаёт новости по построению — нет `doChangeRecords`-точки в момент backfill).

## Шаг 2: Включение kill-switch на PROD

**Цель**: Заблокировать создание auto-новостей на PROD во время sync-окна (FR-010, FR-011).

### 2.1. Включить kill-switch

Выполняется на PROD (через админский UI karaoke-web, либо прямой curl на PROD):

```bash
# На PROD (заменить localhost на PROD-адрес при удалённом вызове):
curl -s -X POST "http://localhost:8897/api/properties/setproperty" \
  -d "key=newsAutoPublishKillSwitch&stringValue=true"
```

### 2.2. Проверить kill-switch

```bash
curl -s -X POST "http://localhost:8897/api/properties/getproperty" \
  -d "key=newsAutoPublishKillSwitch"
```

**Ожидаемый результат**: `true`.

## Шаг 3: Sync LOCAL→PROD

**Цель**: Разнести backfill-изменения флагов на PROD штатным sync-движком (FR-009).

### 3.1. Запустить sync

В webvue3 (админский UI) нажать кнопку «Sync» (существующий механизм sync LOCAL→PROD, по образцу обычного sync). Дождаться завершения sync.

### 3.2. Проверка `tbl_news` на PROD «после»

```bash
docker exec karaoke-db psql -U postgres -d karaoke -c \
  "SELECT COUNT(*) FROM tbl_news WHERE created_at >= '<время_до_Шага_1>' AND source='auto';"
```

**Ожидаемый результат**: `0` — kill-switch заблокировал создание auto-новостей во время sync (SC-003, SC-011).

### 3.3. Проверка тестовой песни на PROD

```bash
docker exec karaoke-db psql -U postgres -d karaoke -c \
  "SELECT id, id_status, player_readiness_flags FROM tbl_songs WHERE id = X;"
```

**Ожидаемый результат**: тот же complete-набор флагов, что на LOCAL (SC-001, SC-002) — sync разнёс изменения `player_readiness_flags`.

### 3.4. Проверка Telegram/VK scheduler'ов на PROD

```bash
# Логи karaoke-web на PROD — проверить, что PremiumAutoPublishScheduler/VkAutoPublishScheduler
# не активировались (нет "processSong" / "publishToVk" для старых песен):
docker logs karaoke-web 2>&1 | grep -E "PremiumAutoPublish|VkAutoPublish" | tail -50
```

**Ожидаемый результат**: нет записей о публикации старых песен. `PremiumAutoPublishScheduler.loadPendingIds` ищет по `"newsPremiumPublishPending":true` — после backfill этого ключа у готовых песен нет → scheduler не находит кандидатов (SC-004).

## Шаг 4: Снятие kill-switch

**Цель**: Восстановить нормальный flow новостей для truly новых песен (FR-012, SC-009).

### 4.1. Снять kill-switch

```bash
curl -s -X POST "http://localhost:8897/api/properties/setproperty" \
  -d "key=newsAutoPublishKillSwitch&stringValue=false"
```

### 4.2. Проверить

```bash
curl -s -X POST "http://localhost:8897/api/properties/getproperty" \
  -d "key=newsAutoPublishKillSwitch"
```

**Ожидаемый результат**: `false`.

## Шаг 5: Проверка отсутствия рецидива (save готовой старой песни)

**Цель**: Подтвердить, что обычный save() готовой старой песни после backfill+sync НЕ создаёт ложную новость (SC-005, SC-006).

### 5.1. Изменить поле готовой песни в webvue3

Открыть карточку готовой старой песни (id=X) в webvue3 → изменить любое поле (например, «Описание») → нажать Save.

### 5.2. Дождаться sync

В webvue3 нажать «Sync» (или дождаться автоматического, если настроен).

### 5.3. Проверка `tbl_news` на PROD

```bash
docker exec karaoke-db psql -U postgres -d karaoke -c \
  "SELECT COUNT(*) FROM tbl_news WHERE song_id = X AND created_at >= NOW() - INTERVAL '10 minutes';"
```

**Ожидаемый результат**: `0` (SC-005). Идемпотентность `markNewsAvailableIfReady` + `premiumAutoPublishState=COMPLETE` предотвратили рецидив.

### 5.4. Проверка `newsPremiumPublishPending` на PROD

```bash
docker exec karaoke-db psql -U postgres -d karaoke -c \
  "SELECT player_readiness_flags->>'newsPremiumPublishPending' as pending FROM tbl_songs WHERE id = X;"
```

**Ожидаемый результат**: `false` (SC-006). `markNewsAvailableIfReady` не переустановил флаг (условие `premiumAutoPublishState.isBlank() || "RUNNING"` ложно при `COMPLETE`).

## Шаг 6: Проверка нормального flow новой песни

**Цель**: Подтвердить, что kill-switch снят и truly новая песня (впервые становящаяся готовой) корректно получает новость и премиум-публикацию (SC-009).

### 6.1. Взять тестовую новую песню

Выбрать песню с `id_status < 6` (в работе), у которой все стемы/обложки/маркеры готовы, но статус ещё не 6. В webvue3 перевести в `id_status=6` (готово).

### 6.2. Дождаться sync LOCAL→PROD

### 6.3. Проверка `tbl_news` на PROD

```bash
docker exec karaoke-db psql -U postgres -d karaoke -c \
  "SELECT id, title, category, source FROM tbl_news WHERE song_id = <новая_id> ORDER BY created_at DESC LIMIT 1;"
```

**Ожидаемый результат**: 1 запись, `category='premium'`, `source='auto'`, заголовок «Новая песня: ...» (SC-009).

### 6.4. Проверка премиум-публикации

```bash
# Логи karaoke-web на PROD — PremiumAutoPublishScheduler должен обработать новую песню:
docker logs karaoke-web 2>&1 | grep -E "PremiumAutoPublish.*<новая_id>" | tail -20
```

**Ожидаемый результат**: записи о публикации в TG/VK (SC-009).

## Итоговая проверка (Definition of Done)

| Критерий | Проверка | Статус |
|---|---|---|
| SC-001: ≥99.9% готовых песен `newsAvailableAnnounced=true` на LOCAL и PROD | Шаг 1.3 + Шаг 3.3 | ☐ |
| SC-002: ≥99.9% готовых песен в complete-состоянии | Шаг 1.3 + Шаг 3.3 | ☐ |
| SC-003: 0 новых записей в `tbl_news` на PROD за окно kill-switch | Шаг 3.2 | ☐ |
| SC-004: 0 новых публикаций в TG/VK за период backfill+sync | Шаг 3.4 | ☐ |
| SC-005: save() готовой старой песни + sync → 0 новостей | Шаг 5.3 | ☐ |
| SC-006: `newsPremiumPublishPending` не становится `true` после save() | Шаг 5.4 | ☐ |
| SC-007: backfill ≤ 15 минут на LOCAL | Шаг 1.1 (`durationMs`) | ☐ |
| SC-008: повторный backfill → 0 исправлено | Шаг 1.2 | ☐ |
| SC-009: новая песня корректно получает новость + премиум | Шаг 6.3 + 6.4 | ☐ |
| SC-010: dry-run числа = real backfill числа | Шаг 0.1 vs 1.1 | ☐ |
| SC-011: 0 `source='auto'` в `tbl_news` за окно kill-switch | Шаг 3.2 | ☐ |

Если все ☐ → ✅ — фича валидирована end-to-end.