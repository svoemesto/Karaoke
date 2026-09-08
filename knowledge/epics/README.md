# Epics (Strategic Transitions)

> **Статус**: в разработке. Каркас развёрнут в
> `322-knowledge-scaffold`. Наполнение ведётся в следующих
> спецификациях.

Эпик — это **стратегический переход**, затрагивающий несколько доменов
или specs. Эпики не заменяют спецификации: specs описывают **что
конкретно** делается сейчас, эпики — **куда движется** система.

## Karaoke-override (см. `knowledge/README.md` § Karaoke-overrides, п.4)

В Karaoke жизненный цикл фичи живёт в `specs/NNN-<slug>/` (spec →
plan → tasks → implementation). `knowledge/epics/` заводится только для
переходов, которые:

- объединяют несколько specs в одну цель (например, «закрыть
  paid-monetization funnel» → specs 100, 105, 112);
- или описывают архитектурный shift (например, «миграция на
  knowledge/» — это как раз текущая спецификация 322).

## Структура эпика

См. [`knowledge/templates/epic.md`](../templates/epic.md):

- `id: EPIC-XXXX` (Karaoke-нумерация: см. `domains/README.md` аналогично)
- Target State — архитектура будущего.
- Roadmap & Steps — ссылки на конкретные specs.
- Definition of Done.

## Текущие эпики

| ID | Название | Связанные specs | Статус |
| --- | --- | --- | --- |
| `EPIC-KNOWLEDGE-V2` | Миграция LivingDocs v1 → v2 | `specs/322-knowledge-*`, последующие `323-…` | Active |
