CREATE TABLE public.tbl_events (
                                   id integer NOT NULL,
                                   event_type character varying(255),
                                   rest_name character varying(255),
                                   rest_parameters text,
                                   link_type character varying(255),
                                   link_name character varying(255),
                                   song_id integer,
                                   song_version character varying(255),
                                   last_update timestamp without time zone default now()
);

ALTER TABLE public.tbl_events OWNER TO postgres;

ALTER TABLE public.tbl_events ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.tbl_events_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

ALTER SEQUENCE public.tbl_events_id_seq OWNER TO postgres;

ALTER TABLE ONLY public.tbl_events
    ADD CONSTRAINT tbl_events_id_key UNIQUE (id);

ALTER TABLE ONLY public.tbl_events
    ADD CONSTRAINT tbl_events_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.tbl_events ALTER COLUMN id SET DEFAULT nextval('public.tbl_events_id_seq'::regclass);

CREATE INDEX tbl_events_last_update_index ON public.tbl_events USING btree (last_update);

CREATE TRIGGER update_last_updated_events_trigger BEFORE UPDATE ON public.tbl_events FOR EACH ROW EXECUTE FUNCTION public.update_last_updated();