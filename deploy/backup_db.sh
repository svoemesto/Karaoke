#!/bin/bash
echo Создание файла бэкаба базы данных из контейнера karaoke-db...
docker exec -it karaoke-db bash -c 'pg_dump -U postgres --file="/docker-entrypoint-initdb.d/karaoke_dump.sql" --dbname=karaoke --create'
sudo chmod -R 777 /home/nsa/Karaoke/deploy/karaoke-db
echo Перемещение файла бэкаба базы данных в папку /sm-karaoke/system/dumps
mv /home/nsa/Karaoke/deploy/karaoke-db/karaoke_dump.sql /sm-karaoke/system/dumps
