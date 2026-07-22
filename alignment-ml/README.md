# alignment-ml

Forced-alignment модуль: по известному тексту песни и вокальному стему проставляет тайминг каждого
слога — альтернатива/дополнение к ASR-варианту (Whisper, кнопка в SubsEdit), у которого качество
оказалось недостаточным (Whisper не знает текст заранее и может его расслышать неверно, а точность
таймкодов не выше слова). Здесь используется forced alignment — выравнивание уже ИЗВЕСТНОГО текста
по аудио на CTC-модели, что в принципе точнее и не подвержено ошибкам распознавания.

Стек полностью отдельный от остального проекта (Python/PyTorch, не Kotlin/Vue) — обычная
директория этого репозитория, не отдельный git-репозиторий (осознанное решение), но со своим
`requirements.txt`/venv, никак не завязана на Gradle-сборку.

## Как это связано с остальным проектом

1. **Экспорт** — в karaoke-app: кнопка «Экспорт датасета для forced-alignment» на Home
   (`ExportAlignmentDataset.kt`) сканирует все песни с `idStatus >= 3` (маркеры уже финальны/
   проверены) и пишет `manifest.jsonl` в `/sm-karaoke/system/alignment-dataset/` — **без копирования
   аудио**: в манифесте абсолютные пути к уже существующим на диске FLAC-файлам
   (`Settings.vocalsNameFlac`). Положите (или симлинкните — файл маленький, KB) этот `manifest.jsonl`
   в `alignment-ml/data/`.
2. **Baseline** — `align.py` использует готовую multilingual CTC-модель
   `torchaudio.pipelines.MMS_FA` (Meta MMS, forced alignment, включая русский) БЕЗ дообучения.
3. **Оценка** — `evaluate.py` прогоняет `align.py` по манифесту и считает среднюю/медианную
   абсолютную ошибку (мс) на слог против уже проверенной вручную разметки. Это критерий
   "приемлемо/нет" перед тем, как думать про дообучение или интеграцию.
4. **Дообучение (опционально)** — `train.py`, только если `evaluate.py` показал, что baseline
   недостаточно точен на реальном пении (растянутые гласные, вибрато — вероятный домен-мисматч с
   речью, на которой модель обучена).
5. **Сервис (позже)** — `serve.py`, тонкая FastAPI-обёртка, прототип контракта, который сможет
   заменить/дополнить `WhisperAsrService` в karaoke-app — интеграция сюда не входит.

## GPU

Обучение и инференс — на машине админа, на той же GPU, что Whisper (`hwdsl2/whisper-server`) и
Ollama. **Не гонять одновременно** — конкуренция за видеопамять уже приводила к OOM у Demucs на
этой же машине. Планируйте запуск в окна, когда Whisper/Ollama простаивают.

## Запуск

```bash
cd alignment-ml
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt

# baseline на одном файле - смоук-тест
python align.py --audio /path/to/vocals.flac --text "Текст песни..."

# оценка на части датасета
python evaluate.py --manifest data/manifest.jsonl --limit 50

# (опционально, только если evaluate.py показал недостаточную точность)
python train.py --manifest data/manifest.jsonl --limit 500   # сначала на малой выборке
python align.py --audio ... --text ... --model checkpoints/mms-ft   # TODO: параметр --model в align.py

# сервис
uvicorn serve:app --host 0.0.0.0 --port 8017
```

Первый реальный прогон почти наверняка потребует правок (конкретные версии
torch/torchaudio/transformers, доступность чекпоинта `facebook/mms-1b-all`, романизация через
`uroman` для кириллицы и т.п.) — это ожидаемо, код писался и проверялся без GPU/датасета под рукой.
