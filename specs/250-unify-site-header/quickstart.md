# Quickstart: 250 — Унификация шапки сайта

**Branch**: `250-unify-site-header` | **Date**: 2026-08-27
**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

## Prerequisites

- Node 22 (LTS), npm.
- Работающий `karaoke-public` dev-server (`npm run dev` в `karaoke-public/`).
- Тестовая БД + mock-данные (как минимум: 1 автор с песнями, 1 залогиненный пользователь для проверки `AuthStatusWidget`).
- 5-10 минут на manual walk-through по 16 страницам.

## Setup

```bash
# 1. Уже на ветке 250-unify-site-header (созданной /speckit.specify)
git branch --show-current
# Ожидаемый вывод: 250-unify-site-header

# 2. Запустить dev-server
cd karaoke-public
npm run dev
# Ожидаемый URL: http://localhost:5173

# 3. Проверить, что <AppHeader> существует
ls karaoke-public/src/components/AppHeader.vue
# Ожидаемый вывод: файл существует

# 4. Проверить, что views мигрированы
grep -l "AppHeader" karaoke-public/src/views/*.vue | wc -l
# Ожидаемый вывод: 16 (или ≥14 — минимум, исключая PlayerView/ShareView/SubscriptionReturnView/EditorWorkView)

# 5. Проверить, что CSS-дубли удалены
grep -rn "\.km-header\|\.km-back\|\.km-logo" karaoke-public/src/views/ --include="*.vue"
# Ожидаемый вывод: пусто (только в AppHeader.vue, не в views)
```

## Smoke-Test Scenarios

### Scenario 1 — Логотип всегда справа и кликабельный

**Цель**: проверить SC-003 (кликабельность) + SC-004 (положение справа).

**Шаги**:
1. Открыть `http://localhost:5173/` (Home).
2. **Ожидаемо**: логотип `KARAOKE_LOGO.png` в правой части шапки, обёрнут в `<a>`/`<RouterLink>` с `href="/"`.
3. Кликнуть на логотип → переход на `/` (no-op на Home, но ссылка должна сработать).
4. Перейти на `/zakroma`.
5. **Ожидаемо**: логотип снова справа, кликабельный.
6. Повторить для 3 страниц: `/about`, `/account/playlists`, `/song/<id>`.

**Acceptance**:
- [ ] Логотип на всех 5 страницах находится в правой части шапки (визуально).
- [ ] DOM содержит `<a href="/">` или `<a class="..." href="/">` вокруг `<img class="km-logo">`.
- [ ] Alt-текст логотипа — «Своё Место» (или эквивалент).

### Scenario 2 — Back-ссылки соответствуют контексту

**Цель**: проверить FR-004..FR-010 (типизированные back-ссылки).

**Шаги**:

| Страница | Ожидаемая back-ссылка | Ожидаемый target |
|----------|----------------------|------------------|
| `/` | (нет) | — |
| `/zakroma` | «← Главная» | `/` |
| `/about` | «← Главная» | `/` |
| `/news` | «← Главная» | `/` |
| `/premium` | «← Главная» | `/` |
| `/login` | «← Главная» | `/` |
| `/register` | «← Главная» | `/` |
| `/oferta` | «← Главная» | `/` |
| `/account/playlists` | «← Главная» | `/` |
| `/account/playlists/<id>/edit` | «← Мои плейлисты» | `/account/playlists` |
| `/account/editor` | «← Личный кабинет» | `/account` |
| `/account/editor/<id>` | «← Мои задания» | `/account/editor` |
| `/song/<id>` | «← Назад» или «← Закрома» | `/zakroma` |
| `/author-playlist/<slug>` | «← Закрома» (с `?author=<slug>`) | `/zakroma?author=<slug>` |
| `/cart` | «← Главная» | `/` |
| `/premium/stems` | «← Личный кабинет» | `/account` |
| `/account/subscriptions` | «← Личный кабинет» | `/account` |
| `/chat` | «← Главная» | `/` + **Профиль →** в правом слоте |

**Acceptance**:
- [ ] Каждая back-ссылка соответствует ожидаемой.
- [ ] Клик по каждой back-ссылке ведёт на ожидаемый URL.

