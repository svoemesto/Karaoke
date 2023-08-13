select count(DISTINCT id) as songs
from tbl_settings
where id_boosty != '' AND id_boosty IS NOT NULL