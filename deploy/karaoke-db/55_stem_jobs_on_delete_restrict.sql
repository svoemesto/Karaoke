-- BR-537 (аудит #336 / P-71-05, OpenProject #393): запрет каскадного удаления
-- заданий «Создать минусовку» вместе с пользователем сайта.
--
-- ЧТО БЫЛО. tbl_stem_jobs.site_user_id → ON DELETE CASCADE (22_stem_jobs.sql). Удаление
-- пользователя сайта (webvue3 → POST /api/siteusers/delete) уносило строки его заданий.
-- Объекты MinIO (`karaoke/stemjobs/<id>/original.*` и стемы) удаляет ТОЛЬКО код
-- (`StemJobCleanup.cleanupJob`) и только по строкам tbl_stem_jobs; после каскада уборщик
-- задание не находит, объекты остаются в бакете навсегда (бакет растёт, квота кончается),
-- а пользователь теряет доступ к уже оплаченным результатам.
--
-- ЧТО СТАЛО. FK пересоздан с ON DELETE RESTRICT: пока у пользователя есть задания,
-- удаление отклоняется БД. Строки и объекты уходят штатной уборкой — по `delete_requested`
-- или по истечении `expires_at` (StemJobPollScheduler.cleanup → StemJobCleanup), после чего
-- пользователя можно удалить.
--
-- 22_stem_jobs.sql теперь создаёт таблицу сразу с RESTRICT (для новых БД), но на
-- существующих БД это no-op (CREATE TABLE без IF NOT EXISTS — файл применяется один раз),
-- поэтому для LOCAL и PROD нужен отдельный шаг ниже.
--
-- Идемпотентно: повторный запуск ничего не ломает.
-- Применять вручную на КАЖДОЙ БД отдельно (см. knowledge/system/infra/sql-migrations.md,
-- Решение 4).
--
-- NB: удаление пользователя с активными/готовыми заданиями теперь отклоняется БД
-- ("update or delete on table \"tbl_stem_jobs\" violates foreign key constraint
-- \"tbl_stem_jobs_site_user_id_fkey\"") — это ожидаемое fail-safe поведение. Автоматическую
-- уборку объектов перед удалением пользователя (app-level outbox) решает владелец: она
-- требует удаления объектов MinIO ДО строк, а БД сама объекты удалять не умеет.

-- 1. Убираем все FK-ограничения на site_user_id (имя в конкретной БД может отличаться).
DO $$
DECLARE
    fk_name text;
    site_user_attnum smallint;
BEGIN
    IF to_regclass('public.tbl_stem_jobs') IS NULL THEN
        RAISE NOTICE 'tbl_stem_jobs не найдена — миграция не применима';
        RETURN;
    END IF;

    SELECT attnum INTO site_user_attnum
    FROM pg_attribute
    WHERE attrelid = 'public.tbl_stem_jobs'::regclass
      AND attname = 'site_user_id'
      AND NOT attisdropped;

    IF site_user_attnum IS NULL THEN
        RAISE NOTICE 'tbl_stem_jobs.site_user_id не найден — миграция не применима';
        RETURN;
    END IF;

    FOR fk_name IN
        SELECT c.conname
        FROM pg_constraint c
        WHERE c.conrelid = 'public.tbl_stem_jobs'::regclass
          AND c.contype = 'f'
          AND c.conkey = ARRAY[site_user_attnum]
    LOOP
        EXECUTE format('ALTER TABLE public.tbl_stem_jobs DROP CONSTRAINT %I', fk_name);
    END LOOP;
END;
$$;

-- 2. Создаём ограничение заново — с запретом удаления пользователя, у которого есть задания.
ALTER TABLE public.tbl_stem_jobs
    ADD CONSTRAINT tbl_stem_jobs_site_user_id_fkey
    FOREIGN KEY (site_user_id)
    REFERENCES public.tbl_site_users(id)
    ON DELETE RESTRICT;
