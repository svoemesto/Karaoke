#!/bin/bash
echo Создание файла чистой базы данных из контейнера karaoke-db...
docker exec -it karaoke-db bash -c 'pg_dump -U postgres --file="/docker-entrypoint-initdb.d/karaoke_clear_dump.sql" --dbname=karaoke --create --schema-only --no-owner --no-privileges'
sudo chmod -R 777 /home/nsa/Karaoke/deploy/karaoke-db
echo Перемещение файла в папку /home/nsa/Documents/dumps
mv /home/nsa/Karaoke/deploy/karaoke-db/karaoke_clear_dump.sql /home/nsa/Documents/dumps
