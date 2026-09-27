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

### Роли: boolean-флаги, а не enum

**[WARN] Поправка Pass 474: enum `UserRole` не существует.**
Файла `model/UserRole.kt` в репозитории нет, как и колонки
`site_users.roles` (проверено grep по `*.kt` и по всем миграциям).
Реальная модель ролей — независимые boolean-флаги на `SiteUser`
(`model/SiteUser.kt`):

- `isEditor` (`:133`) — редактор, может брать задания;
- `canSelfAssignTasks` (`:140`) — может назначать задания себе;
- `canWorkWithSkipped` (`:146`) — доступ к пропущенному контенту.

Флаги независимы: админ может выдать любой из них любому
пользователю, автоматической выдачи нет. Ниже — прежнее описание
enum, сохранённое как историческое (не соответствует коду):

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

### Алгоритм выставления флагов (вместо `UserRole`)

1. Флаги приходят из admin-UI (`webvue3`) как обычные boolean-поля
   и сохраняются в одноимённые колонки `tbl_site_users`.
2. Проверка прав — по флагу, а не по роли: `siteUser.isEditor`,
   `siteUser.canSelfAssignTasks`, `siteUser.canWorkWithSkipped`.
3. Парсинга `valueOf` нет — строкового представления роли в коде
   не существует.

### Алгоритм сериализации boolean-полей

1. Если поле объявлено как `isSpecialOrder: Boolean`, **обязательна**
   аннотация `@JsonProperty("isSpecialOrder")` на getter'е.
   (NB Pass 474: поле принадлежит `Author` — `model/Author.kt:92`,
   `AuthorDTO.kt:32-33`; в `SiteUser` такого поля нет. Приведено как
   пример ловушки, а не как поле identity.)
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
