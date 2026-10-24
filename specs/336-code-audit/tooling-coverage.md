# Инвентаризация существующих проверок (tooling coverage)

> Тикет: OpenProject #340, research-субагент карты #336 «Аудит кода Karaoke».
> Снимок: `master` @ `1977838a9b80ff5a3fc58f630ab991d6e9b67cc7` (2026-10-23 14:00 +03).
> Метод: только чтение файлов на диске. Линтеры, сборки и тесты не запускались
> (ограничение тикета). Все числа ниже — из файлов на указанном SHA, а не из памяти.

## Сводка

| Показатель | Значение | Источник |
|---|---|---|
| Guard/check-скриптов `tools/check-*.sh` | 21 | `ls tools/check-*.sh` |
| Всего `tools/check-*` вместе с Python | 24 (21 `.sh` + 3 `.py`) | `ls tools/check-*` |
| CI jobs в `.github/workflows/lint.yml` | 11, из них блокирующих 10 | workflow |
| Baseline-нарушений в трёх baseline (ktlint app/db/web + ESLint webvue3/public) | 1 | baseline-файлы |
| Kotlin-тестов | 53 файла (50 app, 3 web, 0 db) | `src/test` |
| Playwright e2e-спек | 15 (14 webvue3, 1 karaoke-public) | `*/e2e` |
| CI job, запускающий тесты | нет | workflow |
| Инструмент измерения покрытия | нет (ни jacoco, ни kover, ни coverage-порогов) | build-файлы |

Главный вывод: существующие проверки плотно закрывают **формат, стиль, часть
архитектурных запретов и документационную дисциплину**, но почти не касаются
**рантайм-дефектов** (конкурентность, ресурсы, исключения, контракты, безопасность).
Тесты в репозитории есть, но merge они не блокируют.

## Линтеры и статические анализаторы

| Проверка | Где и как настроена | Что ловит | Что не ловит | Как использовать в аудите |
|---|---|---|---|---|
| ktlint 1.5.0 (ktlint-gradle 12.3.0) | `build.gradle.kts`, `.editorconfig`, per-module baseline `config/ktlint/baseline-<module>.xml`; CI job `backend-lint` + pre-commit | Форматирование и standard-правила Kotlin/Java: отступы, пробелы, переносы, naming, imports, `max_line_length=200`, `chain-method-continuation`. Стиль `ktlint_official` | Семантику, nullability, утечки ресурсов, потоки, обработку исключений, SQL, безопасность, сложность, дублирование | Находки уровня форматирования в отчёт не включать. Baseline — 1 запись (см. ниже). Прогон `ktlintCheck` в рамках тикета не делался |
| ESLint webvue3 | `webvue3/.eslintrc.cjs`; CI `frontend-lint` (matrix) + pre-commit | `plugin:vue/vue3-recommended` (шаблонные правила Vue 3, props, v-for/key и т. п.), `vue/html-self-closing`, `no-unused-vars` (warn) | Типы TypeScript (`@vue/eslint-config-typescript` отключён), `eslint:recommended` (закомментирован), XSS (`vue/no-v-html` off), гонки, бизнес-логику, форматирование (отдано Prettier) | Baseline пуст (0). Любое срабатывание — новое. Не выдавать за lint-нарушения то, что закрыто отключёнными правилами: `vue/no-v-html`, `vue/require-explicit-emits`, `vue/no-template-shadow`, `vue/require-toggle-inside-transition` |
| ESLint karaoke-public | `karaoke-public/.eslintrc.cjs`; CI `frontend-lint` (matrix) + pre-commit | То же ядро `vue3-recommended`, `vue/html-self-closing`, `no-unused-vars` (warn) | Типы TS, `eslint:recommended`, XSS (`vue/no-v-html` off), гонки, логику | Baseline пуст (0). Отключены `vue/require-explicit-emits`, `vue/no-v-html`; `vue/no-template-shadow` и `vue/require-toggle-inside-transition` здесь остаются включёнными |
| Prettier | `webvue3/package.json`, `karaoke-public/package.json`; CI strict `prettier --check`, pre-commit | Только форматирование (`src/**/*.{vue,js,ts,json}`, в public ещё `md`) | Всё, что не формат | Форматные расхождения не считать дефектами аудита |
| detekt | `config/detekt/detekt.yml` и `config/detekt/baseline.xml` готовы, но плагин **закомментирован** в `build.gradle.kts` (detekt 1.23.x несовместим с Kotlin 2.2.20); pre-commit hook тоже закомментирован | Ничего в текущем состоянии. При включении ловил бы `CyclomaticComplexMethod`, `LongMethod`, `SwallowedException`, `TooGenericExceptionCaught`, `MagicNumber`, `UnreachableCode`, naming и др. | — (не работает) | Классы «сложность», «проглоченное исключение», «слишком общий catch» **сейчас не покрыты ничем**. Это ручной аудит, а не дубль detekt |
| SonarQube, Semgrep, CodeQL, SpotBugs, PMD, OWASP dependency-check, Trivy/Grype, Dependabot/Renovate | Следов нет: ни в `build.gradle.kts`, `settings.gradle.kts`, `gradle/`, `package.json`, `.github/`, ни в `.pre-commit-config.yaml` | — | Весь SAST/SCA-класс: инъекции, утечки, небезопасные зависимости, секреты, taint-анализ | Не ссылаться на «анализатор это поймает». Класс безопасности и зависимостей — только ручной аудит |
| `vue-tsc --noEmit` | Не подключён ни в `package.json`, ни в CI. Упоминается только в комментариях `eslintrc.cjs` и `docs/api/README.md` | — | Ошибки типов TypeScript/Vue | Типовые ошибки фронта merge не блокируют; при аудите проверять вручную |

