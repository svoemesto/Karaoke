# Bounded Context: <Context>

> DDD bounded context проекта Karaoke. Описывает границы домена, ubiquitous
> language, aggregate roots, ключевые инварианты.

## Назначение

[1-2 абзаца: зачем существует этот контекст, какую часть домена закрывает]

## Aggregate Roots

- **<AR1>**: [что это, зачем нужен, ключевые инварианты]
- **<AR2>**: [аналогично]

## Entities

- **<E1>**: [жизненный цикл, identity]
- **<E2>**: [аналогично]

## Value Objects

- **<VO1>**: [что описывает, как сравнивается]

## Domain Events

- **<Event1>**: [когда публикуется, кто консьюмит]
- **<Event2>**: [аналогично]

## Ubiquitous Language (глоссарий)

| Термин | Определение | Пример в коде |
|--------|-------------|----------------|
| <Term1> | <определение> | `<ClassName>` |
| <Term2> | <определение> | `<ClassName>` |

## Связанные фичи

- [182-editor-self-assign-tasks.md](../features/182-editor-self-assign-tasks.md)
- [184-approve-status-choice.md](../features/184-approve-status-choice.md)

## Связанные LiveDocs

- Architecture: [L3-components.md](../architecture/L3-components.md)

## Код

- Модели: `karaoke-app/src/main/kotlin/.../model/<Context>*.kt`
- Сервисы: `karaoke-app/src/main/kotlin/.../service/<Context>*.kt`
- DTO: `karaoke-app/src/main/kotlin/.../dto/<Context>*DTO.kt`
- SQL: `deploy/karaoke-db/<NNN>_<table>.sql`

## История

- Создан: <YYYY-MM-DD>
- Последнее обновление: <YYYY-MM-DD>