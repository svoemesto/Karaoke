# Кортежное исполнение очереди обработки (674)

**Спека**: `specs/674-kortege-execution/spec.md` · **Карта**: KARAOKE-309 · **Plane**: KARAOKE-318

## Что это

Кортеж — набор шагов одной песни (`tbl_processes` с общим `process_chain_id`), который
исполняется как единица: шаги `0..N-1` идут подряд, лейн занят кортежем до завершения, временные
ресурсы изолированы по `scopeId = process_chain_id ?: id`.

## Модель

- Планировщик (`KaraokeProcess.getProcessesToStart`) выбирает один кортеж на лейн и отдаёт его
  первый исполняемый шаг (`MIN(process_order)` среди `WAITING`). Уже начатый кортеж
  (`started DESC`) не вытесняется новым по приоритету.
- Поток кортежа (`TupleExecutor` + `KaraokeProcessThread`) исполняет шаги подряд; `runStep`
  остаётся в `KaraokeProcessWorker.kt` (exit-code guard и `redirectErrorStream(true)`).
- `scopeId`: каталоги `.../tmp/<scopeId>/`, контейнеры `demucs-<scopeId>`,
  `keybpmfinder-<scopeId>`; уборка — только владельцем + `TupleJanitor` (TTL).
- Сбой шага → весь кортеж `ERROR`, причина в `process_description`; один CPU-retry для
  `DEMUCS2/5` отдельным самодостаточным кортежем.
- Успех подтверждается содержимым: длительность стема = длительность источника ±0.5 с;
  key/bpm применяются только при совпадении sidecar-отпечатка источника.
- Восстановление: нормализация при старте (`CREATING/WORKING → WAITING`, chainless → self,
  безголовые → голова) и resume с первого не-`DONE` шага.
- Админка: шаг «k из N», состояние кортежа, причина; retry — по кортежу (в `inTransaction`).

## Ограничения

- Схема `tbl_processes` не меняется; `tbl_processes` не входит в `SyncRegistry` (FR-017),
  через границу БД идут только проверенные поля песни.
- Прод не затрагивается: e2e — на копиях, `karaoke-app` не перезапускается без согласия.
- Ежедневная проверка целостности: включение — `KaraokeProperties` `queue.integrity.enabled`
  (по умолчанию false); расписание — Spring-свойство `karaoke.integrity.cron`
  (по умолчанию `0 0 4 * * *`), второго ключа cron нет.

## Ссылки

- Контракты: `specs/674-kortege-execution/contracts/`.
- Проверка: `specs/674-kortege-execution/quickstart.md`.
- Тесты: `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/tuple/`.
