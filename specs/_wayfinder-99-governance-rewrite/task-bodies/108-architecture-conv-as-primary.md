<!-- description task-тикета #108 (Step B: content rewrite) -->

## Question

Расширить `knowledge/guidelines/architecture-conventions.md` как **primary
source** для build/docker/runtime/sanitizer правил.

Зачем:
- Из Q2 (Pass 379, #104 resolution): architecture-conventions.md становится
  single source of truth для R-04 (nginx), R-05 (node), R-06 (TOP-10 ловушек),
  R-08 (sanitizer), R-11 (MP4), R-44 (MLT).
- Сейчас файл 79 строк, упоминается только в AGENTS.md.
- После этого тикета архитектурные конвенции будут **scan-friendly
  справочник** для всех dev-агентов.

**Что должно быть в ответе**:

1. Расширить `architecture-conventions.md` до ~150-200 строк:
   - Каждое из правил R-04, R-05, R-06, R-08, R-11, R-44 получает свою секцию.
   - Каждая секция ≤ 10 строк.
   - Формат: `### <Rule>` → `**Rule**: ...` → `**Why**: ...` → `**Enforcement**: check-*.sh`.
   - MLT rendering (R-44) — отдельный раздел с явным указанием «НЕ ffmpeg» (ADR-0002).

2. Добавить header `> This file is the canonical reference for build/runtime/docker conventions.` —
   чтобы модель знала, что это primary source.

3. Удалить дубликаты из CLAUDE.md L123-135 (TOP-10 ловушек) и L127-128 (nginx/node) — заменить
   однострочными cross-ref `→ knowledge/guidelines/architecture-conventions.md § <Rule>`.

4. Удалить дубликаты из AGENTS.md L287 (🚦 не nginx:alpine) — оставить как memory aid,
   но добавить cross-ref.

5. Запустить `tools/lint-knowledge.py` после правок (cross-links обязательны).

## Notes

- Это **Step B** в имплементации. Зависит от #4 (Step A: check-docker-image-tags.sh
  нужен, чтобы enforcement был).
- **Готов к старту сразу** после #1 и #2 (или вместе с ними, если файлы
  не пересекаются).

## Тип

`[wayfinder:task]`.

## Блокирует

#105, #109 (cleanup).

## Заблокирован

Ничего.
