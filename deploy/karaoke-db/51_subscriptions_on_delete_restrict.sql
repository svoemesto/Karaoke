-- BR-041 (аудит #336 / P-70-03, OpenProject #371): запрет каскадного удаления
-- платёжной истории вместе с пользователем сайта.
--
-- Что было: tbl_subscriptions.site_user_id → ON DELETE CASCADE. Админская кнопка
-- «удалить пользователя сайта» (webvue3 → POST /api/siteusers/delete) удаляла
-- строку tbl_site_users, и FK-каскад уносил все tbl_subscriptions пользователя —
-- включая PAID-записи с yookassa_payment_id, final_price и paid_at. Это
-- необратимая потеря платёжной истории (чеки 54-ФЗ, сверка с ЮKassa, возвраты,
-- сохранённый мандат автопродления yookassa_payment_method_id).
--
-- Что стало: 15_monetization.sql теперь создаёт таблицу с ON DELETE RESTRICT
-- (для новых БД). Но 15_monetization.sql на существующей БД — no-op
-- (CREATE TABLE IF NOT EXISTS), поэтому для LOCAL и PROD нужен отдельный шаг:
-- пересоздать FK-ограничение с RESTRICT.
--
-- Идемпотентно: повторный запуск ничего не ломает.
-- Применять вручную на КАЖДОЙ БД отдельно (см.
-- knowledge/system/infra/sql-migrations.md, Решение 4).
--
-- NB: после применения удаление пользователя с платежами будет отклонено БД
-- ("update or delete on table \"tbl_subscriptions\" violates foreign key
-- constraint \"tbl_subscriptions_site_user_id_fkey\"") — это ожидаемое
-- fail-safe поведение, а не регресс.

-- 1. Убираем все FK-ограничения на site_user_id (независимо от того, как он
--    назван в конкретной БД).
DO $$
DECLARE
    fk_name text;
    site_user_attnum smallint;
BEGIN
    -- На БД, где 15_monetization.sql ещё не применён, таблицы нет —
    -- миграция не применима (не падаем: файл лежит в initdb-каталоге и
    -- выполняется на каждой новой БД подряд).
    IF to_regclass('public.tbl_subscriptions') IS NULL THEN
        RAISE NOTICE 'tbl_subscriptions не найдена — миграция не применима';
        RETURN;
    END IF;

    SELECT attnum INTO site_user_attnum
    FROM pg_attribute
    WHERE attrelid = 'public.tbl_subscriptions'::regclass
      AND attname = 'site_user_id'
      AND NOT attisdropped;

    IF site_user_attnum IS NULL THEN
        RAISE NOTICE 'tbl_subscriptions.site_user_id не найден — миграция не применима';
        RETURN;
    END IF;

    FOR fk_name IN
        SELECT c.conname
        FROM pg_constraint c
        WHERE c.conrelid = 'public.tbl_subscriptions'::regclass
          AND c.contype = 'f'
          AND c.conkey = ARRAY[site_user_attnum]
    LOOP
        EXECUTE format('ALTER TABLE public.tbl_subscriptions DROP CONSTRAINT %I', fk_name);
    END LOOP;
END;
$$;

-- 2. Создаём ограничение заново — с запретом удаления пользователя с платежами.
ALTER TABLE public.tbl_subscriptions
    ADD CONSTRAINT tbl_subscriptions_site_user_id_fkey
    FOREIGN KEY (site_user_id)
    REFERENCES public.tbl_site_users(id)
    ON DELETE RESTRICT;
