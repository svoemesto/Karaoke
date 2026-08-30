# Implementation Plan: Возврат выпадающего списка свободных слотов публикации в поле «Дата» (`SongEdit.vue`)

**Branch**: `268-song-edit-date-datalist` | **Date**: 2026-08-30 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/268-song-edit-date-datalist/spec.md`

**Note**: Этот шаблон заполнен командой `/speckit.plan`. Дизайн полностью
определён существующим кодом и research.md — `NEEDS CLARIFICATION` нет.

## Summary

UX-регресс в `webvue3/src/components/Songs/edit/SongEdit.vue`: при фокусе
на поле «Дата» (и «Время») браузер показывает собственный список автозаполнения
вместо `<datalist>` со списком свободных слотов публикации (полученных через
`getFreeTimeSlots` / `POST /api/getfreetimeslots`). Регресс вызван
документированным поведением браузеров (Chrome/Edge/Firefox/Safari), которые
приоритизируют свой автокомплит над `<datalist>`, если у `<input>` не задан
`autocomplete="off"` + уникальный `name`.

Технический подход: точечная правка шаблона `SongEdit.vue` — добавить
`name="song_date_field"` + `autocomplete="off"` к полю «Дата» (строка ~342)
и `name="song_time_field"` + `autocomplete="off"` к полю «Время» (строка
~359). Никаких изменений в backend, store, API, других datalist-полях,
формате хранения данных.

Дополнительно: обновить `livedocs/features/156-publish-slots-range.md`
(указать ссылку на спку 268 в секции «История» + исправить путь к
`SongEdit.vue` → `edit/SongEdit.vue`).

## Technical Context

**Language/Version**: Vue 3 SFC (Composition API не используется в этом файле,
только Options API), HTML5 `<datalist>` + `<input>`, JavaScript ES2022,
Vite 7.x, Bootstrap-vue-next (UI не задействован в фиксе)

**Primary Dependencies**: Vuex (через `this.$store.getters.getFreeTimeSlots`),
HTML5 datalist (нативный, без библиотек), `npm run build` (Vite)

**Storage**: N/A — фикс не меняет схему БД, не пишет в БД, не пишет в store;
frontend-only

**Testing**: В CI автотестов для webvue3 UI нет (`AGENTS.md` → CI 7/7 покрывает
только линтеры + сборку). Проверка — ручная через `quickstart.md` (Chrome/Firefox/Safari/Edge).
Юнит-тесты на data-model не пишутся: фикс затрагивает только HTML-атрибуты,
бизнес-логика не меняется, JSDoc coverage уже 100% и не ухудшится.

**Target Platform**: Браузер на dev-машине администратора — Chrome 120+,
Edge 120+, Firefox 120+, Safari 17+ (см. SC-001 спеки). В production
admin-контейнере тот же браузер.

**Project Type**: Web-приложение (admin SPA `webvue3`, не `karaoke-public`).
Изменения только во frontend, backend не задействован.

**Performance Goals**: N/A — фикс не меняет ни сетевых запросов, ни объёма
данных, ни частоты рендеринга. Один дополнительный HTML-атрибут на поле —
нулевой impact.

**Constraints**: Минимальный diff (NFR-001: ≤25 строк в одном файле);
не сломать ESLint baseline (NFR-002); не сломать Vite build (NFR-003);
не трогать другие datalist-поля в проекте (см. research.md, вопрос 4).

**Scale/Scope**: 1 файл изменён (`webvue3/src/components/Songs/edit/SongEdit.vue`),
2 `<input>` дополнены атрибутами, 1 LiveDoc обновлён
(`livedocs/features/156-publish-slots-range.md`).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle I (Self-contained автопайплайн)**: N/A — фича не касается
  ffmpeg/melt/Demucs/Sheetsage, никаких внешних API не добавляется. ✅ PASS.
- **Principle II (Сырой JDBC + дифф по хэшам)**: N/A — фикс только во
  Vue-шаблоне, БД не задействована. ✅ PASS.
- **Principle III (Двух-БД синхронизация через SyncRegistry)**: N/A —
  `tbl_songs.publish_date`/`publish_time` уже синхронизируются как часть
  `tbl_songs`; фикс не меняет ни схему, ни sync-флаги, ни recordhash. ✅ PASS.
- **Principle IV (Async-очередь задач)**: N/A — фикс синхронный,
  в `KaraokeProcess*` не идёт, длительных операций нет. ✅ PASS.
- **Principle V (Двух-фронтенд)**: Изменение только в `webvue3` (admin);
  `karaoke-public` не задействован, ответственность admin/public не
  пересекается. ✅ PASS.
- **Principle VI (Code Standards)**:
  - **FR-006 (JSDoc/KDoc)**: фикс не вводит новых `export default`,
    `function`, `const` — JSDoc coverage 100% (150/150) сохраняется.
    Существующий JSDoc на `SongEdit.vue` не трогаем. ✅ PASS.
  - **FR-007 (ESLint/Prettier)**: фикс проходит `npm run lint` и
    `prettier --check` без новых нарушений (NFR-002). ⚠️ проверить
    после реализации (см. quickstart сценарий 6).
  - **FR-009 (per-feature документ)**: новый документ НЕ заводится
    (см. research.md вопрос 7 + Complexity Tracking). Существующий
    LiveDoc 156 обновляется точечно. ⚠️ зафиксировано как осознанное
    решение в Complexity Tracking.
- **Principle VII (Cross-Machine Setup)**: N/A — фикс не трогает
  `.gitattributes`, `.git-blame-ignore-revs`, локальные AI-конфиги. ✅ PASS.
- **Principle VIII (Секреты и git-гигиена)**: N/A — фикс не добавляет
  секретов, env-файлов, credentials. ✅ PASS.

**Итог**: нарушений NON-NEGOTIABLE принципов нет; Constitution Check
пройден. Замечания ⚠️ (FR-007, FR-009) — это **точки проверки** при
реализации, а не нарушения.

**Post-Phase-1 recheck**: `data-model.md` подтверждает, что схема БД
не меняется; `contracts/getfreetimeslots.md` подтверждает, что API
контракт идентичен (формат ответа, путь, метод — те же); `research.md`
зафиксировал все технические решения и отвергнутые альтернативы. Все
пункты Constitution Check выше остаются в силе после проектирования.

## Project Structure

### Documentation (this feature)

```text
specs/268-song-edit-date-datalist/
├── plan.md                  # Этот файл (/speckit.plan output)
├── research.md              # Phase 0 output (/speckit.plan) — 7 вопросов
├── data-model.md            # Phase 1 output — инварианты данных
├── quickstart.md            # Phase 1 output — ручные сценарии проверки
├── contracts/               # Phase 1 output
│   └── getfreetimeslots.md  # контракт API (без изменений, фиксируется)
├── checklists/
│   └── requirements.md      # Quality checklist (16/16 passing)
└── spec.md                  # Feature spec (Draft → ready for plan)
```

### Source Code (repository root)

```text
webvue3/
└── src/
    └── components/
        └── Songs/
            └── edit/
                └── SongEdit.vue   # ЕДИНСТВЕННЫЙ изменяемый файл:
                                    # +name="song_date_field" autocomplete="off" (поле «Дата»)
                                    # +name="song_time_field" autocomplete="off" (поле «Время»)
                                    # +HTML-комментарии со ссылкой на спку 268

