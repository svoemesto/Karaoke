-- Persistent storage metadata cache (Pass 345, спека #348, supersede #344).
--
-- Кеш результатов fileExists / fileIsActual / getFileInfo.
-- Заменяет in-memory PollingCache (Pass 344, спека #344) на persistent cache:
--   * TTL = ∞ (нет autoexpiration)
--   * Write-through: hooks в StorageApiClient.uploadFile/deleteFile и
--     KaraokeStorageService.uploadFile/deleteFile обновляют кеш атомарно с MinIO.
--   * Manual refresh: POST /api/health/cache/refresh (admin UI).
--   * Переживает restart/rebuild karaoke-app и webvue3 (Single Source of Truth = PG).
--
-- Контракт:
--   * PRIMARY KEY (source, bucket, file_name) — уникальная запись.
--   * source ∈ {'LOCAL', 'REMOTE'} — какой MinIO.
--   * exists — флаг (Boolean). NULL допустим только если row вообще нет.
--   * etag, size — из getFileInfo; NULL если fileExists = false.
--   * updated_at — время последнего MinIO запроса ИЛИ последнего upload/delete.
--
-- НЕ участвует в SyncRegistry (только local, не синхронизируется между LOCAL и SERVER).
-- Это сознательное решение: каждый инстанс MinIO (karaoke-storage LOCAL + remote SRV)
-- имеет СВОЮ копию файлов, поэтому кеш per-source и per-machine. На проде
-- remote-кеш живёт на karaoke-app (admin-машина, имеет прямой MinioClient SDK
-- к обоим MinIO). На SERVER-БД эта таблица пуста.

CREATE TABLE IF NOT EXISTS tbl_storage_metadata_cache (
    source     VARCHAR(8)  NOT NULL,
    bucket     VARCHAR(64) NOT NULL,
    file_name  TEXT        NOT NULL,
    exists     BOOLEAN     NOT NULL,
    etag       VARCHAR(64),
    size       BIGINT,
    updated_at TIMESTAMP   NOT NULL DEFAULT NOW(),
    PRIMARY KEY (source, bucket, file_name)
);

CREATE INDEX IF NOT EXISTS idx_storage_metadata_cache_updated
    ON tbl_storage_metadata_cache(updated_at);

-- Комментарии для будущих разработчиков
COMMENT ON TABLE tbl_storage_metadata_cache IS
    'Persistent metadata cache for MinIO (Pass 345). TTL=∞, write-through hooks + manual refresh.';
COMMENT ON COLUMN tbl_storage_metadata_cache.source IS
    'LOCAL = karaoke-storage (admin), REMOTE = прод MinIO через nginx-proxy.';
COMMENT ON COLUMN tbl_storage_metadata_cache.updated_at IS
    'Время последнего обращения к MinIO (read miss или write-through upload/delete).';
