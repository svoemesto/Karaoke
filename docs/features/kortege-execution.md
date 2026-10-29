# Кортежное исполнение очереди обработки

> **Status**: active
> **Feature Key**: kortege-execution
> **Last Updated**: 2026-10-29
> **Spec**: [specs/674-kortege-execution/spec.md](../../specs/674-kortege-execution/spec.md)
> Per-feature документ по [Constitution VI FR-009](../../.specify/memory/constitution.md).

## Что делает

Исполняет набор шагов одной песни (кортеж, строки `tbl_processes` с общим `process_chain_id`)
как единицу: шаги `0..N-1` идут подряд, лейн занят кортежем до завершения, временные ресурсы
изолированы по `scopeId = process_chain_id ?: id`, а результат принимается только по содержимому.

## Зачем

Инцидент KARAOKE-308 (2026-10-29): при импорте папки «Сектор Газа / 2026 — Чёрный вторник»
из 8 песен стемы и `[key].json` появились у одной-двух, причём чужие. Причина — планировщик
сравнивал `process_order` между разными кортежами: шаги всех песен шли «по столбцам», все
`cp` перезаписывали один `file.flac`, первый `mv` забирал единственный результат, остальные
падали с `cannot stat`. Общие каталоги и фиксированные имена контейнеров делали подмену
данных возможной, а проверка «файл существует» — невидимой. Карта решений — KARAOKE-309,
исполнение — KARAOKE-318.

## Как работает

1. **Планировщик** (`KaraokeProcess.getProcessesToStart`) выбирает один кортеж на лейн и отдаёт
   его первый исполняемый шаг (`MIN(process_order)` среди `WAITING`, при условии, что более
   ранние шаги `DONE`). Уже начатый кортеж (`started DESC`) не вытесняется новым по приоритету.
2. **Поток кортежа** (`TupleExecutor` + `KaraokeProcessThread`) исполняет шаги подряд; `runStep`
   остаётся в `KaraokeProcessWorker.kt` (exit-code guard и `redirectErrorStream(true)`), соединение
   закрывается в `finally` (ADR [local-0009](../../knowledge/adr/local-0009-thread-local-connection-lifecycle.md)).
3. **Изоляция**: `scopeId`, каталоги `.../tmp/<scopeId>/`, контейнеры `demucs-<scopeId>`,
   `keybpmfinder-<scopeId>`, SHEETSAGE в своём каталоге; уборка — только владельцем плюс
   `TupleJanitor` (TTL, живые кортежи не трогает).
4. **Сбой шага** переводит весь кортеж в `ERROR` с причиной в `process_description`; для
   `DEMUCS2/5` допускается один CPU-retry отдельным самодостаточным кортежем.
5. **Приёмка по содержимому**: длительность стема = длительность источника ±0.5 с; key/bpm
   применяются только при совпадении sidecar-отпечатка источника. Легаси-файлы без отпечатка —
   отдельная категория `legacyNoSidecar`, не «чужое».
6. **Восстановление**: при старте — нормализация (`CREATING/WORKING → WAITING`, chainless → self,
   безголовые → голова, кортеж с `ERROR` гасит оставшиеся `WAITING`), затем resume с первого
   не-`DONE` шага.
7. **Админка**: шаг «k из N», состояние кортежа, причина; retry — по кортежу, в `inTransaction`.

## Инварианты

- Шаги одного кортежа не перемежаются с шагами других кортежей в одном лейне и не выполняются
  параллельно.
- Временные файлы и контейнеры кортежа недоступны другим кортежам; уборка — только владельцем.
- Ненулевой код шага не может дать `DONE`; незавершённые шаги кортежа не исполняются после ошибки.
- `DONE`/`ERROR` подтверждаются содержимым (длительность, sidecar), а не `exists()`.
- Схема `tbl_processes` не меняется; `tbl_processes` не входит в `SyncRegistry`, через границу
  двух БД идут только проверенные поля песни.

## Известные ловушки

- **`scopeId` не только в каталогах.** Любой путь результата (`mv`, `chmod`, `rm`) обязан быть
  scoped: одна не-scoped строка (key-bpm `mv`) роняет шаг 4 и оставляет песню без key/bpm.
- **`chmod 777` в новых шагах запрещён guard'ом** (`check-file-permissions.sh`): для каталогов —
  `775`/`755`, файлы — `644`, секреты — `600`.
- **Пост-хуки — раз на кортеж**, не на шаг: `HealthReport.onRepairProcessFinished` посреди кортежа
  ставит новые задания, пока кортеж жив.
- **`queue.integrity.cron` не существует**: расписание — Spring-свойство `karaoke.integrity.cron`,
  включение — `queue.integrity.enabled` (по умолчанию `false`).
- **Ежедневная проверка ничего не правит**: находки — только отчёт и сигнал оператору; ремонт —
  явный вызов API после `dryRun`.

## Ссылки

- [Спека и контракты](../../specs/674-kortege-execution/spec.md)
- [План и фазы](../../specs/674-kortege-execution/plan.md)
- [Быстрая проверка (quickstart)](../../specs/674-kortege-execution/quickstart.md)
- [Стендовый чек-лист](../../specs/674-kortege-execution/stand-checklist.md)
- [Очередь в SSoT](../../knowledge/domains/processing/components/async-process-queue.md)
- [Key-bpm в SSoT](../../knowledge/domains/processing/components/key-bpm-from-file.md)
