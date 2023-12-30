select
    tbl_events.song_id,
    sett.song_author,
    sett.song_album,
    sett.song_name,
    (CASE WHEN selSong.song is null THEN 0 ELSE selSong.song END +
     CASE WHEN selBoosty.boosty is null THEN 0 ELSE selBoosty.boosty END +
     CASE WHEN selVKKaraoke.vk_karaoke is null THEN 0 ELSE selVKKaraoke.vk_karaoke END +
     CASE WHEN selVKLyrics.vk_lyrics is null THEN 0 ELSE selVKLyrics.vk_lyrics END +
     CASE WHEN selDzenKaraoke.dzen_karaoke is null THEN 0 ELSE selDzenKaraoke.dzen_karaoke END +
     CASE WHEN selDzenLyrics.dzen_lyrics is null THEN 0 ELSE selDzenLyrics.dzen_lyrics END +
     CASE WHEN selTgKaraoke.tg_karaoke is null THEN 0 ELSE selTgKaraoke.tg_karaoke END +
     CASE WHEN selTgLyrics.tg_lyrics is null THEN 0 ELSE selTgLyrics.tg_lyrics END) as total,
    CASE WHEN selSong.song is null THEN 0 ELSE selSong.song END as song,
    CASE WHEN selBoosty.boosty is null THEN 0 ELSE selBoosty.boosty END as boosty,
    CASE WHEN selVKKaraoke.vk_karaoke is null THEN 0 ELSE selVKKaraoke.vk_karaoke END as vk_kar,
    CASE WHEN selVKLyrics.vk_lyrics is null THEN 0 ELSE selVKLyrics.vk_lyrics END as vk_lyr,
    CASE WHEN selDzenKaraoke.dzen_karaoke is null THEN 0 ELSE selDzenKaraoke.dzen_karaoke END as dzen_kar,
    CASE WHEN selDzenLyrics.dzen_lyrics is null THEN 0 ELSE selDzenLyrics.dzen_lyrics END as dzen_lyr,
    CASE WHEN selTgKaraoke.tg_karaoke is null THEN 0 ELSE selTgKaraoke.tg_karaoke END as tg_kar,
    CASE WHEN selTgLyrics.tg_lyrics is null THEN 0 ELSE selTgLyrics.tg_lyrics END as tg_lyr
from tbl_events
left join tbl_settings sett on tbl_events.song_id = sett.id
left join
     (
         select song_id, count(*) as song
         from tbl_events
         where rest_name = 'song'
         group by song_id
     ) selSong on tbl_events.song_id = selSong.song_id
left join
     (
         select song_id, count(*) as boosty
         from tbl_events
         where link_name = 'boosty'
         group by song_id
     ) selBoosty on tbl_events.song_id = selBoosty.song_id
left join
     (
         select song_id, count(*) as vk_lyrics
         from tbl_events
         where song_version = 'lyrics' and link_name = 'vk'
         group by song_id
     ) selVKLyrics on tbl_events.song_id = selVKLyrics.song_id
left join
     (
         select song_id, count(*) as vk_karaoke
         from tbl_events
         where song_version = 'karaoke' and link_name = 'vk'
         group by song_id
     ) selVKKaraoke on tbl_events.song_id = selVKKaraoke.song_id
left join
     (
         select song_id, count(*) as dzen_lyrics
         from tbl_events
         where song_version = 'lyrics' and link_name = 'dzen'
         group by song_id
     ) selDzenLyrics on tbl_events.song_id = selDzenLyrics.song_id
left join
     (
         select song_id, count(*) as dzen_karaoke
         from tbl_events
         where song_version = 'karaoke' and link_name = 'dzen'
         group by song_id
     ) selDzenKaraoke on tbl_events.song_id = selDzenKaraoke.song_id
left join
     (
         select song_id, count(*) as tg_lyrics
         from tbl_events
         where song_version = 'lyrics' and link_name = 'tg'
         group by song_id
     ) selTgLyrics on tbl_events.song_id = selTgLyrics.song_id
left join
     (
         select song_id, count(*) as tg_karaoke
         from tbl_events
         where song_version = 'karaoke' and link_name = 'tg'
         group by song_id
     ) selTgKaraoke on tbl_events.song_id = selTgKaraoke.song_id
where tbl_events.song_id is not null
group by
    tbl_events.song_id,
    sett.song_author,
    sett.song_album,
    sett.song_name,
    selSong.song,
    selBoosty.boosty,
    selVKKaraoke.vk_karaoke,
    selVKLyrics.vk_lyrics,
    selDzenKaraoke.dzen_karaoke,
    selDzenLyrics.dzen_lyrics,
    selTgKaraoke.tg_karaoke,
    selTgLyrics.tg_lyrics
order by total desc
;