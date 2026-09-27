---
id: domain-identity
title: "Domain: Identity (Идентификация)"
status: Active
slug: identity
related:
  - ../../adr/0001-raw-jdbc.md
  - ../publishing/domain.md
  - ../editorial/domain.md
  - ../../system/02-containers.md
---

# Domain: Identity (Идентификация)

> Пользователи, авторизация, сессии.
>

## Обзор контекста (Bounded Context)

Identity — контекст для управления пользователями и их авторизацией.
В проекте **два разных flow авторизации**:

- **Spring Security + cookies** для `karaoke-web`.
- **Отдельный flow** для `karaoke-public`.

Это историческое наследие и одна из ловушек: агенту важно знать разницу
(см. [security-config](components/security-config.md)).

**Граница**: контекст НЕ отвечает за:

- управление подписками и платежами (→ [publishing](../publishing/domain.md));
- назначение задач редакторам (→ [editorial](../editorial/domain.md));
- права доступа к конкретным фичам (реализуются per-feature через
  `SecurityContextHolder` в местах использования).

## Ubiquitous Language | Единый язык

| Термин | Определение | Пример в коде |
| --- | --- | --- |
| **SiteUser** | Пользователь сайта (читатель, редактор, админ) | `SiteUser.kt` |
| **Session** | Серверная сессия Spring Security | `HttpSession` |
| **Флаги доступа** | `isEditor` / `canSelfAssignTasks` / `canWorkWithSkipped` — enum ролей в коде отсутствует (Pass 474) | `SiteUser.kt` |
| **editor** | Редактор (может брать задания) | `isEditor=true` |
| **canSelfAssignTasks** | Флаг: пользователь может брать задания себе | `SiteUser.canSelfAssignTasks` |
| **isSpecialOrder** | Boolean-поле **не identity, а catalog** — ловушка Jackson показана здесь как пример | `Author.isSpecialOrder` (нужен `@JsonProperty`) |
| **JWT** | Не используется (только cookies) | — |
| **cookie** | Spring Security session cookie | `JSESSIONID` |
| **principal** | Текущий пользователь в Spring Security | `SecurityContextHolder` |
| **permitAll** | Эндпоинт без авторизации | `webvue3` использует `permitAll()` |
| **SecurityConfig** | Конфигурация Spring Security | `SecurityConfig.kt` |

Полный словарь домена (включая enum `UserRole` и Jackson-ловушки) —
см. [dictionaries](components/dictionaries.md).

## Aggregate Roots

- **SiteUser (Пользователь сайта)**: AR контекста. Identity = `id`. Содержит
  `email`, `passwordHash`, `isActive` и три флага доступа
  (`isEditor`, `canSelfAssignTasks`, `canWorkWithSkipped`).
  Enum ролей и колонки `roles` не существует (Pass 474).
  Инварианты:
  - email уникален;
  - `passwordHash` ≠ null для активных пользователей;
  - флаги доступа независимы и выставляются только админом;
    автоматической выдачи нет (см. [dictionaries](components/dictionaries.md)).

- **Session (Сессия)**: серверная сессия (Spring Security). Identity =
  `sessionId`. Содержит `userId`, `createdAt`, `expiresAt`; роли в сессии не хранятся (проверка прав идёт по флагам `SiteUser`).
  Инварианты:
  - `userId` ссылается на существующего `SiteUser`;
  - `expiresAt` > `createdAt`.

## Entities

- **Флаги доступа (не роль)**: `isEditor`, `canSelfAssignTasks`,
  `canWorkWithSkipped` на `SiteUser`. Enum `UserRole` и его значения
  (admin/editor/user/guest) в коде отсутствуют — Pass 474.
  См. [dictionaries](components/dictionaries.md).
- **PasswordResetToken (Токен сброса)**: для forgot-password flow.

## Value Objects

- **Email (string)**: email пользователя, уникален.
- **PasswordHash (string)**: bcrypt-хэш пароля.
- **Roles (set of UserRole)**: набор ролей пользователя.

## Domain Events

- **UserRegistered**: новый пользователь создан.
- **UserLoggedIn**: успешный логин (создана сессия).
- **UserLoggedOut**: сессия завершена.
- **PasswordReset**: пароль сброшен.
- **CanSelfAssignToggled**: флаг self-assign изменён.

## Domain Invariants | Инварианты и правила бизнеса

1. **Email уникален** на уровне БД (`UNIQUE` constraint).
2. **`passwordHash` всегда bcrypt** — plain text не хранится ни при каких
   условиях (включая миграции, импорт, тестовые fixture'ы).
3. **Roles — это enum, не string**: миграции и API-контракты обязаны
   использовать `UserRole`, не строковые литералы.
4. **`isSpecialOrder` сериализуется через `@JsonProperty`**: без
   аннотации Jackson вырежет поле при сериализации (см. ADR по
   Jackson-конвенциям и [dictionaries](components/dictionaries.md)).
5. **Двух-flow авторизация**: `karaoke-web` (cookies) и
   `karaoke-public` (отдельный flow) — НЕ унифицировать без отдельного
   эпика (см. [security-config](components/security-config.md)).
6. **Сессия Spring Security живёт в Redis/БД**: при перезапуске
   `karaoke-web` сессии пользователей не должны теряться
   (если не сконфигурировано иначе в `SecurityConfig`).

## Публичные контракты (API)

### Internal API (для других модулей системы)

- `SiteUserService` — CRUD пользователей.
- `AuthService` — login/logout/register/forgot-password.

### Public API (для внешних клиентов)

- Делегируется фичевым контроллерам. Например, эндпоинты share-link
  гостевого доступа — в контексте [publishing](../publishing/domain.md),
  используют `SiteUser` как зависимость.

## Структура компонентов (C4 L3)

- [security-config](components/security-config.md) — два разных flow
  авторизации: Spring Security + cookies для `karaoke-web`, отдельный
  flow для `karaoke-public`. Содержит ловушки и обоснование разделения.
- [dictionaries](components/dictionaries.md) — `UserRole` enum и
  Jackson-конвенции для boolean-полей (`is`-prefix).

## Связанные ADR

- [0001-raw-jdbc](../../adr/0001-raw-jdbc.md) — сырой JDBC, без JPA/Hibernate.

## Код (физическая реализация)

- Модели: `karaoke-app/src/main/kotlin/.../model/SiteUser.kt`, `Session.kt`
- Контроллер: `karaoke-app/.../controllers/SiteUsersController.kt`
- DTO: `karaoke-app/.../model/SiteUserDto.kt` (с `@JsonProperty`)
- Сервисы: `karaoke-web/.../services/SiteUserResolver.kt`,
  `karaoke-web/.../services/SiteUserTokenService.kt`
- Security: `karaoke-app/.../config/SecurityConfig.kt`
- SQL: `deploy/karaoke-db/06_site_users.sql`

(Исправлено в Pass 473: прежний список называл `SiteUserService.kt`,
`AuthService.kt`, `SiteUserDTO.kt`, `SiteUserPublicDTO.kt` и путь
`.../security/SecurityConfig.kt` — таких файлов в репозитории нет.)
