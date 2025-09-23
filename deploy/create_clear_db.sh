#!/bin/bash
echo Создание файла чистой базы данных из контейнера karaoke-db...
docker exec -it karaoke-db bash -c 'pg_dump -U postgres --file="/dumps/karaoke_clear_dump.sql" --dbname=karaoke --create --schema-only --no-owner --no-privileges'
sudo chmod -R 777 /home/nsa/Karaoke/deploy/karaoke-db
echo Перемещение файла в папку /sm-karaoke/system/dumps
mv /home/nsa/Karaoke/deploy/karaoke-db/karaoke_clear_dump.sql /sm-karaoke/system/dumps
