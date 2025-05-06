update tbl_processes
set
    process_priority = 10
where
    process_name like '%Окопные свечи%'
and process_status = 'WAITING'