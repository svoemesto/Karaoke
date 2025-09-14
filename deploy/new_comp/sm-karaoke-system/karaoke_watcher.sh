#!/bin/bash

# Путь к директории с файлами
SCRIPTS_DIR="/sm-karaoke/system/scriptsFromDocker"

# Файл для хранения списка уже обработанных файлов
PROCESSED_FILE="/tmp/karaoke_processed_scripts"

# Создаем файл списка обработанных файлов, если его нет
if [[ ! -f "$PROCESSED_FILE" ]]; then
    touch "$PROCESSED_FILE"
fi

echo "Скрипт запущен. Отслеживаю директорию: $SCRIPTS_DIR"

# Бесконечный цикл проверки
while true; do
    # Получаем список всех .sh файлов, отсортированных по имени
    ALL_SCRIPTS=$(find "$SCRIPTS_DIR" -name "*.sh" -type f | sort -V)

    # Если есть файлы
    if [[ -n "$ALL_SCRIPTS" ]]; then
        # Обрабатываем каждый файл по очереди
        while IFS= read -r script; do
            # Проверяем, не обрабатывали ли мы этот файл ранее
            # if ! grep -Fxq "$script" "$PROCESSED_FILE"; then
                echo "[$(date)] Обнаружен новый скрипт: $script"

                # Делаем файл исполняемым
                chmod +x "$script"

                # Запускаем скрипт
                echo "[$(date)] Запускаю: $script"
                "$script"

                # Проверяем код возврата
                if [[ $? -eq 0 ]]; then
                    echo "[$(date)] Скрипт выполнен успешно: $script"
                else
                    echo "[$(date)] Ошибка при выполнении скрипта: $script"
                fi

                # Удаляем файл
                rm -f "$script"
                echo "[$(date)] Файл удалён: $script"

                # Добавляем в список обработанных
                echo "$script" >> "$PROCESSED_FILE"
            # fi
        done <<< "$ALL_SCRIPTS"
    fi

    # Ждем 1 секунду перед следующей проверкой
    sleep 1
done
