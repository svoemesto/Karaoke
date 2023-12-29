Выключение Убунту из командной строки:
`sudo shutdown -h now`

Перезагрузить Убунту из командной строки:
`sudo reboot`

Подключение по shh на примере сервера в ВиртуалБоксе:

`ssh nsa@localhost -p 2222`


Как скопировать файл по SSH с локальной машины на удалённый сервер:

scp -P [порт] [путь к файлу на локальной машине] [имя пользователя]@[имя сервера/ip-адрес]:[путь к файлу на сервере]

Если на удалённой машине нестандартный порт - указываем параметр -P

Пример команды:

`scp -P 2222 "/home/nsa/Documents/Караоке/censored.txt" nsa@localhost:~/Karaoke`

Если нужно скопировать папку - указываем параметр -r

`scp -r -P 2222 "/home/nsa/Documents/Караоке/Digest" nsa@localhost:~/Karaoke`

Очистка корневой файловой системы:

`sudo apt autoremove`

`sudo apt autoclean`

`sudo apt clean`

Запустить bash в контейнере:

`docker exec -it <container-name-or-id> bash`

Смена версии Java:

`sudo update-alternatives --config java`

Регулярно чистить докер от мусора:

`docker system prune -a`
