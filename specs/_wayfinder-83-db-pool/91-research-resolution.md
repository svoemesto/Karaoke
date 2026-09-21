# #91 Resolution — Secondary Source НЕ найден: фикс #89 не задеплоен

> **Resolution**: 2026-09-13T14:55Z.
> **Источник**: research субагент `44f1c48c-12d5-460f-b29d-d1a714eb9f43`.

## Verdict (в 1 строку)

**Вторичный источник НЕ найден.** Фикс #89 (`cacheFillerExecutor maxPoolSize=16`)
**корректен**, но **не задеплоен** в работающий `karaoke-app` — образ от 11 сентября.

## Ключевые факты

1. **320 ошибок** `getConnection Exception` за 9 минут после рестарта (cold-start spike):
   - 173 за 14:42 MSK
   - 147 за 14:43 MSK
   - 272 от `pool-2-thread-N` (диапазон 1..115) ← это **старый** `cacheFillerExecutor`
   - 48 от `http-nio-8899-exec-N`

2. **Байт-код проверка** работающего контейнера:
   ```bash
   docker exec karaoke-app bash -c 'unzip -p /app.jar \
     BOOT-INF/classes/com/svoemesto/karaokeapp/services/StorageMetadataCache.class \
     | od -An -c | grep -oE "newCachedThreadPool|ThreadPoolExecutor"'
   # → newCachedThreadPool  ← СТАРЫЙ КОД
   ```

3. **Собранный jar** (сегодня): содержит `ThreadPoolExecutor` (фикс применён).

4. **Источник проблемы**: образ в registry устарел. `docker-compose-app-new-comp.yml`
   указывает на старый тег, в то время как новый код собран и доступен локально.

5. **Остальные пулы проверены**:
   - `cacheFillerExecutor` (после фикса) — bounded
   - `repairExecutor` — 4 потока (bounded)
   - `AdminTaskService` — 2 потока (bounded)
   - `lyricsSearchExecutor` — 4 потока (bounded)
   - `taskScheduler` — 4 потока (bounded)
   - `KaraokeStorageService` write-through hooks — НЕ источник (2 sync hook)
   - `KaraokeProcessWorker` — НЕ источник (только 6 RENDER_MP4_DEMO за пик)

## Минимальное вмешательство

1. **Пересобрать Docker-образ** `karaoke-app`:
   ```bash
   cd /home/nsa/Karaoke/deploy && bash do.sh build_app
   ```
   ИЛИ вручную (если `do.sh` зависает на read-only FS — прецедент Pass 372):
   ```bash
   cd /home/nsa/Karaoke/karaoke-app/build/libs/
   DOCKER_BUILDKIT=0 docker build \
     --build-arg APP_VERSION=1 \
     -t svoemesto/karaoke-app:1 \
     -f /home/nsa/Karaoke/deploy/karaoke-app/Dockerfile .
   ```

2. **Перезапустить `karaoke-app`**:
   ```bash
   cd /home/nsa/Karaoke/deploy && bash do.sh start_app
   ```

3. **После перезапуска** проверить acceptance:
   ```bash
   # Байт-код в новом контейнере
   docker exec karaoke-app bash -c 'unzip -p /app.jar \
     BOOT-INF/classes/com/svoemesto/karaokeapp/services/StorageMetadataCache.class \
     | od -An -c | grep -oE "newCachedThreadPool|ThreadPoolExecutor"'
   # Ожидаемо: ThreadPoolExecutor

   # Cold-start спайк должен исчезнуть
   docker logs --since 5m karaoke-app | grep -c "getConnection Exception"
   # Ожидаемо: 0
   ```

## Артефакты

- **Research отчёт**: `/home/nsa/Karaoke/research/83-followup-secondary-source/REPORT.md`
  (SHA256: `a74c903a5a9feb25124c7b5d51e0bc06f742c8cd3a07bdf0facbf531c039b34e`)
- **Логи**: `karaoke-app-logs-5m.txt` (5468 строк), `karaoke-web-logs-5m.txt`, `pg_stat_activity.txt`

## Что осталось неизвестным

1. **Почему деплой не произошёл** — две копии образа в registry (technokad.ru:5000 vs svoemestodev). docker-compose указывает на устаревший.
2. **`docker-compose-app-new-comp.yml`** — какой именно image tag там прописан. Проверить нужно.

— resolution для #91