Дополнительно: `.editorconfig` в комментарии называет ktlint 1.7.1, а
`build.gradle.kts` фиксирует `version = "1.5.0"`. Для воспроизведения
поведения доверять gradle-файлу, не комментарию.

## Guard/check-скрипты

Колонка «Запуск»: CI — job в `lint.yml`; PC — hook в `.pre-commit-config.yaml`;
«вручную» — скрипт не подключён ни к CI, ни к pre-commit.

| Скрипт | Запуск | Что проверяет | Что не проверяет | Как использовать в аудите |
|---|---|---|---|---|
| `check-audit-coverage.sh` | вручную | Каждый контроллер с `@GetMapping`/`@PostMapping` и каждый `@Scheduled`-сервис упомянут в `specs/187-site-traffic-anomaly-investigation/research.md` | Корректность кода, реальный трафик, полноту аудита по существу | Не дублировать «контроллер не учтён в аудите нагрузки» — это уже гейт |
| `check-cert-expiry.sh` | вручную | Срок TLS-сертификата `host:port`, порог 45 дней | Код, конфиги nginx, цепочку сертификатов | К аудиту кода отношения не имеет |
| `check-container-restart.sh` | PC | Прямые `docker restart/stop/start`, `docker-compose restart` в shell-файлах, `deploy/do.sh`, workflows (по diff HEAD) | Ручные команды вне git, markdown, фактический эффект перезапуска | Не считать нарушением, если находка не про деплой-путь |
| `check-docker-config.sh` | PC | `docker build` без `DOCKER_CONFIG` в shell-файлах | Код приложения, Dockerfile | Только инфраструктурный класс |
| `check-docker-image-tags.sh` | CI + PC | `FROM nginx:` и `FROM node:` с запрещёнными тегами (`nginx:alpine`, `nginx:latest`, `node:alpine`, `node:latest`, `node:lts`) | Остальные образы, digest-pinning, уязвимости базовых образов | Находки R-04/R-05 не дублировать |
| `check-do-sh-guards.sh` | вручную | Опыт A: заведомо падающая сборка обязана вернуть ненулевой код. Опыт B (`--prod`): отсутствующий образ не должен убивать контейнер | Логику `do.sh` целиком, все ветки деплоя | Инфраструктурный smoke, к аудиту кода не относится |
| `check-endpoint-field-coverage.sh` | PC | Поля UI против полей, принимаемых контроллером, для пар из `tools/endpoint-pairs.yml` (сейчас **1 активная пара**: SongEdit ↔ `/api/song/update`) | Остальные файлы контроллеров (всего 53 `*Controller.kt`), схемы ответов, контракты БД, типы полей | Не считать «контракты API покрыты»: покрыта одна пара |
| `check-enforcement.sh` | вручную | MUST-правила `CONTRIBUTING.md` (32 MUST из 35 блоков) имеют baseline или 0 нарушений; выдаёт warning | Реальное соблюдение правил, если baseline пуст и линтер не запускался | Использовать как карту «правило → инструмент», а не как доказательство |
| `check-eslint-baseline.sh` | CI | Новые нарушения ESLint относительно `.eslint-baseline.json` по ключу `ruleId + file + line + column`; exit 1 | Отключённые правила, типы TS, качество самих правил | Главный источник «что уже известно» для фронта; baseline пуст |
| `check-feature-doc.sh` | CI + PC | 6 обязательных секций per-feature документа, slug = имя файла, status из допустимого набора | Содержание и правдивость документа | Документационный класс, не код |
| `check-frontend-build.sh` | PC | `npm run` без `cd webvue3`/`cd karaoke-public` в shell-файлах | Запуск вручную, корректность сборки | Инфраструктурный класс |
| `check-gradle-user-home.sh` | PC | `./gradlew` без `GRADLE_USER_HOME`, включая pre-commit и `deploy/do.sh` | Другие команды и окружения | Инфраструктурный класс |
| `check-jsdoc-coverage.sh` | CI (`--strict`) | Доля Vue/TS-компонентов (`export default`, `defineComponent`, `<script setup>`) с JSDoc ≥ 50% | Качество и правдивость JSDoc, логику | Не считать отсутствие JSDoc дефектом, если порог пройден; метрика грубая (ищет `/**` в 50 строках выше) |
| `check-kdoc-coverage.sh` | CI (`--strict`) | Доля top-level `class/object/interface/enum/data class` с KDoc ≥ 50% в `karaoke-app` и `karaoke-web` | Методы, параметры, качество KDoc | То же; порог 50%, а не 100% |
| `check-knowledge-cross-links.sh` | CI | Относительные ссылки `../X.md` и `related:` внутри `knowledge/` | `docs/`, `archive/`, `specs/` | Документационный класс |
| `check-knowledge-structure.sh` | CI | Обязательные каталоги/файлы `knowledge/`, 9 доменов, ≥1 ADR, README каждого слоя, cross-link на домены, frontmatter | Содержание знаний | Документационный класс |
| `check-no-jpa-imports.sh` | CI + PC | Импорты JPA/Hibernate (`org.springframework.data.jpa`, `javax.persistence`, `hibernate`) в `karaoke-app/src/main` и `karaoke-web/src/main` | Неправильное использование JDBC, SQL, транзакции | R-07 закрыт; «JPA нет» не означает «работа с БД корректна» |
| `check-no-legacy-tbl-settings.sh` | CI | `tbl_settings` в SQL, Kotlin и ```sql-блоках спек (правильное имя — `tbl_songs`) | Другие устаревшие имена, схему БД, типы колонок | Не выдавать `tbl_settings` за новую находку; остальные имена таблиц — ручной аудит |
| `check-no-mp4-mentions.sh` | CI + PC | `\bmp4\b` и «скачивани» в production-коде; baseline 125 записей по хешу содержимого строки | Смысл текста, рекламные материалы вне кода, тесты (исключены) | Совпадение с baseline — известное исключение, не новая находка. Правка текста baseline-строки намеренно выглядит как новое |
| `check-songedit-field-coverage.sh` | PC | `v-model="song.X"` в `SongEdit.vue` против полей, принимаемых `/api/song/update` | Другие формы и эндпоинты | Частный случай контракта, не общий |
| `check-subagent-isolation.sh` | PC | PR-ветка не содержит чужих коммитов; `git worktree list` — advisory | Качество кода, race внутри рантайма | Процессный guard, к дефектам кода не относится |
| `check-doc-references.py` | CI + PC | Относительные `.md`-ссылки, пути к файлам кода и `/api`-эндпоинты в `knowledge/`, `docs/`, `archive/docs/`; в `specs/` — только ссылки | Упоминания в спеках, внешние URL | Документационный класс; 51 осознанное исключение в whitelist |
| `check-spec-issue-link.py` | CI | «Современная» спека (есть `## Knowledge References`) имеет `## OpenProject Tracking` с Issue ID, Title, Workflow | Старые спеки (grandfathered), правдивость трекинга | Governance, не код |
| `check-ssot-impact.py` | CI | Изменения кода сопровождаются изменениями в `knowledge/` по `.ssot-map.yml` | Корректность и полноту знаний | Governance, не код |
| `lint-knowledge.py` | CI (`--baseline`) | Эмодзи, обязательные секции шаблона, structural links L2 → L1 | Содержание | Baseline — 0 fingerprints (долг закрыт) |
| `baseline-stats.sh` | CI (informational) | Печатает количество нарушений в baseline-файлах | Ничего не блокирует | Быстрый способ перепроверить числа ниже |

