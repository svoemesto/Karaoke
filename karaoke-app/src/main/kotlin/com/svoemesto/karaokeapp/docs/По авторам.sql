select song_author, count(DISTINCT song_album) as songAlbums, count(DISTINCT id) as songs
from tbl_songs
group by song_author