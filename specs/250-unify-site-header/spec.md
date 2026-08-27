# Feature Specification: Унификация шапки сайта

**Feature Branch**: `250-unify-site-header`
**Created**: 2026-08-27
**Status**: Draft
**Input**: User description: "Задача - унифицировать шапку сайта. Сейчас она выглядит по-разному для разных страниц. На главной странице в шапке - превью лого сайта слева, просто картинка. На других страницах перед превью лого сайта слева появляется ссылка на главную страницу и сама картинка становится кликабельной ссылкой на главную. На странице 'Избранное и плейлисты' появляется ссылка на профиль. На странице избранного или плейлиста картинка превью лого сайта в шапке уезжает вправо. Предлагаю так: превью лого сайта в шапке всегда справа, всегда кликабельна и ведёт на главную страницу. В слева в шапке (по необходимости) появляются ссылки типа 'Назад', 'К плейлитам', 'В закрома' и т.п. Так же готов выслушать твои предложения."

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Единая шапка на всех страницах сайта (Priority: P1)

Посетитель публичного сайта переходит между страницами (`/`, `/zakroma`, `/filter`, `/song/<id>`, `/about`, `/news`, `/account`, `/account/playlists`, `/account/playlists/<id>`, `/chat`, `/premium`, `/subscriptions`, `/premium/stems`, `/cart`, `/login`, `/register`, `/oferta`). На каждой странице он видит **одну и ту же структуру шапки**: слева — навигационные ссылки (или пусто), справа — логотип, всегда кликабельный и ведущий на `/`. Расположение логотипа и поведение ссылок не меняются от страницы к странице.

**Why this priority**: текущая шапка дублируется в каждом view-файле с разметкой «как придумал автор» (лого слева, справа, в одной обёртке с back-ссылкой, без ссылки и т.п.). Это создаёт визуальный шум при навигации и ~140 строк дублирующейся CSS/разметки на каждый view. Главная цель рефакторинга — единообразие UX (пользователь не переучивается на каждой странице) и устранение дублей.

**Independent Test**: открыть в браузере любые две страницы (например, `/` и `/zakroma`) — DOM-структура `<AppHeader>` идентична: тот же порядок слотов (left → center? → logo), тот же CSS-класс, тот же alt у `<img>`, та же ссылка-обёртка вокруг лого.

**Acceptance Scenarios**:
1. **Given** пользователь на любой странице публичного сайта, **When** он смотрит на шапку, **Then** логотип `KARAOKE_LOGO.png` расположен справа, обёрнут в `<RouterLink to="/">` (или `<a href="/">`), имеет `alt="Своё Место"` (или эквивалент для a11y).
2. **Given** пользователь на любой странице, **When** он кликает по логотипу, **Then** он попадает на `/` (главную).
3. **Given** пользователь на странице без «родительской» страницы (например, `/`), **When** он смотрит на левый слот шапки, **Then** слот пуст (нет back-ссылок).
4. **Given** пользователь на странице второго уровня (например, `/zakroma`, `/about`, `/news`, `/premium`, `/login`, `/register`, `/oferta`), **When** он смотрит на левый слот, **Then** там одна ссылка «← Главная», ведущая на `/`.
5. **Given** пользователь на странице третьего уровня (например, `/author-playlist/<slug>`, `/playlist/<id>/edit`), **When** он смотрит на левый слот, **Then** там ссылка на непосредственного «родителя» (например, «← Закрома», «← Мои плейлисты»).
6. **Given** пользователь на странице из «Избранного и плейлистов» (`/account/playlists`, `/chat`), **When** он смотрит на правый слот, **Then** рядом с логотипом есть ссылка «Профиль →», ведущая на `/account`.
7. **Given** пользователь на любой странице (включая `/`), **When** он смотрит на область рядом с логотипом, **Then** там виджет авторизации (`AuthStatusWidget`) и переключатель темы (light/system/dark). На страницах редактора (`/account/editor`, `/account/editor/<id>`) — без виджета/темы (это рабочий инструмент, а не витрина).

---

### User Story 2 — Единый CSS-стиль шапки (Priority: P2)

