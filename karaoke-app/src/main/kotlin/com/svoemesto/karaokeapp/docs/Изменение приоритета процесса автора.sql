update tbl_processes
set
    process_priority = process_priority + 20
where
    process_priority = 19 and
    process_name like '%Infornal%'
and process_status = 'WAITING'