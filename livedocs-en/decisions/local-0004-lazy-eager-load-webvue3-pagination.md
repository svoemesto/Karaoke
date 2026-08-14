# Local ADR-0004: Convention for lazy/eager-load in webvue3 pagination

* **Status**: Accepted
* **Date**: 2026-08-14
* **Deciders**: Karaoke team

> **Russian version**: [../../../livedocs/architecture/decisions/local-0004-lazy-eager-load-webvue3-pagination.md](../../../livedocs/architecture/decisions/local-0004-lazy-eager-load-webvue3-pagination.md)
>
> **Note**: this is **local** ADR — describes convention in code (not
> global architecture decision).

## Context

In `webvue3` admin tables (`SongsTable`, `AuthorsTable`, etc.) data is
loaded via Vuex actions + REST endpoints. Historically there was confusion:
«when to load data?».

Problems without explicit convention:
- **Initial load vs filter change**: should each filter change make
  a new API request?
- **Pagination**: on page change — new request or client-side?
- **Real-time updates**: on change in another tab — refresh?
- **Mass operations**: on delete 100 records — refresh or optimistic update?

## Decision

**Convention for webvue3 admin tables**:

```javascript
// === Standard pattern for admin tables in webvue3 ===

export default {
    data() {
        return {
            // Local data (from initial load)
            items: [],
            currentPage: 1,
            perPage: 25,
            // Loading state
            loading: false,
            error: null,
        };
    },

    async created() {
        // 1. Initial load — REQUIRED
        await this.loadData();
        // 2. Subscribe to SSE for real-time updates
        this.$store.dispatch('subscribeToTable', { table: 'songs', callback: this.onSseEvent });
    },

    methods: {
        // Load data (initial / page change / filter change)
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

        // Filters — WATCH (auto-load on change)
        watch: {
            filters: {
                handler() { this.currentPage = 1; this.loadData(); },
                deep: true,
            },
            currentPage() { this.loadData(); },
            perPage() { this.loadData(); },
        },

        // SSE event — partial reload (only changed item)
        async onSseEvent(event) {
            if (event.type === 'SETTINGS_CHANGED' && event.id) {
                // Partial update — fetch only one item
                const updated = await this.$store.dispatch('fetchSong', event.id);
                const idx = this.items.findIndex(i => i.id === event.id);
                if (idx >= 0) this.items.splice(idx, 1, updated);
                // Don't do full reload — slow for 10k+ records
            }
        },

        beforeDestroy() {
            this.$store.dispatch('unsubscribeFromTable', { table: 'songs' });
        },
    },
};
```

### Rules

1. **Initial load in `created()`** — ALWAYS, no exceptions.
2. **Page change → loadData()** — server-side pagination (NOT client-side).
3. **Filter change → reset page = 1 + loadData()**.
4. **Real-time update → partial reload** (one item, not whole list).
5. **Cleanup in `beforeDestroy()`** — unsubscribe from SSE.
6. **Mass operations (bulk delete)** — refresh only one page, not whole list.

### Performance budget

- Initial load: ≤ 2 sec (10k records).
- Page change: ≤ 500 ms.
- Single item update: ≤ 100 ms.
- SSE throughput: ≤ 100 events/sec (admin).

## Consequences

### Positive
- **Predictable**: new table = copy template.
- **Real-time updates** without full reload (fast).
- **Server-side pagination** = scales to 100k+ records.
- **Cleanup** — no SSE subscription leaks.

### Negative
- **More code** in each table (template required).
- **Discipline** — without template, easy to "load everything".

### Neutral
- **Vuex modules** — each with own actions: `fetchSongs`, `fetchAuthors`, etc.

## Alternatives Considered

- **Only initial load** (without SSE): rejected — doesn't see changes from others.
- **Full reload on any SSE event**: rejected — slow for large tables.
- **Client-side pagination** (load all 10k records): rejected — doesn't scale.
- **WebSocket for all changes**: rejected — overkill for admin (SSE sufficient).

## References

- [architecture/webvue3-patterns.md](../../webvue3-patterns.md) — Vuex pagination persistence (related).
- [features/176-authors-new-albums-badge.md](../../features/176-authors-new-albums-badge.md) —
  example of polling for badge (not realtime, but similar pattern).
- [architecture/observability.md](../../observability.md) — where SSE Hub lives.
- [architecture/cache-invalidation.md](../../cache-invalidation.md) — SSE live-updates.

## Code

- `webvue3/src/components/Songs/SongsTable.vue` — example.
- `webvue3/src/components/Authors/AuthorsTable.vue` — example.
- `webvue3/src/store/modules/Songs/store.js` — `fetchSongs`, `subscribeToTable`.

## History

- Created: 2026-08-14
- Last updated: 2026-08-14