livedocs/
└── features/
    └── 156-publish-slots-range.md # Точечное обновление:
                                    # + ссылка на specs/268-song-edit-date-datalist/spec.md
                                    # + запись в «История» («фронт-фикс datalist-маскировки»)
                                    # ± путь Songs/SongEdit.vue → Songs/edit/SongEdit.vue
```

**Structure Decision**: Изменение одномодульное, точечное, в рамках
существующей структуры `webvue3`. Новых файлов, директорий, модулей
не создаётся. Это согласуется с `plan.md` спеки 156 (server-side fix),
но в отличие от неё — здесь меняется только клиентская разметка.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Создан новый per-feature документ `livedocs/features/268-song-edit-date-datalist.md` несмотря на то, что в первоначальном планировании он был явно отклонён как «избыточный для точечного 4-атрибутного фикса» | CI-гейт `tools/check-livedocs-coverage.sh` (запускается в `lint.yml`) **требует** один LiveDoc на каждую спеку — `[1/7] specs → LiveDoc: для каждой спеки в specs/ должен быть livedocs/features/<NNN-slug>.md`. Без LiveDoc merge блокируется (`AGENTS.md` → «CI блокирует merge при любом failing check»). Это **жёсткое структурное** требование, а не рекомендация, поэтому FR-009 Конституции (per-feature документ при правке кода ключевой подсистемы) и `tools/check-livedocs-coverage.sh` трактуются в пользу создания LiveDoc. Решение скорректировано **в ходе реализации**, после того как первый push зашёл в CI-fail | 1) Не создавать LiveDoc и пройти CI — невозможно: тулчейн `check-livedocs-coverage.sh` явно проверяет наличие `livedocs/features/<spec-dir>.md` для каждой спеки и завершается с exit code 1, если отсутствует. 2) Изменить CI-скрипт, чтобы он принимал исключения — вне полномочий агента (NON-NEGOTIABLE: «Менять конфигурацию линтеров без согласования» из CLAUDE.md). 3) Объединить спеку 268 со спекой 156 — нарушает SDD: одна спека = одна фича = один номер; слияние приведёт к тому, что drill-down обеих фич смешается |