select id_sponsr as id,
       song_author as tags,
       concat('Петь караоке композицию «', song_name, '» группы «', song_author, '» онлайн со словами. Текст песни с альбома «', song_album, '» ', song_year, ' года под оригинальный аудиотрек, под минус, под плюс. Любимые русские хиты группы «', song_author, '» с идеально синхронизированным текстом, минусовкой и плюсовкой в видео формате караоке у вас дома!') as teaser
from public.tbl_settings
where id_sponsr <> ''
order by id_sponsr
