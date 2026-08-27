# Research: 250 — Унификация шапки сайта

**Branch**: `250-unify-site-header` | **Date**: 2026-08-27
**Spec**: [spec.md](spec.md)

## Summary

Phase 0 research. Все ключевые решения в спеке зафиксированы (FR-001..FR-016, A-001..A-012). Это **UI-only рефакторинг** в `karaoke-public` — никаких backend, БД, API или cross-machine изменений. Phase 0 ниже фиксирует 3 оставшихся research-вопроса (slots vs props, sticky default, порядок правых элементов) и best-practice проверки.

## Research Tasks

### RT-1 — API `<AppHeader>`: props-only vs slots-only vs гибрид?

**Decision**: **Гибрид** — `back` и `profileLink` как props (типизированные, частые случаи, 90% страниц), `left` / `center` / `right` как slots (escape hatch для EditorWorkView и будущих кастомных шапок).

**Rationale**:
- Большинство view-файлов (14 из 16) — одинаковый паттерн: одна back-ссылка + опциональный profileLink. Props дают type-safety и лаконичность в usage (`<AppHeader :back="{ to: '/zakroma', label: '← Закрома' }" :profile-link="{ to: '/account', label: 'Профиль →' }" />` — 1 строка).
- EditorWorkView требует центральный слот (заголовок песни + автор) — slot здесь естественнее, чем 2 пропа (`title`, `subtitle`).
- Гибрид = типизированный happy-path + гибкость для исключений. Минус: API шире, нужно документировать приоритет (slot перебивает prop, если оба заданы).

**Alternatives**:
- **Только props** (`back`, `profileLink`, `title`, `subtitle`, `showAuthWidget`, `showThemeToggle`): компактнее API, но для `EditorWorkView` пришлось бы добавлять ещё `centerTitle`, `centerSubtitle`, `rightSlot` — props-взрыв.
- **Только slots**: гибкость, но для 14 страниц шаблонная разметка `<template #left>...</template>` многословнее, чем props.
- **Выбран гибрид** — баланс.

**@see** — Vue 3 documentation: [Slots](https://vuejs.org/guide/components/slots.html), [Props](https://vuejs.org/guide/components/props.html).

### RT-2 — Sticky-шапка: на всех страницах или opt-in?

**Decision**: **Sticky по умолчанию** на всех страницах с `<AppHeader>`, **отключаемо** через prop `sticky: boolean` (default `true`).

**Rationale**:
- Текущие sticky-страницы (`/filter`, `/song/<id>`) — длинный скролл (список песен/фильтры). Шапка плавает, пользователь не теряет навигацию.
- На статичных страницах (`/about`, `/oferta`) sticky не повредит — контент короткий, sticky = на 0px от top. Поведение консистентное.
- Opt-out через prop нужен для будущих full-screen layouts (например, если `/account/editor/<id>` будет рендерить шапку, её sticky логика может конфликтовать со sticky-toolbar'ом).
- Решение соответствует FR-012.

**Alternatives**:
- **Opt-in** (`sticky: false` default): требует передавать `:sticky="true"` в 14 местах — boilerplate.
- **opt-out** (`sticky: true` default): одна правка в `<AppHeader>` если когда-то потребуется.
- **Выбран opt-out**.

### RT-3 — Порядок элементов в правом слоте

**Decision**: **[profileLink] → [AuthStatusWidget] → [theme toggle] → [logo]**, слева направо.

**Rationale**:
- `profileLink` («Профиль →») — пользовательский переход, должен быть визуально рядом с лого (логотипно-профильная зона).
- `AuthStatusWidget` — состояние авторизации, контекстно рядом с profileLink.
- Theme toggle — утилитарная иконка, всегда справа.
- Logo — крайняя правая точка (как у пользователя «дом» = визуально «правее всего»).
- Соответствует ожиданиям пользователя («лого справа, всё остальное — левее лого»).

**Alternatives**:
- **[logo] → [profileLink] → [AuthStatusWidget] → [theme toggle]**: лого первым слева, утилитарные элементы после. Менее интуитивно — лого = «бренд», обычно он «уходит» в угол.
- **[AuthStatusWidget] → [theme toggle] → [logo]** (без profileLink в header): profileLink убирается на страницах без явного перехода на `/account`. Но profileLink нужен на `/chat` и `/account/playlists` (см. FR-005, FR-007) — без него в шапке некуда.
- **Выбран гибрид с profileLink ближе к лого**.

### RT-4 — Миграция CSS: удалить полностью или оставить алиасы?

**Decision**: **Полностью удалить** `.km-header*`, `.km-back`, `.km-logo` (использованные в `<style scoped>` view-файлов) после рефакторинга. Класс `.km-brand-logo` на HomeView — алиас `.km-logo` (визуально отличается размером: `max-width: 200px` vs `max-width: 100px`), **сохраняем** как алиас внутри `<AppHeader>`.

**Rationale**:
- 18 view-файлов × ~20 строк CSS = ~360 строк копипасты. После рефакторинга все стили в `<AppHeader>.vue` — single source of truth.
- Алиас `.km-brand-logo` — для главной, где логотип крупнее (брендовая шапка без back-ссылки). Внутри `<AppHeader>` это просто CSS-класс с `max-width: 200px`.
- `grep -rn "\.km-header\|\.km-back\|\.km-logo" karaoke-public/src/views/ --include="*.vue"` должен возвращать пусто (только в `<AppHeader>.vue` или несуществующие ссылки — точно не в `<style scoped>`).

**Alternatives**:
- **Оставить алиасы как deprecated** (`:class="{ 'km-brand-logo': isHome }"`) — boilerplate, нет смысла после рефакторинга.
- **Hard-remove** — выбран.

### RT-5 — Сохранение `?author=<slug>` query в back-ссылке

**Decision**: **Сохранить** в `<AppHeader>` через prop `back.query: Record<string, string>` (или сразу передавать `back: { to, query, label }`).

**Rationale**:
- Текущее поведение `AuthorPlaylistView` — back-ссылка с `?author=<slug>` для возврата на ту же позицию скролла в `/zakroma`.
- Полезно для UX (длинные списки авторов).
- `<RouterLink :to="{ path: '/zakroma', query: { author: route.params.slug } }">` — стандартный паттерн Vue Router.
- Соответствует A-009.

**Alternatives**:
- **Скролл-позиция через sessionStorage** (без query): усложняет логику, нужен scroll-restoration listener.
- **Без query** (просто `<RouterLink to="/zakroma">`): теряем позицию скролла, но проще.
- **Выбран prop `back.query`** — компромисс.

## Constitution Check (Re-evaluation post-research)

- ✅ **Principle I**: не затрагивается.
- ✅ **Principle II**: не затрагивается.
- ✅ **Principle III**: не затрагивается.
- ✅ **Principle IV**: не затрагивается.
- ✅ **Principle V (Двух-фронтенд)**: затрагивается в `karaoke-public` — допустимо (это «наш» фронтенд по Constitution). Admin `webvue3` не трогаем.
- ✅ **Principle VI (Code Standards)**: KDoc обязателен для `<AppHeader>` (FR-006 spec.md → KDoc на Vue-компонент). Per-feature документ `livedocs/features/250-unify-site-header.md` будет создан.
- ✅ **Principle VII**: не затрагивается.
- ✅ **Principle VIII**: не затрагивается (нет секрет-файлов).

**Constitution Check: PASS** после Phase 0.

## Open Questions (None)

Все спорные моменты разрешены в RT-1..RT-5. Spec не содержит `[NEEDS CLARIFICATION]` маркеров.

## Next Step

→ Phase 1: [data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md).