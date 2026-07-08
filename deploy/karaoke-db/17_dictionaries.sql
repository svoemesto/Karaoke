-- Единая таблица словарей вместо файловых TextFileDictionary (/sm-karaoke/system/*.txt):
-- «Слова с Ё», «Censored», «Sync Ids». Каждая запись — одна строка словаря (dict_name, dict_value).
-- Участвует в LOCAL<->SERVER синхронизации (KaraokeDbTable + recordhash-триггер, SyncRegistry:
-- dictionaries, направление по умолчанию LOCAL_TO_SERVER, как pictures/authors).
--
-- ВАЖНО: применять вручную на КАЖДОЙ БД отдельно (LOCAL + PROD 79.174.95.69:8832) —
-- миграция сама на сервер не попадает (см. CLAUDE.md).

CREATE TABLE public.tbl_dictionaries (
    id integer NOT NULL,
    dict_name character varying(255) NOT NULL,
    dict_value text NOT NULL,
    recordhash character varying(32)
);

ALTER TABLE public.tbl_dictionaries ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.tbl_dictionaries_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
    );

ALTER TABLE ONLY public.tbl_dictionaries
    ADD CONSTRAINT tbl_dictionaries_pkey PRIMARY KEY (id);

-- Одно и то же значение не дублируется внутри словаря.
CREATE UNIQUE INDEX uq_tbl_dictionaries_name_value ON public.tbl_dictionaries (dict_name, dict_value);

CREATE INDEX idx_tbl_dictionaries_dict_name ON public.tbl_dictionaries (dict_name);

CREATE FUNCTION public.update_tbl_dictionaries_recordhash() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    NEW.recordhash = md5(
                                COALESCE(NEW.id::TEXT, '') ||
                                COALESCE(NEW.dict_name, '') ||
                                COALESCE(NEW.dict_value, '')
        );
RETURN NEW;
END;
$$;

CREATE INDEX idx_tbl_dictionaries_recordhash ON public.tbl_dictionaries USING btree (recordhash);

CREATE TRIGGER update_recordhash_dictionaries_trigger BEFORE INSERT OR UPDATE ON public.tbl_dictionaries FOR EACH ROW EXECUTE FUNCTION public.update_tbl_dictionaries_recordhash();
