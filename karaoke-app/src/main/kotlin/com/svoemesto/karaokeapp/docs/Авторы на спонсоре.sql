select distinct song_author
from public.tbl_settings
where id_sponsr <> '' and tags not like '%+%'
order by song_author