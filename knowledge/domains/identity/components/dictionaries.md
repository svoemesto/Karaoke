# Component: dictionaries

> **Домен**: [identity](../domain.md)
> **Компонент**: централизованное хранение enum'ов, кодов и
> Jackson-конвенций домена. Любые «магические строки» или технические
> коды, упоминаемые в коде или API, **обязаны** быть определены здесь.

## Ответственность | Responsibility

В DDD-домене часто возникают ситуации, когда:

- строковое значение роли (`"admin"`, `"editor"`, `"user"`) кочует
  между БД, API и UI;
- boolean-поле с `is`-prefix ломает Jackson-сериализацию;
- magic-числа или коды появляются в DTO, миграциях или шаблонах.

Эта компонента — единственное место, где эти константы и конвенции
**определены**. Использование литералов в L3-спецификациях и коде
без ссылки на эту страницу — **критический дефект** (см. `audit-living-docs`
«Magic Codes»).

## Интерфейсы и Контракты | Interfaces and Contracts

### `UserRole` (enum)

- **Значения**: `ADMIN`, `EDITOR`, `USER`, `GUEST`.
- **Место определения**: `karaoke-app/src/main/kotlin/.../model/UserRole.kt`.
- **Контракт**: стабильный, не должен меняться без миграционного эпика
  (значения записываются в БД).

| Значение | Описание | Флаг `canSelfAssign` |
| --- | --- | --- |
| `admin` | Полный доступ ко всем фичам | не применимо |
| `editor` | Может брать задания на редактуру | `true` |
| `user` | Зарегистрированный читатель | `false` |
| `guest` | Анонимный пользователь (share-link) | `false` |

**Использование**:

- В БД: колонка `site_users.roles` (тип: text[] или varchar с
  разделителем).
- В API: через `SiteUserDTO.roles`.
- В UI: проверка `principal.role == UserRole.ADMIN`.

**Запрещено**:

- Строковые литералы `"admin"`, `"editor"`, `"user"` в коде (вместо
  этого — `UserRole.ADMIN.name`).
- Хардкод числовых кодов (1, 2, 3) для ролей в миграциях.

### Jackson-конвенция `is`-prefix

- **Контракт**: boolean-поля НЕ используют `is`-prefix (исключение —
  `isSpecialOrder` с `@JsonProperty`).
- **Место применения**: все `*DTO.kt` файлы, API-контракты для
  `webvue3`.

**Правило**:

- Новые поля: `published: Boolean`, НЕ `isPublished: Boolean`.
- Исключение `isSpecialOrder`: существующее поле с аннотацией
  `@JsonProperty("isSpecialOrder")` для корректной сериализации.
  Удалять `is`-prefix запрещено без миграционного эпика.

**Почему это важно**: Jackson по умолчанию вырезает `is`-prefix из
поля `isSpecialOrder` при сериализации → поле становится `specialOrder`,
клиенты не получают значение → регресс в фиче `185-song-dto-audit-sponsr-remove`.

**Где применяется**:

- Все `*DTO.kt` файлы.
- Все API-контракты (генерация типов для `webvue3`).

## Логика и Алгоритмы | Logic and Algorithms

### Алгоритм выбора значения `UserRole`

1. При создании `SiteUser` через API: парсится строковое значение из
   запроса через `UserRole.valueOf(...)`.
2. При чтении из БД: парсится через тот же `valueOf`.
3. При сериализации в JSON: `UserRole.ADMIN.name` (строка `"ADMIN"`).

### Алгоритм сериализации boolean-полей

1. Если поле объявлено как `isSpecialOrder: Boolean`, **обязательна**
   аннотация `@JsonProperty("isSpecialOrder")` на getter'е.
2. Без аннотации Jackson сериализует поле как `specialOrder` →
   клиент не получает значение → регресс.

## Зависимости | Dependencies

- → `UserRole.kt` — Kotlin enum, источник истины для значений.
- → Jackson (`com.fasterxml.jackson`) — конвенция сериализации
  boolean-полей.
- → [security-config](security-config.md) — потребитель `UserRole`
  в `Authentication.roles`.
- → [domain](../domain.md) — AR `SiteUser` хранит `Roles` как
  `Set<UserRole>`.

## Связанные ADR | Related ADRs

- ADR по Jackson-конвенциям (запланировано в `epics/`).
