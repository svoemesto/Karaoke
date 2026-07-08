-- Корзина: копим песни для пакетной оплаты одним заказом (несколько tbl_subscriptions с общим order_id
-- и общим yookassa_payment_id). Одиночная мгновенная покупка (SongSubscriptionModal) не меняется —
-- у неё order_id остаётся NULL.
--
-- ВАЖНО: применять вручную на КАЖДОЙ БД отдельно (LOCAL + PROD 79.174.95.69:8832).

CREATE TABLE IF NOT EXISTS public.tbl_cart_items (
    id integer NOT NULL,
    site_user_id integer NOT NULL REFERENCES public.tbl_site_users(id) ON DELETE CASCADE,
    id_song bigint NOT NULL,
    added_at timestamp without time zone DEFAULT now() NOT NULL
);

ALTER TABLE public.tbl_cart_items ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.tbl_cart_items_id_seq START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1
    );

ALTER TABLE ONLY public.tbl_cart_items ADD CONSTRAINT tbl_cart_items_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.tbl_cart_items ADD CONSTRAINT uq_tbl_cart_items_user_song UNIQUE (site_user_id, id_song);
CREATE INDEX idx_tbl_cart_items_site_user_id ON public.tbl_cart_items (site_user_id);

-- Заказ корзины: все позиции одного оформления делят один order_id и один yookassa_payment_id.
ALTER TABLE public.tbl_subscriptions ADD COLUMN IF NOT EXISTS order_id character varying(36);
CREATE INDEX IF NOT EXISTS idx_tbl_subscriptions_order_id ON public.tbl_subscriptions (order_id) WHERE order_id IS NOT NULL;

-- order_id участвует в recordhash tbl_subscriptions (уже существующий триггер) — обновляем функцию +
-- backfill для существующих строк (см. инвариант «recordhash trigger new columns»).
CREATE OR REPLACE FUNCTION public.update_tbl_subscriptions_recordhash() RETURNS trigger
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
                                COALESCE(NEW.paid_at::TEXT, '') ||
                                COALESCE(NEW.order_id, '')
        );
RETURN NEW;
END;
$$;

UPDATE public.tbl_subscriptions SET recordhash = md5(
    COALESCE(id::TEXT, '') ||
    COALESCE(site_user_id::TEXT, '') ||
    COALESCE(scope, '') ||
    COALESCE(id_song::TEXT, '') ||
    COALESCE(tariff_id::TEXT, '') ||
    COALESCE(period_days::TEXT, '') ||
    COALESCE(base_price::TEXT, '') ||
    COALESCE(discount::TEXT, '') ||
    COALESCE(final_price::TEXT, '') ||
    COALESCE(promo_applied, '') ||
    COALESCE(status, '') ||
    COALESCE(yookassa_payment_id, '') ||
    COALESCE(auto_renew::TEXT, '') ||
    COALESCE(yookassa_payment_method_id, '') ||
    COALESCE(paid_at::TEXT, '') ||
    COALESCE(order_id, '')
) WHERE id > 0;

-- Также ЮKassa-обновляемый yookassa_payment_id раньше был UNIQUE в отдельных строках — для заказа
-- корзины несколько строк tbl_subscriptions делят ОДИН и тот же yookassa_payment_id, поэтому уникальный
-- индекс по нему больше не может быть настоящим UNIQUE.
DROP INDEX IF EXISTS idx_tbl_subscriptions_yookassa_payment_id;
CREATE INDEX idx_tbl_subscriptions_yookassa_payment_id ON public.tbl_subscriptions (yookassa_payment_id) WHERE yookassa_payment_id <> '';
