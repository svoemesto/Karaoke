-- Спека 319 — Bulk actions for processes (v2, OpenProject WP #68).
--
-- Расширяет `tbl_processes_audit.action` CHECK constraint для bulk-операций
-- и добавляет колонку `batch_id UUID` для группировки audit-записей по batch.
-- Зависимости: миграция 47 (admin_process_audit). Обратная совместимость: да
-- (CHECK расширяется, колонка nullable, индекс partial).
--
-- Применяется один раз на LOCAL и PROD отдельно.
--
-- ВАЖНО: для bulk_delete каскадное удаление (FK ON DELETE CASCADE на tbl_processes.id
-- из миграции 47) стирает audit-строки вместе с процессом. Это согласованное поведение
-- для hard-delete («без следов в БД», Clarifications Q1 спеки 319). Если в будущем
-- потребуется сохранять audit после hard-delete — отдельная миграция (FK → SET NULL).

-- 1. Расширить CHECK constraint на action.
ALTER TABLE tbl_processes_audit
    DROP CONSTRAINT IF EXISTS tbl_processes_audit_action_check;

ALTER TABLE tbl_processes_audit
    ADD CONSTRAINT tbl_processes_audit_action_check
    CHECK (action IN ('EDIT', 'RETRY', 'DELETE', 'bulk_update_field', 'bulk_delete'));

-- 2. Добавить колонку batch_id для группировки bulk-операций.
ALTER TABLE tbl_processes_audit
    ADD COLUMN IF NOT EXISTS batch_id UUID NULL;

-- 3. Индекс по batch_id для быстрого report-эндпоинта (US3 спеки 319).
CREATE INDEX IF NOT EXISTS idx_tbl_processes_audit_batch_id
    ON tbl_processes_audit(batch_id) WHERE batch_id IS NOT NULL;

-- DOWN-секция (выполнять вручную при необходимости):
--   DROP INDEX IF EXISTS idx_tbl_processes_audit_batch_id;
--   ALTER TABLE tbl_processes_audit DROP COLUMN IF EXISTS batch_id;
--   ALTER TABLE tbl_processes_audit DROP CONSTRAINT IF EXISTS tbl_processes_audit_action_check;
--   ALTER TABLE tbl_processes_audit ADD CONSTRAINT tbl_processes_audit_action_check
--       CHECK (action IN ('EDIT', 'RETRY', 'DELETE'));
