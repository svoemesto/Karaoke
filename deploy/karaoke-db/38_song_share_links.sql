-- Временный полный доступ к песне (add-song-share-link): премиум-пользователь
-- karaoke-public создаёт временную ссылку на одну песню, по которой любой получатель открывает
-- онлайн-плеер в полном режиме без авторизации. Ссылка живёт ограниченное время, не более 2
-- одновременных playback-устройств, аудит-трейл для админа.
--
-- Две таблицы (аналогично 27_listening_history.sql — tbl_events не подходит как персистентное
-- хранилище, см. его же header):
--   tbl_song_share_links      — метаданные ссылки, lease, счётчики.
--   tbl_song_share_sessions   — детальные сессии: открытие/завершение/результат/IP-hash.
--
-- Обе таблицы живут ЦЕЛИКОМ на PROD-БД (по образцу tbl_listening_history, tbl_site_playlists):
-- данные создаются и потребляются реальными пользователями на проде, syncRegistry не нужны.
-- SyncRegistry.all НЕ расширяется. Все 8 sync_<key>_*_allowed флагов в KaraokeProperties —
-- выключены по умолчанию, ничего не уезжает.
--
-- Контракт KaraokeDbTable сохранён (id + recordhash) для единообразия с остальным кодом, даже
-- если sync-движок таблицу не видит.
--
-- ВАЖНО: применять вручную на КАЖДОЙ БД отдельно (LOCAL — для локальной отладки — и PROD) —
-- миграция сама на сервер не попадает. Порядок деплоя: эта миграция ДО/вместе с
-- 39_song_share_recordhash.sql, и в один деплой с новым karaoke-web/karaoke-app,
-- иначе SongShareLinkService упадёт "relation does not exist".
--
-- Восстановлено из dangling git blob c8cc7472af57616ed25d22650722f55a4ce444eb (Pass 47,
-- 2026-08-10): DDL утерян при переключении веток, восстановлен по reflog/fsck. Идемпотентен
-- (CREATE TABLE IF NOT EXISTS + DO-блоки для IDENTITY и PRIMARY KEY) — безопасно применять
-- повторно и на БД, где таблицы созданы вручную/частично.

-- ==========================================================================================
-- tbl_song_share_links
-- ==========================================================================================
CREATE TABLE IF NOT EXISTS public.tbl_song_share_links (
    id integer NOT NULL,
    owner_site_user_id integer NOT NULL REFERENCES public.tbl_site_users(id) ON DELETE CASCADE,
    song_id bigint NOT NULL,                                  -- -> tbl_settings.id, БЕЗ FK
                                                                --   (не связываем с sync песен)
    token_hash character varying(64) NOT NULL,                -- SHA-256 от исходного секрета в hex
                                                                --   (64 hex-символа для 32 байт)
    active boolean DEFAULT true NOT NULL,                     -- true ровно одна на (owner, song)
    expires_at timestamp without time zone NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    revoked_at timestamp without time zone,
    revoke_reason character varying(64) DEFAULT '' NOT NULL, -- ''|'manual'|'replaced'|'premium_lost'
                                                                --   |'song_unavailable'|'admin:<text>'
    first_used_at timestamp without time zone,
    last_used_at timestamp without time zone,
    active_session_token_hash character varying(64),          -- SHA-256 от sessionToken (hex)
    active_session_lease_until timestamp without time zone,   -- конец текущего heartbeat-окна
    active_session_browser_hash character varying(64),        -- SHA-256 от browserId
    sessions_total integer DEFAULT 0 NOT NULL,                -- счётчик созданных сессий
    rejected_concurrent integer DEFAULT 0 NOT NULL,           -- счётчик отказов concurrentLimit
    last_update timestamp without time zone DEFAULT now(),
    recordhash character varying(32)
);

-- IDENTITY (idempotent: только если ещё не настроена).
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_attribute a
        JOIN pg_class c ON c.oid = a.attrelid
        WHERE c.relname = 'tbl_song_share_links'
          AND a.attname = 'id'
          AND a.attidentity = 'a'
    ) THEN
        ALTER TABLE public.tbl_song_share_links ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
            SEQUENCE NAME public.tbl_song_share_links_id_seq
            START WITH 1
            INCREMENT BY 1
            NO MINVALUE
            NO MAXVALUE
            CACHE 1
        );
    END IF;
END $$;

-- PRIMARY KEY (idempotent: pg_constraint lookup).
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'tbl_song_share_links_pkey'
    ) THEN
        ALTER TABLE ONLY public.tbl_song_share_links
            ADD CONSTRAINT tbl_song_share_links_pkey PRIMARY KEY (id);
    END IF;
END $$;

