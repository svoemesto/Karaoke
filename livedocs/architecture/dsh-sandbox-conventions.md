---
status: Active
slug: dsh-sandbox-conventions
type: topic
related:
  - ../architecture/ci-cd-pipeline.md
  - ../architecture/docker-conventions.md
  - ./livedocs/CONVENTIONS.md
---

# DSH sandbox — конвенции запуска gradle / docker

> Этот LiveDoc — НЕ про прод, а про локальный sandbox DSH (DeepSeek Harness)
> сессий, где `$HOME/.gradle/`, `$HOME/.docker/` read-only.

## Gradle: обязательный `GRADLE_USER_HOME`

**Проблема**: `./gradlew ...` без override → wrapper пытается писать в
`/home/nsa/.gradle/wrapper/dists/...` — read-only для sandbox. Билд падает:
```
FileNotFoundException: .../gradle-8.14-bin.zip.lck (Файловая система доступна только для чтения)
```

**Решение**: всегда указывать `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle` (папка
`.gradle` ВНУТРИ проекта — writable для sandbox).

**Канонический паттерн** (из `specs/304-idempotent-path-sanitize/tasks.md`):
```bash
JAVA_HOME=/usr/lib/jvm/jdk-18 GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle \
  ./gradlew :karaoke-app:compileKotlin --parallel
```

**Не использовать**:
- ❌ `GRADLE_USER_HOME=/home/nsa/.gradle` — read-only
- ❌ `GRADLE_USER_HOME=/tmp/gradle-home` — кеши теряются между сессиями
- ❌ `/home/nsa/.gradle/wrapper/dists/gradle-9.1.0-bin/.../gradle --version` напрямую — native lib не инициализируется без правильного GRADLE_USER_HOME

**5 шагов обязательной проверки** (AGENTS.md § «Обязательная проверка после ЛЮБОГО изменения кода»):
1. Backend compile: `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel`
2. Линтеры: `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:ktlintCheck` + `cd webvue3 && npm run lint` + `cd karaoke-public && npm run lint`
3. Backend bootJar: `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:bootJar --parallel` (на `nsa-i9` — также `:karaoke-app:bootJar`)
4. Frontend Vite: `cd webvue3 && npm run build && npm run format:check` (то же для `karaoke-public`)
5. Docker: `cd deploy && bash do.sh build_webvue3` (см. ниже про docker)

## Docker: buildx cache в writable location

**Проблема**: `bash do.sh build_webvue3` → `docker image build` →
```
failed to update builder last activity time:
open /home/nsa/.docker/buildx/activity/.tmp-defaultXXX: read-only file system
```

buildx пишет activity в `~/.docker/buildx/activity/` по умолчанию — read-only для sandbox.

**Решение**: три env переменные перенаправляют writable-локации:
```bash
export DOCKER_BUILDKIT=1 \
       DOCKER_CONFIG=/tmp/docker-config \
       BUILDX_STATE_DIR=/tmp/buildx

# Создать каталоги заранее:
mkdir -p /tmp/docker-config /tmp/buildx

# Теперь do.sh работает:
bash do.sh build_webvue3
```

**Альтернатива** (если do.sh напрямую вызывает gradle внутри — что не должно быть, но проверьте):
```bash
JAVA_HOME=/usr/lib/jvm/jdk-18 \
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle \
DOCKER_BUILDKIT=1 \
DOCKER_CONFIG=/tmp/docker-config \
BUILDX_STATE_DIR=/tmp/buildx \
bash do.sh build_webvue3
```

**Кеш buildx — sticky**: после первой успешной сборки повторные билды используют кеш
(даже если `docker rmi` сделан). Для force-rebuild:
```bash
docker rmi -f svoemestodev/karaoke-app:1    # удалить image
rm -rf /tmp/buildx                          # очистить buildx cache
mkdir -p /tmp/buildx
# затем пересобрать
```

## Когда sandbox-сессия переходит в /tmp

`/tmp/` writable для всех процессов в сессии DSH. Использование `/tmp/docker-config`
и `/tmp/buildx` — стандартный workaround. Файлы не переживают рестарт сессии — но
это нормально, потому что:
- `DOCKER_CONFIG` нужен только для redirect'а конфига (новый каталог при рестарте — нормально).
- `BUILDX_STATE_DIR=/tmp/buildx` нужен только для redirect'а cache (новый кеш создаётся при следующем билде).

## Раскладка контейнеров проекта Karaoke

Частая путаница между `karaoke-web` и `karaoke-webvue3`:

| Контейнер | Роль | Порт | Compose-файл |
|-----------|------|------|--------------|
| `karaoke-app` | Backend Spring Boot (admin API) | 8899 (?) | `docker-compose-app.yml` |
| `karaoke-webvue3` | **Admin SPA** (Vue 3, webvue3) | 7906 | `docker-compose-webvue3.yml` |
| `karaoke-web` | Public web (Thymeleaf) — **НА ПРОДЕ**, к админке отношения не имеет | 8897 | `deploy_web.sh` |
| `karaoke-public` | Public Vue SPA | — | `docker-compose-public.yml` |
| `karaoke-database` | PostgreSQL | 5432 | `docker-compose-database.yml` |

**Админский SPA** — это `karaoke-webvue3` (порт 7906), **НЕ** `karaoke-web`.

## Код

- `deploy/do.sh` — все bulk-команды (`build_webvue3`, `build_public`, `build_karaoke-app`, etc.)
- `webvue3/src/components/Common/SmartCopy/SmartCopyModal.vue` — эталон inline-form модалки

## История

- Создан: 2026-09-08 (извлечено из lessons spec 319)
- Последнее обновление: 2026-09-08
