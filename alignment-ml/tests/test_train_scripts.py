"""Регресс-тесты «до/после» для BR-497 (аудит #336, порция P-69, карточка P-69-09).

Проверяют `start_trainig.sh`/`resume_trainig.sh`: строгий режим (`set -euo pipefail`), каталог
берётся из расположения скрипта (а не из `~/Karaoke/alignment-ml`), venv и манифест проверяются
явно, интерпретатор venv вызывается по пути. Для проверки в каталог-фикстуру кладётся заглушка
`.venv/bin/python`, которая записывает PWD и аргументы.

Запуск (torch не нужен, подойдёт системный python3):

    cd alignment-ml
    python3 -m unittest tests.test_train_scripts -v

На коде ДО правки падают все тесты: скрипт уходит в `~/Karaoke/alignment-ml`, не проверяет ни venv,
ни манифест, не использует `set -e` и зовёт системный `python`.
"""

from __future__ import annotations

import os
import stat
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ALIGN_ML = Path(__file__).resolve().parents[1]
START_SCRIPT = ALIGN_ML / "start_trainig.sh"
RESUME_SCRIPT = ALIGN_ML / "resume_trainig.sh"

STUB = """#!/bin/sh
{
  printf '%s\\n' "$PWD"
  for arg in "$@"; do printf '%s\\n' "$arg"; done
} > "$STUB_LOG"
exit 0
"""


class TrainScriptTest(unittest.TestCase):
    def setUp(self) -> None:
        self._tmp = tempfile.TemporaryDirectory()
        self.root = Path(self._tmp.name)
        self.project = self.root / "proj"
        self.project.mkdir()
        self.home = self.root / "empty-home"
        self.home.mkdir()
        self.log = self.root / "stub.log"
        self.manifest = self.project / "manifest.jsonl"
        self.manifest.write_text("", encoding="utf-8")

    def tearDown(self) -> None:
        self._tmp.cleanup()

    def _install_stub_python(self) -> None:
        bin_dir = self.project / ".venv" / "bin"
        bin_dir.mkdir(parents=True)
        stub = bin_dir / "python"
        stub.write_text(STUB, encoding="utf-8")
        stub.chmod(stub.stat().st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)

    def _copy_script(self, source: Path) -> Path:
        target = self.project / source.name
        target.write_text(source.read_text(encoding="utf-8"), encoding="utf-8")
        target.chmod(0o755)
        return target

    def _run(self, script: Path, manifest: str | None = None) -> subprocess.CompletedProcess:
        env = {
            "PATH": "/usr/bin:/bin",
            "HOME": str(self.home),
            "STUB_LOG": str(self.log),
            "ALIGN_MANIFEST": str(self.manifest if manifest is None else manifest),
        }
        return subprocess.run(
            ["bash", str(script)],
            cwd=str(self.root),  # не каталог скрипта - доказательство, что скрипт сам туда переходит
            env=env,
            capture_output=True,
            text=True,
            timeout=60,
        )

    # --- BR-497: главный сценарий карточки P-69-09 -------------------------------------------

    def test_script_runs_from_own_directory_with_venv_python(self) -> None:
        """Скрипт не зависит от ~/Karaoke и зовёт ./.venv/bin/python из своего каталога."""
        self._install_stub_python()
        script = self._copy_script(START_SCRIPT)
        result = self._run(script)
        self.assertEqual(result.returncode, 0, result.stderr)
        lines = self.log.read_text(encoding="utf-8").splitlines()
        self.assertEqual(lines[0], str(self.project))
        self.assertIn("--manifest", lines)
        self.assertIn(str(self.manifest), lines)
        self.assertIn("train.py", lines)

    def test_missing_venv_fails_with_clear_message(self) -> None:
        """Нет .venv — код 1 и понятная ошибка, а не запуск системным python."""
        script = self._copy_script(START_SCRIPT)
        result = self._run(script)
        self.assertEqual(result.returncode, 1, result.stderr)
        self.assertIn("нет виртуального окружения", result.stderr)
        self.assertFalse(self.log.exists(), "python не должен запускаться без venv")

    def test_missing_manifest_fails_with_clear_message(self) -> None:
        """Нет файла манифеста — код 1 и понятная ошибка до запуска обучения."""
        self._install_stub_python()
        script = self._copy_script(START_SCRIPT)
        result = self._run(script, manifest=str(self.root / "nope.jsonl"))
        self.assertEqual(result.returncode, 1, result.stderr)
        self.assertIn("нет файла манифеста", result.stderr)
        self.assertFalse(self.log.exists())

    def test_resume_script_passes_resume_flag(self) -> None:
        self._install_stub_python()
        script = self._copy_script(RESUME_SCRIPT)
        result = self._run(script)
        self.assertEqual(result.returncode, 0, result.stderr)
        lines = self.log.read_text(encoding="utf-8").splitlines()
        self.assertIn("--resume", lines)

    def test_strict_mode_and_no_hardcoded_home_path(self) -> None:
        import re

        for script in (START_SCRIPT, RESUME_SCRIPT):
            text = script.read_text(encoding="utf-8")
            self.assertIn("set -euo pipefail", text, script.name)
            self.assertIn('dirname "${BASH_SOURCE[0]}"', text, script.name)
            self.assertIn('exec ./.venv/bin/python train.py', text, script.name)
            self.assertIsNone(
                re.search(r"(?m)^\s*cd\s+~/", text),
                f"{script.name}: запуск всё ещё зависит от домашнего каталога",
            )


if __name__ == "__main__":
    unittest.main()
