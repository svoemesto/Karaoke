## Что это

Хотфикс P0: после merge #635 `karaoke-app` перестал стартовать.

```
IllegalStateException: Property APP_WORK_ON_SERVER should be initialized before get
  at ConstantsKt.<clinit>(Constants.kt:216)
  at ArtifactCleanupPlanner.<init>(ArtifactCleanupPlanner.kt:78)
  at ArtifactCleanupService.<init>
  at CleanupController.<init>
→ BeanCreationException: Error creating bean with name 'cleanupController'
→ контейнер вышел с кодом 1
```

## Причина

`ArtifactCleanupPlanner` имел значение по умолчанию `roots: List<String> = PROJECT_ROOT_FOLDERS`. Kotlin вычисляет такие значения **в конструкторе**, а `CleanupController` — eager-бин Spring: он создаётся раньше, чем `KaraokeAppService` выставит `APP_WORK_ON_SERVER`. Обращение к `PROJECT_ROOT_FOLDERS` тянуло `Constants.<clinit>` → `Connection.<clinit>` → падение.

## Исправление

Корни обхода вычисляются лениво: поле по умолчанию `null`, обращение к `PROJECT_ROOT_FOLDERS` перенесено в `effectiveRoots()`, который вызывается только из `plan()` — то есть по HTTP-запросу, уже после старта. Явно переданные корни (в тестах) работают как прежде.

## Регрессионный тест

`ArtifactCleanupStartupWiringTest` (4 теста): конструктор контроллера и сервиса с параметрами по умолчанию не трогает глобалы приложения; поле `roots` пусто до первого `plan()`; явный override не подменяется.

**Проверено, что тест падает на старом коде**: с жадным вычислением воспроизводятся `ExceptionInInitializerError` и `NoClassDefFoundError: Could not initialize class ConstantsKt` — страж ловит именно этот класс дефекта, а не является тавтологией.

## Проверки

```
compileKotlin + ktlintCheck + test (--rerun-tasks) → BUILD SUCCESSFUL, 389 тестов, 0 падений
```

## Пробел, который этот инцидент вскрыл (вне объёма PR)

В CI **нет job'а с запуском тестов** и нет smoke-старта приложения: компиляция, ktlint и doc-гейты дефект порядка инициализации не видят. Пока smoke-проверка старта не заведена, такие дефекты ловятся только выкатом — как в этот раз. Предлагаю отдельной задачей.