## Тесты

| Инструмент | Где лежит и как запускается | Что покрывает | Что не покрывает | Как использовать в аудите |
|---|---|---|---|---|
| JUnit 5 (через `spring-boot-starter-test`, `useJUnitPlatform()`) | 50 файлов в `karaoke-app/src/test`, 3 в `karaoke-web/src/test`, 0 в `karaoke-db`; запуск только вручную `./gradlew test`, в CI job'а нет | Модель (`Song`, `Zakroma`, `PublicationFilters`), сервисы (storage circuit breaker, cleanup, VK, sync), контроллеры (фильтр дат), LLM-утилиты, точечные race-тесты (`HealthReportRepairRaceTest`, `StorageCircuitBreakerIsolationTest`, `StorageTimeoutInterruptTest` и др.) | Интеграцию с реальной БД/хранилищем, HTTP-контракты, `karaoke-db`, покрытие, мутационную прочность | Тест — доказательство только после локального прогона на этом SHA. «Зелено в CI» про тесты сказать нельзя: CI их не запускает |
| Playwright (`@playwright/test`) | `webvue3/e2e` (14 спек), `karaoke-public/e2e` (1 спека), конфиги `playwright.config.js`; запуск `npm run test:e2e` вручную против запущенного контейнера на `http://localhost:7906` (или `E2E_BASE_URL`) | Редактор: lifecycle, save race, ghost markers, навигация по маркерам, вставка, XSS, задержки ввода, вкладки, smoke | Бэкенд, авторизацию, все страницы, мобильные сценарии; нет unit-тестов SPA | Эмпирика для фронта возможна, но требует поднятого приложения и не является CI-гейтом. Наличие `karaoke-public/playwright-report/` — след ручного прогона (каталог untracked) |
| Покрытие (jacoco/kover/istanbul) | Конфигурации нет, отчётов в репозитории нет | — | Процент покрытия и регресс покрытия | Не ссылаться на «покрыто тестами» без цифры: цифры взять неоткуда |

