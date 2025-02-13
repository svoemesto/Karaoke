select * from tbl_authors
where watched = true AND last_album_ym <> last_album_processed
order by author