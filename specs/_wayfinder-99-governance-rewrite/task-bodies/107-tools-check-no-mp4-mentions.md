<!-- description task-тикета #107 (Step A: guard-скрипты) -->

## Question

Создать **guard-скрипт `tools/check-no-mp4-mentions.sh`** для правила R-11
(MP4/скачивание запрещено упоминать в рекламе и комментариях).

Зачем:
- Из #103: R-11 живёт в 2 файлах (CLAUDE.md + architecture-conventions.md),
  0 guard-скриптов.
- По оферте «доступ — только онлайн», упоминание MP4/скачивания в коде —
  нарушение policy, ловится только ручным review комментариев.
- С этим скриптом hard-gate coverage = 9/50 = 18%.

**Что должно быть в ответе**:

1. Файл `tools/check-no-mp4-mentions.sh` (~30-50 строк).
   - Grep `\bmp4\b|скачивани` в `webvue3/src/**/*.{vue,js,ts}`, `karaoke-public/src/**/*.{vue,js,ts}`.
   - Исключение: `docs/features/idempotent-path-sanitize.md` (там валидное использование).
   - Pretty-print с filename:line.

2. Подключение к `.pre-commit-config.yaml` + `.github/workflows/lint.yml`.

3. Тест.

## Notes

- После merge — cross-ref в architecture-conventions.md.

## Тип

`[wayfinder:task]`.

## Блокирует

Ничего.
