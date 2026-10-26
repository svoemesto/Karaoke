select * from public.tbl_songs inner join
(select id_sponsr as id_dbl
from public.tbl_songs
where id_sponsr <> ''
group by id_sponsr
having count(id) > 1) tbldbl on tbl_songs.id_sponsr = tbldbl.id_dbl
