-- Автоматические новости о выходе песни в эфир (specs/089-auto-news-song-release).
--
-- 1) tbl_news получает song_id/source — ссылка на песню и признак «кто создал новость»
--    (auto/manual). ВАЖНО: обе колонки НЕ участвуют в update_tbl_news_recordhash() (см. 20_news.sql)
--    и намеренно исключены из News.listHashes(), используемого NewsSyncTarget (LOCAL_TO_SERVER)
--    — иначе авто-созданная на PROD новость (которой никогда не будет на LOCAL) была бы
--    воспринята обычным hash-diff sync-движком как «удалённая в источнике» и стёрта при
--    следующей admin-триггерной синхронизации, если когда-либо включат
--    sync_news_push_delete_allowed (см. specs/089-auto-news-song-release/research.md, п.2).
--
-- 2) tbl_song_news_announced — отдельная PROD-локальная таблица-метка «по этой песне уже принято
--    решение об анонсе» (реальная новость создана ИЛИ песня попала в разовый backfill при
--    включении фичи, см. research.md п.5). НЕ регистрируется в SyncRegistry и не участвует в
--    LOCAL↔SERVER синхронизации вообще — по тем же соображениям, что и song_id/source выше.
--    PRIMARY KEY(song_id) — гарантия «не более одного решения на песню» на уровне БД.
--
-- Один раз на LOCAL и на PROD отдельно (см. deploy/karaoke-db/20_news.sql).

ALTER TABLE public.tbl_news ADD COLUMN IF NOT EXISTS song_id integer NULL REFERENCES public.tbl_songs(id);
ALTER TABLE public.tbl_news ADD COLUMN IF NOT EXISTS source varchar(20) NOT NULL DEFAULT 'manual';

CREATE TABLE IF NOT EXISTS public.tbl_song_news_announced (
    song_id integer NOT NULL REFERENCES public.tbl_songs(id),
    news_id integer NULL REFERENCES public.tbl_news(id),
    created_at timestamp without time zone NOT NULL DEFAULT now()
);

ALTER TABLE ONLY public.tbl_song_news_announced
    ADD CONSTRAINT tbl_song_news_announced_pkey PRIMARY KEY (song_id);