Разработчик заходит в любой view-файл публичного сайта и видит: 1) `<AppHeader>`-компонент с пропами (например, `:back="{ to: '/', label: '← Главная' }"` и опциональный `profile`), 2) ни одного блока `.km-header`, `.km-header-inner`, `.km-header-left`, `.km-back`, `.km-logo` в `<style scoped>`. Стили шапки живут в одном месте — `<AppHeader>.vue` или общем CSS-модуле.

**Why this priority**: дубли стилей — технический долг. 18 view-файлов × ~20 строк CSS шапки = ~360 строк копипасты, каждое расхождение (отступ, sticky-позиция, z-index) — потенциальный регресс. Унификация стиля устраняет долг за один PR.

**Independent Test**: `grep -rn "km-header\|km-back\|km-logo" karaoke-public/src/views/` — возвращает только `<template>`-блоки, использующие `<AppHeader>`. `grep -rn "\.km-header\|\.km-back\|\.km-logo" karaoke-public/src/views/` — возвращает пусто (стили ушли из view).

**Acceptance Scenarios**:
1. **Given** рефакторинг выполнен, **When** разработчик открывает любой view, **Then** блок `<header class="km-header">` заменён на `<AppHeader ... />` с пропами.
2. **Given** рефакторинг выполнен, **When** разработчик ищет `.km-header` / `.km-back` / `.km-logo` в `karaoke-public/src/views/`, **Then** находит 0 вхождений в `<style scoped>`.
3. **Given** стили шапки переехали в `<AppHeader>.vue`, **When** правится внешний вид шапки (например, отступ), **Then** изменение применяется ко всем страницам одновременно без правок в view-файлах.

---

### User Story 3 — Специализированные шапки редактора (Priority: P3)

Пользователь-редактор на странице `/account/editor/<id>` (работа над заданием) видит специализированную шапку с заголовком (имя песни + автор) и статус-бейджем (`В работе`/`Готово`/`Отменено`). Эта шапка остаётся как особый случай — она не соответствует обычному паттерну «back слева, лого справа», потому что центральный слот занят контекстом задания.

**Why this priority**: EditorWorkView — рабочий инструмент, а не витрина. Его шапка должна показывать максимум контекста (песня, статус), а не быть «как у всех». Унификация здесь может ухудшить UX (мелкий заголовок в шапке хуже читается, чем большой в контенте).

**Independent Test**: открыть `/account/editor/<id>` — заголовок песни и бейдж статуса видны, логотип справа не отображается (или отображается без ссылки, чтобы не сбивать с задания). На `/account/editor` (список заданий) — обычный `<AppHeader>` с back-ссылкой на «← Личный кабинет».

**Acceptance Scenarios**:
1. **Given** пользователь на `/account/editor/<id>`, **When** он смотрит на шапку, **Then** видит: back «← Мои задания» слева, заголовок (имя песни + автор) в центре, статус-бейдж справа. Логотип либо отсутствует, либо максимально ненавязчив.
2. **Given** пользователь на `/account/editor`, **When** он смотрит на шапку, **Then** видит стандартный `<AppHeader>` с back-ссылкой «← Личный кабинет».

---

### Edge Cases