-- Поиск активной ссылки конкретной песни у конкретного пользователя при создании/перевыпуске.
CREATE UNIQUE INDEX IF NOT EXISTS idx_tbl_song_share_links_active
    ON public.tbl_song_share_links (owner_site_user_id, song_id) WHERE active;

-- Листинг ссылок пользователя в админке.
CREATE INDEX IF NOT EXISTS idx_tbl_song_share_links_owner
    ON public.tbl_song_share_links (owner_site_user_id, created_at DESC);

-- Поиск по хэшу секрета в /claim.
CREATE UNIQUE INDEX IF NOT EXISTS idx_tbl_song_share_links_token_hash
    ON public.tbl_song_share_links (token_hash);

-- Подсчёт «живых» ссылок пользователя (для лимита maxActivePerUser=10).
CREATE INDEX IF NOT EXISTS idx_tbl_song_share_links_owner_active
    ON public.tbl_song_share_links (owner_site_user_id) WHERE active;

-- Подсчёт «генераций за сутки» (для лимита maxGenerationsPerDay=30) — индекс по created_at.
CREATE INDEX IF NOT EXISTS idx_tbl_song_share_links_created_at
    ON public.tbl_song_share_links (created_at);

-- Поиск ссылок с активной lease (для ShareLinkSweeper и rate-limit claim).
CREATE INDEX IF NOT EXISTS idx_tbl_song_share_links_lease
    ON public.tbl_song_share_links (active_session_lease_until)
    WHERE active_session_lease_until IS NOT NULL;

-- ==========================================================================================
-- tbl_song_share_sessions
-- ==========================================================================================
CREATE TABLE IF NOT EXISTS public.tbl_song_share_sessions (
    id integer NOT NULL,
    share_link_id integer NOT NULL REFERENCES public.tbl_song_share_links(id) ON DELETE CASCADE,
    song_id bigint NOT NULL,                                  -- денормализованно для быстрого
                                                                --   листинга в админке
    browser_hash character varying(64) NOT NULL,               -- SHA-256 от browserId (localStorage)
    owner_site_user_id integer NOT NULL,                       -- владелец ссылки (для админских
                                                                --   запросов без JOIN)
    anon_id character varying(64) DEFAULT '' NOT NULL,         -- если гость был авторизован —
                                                                --   заполняется так же, как в tbl_events
    opened_at timestamp without time zone DEFAULT now() NOT NULL,
    started_at timestamp without time zone,                    -- первый PLAY (а не claim)
    last_seen_at timestamp without time zone DEFAULT now() NOT NULL,
    finished_at timestamp without time zone,
    result character varying(16) DEFAULT '' NOT NULL,         -- ''|'ended'|'closed'|'timeout'
                                                                --   |'revoked'|'replaced'
    client_ip_hash character varying(64) DEFAULT '' NOT NULL, -- SHA-256(ip + daily-rotating-salt)
                                                                --   GDPR-совместимо
    user_agent_hash character varying(64) DEFAULT '' NOT NULL,-- SHA-256(userAgent + salt)
    last_update timestamp without time zone DEFAULT now(),
    recordhash character varying(32)
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_attribute a
        JOIN pg_class c ON c.oid = a.attrelid
        WHERE c.relname = 'tbl_song_share_sessions'
          AND a.attname = 'id'
          AND a.attidentity = 'a'
    ) THEN
        ALTER TABLE public.tbl_song_share_sessions ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
            SEQUENCE NAME public.tbl_song_share_sessions_id_seq
            START WITH 1
            INCREMENT BY 1
            NO MINVALUE
            NO MAXVALUE
            CACHE 1
        );
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'tbl_song_share_sessions_pkey'
    ) THEN
        ALTER TABLE ONLY public.tbl_song_share_sessions
            ADD CONSTRAINT tbl_song_share_sessions_pkey PRIMARY KEY (id);
    END IF;
END $$;

-- Листинг сессий по ссылке (админский просмотр).
CREATE INDEX IF NOT EXISTS idx_tbl_song_share_sessions_link
    ON public.tbl_song_share_sessions (share_link_id, opened_at DESC);

-- Поиск незавершённых сессий (для ShareLinkSweeper).
CREATE INDEX IF NOT EXISTS idx_tbl_song_share_sessions_unfinished
    ON public.tbl_song_share_sessions (share_link_id) WHERE finished_at IS NULL;

-- Лимиты по пользователю (≤N активных lease на ссылку, sweep по grace_pause).
CREATE INDEX IF NOT EXISTS idx_tbl_song_share_sessions_last_seen
    ON public.tbl_song_share_sessions (last_seen_at) WHERE finished_at IS NULL;