### Scenario 3 — Profile-ссылка в правом слоте

**Цель**: проверить FR-005, FR-007 (profile-link на PlaylistsView и ChatView).

**Шаги**:
1. Залогиниться (test user).
2. Перейти на `/account/playlists`.
3. **Ожидаемо**: в правом слоте шапки — ссылка «Профиль →» (ведёт на `/account`).
4. Перейти на `/chat`.
5. **Ожидаемо**: в правом слоте — «Профиль →» + AuthStatusWidget + theme toggle + logo.
6. Перейти на `/zakroma`.
7. **Ожидаемо**: profile-link нет (только AuthStatusWidget + theme toggle + logo).

**Acceptance**:
- [ ] «Профиль →» присутствует на `/account/playlists` и `/chat`.
- [ ] «Профиль →» отсутствует на остальных страницах.
- [ ] «Профиль →» ведёт на `/account`.

### Scenario 4 — Editor-страницы без виджетов

**Цель**: проверить FR-014, FR-015 (editor — без AuthWidget и theme toggle).

**Шаги**:
1. Залогиниться как editor (test user с правами редактора).
2. Перейти на `/account/editor` (список заданий).
3. **Ожидаемо**: back-ссылка «← Личный кабинет» слева, **нет** AuthStatusWidget, **нет** theme toggle, лого справа.
4. Перейти на `/account/editor/<id>` (конкретное задание).
5. **Ожидаемо**: «← Мои задания» слева, **заголовок песни + автор в центре**, статус-бейдж справа, **нет** логотипа (или лого без ссылки).

**Acceptance**:
- [ ] `/account/editor`: только back-ссылка + лого.
- [ ] `/account/editor/<id>`: специализированный header (центр + бейдж), без лого.

### Scenario 5 — Sticky-поведение

**Цель**: проверить FR-012 (sticky на всех страницах).

**Шаги**:
1. Открыть `/filter` (длинный список фильтров).
2. Проскроллить вниз.
3. **Ожидаемо**: шапка остаётся наверху (sticky).
4. Повторить для `/zakroma` (длинный список авторов).
5. Открыть `/about` (короткий контент).
6. **Ожидаемо**: sticky работает, но не заметен (контент короткий).

**Acceptance**:
- [ ] Шапка плавает при скролле на `/filter`, `/zakroma`, `/song/<id>`.
- [ ] DOM содержит `class="km-header km-header-sticky"` (или inline `position: sticky`).

### Scenario 6 — Live-обновление premium (регрессия LiveDoc 162)

**Цель**: проверить SC-007 (live premium не сломан).

**Шаги**:
1. Залогиниться как premium user.
2. Открыть `/` — **ожидаемо**: значок 🪙 Premium в `AuthStatusWidget`.
3. Перейти на `/zakroma` — **ожидаемо**: значок 🪙 виден в правом слоте.
4. Симулировать окончание подписки (через админку или БД).
6. Подождать ≤90 секунд (поллинг `usePremiumLiveSync`).
7. **Ожидаемо**: значок 🪙 исчезает в `AuthStatusWidget` на любой странице.

**Acceptance**:
- [ ] Live-логика работает (значок появляется/исчезает live).
- [ ] Поведение идентично pre-рефакторингу.

### Scenario 7 — Theme toggle

**Цель**: проверить A-005 (theme toggle везде с `<AppHeader>`, кроме editor).

**Шаги**:
1. Открыть `/` — кликнуть 🌙 → ожидаемо: тёмная тема.
2. Перейти на `/zakroma` — **ожидаемо**: тема осталась тёмная (state персистится в localStorage).
4. Перейти на `/account/editor` — **ожидаемо**: theme toggle отсутствует.
5. Перейти на `/account/editor/<id>` — **ожидаемо**: theme toggle отсутствует.

**Acceptance**:
- [ ] Theme toggle работает на 16 страницах с `<AppHeader>`.
- [ ] Theme toggle отсутствует на editor-страницах.
- [ ] Тема персистится между переходами (localStorage).

### Scenario 8 — CSS-duplicate check

**Цель**: проверить SC-002 (нет CSS-дублей).

