# Implementation Plan: Расширение диапазона свободных слотов публикации

**Branch**: `156-publish-slots-range` | **Date**: 2026-08-07 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/156-publish-slots-range/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command; its definition describes the execution workflow.

## Summary

Функция `getFreeTimeSlots()` (`karaoke-app/.../Utils.kt`), обслуживающая эндпоинт
`POST /api/getfreetimeslots`, который наполняет подсказки поля «Дата публикации» в
`SongEdit.vue`, сейчас жёстко перебирает 7 часов (11:00–17:00) семью почти
идентичными `UNION ALL`-подзапросами и для каждого часа просто прибавляет 1 день к
самой поздней уже занятой дате — без проверки, что результат в будущем, и без
предложения слота вовсе, если час никогда не использовался. Нужно расширить набор
часов до 13 (10:00–22:00 включительно) и переписать вычисление даты так, чтобы
предложенный слот всегда был строго в будущем относительно текущего момента на
сервере, включая случай «последняя публикация в этом часе была давно, а сегодняшнее
время этого часа уже прошло» → предлагать завтра, и случай «час никогда не
использовался» → предлагать сегодня/завтра, а не пропускать час.

Технический подход: заменить 13 отдельных `UNION ALL`-подзапросов одним
`GROUP BY`-запросом (`SELECT publish_time, MAX(TO_DATE(publish_date,'DD.MM.YY'))
... WHERE publish_time IN (...) GROUP BY publish_time`), затем в Kotlin для каждого
из 13 часов вычислить кандидат-дату по правилу «последняя занятая дата + 1 день,
либо сегодня, если час никогда не занимался» и сдвигать её вперёд по одному дню,
пока результат не станет строго позже `LocalDateTime.now()`. Изменения — только в
`karaoke-app` (backend); фронтенд (`webvue3/SongEdit.vue`) не меняется, так как он
уже потребляет произвольный список строк из этого эндпоинта без знания о диапазоне
часов.

## Technical Context

**Language/Version**: Kotlin (JDK 17), существующий модуль `karaoke-app`

**Primary Dependencies**: Spring Boot (`@PostMapping`/`@ResponseBody` в `ApiController.kt`), сырой JDBC (`KaraokeConnection`/`WORKING_DATABASE`) — без JPA/Hibernate (Principle II)

**Storage**: PostgreSQL, таблица `tbl_songs` (колонки `publish_date` формата `DD.MM.YY` как `text`, `publish_time` формата `HH:mm` как `text`)

**Testing**: В CI нет автотестов для этого пути (см. constitution «Рабочий процесс» — тесты `karaoke-app/src/test` в основном `@Disabled`); проверка — ручная, через `quickstart.md` этого фиче-плана

**Target Platform**: Linux-сервер (admin-машина, `karaoke-app` разворачивается только там, не на проде)

**Project Type**: Web-приложение (backend `karaoke-app` + существующий admin-фронтенд `webvue3`, фронтенд не меняется)

**Performance Goals**: Не критично — один запрос на открытие карточки песни, объём данных (13 строк агрегата) тривиален; N/A специальных целей

**Constraints**: Дата/время вычисляются по времени сервера (`LocalDateTime.now()` JVM `karaoke-app`), без учёта часового пояса администратора (см. Assumptions в spec.md); формат ответа эндпоинта (`List<String>`, `dd.MM.yy HH:mm`) не меняется, чтобы не ломать существующий фронтенд-потребитель

