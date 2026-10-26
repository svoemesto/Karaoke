select song_author, count(DISTINCT song_album) as songAlbums, count(DISTINCT id) as songs
from tbl_songs
where id_boosty != '' AND id_boosty IS NOT NULL AND root_folder NOT LIKE '%/Разное/%'
group by song_author