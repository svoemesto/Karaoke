-- Служебный кэш «IP → страна» для админ-дашборда статистики (karaoke-app / GeoIpService.kt).
-- Заполняется на лету резолвом через api.country.is при построении блоков «География» и лога
-- событий. НЕ доменные данные: вне recordhash, НЕ участвует в LOCAL<->SERVER-синхронизации.
--
-- ВАЖНО: применять ТОЛЬКО на LOCAL админ-БД (машина администратора). На прод (79.174.95.69:8832)
-- НЕ применять — karaoke-app там не разворачивается, страна на проде не нужна.
--   docker exec -i karaoke-db psql -U <user> -d karaoke -f /path/08_ip_country.sql
-- либо: cat deploy/karaoke-db/08_ip_country.sql | docker exec -i karaoke-db psql -U <user> -d karaoke

CREATE TABLE IF NOT EXISTS tbl_ip_country (
    ip character varying(64) PRIMARY KEY,
    country character varying(8),        -- ISO-код страны (RU/DE/...) или '' если не определилась
    resolved_at timestamp without time zone DEFAULT now()
);
