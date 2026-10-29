# Публичный API: видимость песен и пределы выдачи

> **Status**: active
> **Feature Key**: public-api-exposure
> **Last Updated**: 2026-10-24
> **Spec**: [specs/378-pkg-s2-15/report.md](../../../specs/378-pkg-s2-15/report.md)
> Per-feature документ по [Constitution VI FR-009](../../../.specify/memory/constitution.md).

## Что делает

Приводит публичные точки выдачи `karaoke-web` к единому правилу видимости и ограничивает
размер ответа/приёма: точечная песня и обе картинки скрывают неопубликованный и `SKIP`-контент
от не-редактора, лента новостей и батч метрик Закромов ограничены по размеру, rate-limit
картинок действует раздельно на каждый endpoint.

## Зачем

Аудит #336 (BR-004, BR-013, BR-015, порции P-35/P-37). До правки:

- `GET /api/public/song/{id}`, `/song-picture/{id}`, `/song-vk-image/{id}` отдавали ЛЮБУЮ
  песню по id — без `id_status >= 6` и без проверки тега `SKIP`, хотя `/songs`, `/zakroma`,
  `/authors-tiles` фильтруют; аноним получал полный текст черновиковой песни.
- `GET /api/public/news?size=100000` уходил в `LIMIT ?` и вытягивал всю `tbl_news`
  (3.5+ MB JSON), отрицательный `page` давал отрицательный offset (ошибка SQL).
- `POST /api/public/zakroma/stream/metrics` принимал массив любого размера.
- `WebMvcConfig` мутировал один singleton-бин `RateLimitInterceptor` дважды: оба endpoint'а
  картинок работали с лимитом vk-image и делили один bucket.

## Как работает

1. **Видимость** — `PublicApiController.isPubliclyVisible(song)`: `idStatus >= 6` и в `tags`
   нет `SKIP`. Для не-редактора (`onlyPublishedFor(request)`) невидимая песня даёт `404`
   (как несуществующая, факт черновика не раскрывается); редактор с site-user токеном видит.
2. **Лента новостей** — `normalizePaging(page, size)`: `page >= 0`, `size` в `[1, 100]`,
   `offset` считается в `Long`.
3. **Метрики Закромов** — батч больше `MAX_STREAM_METRICS_BATCH = 500` → `413`, в БД не пишется.
4. **Rate limit** — два независимых экземпляра `RateLimitInterceptor` с конструкторными
   `endpointName`/`limitPerMinute` (по одному на `/song-picture/` и `/song-vk-image/`);
   состояние bucket'ов физически разделено.

## Инварианты

- Не-редактор не получает неопубликованную (`idStatus < 6`) или `SKIP`-песню ни одним
  публичным путём (текст, картинка, VK-превью, плеер).
- Публичный размер ответа ограничен: страница новостей ≤ 100 строк, батч метрик ≤ 500.
- Исчерпание лимита одного endpoint'а не даёт `429` на другом.
- Скрытие неопубликованного контента отвечает тем же кодом, что и «не найдено».

## Известные ловушки

- `publishedIdStatus = 6` продублирован в контроллере и в выборках `/songs`/`/zakroma`;
  при изменении порога менять все точки.
- `SKIP` разбирается сплитом по пробелу с `ignoreCase` в трёх местах
  (`PublicApiController`, `PublicPlayerController`, `PlayerAccessService`) — при появлении
  тегов с другим разделителем правку нужно вносить согласованно.
- `client_max_body_size` на location метрик в nginx не задан отдельно (общий 200m) —
  серверное ограничение остаётся за владельцем (ops-конфиг, вне пакета).
- `MAX_TRACKED_CLAIM_IPS`/`MAX_PAGE_SIZE` — константы; менять их в пределах пакета нельзя.

## Ссылки

- [Отчёт пакета PKG-S2-15](../../../specs/378-pkg-s2-15/report.md)
- [Карточки аудита P-35](../../../specs/336-code-audit/findings/P-35.md) и [P-37](../../../specs/336-code-audit/findings/P-37.md)
- [PublicApiController.kt](../../../karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt)
- [PublicNewsController.kt](../../../karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicNewsController.kt)
- [WebMvcConfig.kt](../../../karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/config/WebMvcConfig.kt)
- [public-controllers-3.md](../../domains/karaoke-web/components/public-controllers-3.md)
- [config.md](../../domains/karaoke-web/components/config.md)
- [site-traffic-resilience.md (архив)](../../../archive/docs/features/site-traffic-resilience.md) — FR-010, SC-008

## Changelog

- **2026-10-24** (BR-004/BR-013/BR-015, OpenProject #378): единое правило видимости для
  точечной песни и картинок, ограничение `page`/`size` новостей и батча метрик, раздельные
  экземпляры rate-limit.
