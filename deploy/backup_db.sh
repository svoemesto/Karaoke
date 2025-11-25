#!/bin/bash
#echo Создание файла бэкаба базы данных из контейнера karaoke-db...
#docker exec -it karaoke-db bash -c 'pg_dump -U postgres --file="/docker-entrypoint-initdb.d/karaoke_dump.sql" --dbname=karaoke --create'
#sudo chmod -R 777 /home/nsa/Karaoke/deploy/karaoke-db
#echo Перемещение файла бэкаба базы данных в папку /sm-karaoke/system/dumps
#mv /home/nsa/Karaoke/deploy/karaoke-db/karaoke_dump.sql /sm-karaoke/system/dumps
#!/bin/bash

# --- Настройки ---
# Укажите папку, куда копировать архивы
BACKUP_DIR="/sm-karaoke/store/db_backup"
# Укажите имя файла бэкапа без расширения (дата будет добавлена)
BACKUP_NAME_BASE="karaoke_backup"
# Укажите максимальное количество файлов в папке
MAX_BACKUPS=5

# --- Логика ---
echo "Начинаем создание бэкапа..."

# 1. Получаем текущую дату и время в формате YYYYMMDD_HHMMSS
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")

# 2. Определяем имена файлов
DUMP_FILE_NAME="${BACKUP_NAME_BASE}_${TIMESTAMP}.sql"
ZIP_FILE_NAME="${BACKUP_NAME_BASE}_${TIMESTAMP}.sql.zip"

# 3. Создаём бэкап внутри контейнера с использованием нового имени
echo "Создание файла бэкапа '${DUMP_FILE_NAME}' в контейнере karaoke-db..."
# Используем docker exec и pg_dump, указывая новое имя файла
# Важно: используем -o для вывода в stdout, и перенаправляем в файл внутри контейнера
# Это позволяет избежать проблем с правами доступа к /docker-entrypoint-initdb.d/
# и создаёт бэкап в текущей директории сессии (обычно /).
# Затем копируем файл из контейнера.
docker exec karaoke-db bash -c "pg_dump -U postgres --dbname=karaoke --create --no-password" > "/tmp/${DUMP_FILE_NAME}"

if [ $? -ne 0 ]; then
    echo "Ошибка при создании бэкапа в контейнере."
    exit 1
fi

# Перемещаем файл из /tmp (на хосте) во временную папку, чтобы не мешал
TEMP_DUMP_PATH="/tmp/${DUMP_FILE_NAME}"
FINAL_DUMP_PATH="/home/nsa/Karaoke/deploy/karaoke-db/${DUMP_FILE_NAME}"
mv "$TEMP_DUMP_PATH" "$FINAL_DUMP_PATH"

# 4. Устанавливаем права на файл бэкапа (если нужно)
chmod 644 "$FINAL_DUMP_PATH" # или 777, если требуете, но 644 безопаснее

# 5. Архивируем файл бэкапа в ZIP
echo "Архивация файла бэкапа в $ZIP_FILE_NAME..."
zip -j "${FINAL_DUMP_PATH}.zip" "$FINAL_DUMP_PATH"

if [ $? -ne 0 ]; then
    echo "Ошибка при архивации файла бэкапа."
    # Удаляем временный файл бэкапа, если архивация не удалась
    rm -f "$FINAL_DUMP_PATH"
    exit 1
fi

# 6. Создаём папку назначения, если её нет
mkdir -p "$BACKUP_DIR"

# 7. Копируем ZIP-архив в папку назначения
echo "Копирование архива в $BACKUP_DIR/$ZIP_FILE_NAME..."
cp "${FINAL_DUMP_PATH}.zip" "$BACKUP_DIR/"

if [ $? -ne 0 ]; then
    echo "Ошибка при копировании архива в $BACKUP_DIR."
    # Удаляем локальный архив и исходный sql
    rm -f "${FINAL_DUMP_PATH}" "${FINAL_DUMP_PATH}.zip"
    exit 1
fi

# 8. Удаляем локальные файлы (sql и zip) после копирования
rm -f "$FINAL_DUMP_PATH" "${FINAL_DUMP_PATH}.zip"
echo "Локальные файлы бэкапа и архива удалены."

# 9. Проверяем количество файлов в папке назначения и удаляем старые
echo "Проверка количества файлов в $BACKUP_DIR..."
# Ищем все .zip файлы, содержащие 'karaoke_backup' в имени, сортируем по времени модификации (старые первыми), и получаем количество
ALL_BACKUP_ZIPS=($(find "$BACKUP_DIR" -maxdepth 1 -type f -name "${BACKUP_NAME_BASE}_*.sql.zip" -printf '%T@ %p\n' | sort -n | cut -d' ' -f2-))
NUM_BACKUPS=${#ALL_BACKUP_ZIPS[@]}

if [ $NUM_BACKUPS -gt $MAX_BACKUPS ]; then
    NUM_TO_DELETE=$((NUM_BACKUPS - MAX_BACKUPS))
    echo "Найдено $NUM_BACKUPS архивов, нужно удалить $NUM_TO_DELETE старых."

    for (( i=0; i<NUM_TO_DELETE; i++ )); do
        file_to_delete="${ALL_BACKUP_ZIPS[$i]}"
        if [ -n "$file_to_delete" ] && [ -f "$file_to_delete" ]; then
            # Проверяем, что файл не пустой перед удалением (хотя find с -type f уже проверяет существование)
            if [ -s "$file_to_delete" ]; then
                echo "Удаление старого архива: $file_to_delete"
                rm -f "$file_to_delete"
            else
                echo "Файл $file_to_delete пустой или не существует, пропускаем."
            fi
        fi
    done
else
    echo "Количество архивов ($NUM_BACKUPS) не превышает лимит ($MAX_BACKUPS). Удаление не требуется."
fi

echo "Бэкап завершён успешно. Архив: $BACKUP_DIR/$ZIP_FILE_NAME"