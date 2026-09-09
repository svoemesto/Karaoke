#!/bin/bash
# Диагностика данных для /api/public/authors/{authorId}/albums
# Запускать на той же машине, где работает karaoke-web.
#
# Использование: bash tools/check-albums-data.sh [authorId]
# (если authorId не указан — проверим общее состояние)

set -e

AUTHOR_ID="${1:-}"

echo "=== 1. Подключение к БД ==="
# Используем те же креды, что и karaoke-web
# На локальной машине (nsa-i9) обычно localhost:5432, user=postgres, db=karaoke
# Если у вас другое — поправьте переменные ниже
PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5432}"
PGUSER="${PGUSER:-postgres}"
PGDATABASE="${PGDATABASE:-karaoke}"
export PGHOST PGPORT PGUSER PGDATABASE

echo "host=$PGHOST port=$PGPORT user=$PGUSER db=$PGDATABASE"
echo ""

echo "=== 2. Сколько альбомов всего в tbl_albums? ==="
psql -t -c "SELECT COUNT(*) FROM tbl_albums;"
echo ""

echo "=== 3. Альбомы с author_id > 0 (привязанные к авторам) ==="
psql -t -c "SELECT COUNT(*) FROM tbl_albums WHERE author_id > 0;"
echo ""

echo "=== 4. Альбомы с заполненными счётчиками ==="
psql -t -c "SELECT COUNT(*) FROM tbl_albums WHERE total_song_count > 0;"
echo ""
psql -t -c "SELECT COUNT(*) FROM tbl_albums WHERE ready_song_count > 0;"
echo ""

echo "=== 5. Сколько песен с album_id (FK) ==="
psql -t -c "SELECT COUNT(*) FROM tbl_songs WHERE album_id IS NOT NULL;"
echo ""

echo "=== 6. Первые 5 альбомов (диагностика структуры) ==="
psql -c "SELECT id, author_id, year, name, skip, total_song_count, ready_song_count FROM tbl_albums ORDER BY id LIMIT 5;"
echo ""

if [ -n "$AUTHOR_ID" ]; then
  echo "=== 7. Альбомы автора $AUTHOR_ID ==="
  psql -c "SELECT id, author_id, year, name, skip, total_song_count, ready_song_count FROM tbl_albums WHERE author_id = $AUTHOR_ID ORDER BY year, name;"
  echo ""
fi

echo "=== 8. Что вернёт наш endpoint (имитация запроса) ==="
if [ -n "$AUTHOR_ID" ]; then
  echo "Для автора $AUTHOR_ID:"
  psql -c "SELECT id, author_id, year, name, total_song_count, ready_song_count FROM tbl_albums WHERE skip = false AND author_id = $AUTHOR_ID AND ready_song_count > 0 ORDER BY year ASC NULLS LAST, name ASC;"
fi
echo ""
echo "=== 9. Авторы в tbl_albums (top-10) ==="
psql -c "SELECT author_id, COUNT(*) AS albums_count FROM tbl_albums WHERE author_id > 0 GROUP BY author_id ORDER BY albums_count DESC LIMIT 10;"
echo ""

echo "=== ГОТОВО ==="
echo "Покажите мне вывод — после этого смогу точно сказать, в чём проблема."