6 тестов помечены `@Disabled` (5 Playwright-хелперов в `PlaywrightTests.kt`,
1 в `SearchLastAlbumVkTest.kt`). Они не выполняются даже при ручном прогоне.

## Что реально запускается в CI и что блокирует merge

Единственный workflow — `.github/workflows/lint.yml`. Триггеры: push в `master`,
pull request в `master`, `workflow_dispatch`. Есть `concurrency.cancel-in-progress`.
Других CI-конфигов (GitLab, Jenkins, Azure) в репозитории нет.

| Job | Что выполняет | Блокирует merge |
|---|---|---|
| `backend-lint` | `./gradlew ktlintCheck --no-daemon --stacktrace` | да |
| `frontend-lint` (matrix: webvue3, karaoke-public) | `bash tools/check-eslint-baseline.sh <spa>` + `npx prettier --check "src/**/*.{vue,js,ts,json}"` | да |
| `docs-lint` | lychee `--offline` по `archive/docs/features/` и `CONTRIBUTING.md` + `check-feature-doc.sh` | да |
| `baseline-stats` | `bash tools/baseline-stats.sh` | нет (`continue-on-error: true`) |
| `kdoc-coverage` | `check-kdoc-coverage.sh --strict` (порог 50%) | да |
| `jsdoc-coverage` | `check-jsdoc-coverage.sh --strict` (порог 50%) | да |
| `ssot-impact` | `check-ssot-impact.py` (нужен diff с `origin/master`) | да |
| `knowledge-structure` | 7 шагов: structure, no-legacy-tbl-settings, cross-links, `lint-knowledge --baseline`, doc-references, spec-issue-link, запрет прямых коммитов в master за 24 ч | да |
| `docker-image-tags-guard` | `check-docker-image-tags.sh --quiet` | да |
| `no-jpa-imports-guard` | `check-no-jpa-imports.sh --quiet` | да |
| `no-mp4-mentions-guard` | `check-no-mp4-mentions.sh --quiet` | да |