**Scale/Scope**: Один backend-файл (`Utils.kt`, функция `getFreeTimeSlots`), эндпоинт в `ApiController.kt` не меняется (сигнатура та же), фронтенд не меняется

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle I (Self-contained автопайплайн)**: N/A — фича не касается ffmpeg/melt/Demucs/Sheetsage, никаких внешних API не добавляется. ✅ PASS.
- **Principle II (Сырой JDBC + дифф по хэшам)**: Функция продолжает использовать сырой JDBC (`WORKING_DATABASE`/`Statement`), без JPA/Hibernate. Замена 13 `UNION ALL`-подзапросов одним `GROUP BY`-запросом — это *улучшение* в духе принципа («пакетная загрузка, не по одной в цикле»): один SQL-запрос вместо N. `recordhash`/diff-механизм не затрагивается (эта функция не участвует в LOCAL↔SERVER синхронизации). ✅ PASS.
- **Principle III (Двух-БД синхронизация через SyncRegistry)**: N/A — `getFreeTimeSlots()` только читает существующие данные `tbl_songs` для построения подсказок, не создаёт новую синхронизируемую сущность и не меняет схему таблицы. ✅ PASS.
- **Principle IV (Async-очередь задач)**: N/A — это синхронный HTTP-запрос за данными (доли секунды), не длительная операция, не идёт через `KaraokeProcess*`. ✅ PASS.
- **Principle V (Двух-фронтенд)**: Изменение полностью серверное; `webvue3` (admin) не смешивается с `karaoke-public`. Фронтенд-код не меняется вовсе. ✅ PASS.
- **Principle VI (Code Standards)**: Изменяемая функция `getFreeTimeSlots()` — публичная top-level `fun` в `Utils.kt`, уже подпадает под требование KDoc (FR-006 конституции). План: обновить/добавить KDoc с `@see` на секцию ниже (per-feature документ не заводится — см. обоснование в Complexity Tracking). ktlint должен пройти как обычно. ⚠️ см. ниже про FR-009.
- **Principle VII (Cross-Machine Setup)**: N/A — фича не трогает `.gitattributes`/`.git-blame-ignore-revs`/локальные AI-конфиги. ✅ PASS.
- **Principle VIII (Секреты и git-гигиена)**: N/A — фича не добавляет секретов, файлов конфигурации, паролей. ✅ PASS.
- **FR-009 (per-feature документ)**: `getFreeTimeSlots()` не входит ни в одну из 24 задокументированных ключевых подсистем в `docs/features/README.md` (ближайшая по духу — `songs-table`, но она про таблицу песен, не про поле даты публикации в форме редактирования одной песни). Новую запись в реестре подсистем заводить не требуется для точечного бэкенд-фикса такого масштаба; решение зафиксировано в Complexity Tracking ниже как явное обоснование (не нарушение, а выбор не расширять реестр).

**Итог**: нарушений NON-NEGOTIABLE принципов нет; Constitution Check пройден.

**Post-Phase-1 recheck**: `data-model.md` и `contracts/getfreetimeslots.md`
подтверждают, что дизайн не вводит новых таблиц, sync-сущностей, внешних
зависимостей или изменений контракта эндпоинта (тип ответа/путь/метод те же) —
все пункты выше остаются в силе без изменений после проектирования.

## Project Structure

### Documentation (this feature)

```text
specs/156-publish-slots-range/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
│   └── getfreetimeslots.md
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
karaoke-app/
└── src/main/kotlin/com/svoemesto/karaokeapp/
    ├── Utils.kt                          # getFreeTimeSlots() — единственное место с логикой (меняется)
    └── controllers/
        └── ApiController.kt              # POST /api/getfreetimeslots — сигнатура не меняется (без изменений)

webvue3/
└── src/components/Songs/
    ├── edit/SongEdit.vue                 # datalist "Дата публикации" — без изменений (потребляет тот же формат)
    └── store.js                          # getFreeTimeSlots action/getter — без изменений
```

**Structure Decision**: Одномодульное точечное изменение внутри `karaoke-app`
(backend). Веб-приложение в терминах repo — уже существующая структура
`karaoke-app`/`karaoke-web`/`webvue3`/`karaoke-public` (см. Tech Stack в
constitution.md); новая структура директорий не вводится, новых модулей/файлов не
создаётся — правится существующая функция `getFreeTimeSlots()` и (при необходимости)
добавляется приватная helper-функция вычисления кандидат-даты рядом с ней в том же
файле.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|---------------------------------------|
| Нет нового per-feature документа (`docs/features/<slug>.md`) для «свободных слотов публикации», хотя FR-009 требует документ при правке кода «ключевой подсистемы» | Фича — точечное исправление логики одной utility-функции (~100 строк), не отдельная продуктовая подсистема; создание новой из 24 записей реестра ради 100-строчного фикса добавляет обслуживающий груз (README.md таблица, отдельный `.md`-файл с «Что/Зачем/Как/Инварианты/Ловушки») непропорционально размеру изменения | Расширение существующего документа `songs-table.md` тоже не подходит: тот документ — про таблицу списка песен, а не про форму редактирования одной песни (`SongEdit.vue`), это разные части UI; создание документа именно под эту функцию — решение пользователя/ревьюера при необходимости, не блокер данного плана |
