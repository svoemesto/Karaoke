select * from public.tbl_settings inner join
(select id_sponsr as id_dbl
from public.tbl_settings
where id_sponsr <> ''
group by id_sponsr
having count(id) > 1) tbldbl on tbl_settings.id_sponsr = tbldbl.id_dbl