Практические следствия:

- Merge блокируют 10 job'ов из 11. `baseline-stats` — только информационный.
- **Тесты не запускаются вообще.** Ни JUnit, ни Playwright не входят в CI.
- Не входят в CI и не блокируют merge (только pre-commit, который обходится
  `git commit --no-verify`, или ручной запуск): `check-gradle-user-home.sh`,
  `check-docker-config.sh`, `check-container-restart.sh`,
  `check-frontend-build.sh`, `check-subagent-isolation.sh`,
  `check-songedit-field-coverage.sh`, `check-endpoint-field-coverage.sh`.
- Нигде не подключены (только ручной запуск): `check-audit-coverage.sh`,
  `check-cert-expiry.sh`, `check-do-sh-guards.sh`, `check-enforcement.sh`.
- `CLAUDE.md` говорит «CI 7/7 PASS», фактически в workflow 11 job'ов. Это
  расхождение документации и конфига; ориентироваться на workflow.
- Настройки GitHub branch protection из репозитория не видны, поэтому «10
  блокирующих job'ов» — это то, что блокирует сам workflow; обязательность
  именно этих check'ов на стороне GitHub подтвердить по диску нельзя.

## Слепые зоны

Классы дефектов, которые **не ловит ни один** из перечисленных инструментов.
Числа — оценка поверхности ручного аудита по grep на снимке.

1. **Конкурентность и потоки.** 94 конструкции `Thread(`, `Executors.`,
   `synchronized`, `CompletableFuture`, `@Async` в main-коде. Статического
   анализа гонок, deadlock'ов, видимости и interrupt-безопасности нет. Есть
   точечные тесты (11 тестовых файлов касаются потоков), но они не в CI.
2. **Утечки JDBC-соединений и других ресурсов.** 236 вызовов
   `getConnection()` и 244 `.use {` в main-коде; проект сознательно на сыром
   JDBC (Constitution II). SpotBugs/PMD нет, detekt отключён, ktlint ресурсы не
   проверяет. Утечка `Connection`/`Statement`/`ResultSet` не будет поймана.
3. **Обработка исключений и логирование throwable.** 288 `catch` с
   `Exception`/`Throwable`/`RuntimeException`, 111 `printStackTrace` в
   main-коде. Правила detekt `SwallowedException`,
   `TooGenericExceptionCaught` настроены, но не работают. Semgrep/CodeQL нет.
   Проглоченное исключение, потеря стека, логирование только `e.message` —
   ручная работа.
4. **Контракты API и БД.** 53 файла `*Controller.kt` и 64 SQL-файла в `deploy/`
   (57 из них — миграции `deploy/karaoke-db`). Автоматически
   сверяется **одна** пара UI ↔ backend (`SongEdit` ↔ `/api/song/update`);
   OpenAPI/JSON-schema/миграционной валидации нет. `check-no-legacy-tbl-settings`
   знает только про `tbl_settings`; расхождения «поле DTO ↔ колонка ↔ SQL»
   ничем не покрыты.
