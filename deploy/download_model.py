#!/usr/bin/env python3
"""Скачивание модели faster-whisper через стандартную библиотеку urllib.
Не требует git, curl, wget или huggingface-cli.
Проверяет кеш - не скачивает повторно уже загруженные файлы.

Модель берётся из переменной окружения WHISPER_MODEL (тот же список значений, что
принимает hwdsl2/whisper-server, см. docker-compose-whisper.yml) - по умолчанию "medium",
если переменная не задана. HF_TOKEN (если задан) отправляется как Bearer-токен - нужен для
гейтед-репозиториев на HuggingFace (некоторые модели, в отличие от "обычного" medium, доступны
только авторизованным аккаунтам, принявшим лицензию)."""
import os
import socket
import sys
import urllib.request
import urllib.error
import json

# "Errno 101 Network is unreachable" - классическая проблема Docker + IPv6: huggingface.co
# резолвится и в IPv4, и в IPv6, Python пробует адреса по порядку, а у контейнера часто нет
# реального маршрута в IPv6 (IPv6 включён в хост-ОС, но не настроен на уровне docker-сети).
# Форсируем резолвинг только IPv4 для ВСЕХ соединений в процессе (включая urllib) - не трогает
# конфигурацию docker/хоста, чинится на уровне приложения.
_original_getaddrinfo = socket.getaddrinfo


def _getaddrinfo_ipv4_only(host, port, family=0, type=0, proto=0, flags=0):
    return _original_getaddrinfo(host, port, socket.AF_INET, type, proto, flags)


socket.getaddrinfo = _getaddrinfo_ipv4_only

CACHE_DIR = "/var/lib/whisper"
WHISPER_MODEL = os.environ.get("WHISPER_MODEL", "medium")

# CTranslate2-конверсия (нужна faster-whisper) публикуется НЕ у всех моделей одним и тем же
# издателем на HuggingFace: большинство - у Systran (faster-whisper-{model}), но large-v3-turbo/
# turbo конвертировал не Systran, а сообщество - тот же репозиторий, что использует сама
# библиотека faster-whisper внутри (см. faster_whisper.utils._MODELS). ВАЖНО: openai/whisper-* -
# это ИСХОДНЫЙ чекпоинт в формате HuggingFace Transformers, НЕ формат CTranslate2 - для
# WHISPER_ENGINE=faster-whisper (см. docker-compose-whisper.yml) нужен именно CT2-репозиторий,
# иначе файлы (model.bin и т.п.) не совпадут по формату/имени.
_MODEL_REPO_OVERRIDES = {
    "large-v3-turbo": "mobiuslabsgmbh/faster-whisper-large-v3-turbo",
    "turbo": "mobiuslabsgmbh/faster-whisper-large-v3-turbo",
}
MODEL_ID = _MODEL_REPO_OVERRIDES.get(WHISPER_MODEL, f"Systran/faster-whisper-{WHISPER_MODEL}")
BASE_URL = f"https://huggingface.co/{MODEL_ID}/resolve/main"

HF_TOKEN = os.environ.get("HF_TOKEN", "").strip()
AUTH_HEADERS = {"Authorization": f"Bearer {HF_TOKEN}"} if HF_TOKEN else {}


def fetch_json(url: str):
    request = urllib.request.Request(url, headers=AUTH_HEADERS)
    with urllib.request.urlopen(request, timeout=30) as resp:
        return json.loads(resp.read())


def download_file(url: str, dest: str) -> int:
    """Скачивает файл с авторизацией (в отличие от urlretrieve, который не умеет слать
    заголовки) - возвращает размер скачанного файла в байтах."""
    request = urllib.request.Request(url, headers=AUTH_HEADERS)
    with urllib.request.urlopen(request, timeout=300) as resp, open(dest, "wb") as out:
        while True:
            chunk = resp.read(1024 * 1024)
            if not chunk:
                break
            out.write(chunk)
    return os.path.getsize(dest)


# Создаем структуру каталогов как у HuggingFace cache
model_dir = os.path.join(CACHE_DIR, f"models--{MODEL_ID.replace('/', '--')}")
snapshots_dir = os.path.join(model_dir, "snapshots")
refs_dir = os.path.join(model_dir, "refs")

# Получаем актуальный revision
print(f"Модель: {MODEL_ID} (WHISPER_MODEL={WHISPER_MODEL})")
print("Проверяю актуальную версию модели...")
api_url = f"https://huggingface.co/api/models/{MODEL_ID}"
try:
    revision = fetch_json(api_url).get("sha", "main")
