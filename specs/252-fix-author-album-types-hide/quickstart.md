# Quickstart: 252 — Закрома: корректное скрытие блока типов альбомов

**Branch**: `252-fix-author-album-types-hide` | **Date**: 2026-08-27
**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

## Назначение

Этот документ — **пошаговая валидация** фикса после правки кода в
`karaoke-public/src/views/ZakromaView.vue`. Каждый шаг проверяем
визуально + через DevTools.

Никаких серверных миграций, никаких curl-запросов, никаких unit-тестов
не требуется: фикс чисто клиентский (CSS + опционально template-обёртка).
Полный приёмочный чек-лист — в [spec.md § Acceptance](spec.md#user-scenarios--testing-mandatory)
и [spec.md § Success Criteria](spec.md#success-criteria-mandatory).

## Prerequisites

1. Машина с docker-compose стэком проекта Karaoke (см. `deploy/do.sh`),
   либо прямой `npm run dev` в `karaoke-public/` + локальный backend.
2. Node 22 LTS (требование проекта, см. Конституцию Principle V).
3. Браузер с DevTools (Chromium / Firefox).
4. Git feature-ветка `252-fix-author-album-types-hide` уже создана
   (`tools/specify-bootstrap.sh`).

## Setup

```bash
git checkout 252-fix-author-album-types-hide

# (Опционально) локальный запуск фронта без полного docker-compose
cd karaoke-public
npm install   # если ещё не установлено
npm run dev   # поднимает dev-server (обычно на :5173)

# (Альтернативно) полный docker-compose подъём через deploy/do.sh
cd ../deploy
bash do.sh build_start_public   # пересобирает только karaoke-public
```

Бэкенд `karaoke-web` поднимается отдельно (если не запущен):

```bash
cd /home/nsa/Karaoke
./gradlew :karaoke-web:bootJar --parallel
# запуск — через deploy/do.sh build_start_web или docker-compose
```

## Подготовка тестовых данных

Фичу лучше всего проверять на **крупном авторе** (≥ 1000 песен) с
**3+ типами альбомов**, чтобы блок `.km-album-controls-bar` был
визуально больше фильтра и overlap был максимально заметен.

В качестве канонических авторов подходят:

- «Машина Времени» (~2500 песен, 6 типов альбомов).
- «Кино» (~1000 песен, 3-5 типов).
- Любой автор с `SELECT author, count(*) FROM tbl_albums GROUP BY author`
  ≥ 3 типа альбомов и `count(*) FROM tbl_songs WHERE author=…` ≥ 500.

Deep-link для проверки:

```
http(s)://<host>/zakroma?author=Машина Времени
```

(пробел `%20` или `+` — оба работают; бэкенд `MainController.zakroma`
декодирует query).

## Сценарии валидации

### V-1: Исходное состояние (no scroll)

1. Открыть `/zakroma?author=Машина Времени` в браузере.
2. **Ожидание (до фикса)**: сверху `AppHeader`, ниже — поле быстрого
   фильтра, ниже — блок типов альбомов; обе панели видны целиком.
3. **Ожидание (после фикса)**: то же самое; никакого визуального
   изменения в положении при `scrollY = 0`.

### V-2: Скролл вниз 800px (desktop, основной репро бага)

1. На той же странице прокрутить окно до `scrollY ≈ 800`
   (PageDown ~15 раз, или `document.documentElement.scrollTop = 800`
   в консоли DevTools).
2. **Ожидание (после фикса)**:
   - В верхней части viewport виден `AppHeader`.
   - Под ним — одна прилипшая полоса sticky-блока (фильтр + блок типов
     альбомов; либо в виде общего контейнера, либо как два блока без
     пересечения).
   - **Никакой** полосы-хвоста от блока типов альбомов поверх фильтра.
3. DevTools-проверка (вкладка Console):
   ```js
   const fr = document.querySelector('.km-filter-bar').getBoundingClientRect()
   const al = document.querySelector('.km-album-controls-bar').getBoundingClientRect()
   // Должно выполняться (нет вертикального overlap)
   console.log('filter bottom', fr.bottom)
   console.log('album top', al.top)
   // Либо al.top === fr.bottom (без overlap),
   // либо al.bottom <= fr.top (блок типов уехал за viewport).
   // НИКОГДА: al.top < fr.bottom && al.bottom > fr.top
   ```

### V-3: Скролл на самый верх (re-check)

1. `scrollY = 0` (`window.scrollTo(0, 0)` или Home).
2. **Ожидание**: оба блока видны в нормальных in-flow позициях,
   без артефактов.

### V-4: Mobile viewport (375×667)

1. DevTools → Toggle device toolbar → iPhone SE / iPhone 12 mini.
2. Открыть `/zakroma?author=Машина Времени` (6 типов альбомов,
   `flex-wrap` сделает блок типов 2-строчным).
3. Прокрутить на 400px вниз.
4. **Ожидание**:
   - Либо обе строки блока типов видны вместе с фильтром (как
     единая sticky-полоса),
   - либо вся полоса уехала за viewport целиком,
   - **никогда** не видно одной строки блока типов поверх фильтра
     (FR-007).

### V-5: Стрим (если большой автор + медленный backend)

1. Открыть `/zakroma?author=…` где есть `.km-stream-progress`
   во время загрузки.
2. Прокрутить.
3. **Ожидание**: прогресс-бар остаётся под `km-author-header-sticky`
   обёрткой (или под фильтром, если FR-002). Никаких хвостов от блока
   типов поверх прогресс-бара (FR-005).

## Проверка линтеров и сборки

После правки CSS:

```bash
# Линтер фронта
cd karaoke-public
npm run lint

# ESLint-baseline (запрет на новые нарушения)
bash tools/check-eslint-baseline.sh karaoke-public

# Сборка фронта
npm run build
```

**Ожидание**: PASS, 0 warnings, 0 новых нарушений baseline.

## Бэкенд-проверка (должна показать «не задет»)

```bash
cd /home/nsa/Karaoke
./gradlew :karaoke-app:bootJar :karaoke-web:bootJar --parallel
```

**Ожидание**: `UP-TO-DATE` (или краткая компиляция без warning'ов).
Никакие Kotlin-файлы не менялись.

## Edge-кейсы (после фикса)

| Кейс | Где проверить | Ожидание |
|------|----------------|----------|
| Автор с 1 типом альбомов | Любой, в т.ч. автор с одной студией | Блок типов — одна кнопка; фикс работает (no overlap). |
| Resize (десктоп drag-to-resize) | Имитировать resize от 1280 до 900 ширины | Sticky-поведение пересчитывается; nothing broken. |
| Стрим активен | Backend с задержкой / devtools throttle | Прогресс-бар под обёрткой; никаких хвостов. |
| При первом маунте `scrollY > 0` (F5 в середине страницы) | Перезагрузка по URL `/zakroma?author=…` после скролла | Блоки `AppHeader` + обёртка прилипают; ничего не «торчит». |

## Куда смотреть, если что-то не так

| Симптом | Возможная причина | Действие |
|---------|------------------|----------|
| Хвост блока типов всё ещё виден поверх фильтра | Применён FR-002, но `top: calc(53px + 50px)` меньше фактической высоты фильтра | Измерить фильтр в DevTools (`getBoundingClientRect().height`), заменить `50px` на фактическое значение (или ввести CSS-переменную `--km-filter-bar-height`). |
| Контейнер «дёргается» при скролле | Смешанный sticky: один блок в обёртке, другой — нет | Унифицировать: либо **оба** в обёртке (FR-004), либо **оба** вне обёртки (FR-002, одинаковый `top`-механизм). |
| На мобильном 2-строчный блок обрезается по границе | Применён FR-002 и `top` слишком мал | Проверить `getBoundingClientRect().height` при flex-wrap=2 строки; либо перейти на FR-004. |
| Прогресс-бар оказался выше блоков фильтра/типов | Кто-то поменял `z-index` `.km-stream-progress` | Откатить z-index `.km-stream-progress { z-index: 50 }` (спек 181). |

## После успешной валидации

1. Убедиться, что `git status` показывает изменения только в
   `karaoke-public/src/views/ZakromaView.vue` (template + scoped CSS)
   и в `specs/252-fix-author-album-types-hide/*`.
2. `git diff --stat` — без строк-однострочных изменений по всему
   проекту (`.gitattributes` корректно нормализует line endings).
3. `pre-commit` хуки (FR-007 Конституции) сами прогонят ktlint/eslint
   + секрет-чек.
4. PR в master через `gh pr create --base master` (см. AGENTS.md,
   «CI-gate для master»).
5. CI (`.github/workflows/lint.yml`) должен пройти PASS; после — merge.

## Документация

После merge — обновить `livedocs/features/012-entity-description-fields.md`
секцией «Известные баг-фиксы» (или ссылкой на этот LiveDoc), плюс
добавить/обновить feature-doc FR-009 (Конституция Principle VI).
