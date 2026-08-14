---
status: Active
slug: identity
type: bounded-context
related:
  - ../domain/publishing.md
  - ../features/185-song-dto-audit-sponsr-remove.md
  - ../architecture/L3-components.md
---

# Bounded Context: identity (Идентификация)

> Пользователи, авторизация, сессии.

## Назначение

Identity — контекст для управления пользователями и их авторизацией. В проекте
два разных flow'а авторизации: **Spring Security + cookies** для `karaoke-web`
и **отдельный flow** для `karaoke-public`. Это историческое наследие и одна из
ловушек (агенту важно знать разницу).

## Aggregate Roots

- **SiteUser (Пользователь сайта)**: AR контекста. Identity = `id`. Содержит
  `email`, `passwordHash`, `roles`, `isActive`, `canSelfAssign` (для редакторов),
  `isSpecialOrder` (см. Jackson-ловушку). Инварианты: email уникален,
  passwordHash ≠ null.

- **Session (Сессия)**: серверная сессия (Spring Security). Identity = `sessionId`.
  Содержит `userId`, `createdAt`, `expiresAt`, `roles`.

## Entities

- **UserRole (Роль)**: enum ролей (admin, editor, user, guest).
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

## Ubiquitous Language (глоссарий)

| Термин | Определение | Пример в коде |
|--------|-------------|----------------|
| **SiteUser** | Пользователь сайта (читатель, редактор, админ) | `SiteUser.kt` |
| **Session** | Серверная сессия Spring Security | `HttpSession` |
| **Roles** | admin / editor / user | `UserRole.kt` |
| **editor** | Роль редактора (может брать задания) | `canSelfAssign=true` |
| **canSelfAssign** | Флаг: редактор может брать задания | `SiteUser.canSelfAssign` |
| **isSpecialOrder** | Boolean-поле, ловушка Jackson | `SiteUser.isSpecialOrder` (нужен `@JsonProperty`) |
| **JWT** | Не используется (только cookies) | — |
| **cookie** | Spring Security session cookie | `JSESSIONID` |
| **principal** | Текущий пользователь в Spring Security | `SecurityContextHolder` |
| **permitAll** | Эндпоинт без авторизации | `webvue3` использует `permitAll()` |
| **SecurityConfig** | Конфигурация Spring Security | `SecurityConfig.kt` |

## Связанные фичи

- [185-song-dto-audit-sponsr-remove.md](../features/185-song-dto-audit-sponsr-remove.md) — Jackson `is`-prefix

## Связанные LiveDocs

- Architecture: [L3-components.md](../architecture/L3-components.md)
- Domain: [publishing.md](publishing.md) (SiteUser нужен для подписок)

## Код

- Модели: `karaoke-app/src/main/kotlin/.../model/SiteUser.kt`, `Session.kt`
- Сервисы: `SiteUserService.kt`, `AuthService.kt`
- DTO: `SiteUserDTO.kt`, `SiteUserPublicDTO.kt` (с `@JsonProperty`!)
- Security: `karaoke-web/src/main/kotlin/.../security/SecurityConfig.kt`
- SQL: `deploy/karaoke-db/<NNN>_tbl_site_users.sql`

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14