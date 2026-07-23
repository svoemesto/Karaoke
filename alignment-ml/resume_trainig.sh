cd ~/Karaoke/alignment-ml
source .venv/bin/activate
MANIFEST=/sm-karaoke/system/alignment-dataset/manifest.jsonl
python train.py --manifest "$MANIFEST" --resume
