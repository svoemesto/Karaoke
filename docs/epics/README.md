# Epics (Strategic Transitions)

> **Статус**: в разработке. Каркас развёрнут в
> `320-living-docs-v2-bootstrap`. Наполнение ведётся в следующих
> спецификациях.

Эпик — это **стратегический переход**, затрагивающий несколько доменов
или specs. Эпики не заменяют спецификации: specs описывают **что
конкретно** делается сейчас, эпики — **куда движется** система.

## Karaoke-override (см. `docs/README.md` § Karaoke-overrides, п.4)

В Karaoke жизненный цикл фичи живёт в `specs/NNN-<slug>/` (spec →
plan → tasks → implementation). `docs/epics/` заводится только для
переходов, которые:

- объединяют несколько specs в одну цель (например, «закрыть
  paid-monetization funnel» → specs 100, 105, 112);
- или описывают архитектурный shift (например, «миграция с
  `livedocs/` на `docs/`» — это как раз текущая спецификация 320).

## Структура эпика

См. [`docs/templates/epic.md`](../templates/epic.md):

- `id: EPIC-XXXX` (Karaoke-нумерация: см. `domains/README.md` аналогично)
- Target State — архитектура будущего.
- Roadmap & Steps — ссылки на конкретные specs.
- Definition of Done.

## Текущие эпики

| ID | Название | Связанные specs | Статус |
| --- | --- | --- | --- |
| `EPIC-LIVEDOCS-V2` | Миграция LivingDocs v1 → v2 | `specs/320-living-docs-v2-*` | Active |
