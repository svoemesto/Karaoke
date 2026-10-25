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
  `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/config/SecurityConfig.kt`).
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

### Периметр admin-API в `karaoke-app` (BR-114)

Отдельно от двух flow выше существует **третий**, самый проблемный периметр:
`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/config/SecurityConfig.kt`.

- Единственное правило — `authenticated()` для `/api/private/…`; контроллеров с
  таким путём в репозитории НЕТ, поэтому правило защищает пустое множество.
  Всё остальное — `anyRequest().permitAll()`.
- Механизма аутентификации в `karaoke-app` нет вовсе: ни `formLogin`, ни
  `httpBasic`, ни `UserDetailsService`, ни собственного фильтра. Поэтому
  `authenticated()` включить нельзя — админка (единственный UI) получила бы 401
  на каждый запрос без возможности войти. **Выбор механизма — решение владельца**
  (см. `specs/394-pkg-s3-22/report.md` § BR-114).
- Работающий шов: `AdminApiTokenFilter` (`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/config/AdminApiTokenFilter.kt`).
  При заданном `karaoke.security.admin-token` (env `KARAOKE_ADMIN_TOKEN`)
  запросы к admin-префиксам (`/api/admin/`, `/api/health/`, `/api/monitor/`,
  `/api/cleanup/`, `/api/dictionaries/`, `/api/news/`, `/api/promorules/`,
  `/api/publicsettings/`, `/api/sharelinks/`, `/api/stemjobs/`) обязаны нести
  заголовок `X-Admin-Token`, иначе 401. Токен не задан — поведение прежнее,
  но при первом admin-запросе в лог уходит `WARN` «Admin-API открыт».
- `X-Admin-Username` (в `KaraokeProcessAdminController`) — **не идентичность**:
  это клиентский заголовок для audit-записи, его подделывает кто угодно.
  Не строить на нём авторизацию.

## Связанные ADR | Related ADRs

- [0001-raw-jdbc](../../../adr/0001-raw-jdbc.md) — БД для хранения сессий.

## Ловушки и предупреждения

**[WARN] Не пытаться унифицировать flow без отдельного эпика.** Любая
правка, которая «давайте сделаем один общий SecurityContext» — сломает
один из flow и приведёт к регрессу авторизации.

**[WARN] `karaoke-app` — третий, незакрытый периметр.** См. раздел
«Периметр admin-API в `karaoke-app` (BR-114)»: там `permitAll()` на всё,
кроме несуществующего `/api/private/…`, и нет механизма входа. Не считать
этот компонент защищённым и не включать `authenticated()` без механизма
аутентификации.

**[WARN] `webvue3` ≠ `karaoke-public`.** Админка (на `webvue3`, общается
с `karaoke-web`) и публичный сайт (`karaoke-public`) — это разные
приложения с разными auth-flow. Не путать.

**[WARN] `@JsonProperty` для boolean-полей.** См. [dictionaries](dictionaries.md)
раздел «Jackson-ловушки». Без `@JsonProperty` поля с `is`-prefix
вырезаются при сериализации.
