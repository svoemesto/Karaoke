# Quickstart: проверка 562 — экранирование пользовательских данных в подтверждениях админки

Документ — руководство по проверке, а не инструкция реализации. Код живёт в `tasks.md`.

## Предпосылки

- Репозиторий: `/home/nsa/Karaoke/.worktrees/impl360`, ветка `562-impl-xss-escape`.
- Node.js 22+ (для `node --test` и `npm run lint`/`build` в `webvue3`).
- Локальный стенд и БД **не требуются**: проверка идёт через статический анализ
  кода (guard) и unit-тесты.

## Проверка 1. Guard находит дефект на коде ДО правки

```bash
cd /home/nsa/Karaoke/.worktrees/impl360
mkdir -p .scratch/orig && git archive master webvue3/src | tar -x -C .scratch/orig
python3 tools/check-confirm-escape.py --src .scratch/orig/webvue3/src
```

Ожидаемо: **exit 1**, и в выводе — находки R3 по всем 43 местам:

| Файл | Находок R3 |
| --- | --- |
| `components/Songs/edit/SongEdit.vue` | 25 (имя песни) |
| `views/HomeView.vue` | 13 (имя автора — 5, слово и словарь — 8) |
| `components/SiteUsers/edit/SiteUserEdit.vue` | 3 (e-mail) |
| `components/Songs/edit/SubsEdit.vue` | 2 (выделенный текст песни) |
| `components/Common/SmartCopy/SmartCopyModal.vue` | 2 (шаблон переименования, путь копирования) |
| `components/Pictures/edit/PictureEdit.vue` | 1 (имя картинки) |
| `components/Sync/SyncTable.vue` | 3 (отчёт синхронизации) |

Плюс R1 (компонент принимает сырую разметку по умолчанию) и R2 (нет
`allowHtml: true`).

## Проверка 2. Guard проходит на коде ПОСЛЕ правки

```bash
cd /home/nsa/Karaoke/.worktrees/impl360
python3 tools/check-confirm-escape.py
```

Ожидаемо: **exit 0**, «Нарушений: 0», 137 блоков подтверждений, из них 95 в
HTML-режиме.

## Проверка 3. «До/после» для самого guard'а

```bash
cd /home/nsa/Karaoke/.worktrees/impl360
bash tools/check-confirm-escape-fixtures.sh
```

Ожидаемо: **exit 0** и пять строк `OK:` — уязвимый снимок кода отклонён,
исправленный принят, в репозитории нарушений нет.

## Проверка 4. Регресс-контроль экранирования (прецедент спеки #215)

```bash
cd /home/nsa/Karaoke/.worktrees/impl360/webvue3
npm run test:unit
```

Ожидаемо: **39 тестов, 0 падений**, в том числе:

- «значение без спецсимволов совпадает с базовой линией побайтово» — зелёный
  (baseline не изменился);
- «одиночный `&` экранируется ровно один раз (нет `&amp;amp;`)` — зелёный
  (двойного экранирования нет);
- «null/undefined не роняют» — зелёный.

## Проверка 5. Общий компонент: текст по умолчанию, HTML — только явно

```bash
cd /home/nsa/Karaoke/.worktrees/impl360
sed -n '1,12p' webvue3/src/components/Common/CustomConfirm.vue
```

Ожидаемо: рядом с каждым `v-html="params.…"` стоит `v-if="params.allowHtml"`,
а рядом с каждым `v-text="params.…"` — `v-else`. Иначе guard (R1) падает.

## Проверка 6. Оформление подтверждений не изменилось

```bash
cd /home/nsa/Karaoke/.worktrees/impl360
grep -c "allowHtml: true" webvue3/src/views/HomeView.vue webvue3/src/components/Songs/edit/SongEdit.vue
```

Ожидаемо: `HomeView.vue` — 41, `SongEdit.vue` — 25 (все блоки с разметкой
получили явный флаг; блоки без разметки остались в текстовом режиме).

Ручная проверка на стенде: открыть карточку песни → «Удалить песню» — имя
показано **буквально**, жирное начертание и рамки заголовка на месте; то же для
бана пользователя, удаления картинки, Smart Copy, запуска синхронизации.

## Проверка 7. Публичный периметр не затронут (вне охвата, но проверяем)

```bash
cd /home/nsa/Karaoke/.worktrees/impl360
grep -rn "v-html" karaoke-public/src/
grep -rn "th:utext" --include=*.html . | wc -l
```

Ожидаемо: `v-html` только к `formattedText*` (4 места), `th:utext` — **0**.

## Проверка 8. Полный набор проверок проекта

```bash
cd /home/nsa/Karaoke/.worktrees/impl360/webvue3
npm run lint
npm run build
npm run format:check
npm run test:unit
cd ..
bash tools/check-confirm-escape.sh 2>/dev/null || python3 tools/check-confirm-escape.py
bash tools/check-confirm-escape-fixtures.sh
bash tools/check-knowledge-structure.sh
python3 tools/check-doc-references.py
bash tools/check-feature-doc.sh docs/features/*.md
bash tools/check-subagent-isolation.sh
pre-commit run --all-files
```

Ожидаемо: всё зелёное. Бэкенд не затронут — gradle-проверки не требуются.

## Ручная проверка сценария «публичный пользователь → админ» (SC-006)

1. Зарегистрировать на сайте пользователя с e-mail вида `<img src=x onerror=…>@a.b`.
2. В админке открыть карточку пользователя → «Забанить».
3. Ожидаемо: e-mail показан буквально, в консоли браузера нет обращений по
   посторонним адресам, бан применяется после подтверждения как обычно.

## Обратная проверка (обязательная по FR-014)

На коде **до** правки проверка 1 обязана **падать** (exit 1) и находить все 43
перечисленных места. Если она молчит — проверка не выполняет свою задачу, и
правку нельзя считать закрытой.
