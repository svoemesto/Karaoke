-- «Новости» проекта: новая песня в эфире/премиум-доступе, новый функционал сайта («Чат с автором»,
-- «Плейлисты», «скорость плеера» и т.п.). Готовятся заранее из webvue3 (LOCAL), редактируются через
-- karaoke-app (model/News.kt), уезжают на прод через LOCAL->SERVER синхронизацию (KaraokeDbTable +
-- recordhash-триггер, SyncTarget "news", см. sync/SyncTarget.kt) — по тому же паттерну, что «Словари»
-- (17_dictionaries.sql). Публичный сайт (karaoke-web/karaoke-public) читает уже с прод-БД.
--
-- «Опубликовано» — вычисляемое условие publish_at <= now() (как Settings.onAir), а не отдельный
-- статус/планировщик: новость с будущим publish_at, уже синхронизированная на прод, сама «всплывает»
-- в назначенный момент на следующем опросе клиента.
--
-- ВАЖНО: применять вручную на КАЖДОЙ БД отдельно (LOCAL + PROD 79.174.95.69:8832) —
-- миграция сама на сервер не попадает (см. CLAUDE.md).

CREATE TABLE public.tbl_news (
    id integer NOT NULL,
    title character varying(500) NOT NULL,
    body text NOT NULL,
    category character varying(50) NOT NULL DEFAULT 'general',
    link character varying(1000),
    publish_at timestamp without time zone,
    created_at timestamp without time zone,
    recordhash character varying(32)
);

ALTER TABLE public.tbl_news ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.tbl_news_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
    );

ALTER TABLE ONLY public.tbl_news
    ADD CONSTRAINT tbl_news_pkey PRIMARY KEY (id);

-- Публичная лента фильтрует и сортирует именно по этому полю (WHERE publish_at <= now() ORDER BY
-- publish_at DESC) — на каждый опрос бейджа/тоста, поэтому индекс обязателен.
CREATE INDEX idx_tbl_news_publish_at ON public.tbl_news (publish_at);

CREATE FUNCTION public.update_tbl_news_recordhash() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    NEW.recordhash = md5(
                                COALESCE(NEW.id::TEXT, '') ||
                                COALESCE(NEW.title, '') ||
                                COALESCE(NEW.body, '') ||
                                COALESCE(NEW.category, '') ||
                                COALESCE(NEW.link, '') ||
                                COALESCE(NEW.publish_at::TEXT, '')
        );
RETURN NEW;
END;
$$;

CREATE INDEX idx_tbl_news_recordhash ON public.tbl_news USING btree (recordhash);

CREATE TRIGGER update_recordhash_news_trigger BEFORE INSERT OR UPDATE ON public.tbl_news FOR EACH ROW EXECUTE FUNCTION public.update_tbl_news_recordhash();
