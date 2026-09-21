# Runbook: Восстановление после падения nvidia-persistenced

> **Дата создания**: 2026-09-15 | **Инцидент**: OpenProject #126 (Pass 401)
> **Связанные ADR**: нет | **Tier-1 hard gate**: deploy/restart.

## Симптомы

- `karaoke-app` в статусе `Created` после `docker run` (или `deploy/do.sh start_karaoke-app`).
- `docker logs karaoke-app` пустой или короткий.
- `docker inspect karaoke-app` показывает `State.Status = created`, без `running`.
- Конкретная ошибка:
  ```
  Error response from daemon: failed to create task for container: failed to create
  shim task: OCI runtime create failed: runc create failed: unable to start container
  process: error during container init: failed to fulfil mount request:
  open /run/nvidia-persistenced/socket: no such file or directory
  ```

## Причина

После обновления **nvidia-драйвера** или **nvidia-container-toolkit** (apt/dnf/yum)
сервис `nvidia-persistenced` не поднялся автоматически. NVIDIA Container Toolkit при
старте GPU-контейнера делает bind-mount `/run/nvidia-persistenced/socket` — без
файла инициализация падает.

## Решение

**Шаг 1. Поднять сервис** (от root):

```bash
sudo systemctl restart nvidia-persistenced
sudo systemctl enable nvidia-persistenced  # автоподъём при загрузке
```

**Шаг 2. Проверить socket**:

```bash
ls -la /run/nvidia-persistenced/socket
# Ожидаемый вывод: srwxrwxrwx 1 root root 0 ... /run/nvidia-persistenced/socket
```

**Шаг 3. Перезапустить karaoke-app** (через `deploy/do.sh`):

```bash
cd deploy && bash do.sh restart_karaoke-app
```

Проверить:

```bash
docker ps --filter "name=karaoke-app"
# Ожидаемый вывод: karaoke-app ... Up N minutes ...
docker logs --tail 20 karaoke-app
# Должно быть: "Started KaraokeApplicationKt"
```

## Альтернатива: отключить GPU (workaround)

Если GPU-функционал не критичен прямо сейчас (например, рендер/стемы подождут):

```bash
# В deploy/.env:
ENABLE_APP_GPU=0
```

Karaoke-app стартует без GPU. Рендер видео будет CPU-only, заметно медленнее.
Demucs2/Sheetsage через docker — аналогично медленнее.

**Вернуть обратно**:
```bash
# В deploy/.env:
ENABLE_APP_GPU=1
sudo systemctl restart nvidia-persistenced
cd deploy && bash do.sh restart_karaoke-app
```

## Профилактика

1. **NOPASSWD** для nvidia-сервиса (если часто падает):
   ```bash
   sudo visudo
   # Добавить:
   nsa ALL=(ALL) NOPASSWD: /usr/bin/systemctl restart nvidia-persistenced, /usr/bin/systemctl enable nvidia-persistenced
   ```

2. **Health-check сервиса** в cron (раз в час):
   ```cron
   0 * * * * /usr/bin/systemctl is-active --quiet nvidia-persistenced || /usr/bin/systemctl restart nvidia-persistenced
   ```

3. **После обновления драйверов** — всегда проверять, что модуль ядра загружен:
   ```bash
   nvidia-smi  # должно показать GPU; если нет — перезагрузка хоста
   ```

## Связанные документы

- [`docs/ops/log-correlation.md`](log-correlation.md) — диагностика через docker logs.
- [`deploy/docker-compose-app.gpu.yml`](../../deploy/docker-compose-app.gpu.yml) — GPU override.
- AGENTS.md Tier-1: hard-gates Pass 372-375 (Gradle/Docker/Containers/Frontend).
