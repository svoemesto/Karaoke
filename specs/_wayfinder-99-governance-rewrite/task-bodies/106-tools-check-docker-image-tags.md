<!-- description task-тикета #106 (Step A: guard-скрипты) -->

## Question

Создать **guard-скрипт `tools/check-docker-image-tags.sh`** для правил R-04
(`nginx:stable` вместо `nginx:alpine`) и R-05 (`node:22-alpine` вместо
`node:latest`). Скрипт grep'ает `FROM nginx:|FROM node:` в `**/Dockerfile*`
и проверяет, что теги соответствуют whitelist.

Зачем:
- Из #103: правила R-04 и R-05 живут в 4 файлах каждый (CLAUDE.md + AGENTS.md
  + constitution.md + architecture-conventions.md), 0 guard-скриптов.
- В Karaoke уже была проблема (Pass 245): `nginx:alpine` падает на bash missing.
  Сейчас мониторинг только runtime-failure, не pre-deploy.
- С этим скриптом hard-gate coverage = 8/50 = 16%.

**Что должно быть в ответе**:

1. Файл `tools/check-docker-image-tags.sh` (~50-80 строк).
   - Grep `FROM nginx:` → должно быть `:stable|stable-alpine` (одна строка whitelist).
   - Grep `FROM node:` → должно быть `:22-alpine` или паттерн.
   - Любые `:latest`, `:alpine`, без тега → fail.
   - Pretty-print с filename:line.

2. Whitelist читается из `tools/check-docker-image-tags.allowlist` (если есть)
   или hard-coded.

3. Подключение к `.pre-commit-config.yaml` + `.github/workflows/lint.yml`.

4. Тест: запустить на репо.

## Notes

- После merge — cross-ref в architecture-conventions.md.

## Тип

`[wayfinder:task]`.

## Блокирует

Ничего.

## Заблокирован

Ничего.