5. **Безопасность и зависимости.** Нет ни SAST, ни SCA, ни Dependabot.
   `vue/no-v-html` отключён в обоих SPA; проверка секретов — ручная
   (`git ls-files | grep ...` из `AGENTS.md`); авторизация/IDOR/инъекции
   статически не проверяются. Полагаться на линтеры здесь нельзя.
6. **Гонки и типы на фронте.** 15 Playwright-спек не в CI и требуют
   запущенного контейнера. Unit-тестов SPA нет. `vue-tsc --noEmit` не
   подключён; ESLint работает без тип-aware правил и без `eslint:recommended`.
   Ошибки типов и race в UI merge не блокируют.
7. **Тесты и покрытие как доказательство.** JUnit-тесты не запускаются в CI,
   покрытие не измеряется (ни jacoco, ни kover, ни отчётов), 6 тестов
   `@Disabled`. Ссылка «есть тест» без локального прогона на этом SHA —
   не доказательство; «CI зелёный» про тесты ничего не говорит.

Отдельно: **detekt** мог бы закрыть часть пунктов 1 и 3 (сложность,
`SwallowedException`, `TooGenericExceptionCaught`), но он выключен из-за
несовместимости с Kotlin 2.2.20. Это не «слепая зона по недосмотру», а
осознанный долг — в аудите его нельзя записывать как новую находку, но и
нельзя считать класс покрытым.

## Известные baseline-нарушения, которые нельзя выдавать за новые

### Три baseline из тикета

| Baseline | Нарушений | Что зафиксировано | Комментарий для аудита |
|---|---:|---|---|
| `config/ktlint/baseline-karaoke-app.xml` | 1 | `karaoke-app/.../model/Zakroma.kt:192:60`, правило `standard:chain-method-continuation` | Форматное нарушение. Единственная запись во всех трёх baseline |
| `config/ktlint/baseline-karaoke-db.xml` | 0 | — | Модуль `karaoke-db` (legacy Java) чист по baseline |
| `config/ktlint/baseline-karaoke-web.xml` | 0 | — | — |
| `webvue3/.eslint-baseline.json` | 0 | `[]` | Текущее число нарушений не измерялось; пустой baseline означает «известных нет», а не «их нет» |
| `karaoke-public/.eslint-baseline.json` | 0 | `[]` | То же |
| **Итого по трём baseline (ktlint + два ESLint)** | **1** | | `baseline-stats.sh` при запуске покажет `TOTAL 1` |

### Дополнительные подавления, которые тоже не являются новыми находками

| Механизм | Записей | Что подавлено | Как не спутать |
|---|---:|---|---|
| `tools/check-no-mp4-mentions.baseline` | 125 | Упоминания `mp4`/«скачивание» в production-коде (R-11) | Ключ — путь + sha256 содержимого строки. Совпадение — известное исключение; изменение текста такой строки намеренно считается новым |
| `config/detekt/baseline.xml` | 0 | Пустой `<Current>` | detekt выключен, файл — заготовка |
| `config/knowledge/baseline-knowledge-lint.txt` | 0 fingerprints | Долг knowledge-lint закрыт (комментарий: «Captures 0 fingerprints») | Не искать здесь «309 нарушений» из старых отчётов — они закрыты |
| `config/knowledge/doc-references-whitelist.txt` | 51 | Осознанные исключения для ссылок/путей/эндпоинтов | Это whitelist, а не baseline; каждая запись с причиной |
| Отключённые правила ESLint | — | webvue3: `vue/require-explicit-emits` (исторически 50), `vue/no-template-shadow` (1), `vue/no-v-html` (10), `vue/require-toggle-inside-transition` (20). karaoke-public: `vue/require-explicit-emits`, `vue/no-v-html` (исторически 4) | Эти числа — из комментариев конфигов, **не текущие нарушения**. Правила отключены, поэтому линтер их не покажет; `v-html` при этом остаётся ручной зоной безопасности |
| Отключённые правила ktlint | 9 глобальных + 2 точечных | `no-wildcard-imports`, `argument-list-wrapping`, `function-literal`, `parameter-list-wrapping`, `function-signature`, `no-consecutive-comments`, `import-ordering`, `trailing-comma-on-call-site`, `trailing-comma-on-declaration-site`; `property-naming` для двух service-файлов, `backing-property-naming` для `SongVoiceLine.kt` | Стилевые послабления; не считать их нарушениями |

