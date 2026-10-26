select count(DISTINCT id) as songs
from tbl_songs
where id_boosty != '' AND id_boosty IS NOT NULL