- **PlayerView (`/player/<id>`), ShareView (`/share/<token>`), SubscriptionReturnView (`/premium/return`)** — текущие view-файлы **не имеют шапки** (full-screen / минималистичный layout). Нужно ли добавлять `<AppHeader>` на них? Решение: **не добавлять** — это специальные лендинг/полноэкранные страницы, шапка там неуместна (см. Assumptions).
- **Главная (`/`) сейчас НЕ имеет back-ссылки** и логотип НЕ кликабельный. После унификации логотип становится ссылкой на `/` — это no-op, но требует сохранить текущий alt и class.
- **CartView (`/cart`), StemJobsView (`/premium/stems`), SubscriptionsView (`/account/subscriptions`)** — сейчас имеют только left-слот, без лого. После унификации лого появляется справа.
- **ChatView (`/chat`)** — имеет back + лого слева + «Профиль →» справа. После унификации лого должно переехать в правый слот, «Профиль →» остаётся рядом с лого (порядок: «Профиль →» ... лого, или лого ... «Профиль →» — выбрать единообразно).
- **PlaylistEditView (`/account/playlists/<id>/edit`)** — единственный случай, где лого уже справа. После унификации порядок ссылок должен соответствовать новой модели (back «← Мои плейлисты» слева, лого справа).
- **Sticky-поведение**: текущая шапка на SearchView/SongView — `position: sticky; top: 0` (плавающая при скролле), на остальных — статичная. Требуется ли sticky везде? Решение: **да, шапка должна быть sticky на всех страницах** (кроме EditorWorkView, где sticky-top уже есть) — улучшает навигацию при длинном скролле.
- **Theme toggle**: сейчас есть на `/` (HomeView), `/filter` (SearchView), `/song/<id>` (SongView). На остальных — нет. Унификация: добавить везде, где есть `<AppHeader>`, **кроме** editor-страниц.
- **AuthStatusWidget**: сейчас только на `/` (HomeView) и `/filter` (SearchView). На остальных — нет. Унификация: добавить везде, где есть `<AppHeader>`, **кроме** editor-страниц (там же нет theme toggle).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Должен существовать Vue-компонент `<AppHeader>` в `karaoke-public/src/components/AppHeader.vue` (или рядом), который рендерит стандартную шапку с тремя слотами: `left` (навигационные ссылки), `center` (опциональный контент, например заголовок песни), `right` (опциональный контент, например ссылка «Профиль →»), и всегда — логотип справа, обёрнутый в `<RouterLink to="/">` с `alt="Своё Место"` (или `alt="Karaoke logo"`).
- **FR-002**: `<AppHeader>` MUST принимать props для типовых сценариев (например, `back: { to: string, label: string } | null` и `profile: boolean` или `profileLink: { to: string, label: string } | null`) и рендерить соответствующие ссылки. Альтернативно — props через slot-ы (на усмотрение реализации; важно — единый API).
- **FR-003**: На `/` (HomeView) логотип MUST оставаться видимым (как сейчас), стать кликабельной ссылкой на `/` (no-op, но унифицировано). Виджет `AuthStatusWidget` и theme toggle уже есть — должны быть перенесены в правый слот `<AppHeader>`.
- **FR-004**: На страницах `/zakroma`, `/about`, `/news`, `/premium`, `/login`, `/register`, `/oferta`, `/search`, `/account`, `/account/editor` (список заданий) — `<AppHeader>` с `back = { to: '/', label: '← Главная' }` (для публичных) или `back = { to: '/account', label: '← Личный кабинет' }` (для `/account/editor`). Виджеты — справа.
- **FR-005**: На странице `/account/playlists` (`PlaylistsView`) — `<AppHeader>` с `back = { to: '/', label: '← Главная' }` и `profileLink = { to: '/account', label: 'Профиль →' }` в правом слоте (между виджетами и лого или после лого — выбрать единообразно).
- **FR-006**: На странице `/account/playlists/<id>/edit` (`PlaylistEditView`) — `<AppHeader>` с `back = { to: '/account/playlists', label: '← Мои плейлисты' }`.
- **FR-007**: На странице `/chat` — `<AppHeader>` с `back = { to: '/', label: '← Главная' }` и `profileLink = { to: '/account', label: 'Профиль →' }` в правом слоте.
- **FR-008**: На странице `/song/<id>` (`SongView`) — `<AppHeader>` с `back = { to: '/zakroma', label: '← Назад' }` (текущее поведение: возврат на «Закрома»). **Альтернативный вариант**: `back = { to: '/zakroma', label: '← Закрома' }` — единообразная формулировка.
- **FR-009**: На странице `/author-playlist/<slug>` (`AuthorPlaylistView`) — `<AppHeader>` с `back = { to: '/zakroma', label: '← Закрома' }`. Query-параметр `?author=<slug>` в back-ссылке (для возврата на ту же позицию скролла) — **сохранить** текущее поведение, если реализуемо.
- **FR-010**: На странице `/cart` (`CartView`), `/premium/stems` (`StemJobsView`), `/account/subscriptions` (`SubscriptionsView`) — `<AppHeader>` с back-ссылкой (`'← Личный кабинет'` для стемов и подписок; для `/cart` — обсудить с пользователем, по умолчанию `'← Главная'` или без back).
- **FR-011**: На странице `/account/editor/<id>` (`EditorWorkView`) — **специализированный header** остаётся: «← Мои задания» слева, заголовок песни + автор в центре, статус-бейдж справа. **Логотип не отображается** (или отображается без ссылки) — это рабочий инструмент, навигация на главную не нужна во время разметки. Альтернатива — refactor на `<AppHeader>` с использованием slot-ов (center для заголовка, right для бейджа); решение — на этапе plan.
- **FR-012**: `<AppHeader>` MUST быть `position: sticky; top: 0; z-index: 100` (или эквивалент) — плавающая шапка на всех страницах (текущее поведение SearchView/SongView).
- **FR-013**: Все блоки `.km-header`, `.km-header-inner`, `.km-header-left`, `.km-header-right`, `.km-back`, `.km-logo`, `.km-brand-logo` MUST быть удалены из `<style scoped>` всех view-файлов `karaoke-public/src/views/*.vue` и перенесены в `<AppHeader>.vue` (или в общий CSS-модуль). Класс `.km-brand-logo` на HomeView сохраняется как алиас для `.km-logo` (если визуально отличается) или унифицируется.
- **FR-014**: `<AppHeader>` MUST рендерить `AuthStatusWidget` и theme toggle (light/system/dark) в правом слоте **на всех страницах, кроме editor-страниц** (`/account/editor`, `/account/editor/<id>`). Editor — рабочий инструмент, тема/авторизация там не нужны.
- **FR-015**: На странице `/account/editor` (список заданий) — `<AppHeader>` с back «← Личный кабинет», **без** AuthStatusWidget и theme toggle (FR-014). Альтернативно — с виджетами, если пользователь считает это нужным; default = без виджетов на editor.
- **FR-016**: Существующая live-логика `isPremium` (LiveDoc `162-fix-header-stale-premium-status`) MUST продолжать работать без изменений — `AuthStatusWidget` реактивен на `auth.isPremium` независимо от того, где он рендерится.

