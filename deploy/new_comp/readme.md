Скопировать структуру папок из папки new_comp на компьютер
Скопировать папку /home/nsa/Documents/SpaceBox4096

В файле /home/nsa/Documents/Караоке/deploy/nginx.conf поменять айпишники на текущий айпишник машины
Узнать айпишник можно командой `hosthame -I`

Переходим в папку /home/nsa/Documents/Караоке/deploy и запускаем в ней терминал

Создаем новую чистую базу данных: `./do.sh create_clear_db`
Запускаем приложение: `./do.sh start_app2`
Запуск фронтэнда: `./do.sh webvue`

Скачивание свежего приложения: `./do.sh pull_app`
Скачивание свежего фронтэнда: `./do.sh pull_webvue`
