select count(DISTINCT id) as songs
from tbl_settings
where to_date(publish_date, 'DD.MM.YY') <= CURRENT_DATE