### Осторожно при использовании baseline

1. **ktlint baseline, вероятно, устарел по строке.** Запись указывает на
   `Zakroma.kt:192:60`, но на снимке строка 192 — комментарий
   `// Batch 5: реальные Album-сущности...`, а не chain-вызов. Формат baseline
   предполагает сверку по line/column, поэтому запись может уже ничего не
   подавлять. Это требует прогона `ktlintCheck`/`ktlintGenerateBaseline`
   (в рамках тикета не запускалось). Не считать «chain-method-continuation
   в Zakroma.kt» известным нарушением, пока прогон не подтвердит.
2. **ESLint baseline хранит абсолютный путь.** `generate-eslint-baseline.sh`
   пишет `filePath` как есть (например, `/home/nsa/Karaoke/webvue3/src/...`), а
   `check-eslint-baseline.sh` сравнивает `file` побайтно. При непустом
   baseline прогон в CI (другой абсолютный путь) дал бы ложные «новые»
   нарушения. Сейчас baseline пуст, поэтому проблема не проявляется; не
   пытаться «добавить исключение» в baseline без нормализации путей.
3. **ESLint baseline пуст, но это не значит, что текущий прогон чист.**
   Проверка не запускалась. Если на снимке есть хоть одно нарушение, CI
   `frontend-lint` будет красным: baseline пуст, любое нарушение — новое.

## Ограничения этой инвентаризации

- Линтеры, тесты, сборки и guard-скрипты не запускались: тикет запрещает
  долгие прогоны. Всё, что зависит от текущего выполнения (реальные числа
  ESLint, актуальность ktlint baseline, покрытие KDoc/JSDoc, зелёность тестов),
  помечено как непроверенное.
- Настройки GitHub branch protection и фактические статусы PR не читались.
- `playwright-report/` в `karaoke-public` — untracked-артефакт ручного прогона;
  по нему нельзя судить о CI.
- Числа поверхности (`getConnection`, `catch`, `printStackTrace`, число
  компонентов и т. п.) — результат grep, а не семантического анализа; они
  нужны только для приоритизации ручного аудита.

## Источники (прочитанные файлы)

- `build.gradle.kts`, `settings.gradle.kts`, `.editorconfig`
- `config/ktlint/baseline-karaoke-app.xml`, `baseline-karaoke-db.xml`, `baseline-karaoke-web.xml`
- `config/detekt/detekt.yml`, `config/detekt/baseline.xml`
- `config/knowledge/baseline-knowledge-lint.txt`, `config/knowledge/doc-references-whitelist.txt`
- `webvue3/.eslint-baseline.json`, `karaoke-public/.eslint-baseline.json`
- `webvue3/.eslintrc.cjs`, `karaoke-public/.eslintrc.cjs`
- `webvue3/package.json`, `karaoke-public/package.json`
- `.github/workflows/lint.yml`, `.pre-commit-config.yaml`
- `tools/check-*.sh` (21 файл), `tools/check-*.py` (3 файла), `tools/baseline-stats.sh`,
  `tools/generate-eslint-baseline.sh`, `tools/lint-knowledge.py`,
  `tools/endpoint-pairs.yml`, `tools/check-no-mp4-mentions.baseline`
- `karaoke-app/src/test/**`, `karaoke-web/src/test/**`, `webvue3/e2e/**`,
  `karaoke-public/e2e/**`, `webvue3/playwright.config.js`