### Key Entities

- **`<AppHeader>`**: Vue 3 single-file component, рендерит шапку публичного сайта. Props: `back` (объект `{ to, label }` или `null`), `profileLink` (аналогично), `showAuthWidget` (boolean, default `true`), `showThemeToggle` (boolean, default `true`), slots: `left`, `center`, `right`. Всегда рендерит логотип справа как `<RouterLink to="/">`.
- **`back` prop**: типизированная конфигурация back-ссылки (`{ to: string, label: string } | null`). Null = левый слот пуст.
- **`profileLink` prop**: типизированная конфигурация ссылки на профиль (`{ to: string, label: string } | null`). Null = нет ссылки на профиль.
- **`AuthStatusWidget`**: существующий компонент, рендерится через `<AppHeader>` (без изменений в логике).
- **Theme toggle**: существующий блок (3 кнопки light/system/dark), перенесён в `<AppHeader>`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% страниц публичного сайта, имеющих шапку (16 view-файлов), рендерят её через единый `<AppHeader>`-компонент. `grep -c "<AppHeader" karaoke-public/src/views/*.vue` ≥ 16.
- **SC-002**: 0 дублирования CSS-стилей шапки в view-файлах: `grep -rn "\.km-header\|\.km-back\|\.km-logo" karaoke-public/src/views/ --include="*.vue"` возвращает 0 вхождений в `<style scoped>`-блоках (допустимо в `<template>` если через `<AppHeader>`).
- **SC-003**: Логотип на любой странице кликабельный → `/`. Проверяется вручную: открыть 3+ страницы (`/`, `/zakroma`, `/about`), кликнуть на логотип — везде попадаем на `/`.
- **SC-004**: Логотип всегда справа. Проверяется в браузере: на любой странице сайта визуально логотип находится в правой части шапки (порядок: [left slot] ... [center slot?] ... [profile link?] ... [AuthStatusWidget] ... [theme toggle] ... [logo]).
- **SC-005**: Editor-страницы (`/account/editor`, `/account/editor/<id>`) корректно отделены: нет AuthStatusWidget, нет theme toggle на `/account/editor/<id>` (или только на нём — по решению пользователя в plan). `grep "AuthStatusWidget\|km-theme-toggle" karaoke-public/src/views/EditorWorkView.vue` возвращает 0.
- **SC-006**: Регрессионный smoke-test: после рефакторинга 5 случайных страниц (`/`, `/zakroma`, `/account/playlists`, `/song/<id>`, `/account/editor`) визуально идентичны или лучше прежнего (лого справа, back-ссылки на месте, виджеты видны). Проверяется пользователем вручную (CI тестов нет — Constitution § VI/«Рабочий процесс»).
- **SC-007**: Существующая live-логика premium-статуса не сломана: после окончания подписки 🪙-бейдж в `AuthStatusWidget` пропадает в течение ≤90 сек на любой странице (LiveDoc `162-fix-header-stale-premium-status`, AC1).
- **SC-008**: Объём кода уменьшается: суммарно удалено ≥200 строк из view-файлов (`<template>`-блоки шапки + `<style scoped>`-блоки `.km-header*`, `.km-back`, `.km-logo`), добавлено ≤150 строк в `<AppHeader>.vue` (включая KDoc/JSDoc и стили). Net: ≥50 строк удалено.

