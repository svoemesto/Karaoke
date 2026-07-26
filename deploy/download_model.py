#!/usr/bin/env python3
"""Скачивание модели faster-whisper через стандартную библиотеку urllib.
Не требует git, curl, wget или huggingface-cli.
Проверяет кеш - не скачивает повторно уже загруженные файлы.

Модель берётся из переменной окружения WHISPER_MODEL (тот же список значений, что
принимает hwdsl2/whisper-server, см. docker-compose-whisper.yml) - по умолчанию "medium",
если переменная не задана."""
import os
import sys
import urllib.request
import json

CACHE_DIR = "/var/lib/whisper"
WHISPER_MODEL = os.environ.get("WHISPER_MODEL", "medium")
MODEL_ID = f"Systran/faster-whisper-{WHISPER_MODEL}"
BASE_URL = f"https://huggingface.co/{MODEL_ID}/resolve/main"

# Создаем структуру каталогов как у HuggingFace cache
model_dir = os.path.join(CACHE_DIR, f"models--{MODEL_ID.replace('/', '--')}")
snapshots_dir = os.path.join(model_dir, "snapshots")
refs_dir = os.path.join(model_dir, "refs")

# Получаем актуальный revision
print(f"Модель: {MODEL_ID} (WHISPER_MODEL={WHISPER_MODEL})")
print("Проверяю актуальную версию модели...")
api_url = f"https://huggingface.co/api/models/{MODEL_ID}"
try:
    with urllib.request.urlopen(api_url, timeout=30) as resp:
        data = json.loads(resp.read())
        revision = data.get("sha", "main")
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
    with urllib.request.urlopen(tree_url, timeout=30) as resp:
        files = json.loads(resp.read())
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
        urllib.request.urlretrieve(url, dest)
        size_mb = os.path.getsize(dest) / (1024 * 1024)
        print(f"  ✓ {filename} ({size_mb:.1f} MB)")
    except Exception as e:
        print(f"  ✗ Ошибка скачивания {filename}: {e}")

print("\n=== Готово! Все файлы модели в кеше ===")
print(f"Путь: {snapshot_path}")
