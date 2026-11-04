# Тесты контроллеров karaoke-web: как правильно и как не надо

> **Домен**: [karaoke-web](../domain.md)
> **Компонента**: правила написания тестов на контроллеры (`karaoke-web/src/test`).

## Ответственность | Responsibility

Документ задаёт правила написания юнит-тестов на контроллеры домена
`karaoke-web`: как подменять зависимости, что нельзя мокать, как тестировать
логику за статикой и как стабилизировать моки. Отвечает за то, чтобы тест
контроллера был юнитом (без Spring-контекста, без БД и без сети), а не
интеграционным прогоном.

## Интерфейсы и Контракты | Interfaces and Contracts

- Контроллеры `karaoke-web` (`PublicApiController`, `MainController` и др.) —
  тестируются через вызов метода напрямую, экземпляр создаётся в тесте.
- Конструкторные зависимости контроллера (`MainController`, `KaraokeStorageService`,
  `StorageApiClient`, `SiteUserResolver`) — подменяются `Mockito.mock(...)`.
- Швы для тестирования статики — публичные методы, вынесенные из приватной
  статики (например, подсчёт совпадений атрибутов в счётчике статистики).
- Тестовый раннер: `node`/Gradle `:karaoke-web:test`, JUnit 5 + Mockito.

## Логика и Алгоритмы | Logic and Algorithms

## Правило 1. Зависимости контроллера подменяются моками конструктора

`PublicApiController` принимает все зависимости через конструктор. Тест создаёт его
с `Mockito.mock(...)` для каждой зависимости и вызывает метод контроллера напрямую.

Эталон: `karaoke-web/src/test/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiVisibilityTest.kt`

```kotlin
private fun controller(): PublicApiController =
    PublicApiController(
        mainController = Mockito.mock(MainController::class.java),
        storageService = Mockito.mock(KaraokeStorageService::class.java),
        storageApiClient = Mockito.mock(StorageApiClient::class.java),
        siteUserResolver = Mockito.mock(SiteUserResolver::class.java),
        minioProxyUrl = "",
    )
```

**Failure**: тест, который поднимает Spring-контекст, тянет БД или сеть — в проекте так не делают.

## Правило 2. Статику НЕ мокаем

В проекте **ноль** использований `mockStatic`, `mockito-inline`, `mockk` и `org.mockito.kotlin`.
Добавление `mockito-inline` в `build.gradle.kts` — новый паттерн, которого в проекте нет:
он меняет mock-maker для всего модуля и ломает существующие тесты.

**Failure**: `NullPointerException: any(...) must not be null`, `Unresolved reference: kotlin`,
конфликт версий mockito в сборке.

## Правило 3. Если логика за статикой — её не мокают, а тестируют шов

Три приёма, в порядке предпочтения:

1. **Вынести сборку аргументов в чистую функцию** и тестировать её. Например
   `songFilterArgs(...): Map<String, String>` — чистая функция; тест проверяет карту
   для анонима и редактора без БД и без моков. Контроллер вызывает ту же функцию,
   поэтому паритет с существующим эндпоинтом гарантируется структурно.
2. **Принять зависимость параметром** (функция-счётчик, интерфейс) и в тесте передать лямбду.
3. **Интеграционный тест на реальной БД** — только если для этого уже есть фикстуры
   (см. `karaoke-app/src/test`), и никогда с боевой БД.

**Failure**: попытка замокать `Song.countMatchingAttr` через `mockStatic` — тупик:
в проекте нет ни одного примера, и зависимости `mockito-inline` нет.

## Правило 4. Стабирование моков: `doReturn`, а не `when`

Если нужно настроить мок так, чтобы метод **не вызывался** в момент настройки (или он
возвращает `null`), используй `doReturn(...).when(mock).method(...)`, а не
`when(mock.method(...)).thenReturn(...)`.

Причина: в `when(mock.method(any()))` сам вызов `mock.method(...)` выполняется **сразу**
при настройке и возвращает `null`; после этого Mockito видит матчер, применённый к `null`,
и падает с `InvalidUseOfMatchersException`.

```kotlin
// ПЛОХО: resolve() вызывается при стабировании, возвращает null -> InvalidUseOfMatchersException
Mockito.`when`(resolver.resolve(ArgumentMatchers.any())).thenReturn(editor)

// ХОРОШО: настройка без вызова метода
Mockito.doReturn(editor).`when`(resolver).resolve(ArgumentMatchers.any())

// ТОЖЕ ХОРОШО: конкретный аргумент вместо матчера
Mockito.`when`(resolver.resolve(request)).thenReturn(editor)
```

**Failure**: `InvalidUseOfMatchersException`, `NullPointerException: any(...) must not be null`,
`UnfinishedStubbingException` — все три симптома означают одно: матчер применён вне стабирования
или метод вызван в момент настройки.

**Source**: `PublicApiSongsCountTest` (KARAOKE-390) — на этом спотыкались прогоны 2026-11-03.

## Зависимости | Dependencies

- JUnit 5 (`org.junit.jupiter`) и Mockito — как в остальных тестах модуля.
- `mockito-inline`, `mockk`, `org.mockito.kotlin` — **не добавлять**: в проекте
  их нет, и подмена mock-maker ломает существующие тесты.
- Spring-контекст, БД, сеть — не поднимать: тест контроллера обязан быть юнитом.
- Файлы тестов: `karaoke-web/src/test/kotlin/com/svoemesto/karaokeweb/controllers/`.

## Как запускать

```bash
./gradlew :karaoke-web:test --tests "*PublicApiVisibilityTest*"
```

Тесты контроллеров лежат рядом с контроллерами в `karaoke-web/src/test/kotlin/.../controllers/`.

## Источник

`PublicApiVisibilityTest.kt`, `AuthorPartialUpdateContractTest.kt`; AGENTS.md Tier-1 (Knowledge-first).
