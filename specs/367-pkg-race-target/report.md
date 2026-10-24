# Отчёт: OpenProject #367 — `PKG-RACE-TARGET` (BR-397, BR-407, BR-409)

> **Задача**: [#336] S2: Гонка чтения/записи целевого объекта (пакет `PKG-RACE-TARGET`, волна D).
> **Ветка**: `590-pkg-race-target` · **worktree**: `/home/nsa/Karaoke/.worktrees/waveD-race-target`
> **База**: `07126462` (origin/master на момент создания ветки) · **Режим**: короткий цикл по карточкам аудита.
> **Источник правды**: `specs/336-code-audit/findings/P-54.md` (P-54-11), `P-55.md` (P-55-09),
> `P-56.md` (P-56-01); ТЗ — `specs/336-code-audit/backlog.md`, блок `### PKG-RACE-TARGET`.
> **Дата**: 2026-10-24 · **Исполнитель**: AI-агент (DSH), пакет #367.

## Сводка

Три находки — один механизм: админ-SPA работает с двумя БД (`target: 'local' | 'remote'`),
запрос уходит в выбранную БД, а ответ применялся безусловно. При переключении target в окне
запроса побеждал **последний пришедший ответ**, а запись/отзыв адресовались **текущему селекту**,
а не БД строки.

| BR | Severity | Было | Стало |
|---|---|---|---|
| **BR-397** `webvue3/src/components/NewsTemplates/NewsTemplatesEditor.vue` | S3 | Цепочка title→body читала `target` и `bodyValue` в каждом POST: переключение БД между запросами уводило поля в разные БД, `reload()` подменял body чужим значением | `templateSaveChain.js`: target и оба значения фиксируются на входе; `reload()` отбрасывается по поколению; селект и «Сохранить» блокируются на время операции |
| **BR-407** `webvue3/src/components/Promotions/store.js` | S3 | Ответ списка применялся без сверки target; `save`/`delete`/`create` подставляли текущий target | `promoRulesListTarget` — БД показанного списка; устаревший ответ (поколение или target) отбрасывается; смена target очищает список; мутации адресуются БД списка; кнопки заблокированы до синхронизации |
| **BR-409** `webvue3/src/components/ShareLinks/store.js` | S2 | Ответ дайджеста применялся без сверки target; «Отозвать» брал текущий target | `shareLinksDigestTarget` — БД дайджеста; устаревший ответ отбрасывается; смена target очищает дайджест; «Отозвать» фиксирует БД строки при открытии подтверждения и без неё не уходит |

Регрессия: **9/9 тестов падают на коде до правки**, **127/127 проходят после** (весь SPA).
Артефакт прогонов: [`evidence-before-after.txt`](evidence-before-after.txt).

## 1. Где была гонка

### BR-397 — сохранение шаблона новости

- **Локация (до правки)**: `webvue3/src/components/NewsTemplates/NewsTemplatesEditor.vue:301-302`
  (цепочка `saveOne(title).then(() => saveOne(body))`), `:322` (`params: { key, value, target: this.target }`),
  `:247-249` (`watch target → reload()`), `:282-294` (`syncFieldsFromTemplates`).
- **Участники**: (1) цепочка сохранения из `saveTemplate()`, (2) watcher `target`, вызывающий
  `reload()` → `syncFieldsFromTemplates()`.
- **Окно**: между ответом на POST title и отправкой POST body (длительность одного запроса).
  Внутри окна админ меняет селект «Целевая БД»; `reload()` успевает применить ответ новой БД и
  перезаписать `bodyValue`.
- **Что оставалось после**: title записан в БД A, body — в БД B; во вторую БД уходит body,
  подменённый значением из неё же (в пределе — прод-настройки при `remote`).

### BR-407 — список акций

- **Локация (до правки)**: `webvue3/src/components/Promotions/store.js:39-56`
  (`loadPromoRulesList` читает `ctx.state.promoRulesTarget` на отправке и коммитит ответ без
  проверки), `:64`, `:72`, `:80` (`create`/`save`/`delete` подставляют текущий target);
  `PromotionsTable.vue:149-150` (`reload()` на каждое `@change` селекта).
- **Участники**: два параллельных `POST /api/promorules/list` с разными target; обработчик
  сохранения/удаления строки.
- **Окно**: от отправки первого запроса до применения последнего ответа (быстрое двойное
  переключение «Локальная/Сервер»; ответы приходят не в порядке отправки).
- **Что оставалось после**: таблица показывает правила БД A при выбранной БД B; правка/удаление
  строки уходит в B (при совпадении id меняется одноимённое правило, иначе сервер возвращает
  `0`/`false` и UI молча перезагружает список).

### BR-409 — дайджест share-ссылок

- **Локация (до правки)**: `webvue3/src/components/ShareLinks/store.js:73-83`
  (`loadShareLinksDigest` коммитит ответ без сверки), `:96-101` (`revokeShareLink` подставляет
  `ctx.state.shareLinksTarget`); `ShareLinksTable.vue:353-357` (`doRevoke`).
- **Участники**: два `POST /api/sharelinks/digest` с разными target; подтверждение отзыва,
  открытое на строке.
- **Окно**: от смены target до прихода ответа; отдельное окно — между открытием подтверждения
  и нажатием «Отозвать» (селект можно переключить, модалка остаётся).
- **Что оставалось после**: в таблице строки чужой БД; «Отозвать» отзывает запись с тем же `id`
  в другой базе — необратимое действие не над той строкой (S2).

## 2. Что сделано и почему так

| # | Решение | Почему |
|---|---|---|
| R-1 | Номер поколения запроса (`createRequestGeneration`) + сверка `requestTarget === currentTarget` (`isStaleTargetResponse`) | Гонка целиком в клиентском состоянии: важно, какой ответ применить и в какую БД писать. Условный `UPDATE … WHERE`/уникальный ключ — инструмент серверной гонки; запись в БД уже атомарна, API менять нельзя |
| R-2 | Не отменять запрос (`AbortController`), а отбрасывать ответ | `promisedXMLHttpRequest` (`webvue3/src/lib/utils.js`) не отдаёт отмену; правка транспорта ради трёх экранов — лишний риск, отбрасывание решает задачу |
| R-3 | БД происхождения набора: `promoRulesListTarget`, `shareLinksDigestTarget` | Бэкенд не возвращает target в строке дайджеста, поэтому происхождение хранится на уровне набора; мутации адресуются ему, а не селекту |
| R-4 | Смена target очищает набор | Иначе строки прежней БД остаются на экране и выглядят данными новой; кнопки становятся доступны раньше ответа |
| R-5 | UI-блокировка (`canMutate`, `isDigestInSync`) | Требование карточек «блокировать сохранение/удаление/переключение»; защита в глубину к store-контракту |
| R-6 | Для share-ссылок target строки фиксируется в компоненте (`revokeTargetTarget`) и передаётся явно | Смена селекта между открытием подтверждения и нажатием «Отозвать» не должна менять адресата; приоритет: явный target → `shareLinksDigestTarget` → отказ |
| R-7 | Цепочка сохранения шаблона вынесена в `templateSaveChain.js` | `.vue` не импортируется node-тестом; тест должен гонять тот же код, что прод. `saveTemplateFromEditor({ editor, saveOne })` — точка входа компонента |
| R-8 | Поколение — на модуль store, не в Vuex `state` | Счётчик не участвует в рендере; мутация на каждый запрос давала бы лишние перерисовки и «грязный» state |

Новых зависимостей нет (только `node:test`, уже используемый в CI).

### Изменённые файлы

| Файл | Что |
|---|---|
| `webvue3/src/lib/target-request-guard.js` | NEW — `createRequestGeneration`, `isStaleTargetResponse` |
| `webvue3/src/components/NewsTemplates/templateSaveChain.js` | NEW — закреплённая цепочка title+body, точка входа компонента |
| `webvue3/src/components/NewsTemplates/NewsTemplatesEditor.vue` | поколение `reload()`, закреплённый target/значения, блокировка селекта и кнопки |
| `webvue3/src/components/Promotions/store.js` | `promoRulesListTarget`, поколение, мутации по БД списка, очистка при смене target |
| `webvue3/src/components/Promotions/PromotionsTable.vue` | `canMutate`, блокировка «Сохранить»/«Удалить»/«Добавить» |
| `webvue3/src/components/ShareLinks/store.js` | `shareLinksDigestTarget`, поколение, отзыв по БД строки, очистка при смене target |
| `webvue3/src/components/ShareLinks/ShareLinksTable.vue` | `isDigestInSync`, `revokeTargetTarget`, блокировка «Отозвать» |
| `webvue3/src/test-utils/fakeXhr.js` | NEW — шов для перемежающегося порядка ответов |
| тесты (4 файла) | см. §3 |
| `knowledge/system/frontend/target-request-guard.md` | NEW — SSoT |
| `docs/features/target-scoped-admin-requests.md` | NEW — per-feature (FR-009), 6 секций + «Известные ловушки» |

SSoT-ссылки добавлены в `knowledge/system/frontend/vuex-patterns.md`,
`store-monetization-stores.md`, `stores-remaining-admin.md`.

## 3. Регрессия «до/после»

Шов: `webvue3/src/test-utils/fakeXhr.js` подменяет `XMLHttpRequest` и позволяет отвечать в
обратном порядке (запрос A → запрос B → ответ B → ответ A). Тесты гоняют **прод-код** стора и
`templateSaveChain`, а не его копию; для BR-397 в тесте рядом стоит прежняя форма цепочки —
она доказывает, что шов действительно воспроизводит расщепление БД.

### Команда

```bash
cd webvue3 && npm run test:unit      # node --test "src/**/__tests__/*.test.js"
```

### До правки (старый код)

```text
$ git show HEAD:webvue3/src/components/Promotions/store.js > src/components/Promotions/store.js
$ git show HEAD:webvue3/src/components/ShareLinks/store.js > src/components/ShareLinks/store.js
$ node --test src/components/Promotions/__tests__/target-race.test.js \
              src/components/ShareLinks/__tests__/target-race.test.js
ℹ tests 9   ℹ pass 0   ℹ fail 9

✖ ответ прежней БД, пришедший последним, не подменяет список текущей
  AssertionError: в таблице должны остаться акции выбранной БД, а не последнего пришедшего ответа
    actual: [ 'local-rule' ]   expected: [ 'remote-rule' ]
✖ смена target очищает список до прихода ответа новой БД
    actual: [ { id: 1, name: 'local-rule' } ]   expected: []
✖ сохранение адресуется БД показанного списка, а не текущему селекту
    'remote' !== 'local'
✖ удаление адресуется БД показанного списка, а не текущему селекту
    'remote' !== 'local'
✖ ответ прежней БД … не перезаписывает дайджест текущей
    actual: [ 'local@example.test' ]   expected: [ 'remote@example.test' ]
✖ смена target очищает дайджест до прихода ответа новой БД
    actual: [ { id: 1, ownerEmail: 'local@example.test' } ]   expected: []
✖ отзыв адресуется БД строки, даже если селект уже переключён
    actual: undefined   expected: 'local'
✖ без явного target отзыв использует БД показанного дайджеста
    actual: 'remote'   expected: 'local'
✖ пока дайджест не загружен, отзыв отклоняется, а не уходит в случайную БД
    AssertionError: Missing expected rejection.
```

```text
$ node --test src/components/NewsTemplates/__tests__/template-save-chain.test.js
✖ исправленная цепочка фиксирует target и оба значения на всю операцию
  AssertionError: оба поля должны уйти в БД, выбранную в момент нажатия «Сохранить»
    actual: [ 'local', 'remote' ]   expected: [ 'local', 'local' ]
ℹ tests 3   ℹ pass 2   ℹ fail 1
```

### После правки

```text
$ cd webvue3 && npm run test:unit
ℹ tests 127   ℹ suites 17   ℹ pass 127   ℹ fail 0   ℹ cancelled 0
```

Полный вывод обоих прогонов (с восстановлением файлов между ними) —
[`evidence-before-after.txt`](evidence-before-after.txt).

## 4. Проверки

Выполнены ровно гоняемые CI-проверки (AGENTS.md § «Обязательная проверка после изменения»):

| Проверка | Команда | Результат |
|---|---|---|
| webvue3 unit | `cd webvue3 && npm run test:unit` | PASS — 127/127 |
| ESLint (baseline) | `bash tools/check-eslint-baseline.sh webvue3` | PASS — 0 текущих, 0 в baseline, новых нет |
| Prettier (strict) | `cd webvue3 && npx prettier --check "src/**/*.{vue,js,ts,json}"` | PASS — all matched files |
| karaoke-app tests | `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew --max-workers=2 -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" -Djava.awt.headless=true :karaoke-app:test` | BUILD SUCCESSFUL (FROM-CACHE) |
| karaoke-web tests | то же с `:karaoke-web:test` | BUILD SUCCESSFUL |
| ktlint | `./gradlew … ktlintCheck` | BUILD SUCCESSFUL |
| Knowledge structure | `bash tools/check-knowledge-structure.sh` | OK — 9/9 |
| Knowledge lint | `python3 tools/lint-knowledge.py --baseline config/knowledge/baseline-knowledge-lint.txt` | PASS — new violations нет |
| Doc references | `python3 tools/check-doc-references.py` | PASS — 4199 ссылок, 2370 путей, 319 эндпоинтов, 0 находок |
| MP4 mentions | `bash tools/check-no-mp4-mentions.sh` | OK — 130 упоминаний в baseline |
| Feature docs | `bash tools/check-feature-doc.sh docs/features/*.md` | PASS — все документы валидны |
| Subagent isolation | `bash tools/check-subagent-isolation.sh` | PASS в CI-эквивалентных условиях; локально — см. §5 |

## 5. Известные проблемы проверок

**`check-subagent-isolation.sh` локально падает не из-за этого пакета.** Guard сравнивает
`master..HEAD`, а локальная ветка `master` в этом репозитории отстала на 59 коммитов от
`origin/master` (главная копия на `master`, `## master...origin/master [позади 59]`). Поэтому
«своими» считаются merge-коммиты, уже влитые в master (#678–#682), а они достижимы и из других
PR-веток — guard видит «пересечение». Ветку `master` я не двигал: главная копия checked out на
ней, а её изменение рассинхронизировало бы рабочий каталог.

Проверка повторена в CI-эквивалентных условиях — временный `--shared`-клон ветки, локальный
`master` которого указывает на `origin/master` (`f5da6c18`):

```text
$ git clone --shared --branch 590-pkg-race-target <worktree> /tmp/…/repo
$ git update-ref refs/heads/master f5da6c18
$ bash tools/check-subagent-isolation.sh
🔍 Subagent isolation check for branch: 590-pkg-race-target
✅ Все commits в ветке 590-pkg-race-target уникальны (нет race condition)
exit=0
```

## 6. Что за владельцем

- **Push ветки и PR** — не делал (запрет задачи); ветка `590-pkg-race-target` с коммитом
  `c688ee49` ждёт push и PR в `master`.
- **Merge и CI на GitHub** — за владельцем; локальные проверки зелёные.
- **Пересборка/деплой админ-SPA** (`webvue3`) — не делал: изменения фронтовые, но выкатка
  требует сборки образа и согласия владельца (AGENTS.md § Build/Deploy).
- **`specs/336-code-audit/registry.csv`** — не трогал (запрет). Статусы BR-397/BR-407/BR-409
  остаются `TODO`; обновить после merge.
- **Ревью** — `mark-review 367` выставлен после `add-comment`.
- **Закрытие #367** — после ревью владельца.

## 7. Хэши

| Что | Хэш |
|---|---|
| Коммит с правкой, тестами и документацией | `c688ee49` |
| Отчёт (этот файл) + evidence | следующий коммит ветки (см. `git log -1 -- specs/367-pkg-race-target/report.md`) |

База ветки: `07126462` (origin/master на момент создания; текущий origin/master — `f5da6c18`,
на 2 коммита новее, пересечений с изменениями пакета нет).