except Exception as e:
    print(f"Не удалось получить метаданные: {e}, использую main")
    revision = "main"

# Проверяем, есть ли уже скачанная модель. Порог размера не может быть одним фиксированным
# числом для всех моделей (tiny ~75MB, medium ~1.5GB, large-v3 ~3GB) - используем минимальный
# разумный порог "не пустой файл-заглушка" (1MB) вместо жёстких "> 1GB", который раньше молча
# считал tiny/base/small ещё не скачанными при каждом перезапуске контейнера.
MIN_MODEL_BIN_SIZE = 1_000_000  # 1MB - отсекает только пустые/оборванные файлы
existing_snapshot = None
if os.path.exists(snapshots_dir):
    for snap in os.listdir(snapshots_dir):
        # Проверяем, что в каталоге есть model.bin (главный файл модели)
        model_bin = os.path.join(snapshots_dir, snap, "model.bin")
        if os.path.exists(model_bin) and os.path.getsize(model_bin) > MIN_MODEL_BIN_SIZE:
            existing_snapshot = snap
            break

if existing_snapshot:
    size_mb = os.path.getsize(os.path.join(snapshots_dir, existing_snapshot, "model.bin")) / (1024**2)
    print(f"✓ Модель уже в кеше (revision: {existing_snapshot[:12]}..., model.bin: {size_mb:.1f} MB)")
    print("=== Скачивание не требуется ===")
    sys.exit(0)

print(f"Использую revision: {revision}")

# Получаем список файлов через tree API
tree_url = f"https://huggingface.co/api/models/{MODEL_ID}/tree/{revision}"
print("Получаю дерево файлов...")
try:
    files = fetch_json(tree_url)
    file_list = [f["path"] for f in files if f.get("type") == "file"]
    print(f"Найдено файлов: {len(file_list)}")
except Exception as e:
    print(f"Ошибка получения дерева: {e}")
    file_list = ["config.json", "model.bin", "tokenizer.json", "vocabulary.txt", "preprocessor_config.json"]

# Создаем каталоги
os.makedirs(snapshots_dir, exist_ok=True)
os.makedirs(refs_dir, exist_ok=True)

# Сохраняем revision
with open(os.path.join(refs_dir, "main"), "w") as f:
    f.write(revision + "\n")

# Скачиваем каждый файл
snapshot_path = os.path.join(snapshots_dir, revision)
os.makedirs(snapshot_path, exist_ok=True)

failed_files = []
for i, filename in enumerate(file_list, 1):
    dest = os.path.join(snapshot_path, filename)

    # Пропускаем уже скачанные файлы
    if os.path.exists(dest) and os.path.getsize(dest) > 0:
        print(f"[{i}/{len(file_list)}] ✓ {filename} (уже скачан)")
        continue

    print(f"[{i}/{len(file_list)}] Скачиваю {filename}...")
    url = f"{BASE_URL}/{filename}"

    # Создаем подкаталоги если нужно
    os.makedirs(os.path.dirname(dest), exist_ok=True)

    try:
        size_mb = download_file(url, dest) / (1024 * 1024)
        print(f"  ✓ {filename} ({size_mb:.1f} MB)")
    except urllib.error.HTTPError as e:
        hint = " (гейтед репозиторий - нужен HF_TOKEN с принятой лицензией?)" if e.code == 401 else ""
        print(f"  ✗ Ошибка скачивания {filename}: HTTP {e.code}{hint}")
        failed_files.append(filename)
        if os.path.exists(dest):
            os.remove(dest)  # не оставляем пустой/оборванный файл - иначе "уже скачан" соврёт в следующий раз
    except Exception as e:
        print(f"  ✗ Ошибка скачивания {filename}: {e}")
        failed_files.append(filename)
        if os.path.exists(dest):
            os.remove(dest)

if failed_files:
    print(f"\n=== Ошибка: не удалось скачать {len(failed_files)} из {len(file_list)} файлов: {failed_files} ===")
    # Ненулевой код выхода - whisper-downloader "restart: no" + whisper-asr/whisper-asr-cpu
    # "depends_on: condition: service_completed_successfully" (docker-compose-whisper.yml) не
    # запустят ASR-сервисы с заведомо неполной моделью, вместо молчаливого "успеха" с битым кешем.
    sys.exit(1)

print("\n=== Готово! Все файлы модели в кеше ===")
print(f"Путь: {snapshot_path}")