## Assumptions

- **A-001**: Scope ограничен публичным SPA `karaoke-public`. Admin SPA `webvue3` имеет свою шапку и не затрагивается.
- **A-002**: `PlayerView` (`/player/<id>`), `ShareView` (`/share/<token>`), `SubscriptionReturnView` (`/premium/return`) **не получают** `<AppHeader>` — это full-screen / минималистичные страницы, шапка там избыточна. Если пользователь в `/speckit.clarify` или `/speckit.plan` попросит — добавим.
- **A-003**: `EditorWorkView` (`/account/editor/<id>`) — специализированный header сохраняется как исключение. Центральный слот (заголовок песни + автор + статус) — критичен для UX редактирования, его унификация с обычными страницами ухудшит читаемость.
- **A-004**: Sticky-шапка (`position: sticky; top: 0`) применяется **на всех страницах**, имеющих `<AppHeader>` (кроме EditorWorkView, где sticky уже есть). Это улучшает навигацию при длинном скролле (Zакрома, плейлисты).
- **A-005**: Theme toggle и AuthStatusWidget показываются **на всех страницах с `<AppHeader>`**, кроме editor-страниц (`/account/editor`, `/account/editor/<id>`). Default для editor = без виджетов; если пользователь хочет — добавим в plan.
- **A-006**: API `<AppHeader>` — props-based (`back`, `profileLink`, `showAuthWidget`, `showThemeToggle`) + slot-ы (`left`, `center`, `right`) для нестандартных случаев. Финальный API уточняется в `/speckit.plan`.
- **A-007**: Существующая live-логика premium (`AuthStatusWidget` + `usePremiumLiveSync`) не меняется — `<AppHeader>` лишь оборачивает виджет в свой DOM.
- **A-008**: `KARAOKE_LOGO.png` — единственный логотип, мультиязычность/мультибренд не в скоупе. Если в будущем появится второй логотип — `<AppHeader>` примет `logoSrc` prop (сейчас hardcoded).
- **A-009**: Поддержка `?author=<slug>` query в back-ссылке `AuthorPlaylistView` — best-effort: если router поддерживает `query` в `<RouterLink>`, сохраняем; если нет — back-ссылка без query (пользователь попадёт на `/zakroma` с начала списка).
- **A-010**: Mobile/responsive-поведение шапки — наследуется текущее (нет специальной mobile-логики, кроме `flex` + `wrap` где было). Если в будущем потребуется бургер-меню — отдельная задача.
- **A-011**: Тесты в CI нет (Constitution § «Рабочий процесс»). Регрессия проверяется пользователем вручную на dev/staging.
- **A-012**: Логотип на главной (`/`) сейчас не имеет `<RouterLink>`-обёртки, просто `<img>`. После унификации становится ссылкой (no-op, но визуально тот же `<a>` вокруг `<img>` — без `text-decoration`).