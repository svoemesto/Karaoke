# Local ADR-0004: Конвенция для lazy/eager-load в webvue3 пагинации

* **Status**: Accepted
* **Date**: 2026-08-14
* **Deciders**: команда Karaoke

> **English version**: [../../../livedocs-en/decisions/local-0004-lazy-eager-load-webvue3-pagination.md](../../../livedocs-en/decisions/local-0004-lazy-eager-load-webvue3-pagination.md)
>
> **Note**: this is **local** ADR — описывает конвенцию в коде (а не
> глобальное архитектурное решение).

## Context

В `webvue3` админские таблицы (`SongsTable`, `AuthorsTable`, и т.п.)
загружают данные через Vuex actions + REST endpoints. Исторически
была путаница: «когда загружать данные?».

Проблемы без явной конвенции:
- **Initial load vs filter change**: должен ли каждый filter-change
  делать новый запрос к API?
- **Pagination**: при изменении страницы — новый запрос или client-side?
- **Real-time updates**: при изменении в другой вкладке — refresh?
- **Mass operations**: при удалении 100 записей — refresh или optimistic update?

## Decision

**Конвенция для webvue3 admin tables**:

```javascript
// === Standard pattern для admin таблиц в webvue3 ===

export default {
    data() {
        return {
            // Локальные данные (от initial load)
            items: [],
            currentPage: 1,
            perPage: 25,
            // Loading state
            loading: false,
            error: null,
        };
    },

    async created() {
        // 1. Initial load — ОБЯЗАТЕЛЬНО
        await this.loadData();
        // 2. Subscribe to SSE для real-time updates
        this.$store.dispatch('subscribeToTable', { table: 'songs', callback: this.onSseEvent });
    },

    methods: {
        // Загрузка данных (при initial / page change / filter change)
        async loadData() {
            this.loading = true;
            this.error = null;
            try {
                const response = await this.$store.dispatch('fetchSongs', {
                    page: this.currentPage,
                    perPage: this.perPage,
                    filters: this.filters,
                });
                this.items = response.data;
            } catch (e) {
                this.error = e.message;
            } finally {
                this.loading = false;
            }
        },

        // Фильтры — WATCH (auto-load on change)
        watch: {
            filters: {
                handler() { this.currentPage = 1; this.loadData(); },
                deep: true,
            },
            currentPage() { this.loadData(); },
            perPage() { this.loadData(); },
        },

        // SSE event — partial reload (только изменившийся item)
        async onSseEvent(event) {
            if (event.type === 'SETTINGS_CHANGED' && event.id) {
                // Partial update — fetch только один item
                const updated = await this.$store.dispatch('fetchSong', event.id);
                const idx = this.items.findIndex(i => i.id === event.id);
                if (idx >= 0) this.items.splice(idx, 1, updated);
                // Не делать full reload — это будет медленно для 10k+ записей
            }
        },

        beforeDestroy() {
            this.$store.dispatch('unsubscribeFromTable', { table: 'songs' });
        },
    },
};
```

### Правила

1. **Initial load в `created()`** — ВСЕГДА, без исключений.
2. **Page change → loadData()** — server-side pagination (НЕ client-side).
3. **Filter change → reset page = 1 + loadData()**.
4. **Real-time update → partial reload** (один item, не весь список).
5. **Cleanup в `beforeDestroy()`** — отписаться от SSE.
6. **Mass operations (bulk delete)** — refresh только одной страницы, не всего списка.

### Performance budget

- Initial load: ≤ 2 сек (10k записей).
- Page change: ≤ 500 мс.
- Single item update: ≤ 100 мс.
- SSE throughput: ≤ 100 events/sec (на admin).

## Consequences

### Positive
- **Predictable**: новая таблица = скопируй шаблон.
- **Real-time updates** без full reload (быстро).
- **Server-side pagination** = масштабируется на 100k+ записей.
- **Cleanup** — нет утечек SSE subscriptions.

### Negative
- **Больше кода** в каждой таблице (шаблон обязателен).
- **Дисциплина** — без шаблона легко сделать «загрузить всё».

### Neutral
- **Vuex модули** — каждый со своими actions: `fetchSongs`, `fetchAuthors`, etc.

## Alternatives Considered

- **Только initial load** (без SSE): rejected — не видим изменения от других.
- **Полный reload на любое событие SSE**: rejected — медленно для больших таблиц.
- **Client-side pagination** (загрузить все 10k записей): rejected — не масштабируется.
- **WebSocket для всех изменений**: rejected — overkill для админки (SSE достаточно).

## References

- [architecture/webvue3-patterns.md](../../webvue3-patterns.md) — Vuex pagination persistence (смежная).
- [features/176-authors-new-albums-badge.md](../../features/176-authors-new-albums-badge.md) —
  пример polling для бейджа (не realtime, но похожий паттерн).
- [architecture/observability.md](../../observability.md) — где живёт SSE Hub.
- [architecture/cache-invalidation.md](../../cache-invalidation.md) — SSE live-updates.

## Код

- `webvue3/src/components/Songs/SongsTable.vue` — образец.
- `webvue3/src/components/Authors/AuthorsTable.vue` — образец.
- `webvue3/src/store/modules/Songs/store.js` — `fetchSongs`, `subscribeToTable`.

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14