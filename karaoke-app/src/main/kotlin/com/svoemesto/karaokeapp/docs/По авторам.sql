select song_author, count(DISTINCT song_album) as albums, count(DISTINCT id) as songs
from tbl_settings
where root_folder NOT LIKE '%/Разное/%'
group by song_author