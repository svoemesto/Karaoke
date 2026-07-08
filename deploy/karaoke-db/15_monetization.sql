-- Монетизация: подписка на Sponsr (синхронизация — см. 14_site_user_premium_sources.sql),
-- подписка на сайт (периодическая, с автопродлением) и подписка на песню (бессрочная).
-- Единый платёжный конвейер (ЮKassa) обслуживает оба вида через tbl_subscriptions; различается
-- только fulfillment (см. PublicPaymentController.webhook в karaoke-web).
--
-- ВАЖНО: применять вручную на КАЖДОЙ БД отдельно (LOCAL + PROD 79.174.95.69:8832).
-- Колонка id_tariff на tbl_settings — см. правку deploy/recordhash_settings.sql (применять вместе).

-- Тарифы. scope=SONG — бессрочный (period_days игнорируется); scope=SITE — период подписки.
CREATE TABLE IF NOT EXISTS public.tbl_price_tariffs (
    id integer NOT NULL,
    scope character varying(16) NOT NULL, -- SONG | SITE
    name character varying(255) DEFAULT '' NOT NULL,
    price_rub numeric(10,2) DEFAULT 0 NOT NULL,
    period_days integer DEFAULT 0 NOT NULL, -- 0 для SONG (бессрочно)
    is_active boolean DEFAULT true NOT NULL,
    is_default boolean DEFAULT false NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    last_update timestamp without time zone DEFAULT now(),
    recordhash character varying(32)
);

ALTER TABLE public.tbl_price_tariffs ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.tbl_price_tariffs_id_seq START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1
    );

ALTER TABLE ONLY public.tbl_price_tariffs ADD CONSTRAINT tbl_price_tariffs_pkey PRIMARY KEY (id);

CREATE FUNCTION public.update_tbl_price_tariffs_recordhash() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    NEW.recordhash = md5(
                                COALESCE(NEW.id::TEXT, '') ||
                                COALESCE(NEW.scope, '') ||
                                COALESCE(NEW.name, '') ||
                                COALESCE(NEW.price_rub::TEXT, '') ||
                                COALESCE(NEW.period_days::TEXT, '') ||
                                COALESCE(NEW.is_active::TEXT, '') ||
                                COALESCE(NEW.is_default::TEXT, '') ||
                                COALESCE(NEW.sort_order::TEXT, '')
        );
RETURN NEW;
END;
$$;

CREATE INDEX idx_tbl_price_tariffs_recordhash ON public.tbl_price_tariffs USING btree (recordhash);
CREATE INDEX tbl_price_tariffs_last_update_index ON public.tbl_price_tariffs USING btree (last_update);

CREATE TRIGGER update_recordhash_price_tariffs_trigger BEFORE INSERT OR UPDATE ON public.tbl_price_tariffs FOR EACH ROW EXECUTE FUNCTION public.update_tbl_price_tariffs_recordhash();
CREATE TRIGGER update_last_updated_price_tariffs_trigger BEFORE UPDATE ON public.tbl_price_tariffs FOR EACH ROW EXECUTE FUNCTION public.update_last_updated();

-- Акции. applies_to ограничивает область (SONG | SITE | BOTH). params_json — параметры конкретного
-- типа правила (напр. {"percent":10,"hoursAfterRegistration":24} для NEW_USER_PERCENT).
CREATE TABLE IF NOT EXISTS public.tbl_promo_rules (
    id integer NOT NULL,
    name character varying(255) DEFAULT '' NOT NULL,
    type character varying(32) NOT NULL, -- NEW_USER_PERCENT | NTH_FREE | HAPPY_HOUR | FLAT_PERCENT
    params_json text DEFAULT '{}' NOT NULL,
    applies_to character varying(16) DEFAULT 'BOTH' NOT NULL, -- SONG | SITE | BOTH
    is_active boolean DEFAULT true NOT NULL,
    valid_from timestamp without time zone,
    valid_to timestamp without time zone,
    priority integer DEFAULT 0 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    last_update timestamp without time zone DEFAULT now(),
    recordhash character varying(32)
);

ALTER TABLE public.tbl_promo_rules ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.tbl_promo_rules_id_seq START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1
    );

ALTER TABLE ONLY public.tbl_promo_rules ADD CONSTRAINT tbl_promo_rules_pkey PRIMARY KEY (id);

CREATE FUNCTION public.update_tbl_promo_rules_recordhash() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    NEW.recordhash = md5(
                                COALESCE(NEW.id::TEXT, '') ||
                                COALESCE(NEW.name, '') ||
                                COALESCE(NEW.type, '') ||
                                COALESCE(NEW.params_json, '') ||
                                COALESCE(NEW.applies_to, '') ||
                                COALESCE(NEW.is_active::TEXT, '') ||
                                COALESCE(NEW.valid_from::TEXT, '') ||
                                COALESCE(NEW.valid_to::TEXT, '') ||
                                COALESCE(NEW.priority::TEXT, '')
        );
RETURN NEW;
END;
$$;

