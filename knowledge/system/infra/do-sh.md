# Component: do.sh (deploy entry point)

> **Домен**: system (infrastructure)
> **Компонента**: главный скрипт деплоя `deploy/do.sh`.

## Назначение

`deploy/do.sh` — **единая точка входа** для всех операций deploy/build/start/stop на admin-машине. 40 функций. Bash.

## Архитектура

### Группы функций (по KDoc/usage)

**Build (~12 функций)**:

- `do_build` — главная точка (все образы).
- `build_jars` — собирает Gradle bootJar.
- `build_images` — собирает Docker images.
- `do_build_app`, `do_build_app_nocache` — karaoke-app.
- `do_build_demucs` — образ с Demucs.
- `do_build_web` — karaoke-web.
- `do_build_webvue` / `do_build_webvue3` — старая/новая admin UI.
- `do_build_public` — karaoke-public.

**Build + start (~6 функций)**:

- `build_start_app`, `build_start_web`, `build_start_webvue`,
  `build_start_webvue3`, `build_start_public` — собрать И запустить.

**Start/Stop (~10 функций)**:

- `do_start`, `do_stop` — главные.
- `do_load` — загрузить initial data.
- `do_start_db`, `do_stop_db` — только БД.
- `do_start_web`, `do_stop_web` — только web.
- `do_start_webvue`, `do_stop_webvue` — только webvue.
- `do_start_webvue3`, `do_stop_webvue3` — только webvue3.
- `do_start_public`, `do_stop_public` — только public.

**Utility (~10 функций, см. файл)**:

- Health checks.
- Image prune.
- Logs.

**NB**: точная сигнатура — см. исходник `deploy/do.sh`.

## Использование

```bash
# Из директории deploy/
cd deploy
./do.sh build_start_app    # собрать и запустить karaoke-app
./do.sh stop               # остановить всё
./do.sh build_public       # собрать karaoke-public
```

**NB**: путь к `do.env` (секреты) — `web-server-deploy/deploy/do.env`
(для прода) или `do.env` локально.

## Известные TODO

- [ ] **Полная сигнатура** каждой из 40 функций — Pass 343+.
- [ ] **Какие команды поддерживают `*_nocache` / `*_nohup`** —
      варианты флагов.
- [ ] **Какие docker-compose-*.yml соответствуют каким командам**.
- [ ] **Health check endpoints** — где определены.
- [ ] **Error handling** — exit code strategy.

## Связь с другими компонентами

- **[deploy-overview.md](deploy-overview.md)** — общая структура
  deploy/.
- **AGENTS.md** — workflow для admin-машины.

## Changelog

- **Pass 355** (2026-09-09): Initial. Автор: agent (Karaoke).