```bash
grep -rn "\.km-header\|\.km-back\|\.km-logo" karaoke-public/src/views/ --include="*.vue"
# Ожидаемый вывод: пусто

grep -rn "\.km-header\|\.km-back\|\.km-logo" karaoke-public/src/components/AppHeader.vue
# Ожидаемый вывод: стили есть (в <style scoped>)
```

**Acceptance**:
- [ ] Все стили шапки — только в `AppHeader.vue`.
- [ ] View-файлы не имеют `<style scoped>` блоков с `.km-header*`, `.km-back`, `.km-logo`.

### Scenario 9 — Author-playlist query preservation

**Цель**: проверить A-009 (back сохраняет `?author=<slug>`).

**Шаги**:
1. Открыть `/zakroma`.
2. Проскролтить к середине списка авторов (где есть `<slug>`).
3. Кликнуть на автора → переход на `/author-playlist/<slug>`.
4. Проскролтить.
5. Кликнуть «← Закрома».
6. **Ожидаемо**: возврат на `/zakroma?author=<slug>` с той же позицией скролла (если поддерживается).

**Acceptance**:
- [ ] URL после клика содержит `?author=<slug>`.
- [ ] Позиция скролла восстановлена (если браузер поддерживает scroll-restoration).

### Scenario 10 — Player/Share/SubscriptionReturn без шапки

**Цель**: проверить A-002 (эти страницы не получают `<AppHeader>`).

**Шаги**:
1. Открыть `/player/<id>` (или создать тестовую ссылку).
2. **Ожидаемо**: нет `<header class="km-header">` в DOM (full-screen).
3. Открыть `/share/<token>/<secret>`.
4. **Ожидаемо**: нет шапки (минималистичная страница).
5. Открыть `/premium/return` (после оплаты).
6. **Ожидаемо**: нет шапки.

**Acceptance**:
- [ ] Три страницы рендерят без `<AppHeader>`.

## Automated Verification (опционально)

Если есть время — добавить простой grep-test в CI:

```bash
# tools/check-unified-site-header.sh
FAILED=0

# SC-001: ≥14 view используют <AppHeader>
COUNT=$(grep -l "AppHeader" karaoke-public/src/views/*.vue | wc -l)
if [ "$COUNT" -lt 14 ]; then
  echo "FAIL: только $COUNT view используют <AppHeader> (ожидаемо ≥14)"
  FAILED=1
fi

# SC-002: 0 CSS-дублей в views
DUPES=$(grep -rn "\.km-header\|\.km-back\|\.km-logo" karaoke-public/src/views/ --include="*.vue")
if [ -n "$DUPES" ]; then
  echo "FAIL: CSS-дубли в views:"
  echo "$DUPES"
  FAILED=1
fi

# SC-008: ≥50 строк удалено (heuristic)
VIEW_LINES_BEFORE=... # baseline
VIEW_LINES_AFTER=$(wc -l karaoke-public/src/views/*.vue | tail -1)
APP_HEADER_LINES=$(wc -l karaoke-public/src/components/AppHeader.vue | awk '{print $1}')
# Если VIEW_LINES_BEFORE зафиксирован — сравнить.

exit $FAILED
```

## Acceptance (mapping to Success Criteria)

| SC | Verification |
|----|--------------|
| SC-001 | Scenario 1, 2 (AppHeader используется в ≥14 view) |
| SC-002 | Scenario 8 (grep на CSS-дубли) |
| SC-003 | Scenario 1 (лого кликабельный → /) |
| SC-004 | Scenario 1 (лого справа) |
| SC-005 | Scenario 4 (editor без виджетов) |
| SC-006 | Все 10 сценариев проходят вручную |
| SC-007 | Scenario 6 (live premium не сломан) |
| SC-008 | Code review: ≥200 строк удалено, ≤150 добавлено |

## Rollback

Если что-то сломалось:
1. `git revert HEAD` (один коммит с рефакторингом).
2. `cd karaoke-public && npm run dev` — старое поведение восстановлено.

## Next Step

→ `/speckit.tasks specs/250-unify-site-header` для генерации декомпозированных задач.