CREATE INDEX idx_tbl_promo_rules_recordhash ON public.tbl_promo_rules USING btree (recordhash);
CREATE INDEX tbl_promo_rules_last_update_index ON public.tbl_promo_rules USING btree (last_update);

CREATE TRIGGER update_recordhash_promo_rules_trigger BEFORE INSERT OR UPDATE ON public.tbl_promo_rules FOR EACH ROW EXECUTE FUNCTION public.update_tbl_promo_rules_recordhash();
CREATE TRIGGER update_last_updated_promo_rules_trigger BEFORE UPDATE ON public.tbl_promo_rules FOR EACH ROW EXECUTE FUNCTION public.update_last_updated();

-- Обобщённая запись подписки (единый платёжный конвейер для SONG и SITE).
-- PAID scope=SONG -> бессрочный доступ к песне (сам факт наличия записи = владение).
-- PAID scope=SITE -> продлевает tbl_site_users.site_premium_until.
CREATE TABLE IF NOT EXISTS public.tbl_subscriptions (
    id integer NOT NULL,
    site_user_id integer NOT NULL REFERENCES public.tbl_site_users(id) ON DELETE CASCADE,
    scope character varying(16) NOT NULL, -- SONG | SITE
    id_song bigint, -- NULL для scope=SITE
    tariff_id integer REFERENCES public.tbl_price_tariffs(id),
    period_days integer DEFAULT 0 NOT NULL,
    base_price numeric(10,2) DEFAULT 0 NOT NULL,
    discount numeric(10,2) DEFAULT 0 NOT NULL,
    final_price numeric(10,2) DEFAULT 0 NOT NULL,
    promo_applied character varying(64) DEFAULT '' NOT NULL,
    status character varying(16) DEFAULT 'CREATED' NOT NULL, -- CREATED|PENDING|PAID|FAILED|REFUNDED|CANCELED
    yookassa_payment_id character varying(64) DEFAULT '' NOT NULL,
    auto_renew boolean DEFAULT true NOT NULL, -- имеет смысл только для scope=SITE
    yookassa_payment_method_id character varying(64) DEFAULT '' NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    paid_at timestamp without time zone,
    last_update timestamp without time zone DEFAULT now(),
    recordhash character varying(32)
);

ALTER TABLE public.tbl_subscriptions ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.tbl_subscriptions_id_seq START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1
    );

ALTER TABLE ONLY public.tbl_subscriptions ADD CONSTRAINT tbl_subscriptions_pkey PRIMARY KEY (id);

CREATE INDEX idx_tbl_subscriptions_site_user_id ON public.tbl_subscriptions (site_user_id);
CREATE INDEX idx_tbl_subscriptions_song_owned ON public.tbl_subscriptions (id_song, site_user_id) WHERE scope = 'SONG' AND status = 'PAID';
CREATE UNIQUE INDEX idx_tbl_subscriptions_yookassa_payment_id ON public.tbl_subscriptions (yookassa_payment_id) WHERE yookassa_payment_id <> '';

CREATE FUNCTION public.update_tbl_subscriptions_recordhash() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    NEW.recordhash = md5(
                                COALESCE(NEW.id::TEXT, '') ||
                                COALESCE(NEW.site_user_id::TEXT, '') ||
                                COALESCE(NEW.scope, '') ||
                                COALESCE(NEW.id_song::TEXT, '') ||
                                COALESCE(NEW.tariff_id::TEXT, '') ||
                                COALESCE(NEW.period_days::TEXT, '') ||
                                COALESCE(NEW.base_price::TEXT, '') ||
                                COALESCE(NEW.discount::TEXT, '') ||
                                COALESCE(NEW.final_price::TEXT, '') ||
                                COALESCE(NEW.promo_applied, '') ||
                                COALESCE(NEW.status, '') ||
                                COALESCE(NEW.yookassa_payment_id, '') ||
                                COALESCE(NEW.auto_renew::TEXT, '') ||
                                COALESCE(NEW.yookassa_payment_method_id, '') ||
                                COALESCE(NEW.paid_at::TEXT, '')
        );
RETURN NEW;
END;
$$;

CREATE INDEX idx_tbl_subscriptions_recordhash ON public.tbl_subscriptions USING btree (recordhash);
CREATE INDEX tbl_subscriptions_last_update_index ON public.tbl_subscriptions USING btree (last_update);

CREATE TRIGGER update_recordhash_subscriptions_trigger BEFORE INSERT OR UPDATE ON public.tbl_subscriptions FOR EACH ROW EXECUTE FUNCTION public.update_tbl_subscriptions_recordhash();
CREATE TRIGGER update_last_updated_subscriptions_trigger BEFORE UPDATE ON public.tbl_subscriptions FOR EACH ROW EXECUTE FUNCTION public.update_last_updated();

-- Привязка песни к тарифу (0 = не продаётся отдельно / дефолтный тариф). Участвует в recordhash
-- tbl_settings — см. правку deploy/recordhash_settings.sql (применять этот файл ВМЕСТЕ с ней).
ALTER TABLE public.tbl_settings ADD COLUMN IF NOT EXISTS id_tariff integer DEFAULT 0 NOT NULL;
