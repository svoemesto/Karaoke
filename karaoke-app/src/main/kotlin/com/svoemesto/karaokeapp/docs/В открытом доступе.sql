select count(DISTINCT id) as songs
from tbl_settings
where publish_date != ''
  and publish_date is not null
  and publish_time != ''
  and publish_time is not null
  and to_timestamp(CONCAT(publish_date, ' ', publish_time), 'DD.MM.YY HH24:MI') <= current_timestamp