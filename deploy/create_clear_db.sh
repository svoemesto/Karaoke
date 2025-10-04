#!/bin/bash
echo Создание файла чистой базы данных из контейнера karaoke-db...
docker exec -it karaoke-db bash -c 'pg_dump -U postgres --file="/dumps/karaoke_clear_dump.sql" --dbname=karaoke --create --schema-only --no-owner --no-privileges'
sudo chmod -R 777 /sm-karaoke/work/_system/dumps
sudo chmod -R 777 /home/nsa/Karaoke/deploy/new_comp/sm-karaoke-system/dumps
echo Копирование файла в папку /home/nsa/Karaoke/deploy/new_comp/sm-karaoke-system/dumps
rm /home/nsa/Karaoke/deploy/new_comp/sm-karaoke-system/dumps/karaoke_clear_dump.sql
cp /sm-karaoke/system/dumps/karaoke_clear_dump.sql /home/nsa/Karaoke/deploy/new_comp/sm-karaoke-system/dumps
