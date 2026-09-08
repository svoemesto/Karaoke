# Component: security-config

> **Домен**: [identity](../domain.md)
> **Компонент**: конфигурация авторизации и аутентификации
> в двух контейнерах — `karaoke-web` и `karaoke-public` — с учётом
> исторически сложившегося разделения на два разных flow.

## Ответственность | Responsibility

Эта компонента фиксирует разницу между двумя flow авторизации в Karaoke
и служит точкой входа для любого агента, который планирует изменить
логику аутентификации. **Любая правка auth должна начинаться с чтения
этой страницы** — иначе есть риск сломать один из flow.

## Интерфейсы и Контракты | Interfaces and Contracts

### `SecurityConfig` (только `karaoke-web`)

- **Поведение**: настраивает `SecurityFilterChain`, разрешает/запрещает
  пути через `permitAll()` / `authenticated()`.
- **Вход**: HTTP-запрос.
- **Выход**: решение «пропустить / 401 / 403».

### `permitAll()` — список публичных путей

В `webvue3` есть конкретные эндпоинты, открытые без авторизации
(например, поиск песен для анонимных пользователей). Они управляются
через `permitAll()` в `SecurityConfig`.

## Логика и Алгоритмы | Logic and Algorithms

### Два flow авторизации

#### 1. `karaoke-web` — Spring Security + cookies

- **Технология**: Spring Security (filter chain в
  `karaoke-web/src/main/kotlin/.../security/SecurityConfig.kt`).
- **Сессия**: `HttpSession`, идентифицируется `JSESSIONID` cookie.
- **Хранилище сессий**: БД или Redis (зависит от deployment-конфига).
- **Сценарий**: админка (`webvue3`), редакторский API, защищённые
  эндпоинты.
- **Текущий пользователь**: `SecurityContextHolder.getContext().authentication`.

#### 2. `karaoke-public` — отдельный flow

- **Технология**: собственный механизм (НЕ Spring Security).
- **Сценарий**: публичный сайт (`karaoke-public`), где читатель либо
  анонимный, либо имеет share-link сессию.
- **Причина**: публичный сайт исторически разрабатывался отдельно от
  админки, и попытки унифицировать auth ломали оба flow.

### Инициализация контекста (`karaoke-web`)

1. Запрос приходит в `karaoke-web`.
2. Spring Security читает `JSESSIONID` cookie.
3. Если сессия валидна — `SecurityContextHolder` заполняется
   `Authentication` с `principal = SiteUser`.
4. Если нет — цепочка фильтров решает: пропустить (`permitAll`) или
   отклонить (401).

### Cross-flow (как они НЕ пересекаются)

- `karaoke-public` НЕ использует `SecurityContextHolder` из
  `karaoke-web` — у него собственный контекст.
- Попытка расшарить `HttpSession` между двумя WAR/JAR — **запрещена**.

## Зависимости | Dependencies

- → [SiteUser AR](../domain.md#aggregate-roots) — для заполнения `principal`.
- → [dictionaries](dictionaries.md) — `UserRole` enum используется в
  `Authentication.roles`.
- → [0001-raw-jdbc](../../../adr/0001-raw-jdbc.md) — БД для хранения сессий.

## Связанные ADR | Related ADRs

- [0001-raw-jdbc](../../../adr/0001-raw-jdbc.md) — БД для хранения сессий.

## Ловушки и предупреждения

**[WARN] Не пытаться унифицировать flow без отдельного эпика.** Любая
правка, которая «давайте сделаем один общий SecurityContext» — сломает
один из flow и приведёт к регрессу авторизации.

**[WARN] `webvue3` ≠ `karaoke-public`.** Админка (на `webvue3`, общается
с `karaoke-web`) и публичный сайт (`karaoke-public`) — это разные
приложения с разными auth-flow. Не путать.

**[WARN] `@JsonProperty` для boolean-полей.** См. [dictionaries](dictionaries.md)
раздел «Jackson-ловушки». Без `@JsonProperty` поля с `is`-prefix
вырезаются при сериализации.
