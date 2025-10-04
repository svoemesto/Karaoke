Скопировать структуру папок из папки new_comp на компьютер

На новом компьютере должны быть папки:

/sm-karaoke/system
/sm-karaoke/work
/sm-karaoke/done1
/sm-karaoke/done2
/sm-karaoke/store

Их можно создать коммандой:

sudo mkdir -p /sm-karaoke/system
sudo mkdir -p /sm-karaoke/work
sudo mkdir -p /sm-karaoke/done1
sudo mkdir -p /sm-karaoke/done2
sudo mkdir -p /sm-karaoke/store

Допустим на компьютере есть папке /home/nsa/Documents/Karaoke
в ней есть папке _system

И далее прописать монтирование этих папок в /etc/fstab например так:

/home/nsa/Documents/Karaoke/_system /sm-karaoke/system none defaults,bind 0 0
/home/nsa/Documents/Karaoke /sm-karaoke/work none defaults,bind 0 0
/home/nsa/Documents/Karaoke /sm-karaoke/done1 none defaults,bind 0 0
/home/nsa/Documents/Karaoke /sm-karaoke/done2 none defaults,bind 0 0
/disks/HDD_8Tb_Clouds/Yandex.Disk/Karaoke /sm-karaoke/store none defaults,bind 0 0

Применить изменея в /etc/fstab можно командой: sudo mount -a

Скопировать папку /sm-karaoke/system/SpaceBox4096

Переходим в папку /sm-karaoke/system/deploy и запускаем в ней терминал

Создаем новую чистую базу данных: `./do.sh create_clear_db`
Запускаем приложение: `./do.sh start_app2`
Запуск фронтэнда: `./do.sh start_webvue`

Скачивание свежего приложения: `./do.sh pull_app`
Скачивание свежего фронтэнда: `./do.sh pull_webvue`
