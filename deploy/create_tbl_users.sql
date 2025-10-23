
CREATE TABLE public.tbl_users (
                                  id integer NOT NULL,
                                  login character varying(255),
                                  password_hash character varying(255),
                                  email character varying(255),
                                  first_name character varying(255),
                                  last_name character varying(255),
                                  groups character varying(255) default '' not null,
                                  last_update timestamp without time zone DEFAULT now(),
                                  recordhash character varying(32)
);

ALTER TABLE public.tbl_users ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.tbl_users_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
    );

CREATE FUNCTION public.update_tbl_users_recordhash() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    NEW.recordhash = md5(
                                COALESCE(NEW.id::TEXT, '') ||
                                COALESCE(NEW.login, '') ||
                                COALESCE(NEW.password_hash, '') ||
                                COALESCE(NEW.email, '') ||
                                COALESCE(NEW.first_name, '') ||
                                COALESCE(NEW.last_name, '')
        );
RETURN NEW;
END;
$$;


CREATE INDEX idx_tbl_users_recordhash ON public.tbl_users USING btree (recordhash);

CREATE INDEX tbl_users_last_update_index ON public.tbl_users USING btree (last_update);

CREATE TRIGGER update_recordhash_users_trigger BEFORE INSERT OR UPDATE ON public.tbl_users FOR EACH ROW EXECUTE FUNCTION public.update_tbl_users_recordhash();

CREATE TRIGGER update_last_updated_users_trigger BEFORE UPDATE ON public.tbl_users FOR EACH ROW EXECUTE FUNCTION public.update_last_updated();
