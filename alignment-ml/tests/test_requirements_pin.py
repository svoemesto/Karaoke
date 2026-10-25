"""Регресс-тесты «до/после» для BR-498 (аудит #336, порция P-69, карточка P-69-10).

Проверяют `requirements.txt` и README: `torch`/`torchaudio` зафиксированы одной парой (одинаковая
версия — torchaudio ABI-привязан к версии torch), нижняя граница `transformers` поднята до 4.46
(`processing_class` в `TrainingArguments`/`Trainer`), а требование системного `perl` для `uroman`
описано в README.

Запуск (torch не нужен, подойдёт системный python3):

    cd alignment-ml
    python3 -m unittest tests.test_requirements_pin -v

На коде ДО правки падает test_torch_and_torchaudio_pinned_to_same_pair: там `torch>=2.1` и
`torchaudio>=2.1` (независимые нижние границы, версии не зафиксированы).
"""

from __future__ import annotations

import re
import unittest
from pathlib import Path

ALIGN_ML = Path(__file__).resolve().parents[1]
REQUIREMENTS = ALIGN_ML / "requirements.txt"
README = ALIGN_ML / "README.md"

TORCH_PIN = re.compile(r"^torch(?:audio)?==(\d+\.\d+\.\d+)$")
SPEC = re.compile(r"^(?P<name>[A-Za-z0-9_.\-]+)\s*(?P<op>==|>=|<=|>|<|~=|!=)\s*(?P<version>.+)$")


def _specs() -> dict[str, str]:
    specs: dict[str, str] = {}
    for raw in REQUIREMENTS.read_text(encoding="utf-8").splitlines():
        line = raw.split("#", 1)[0].strip()
        if not line:
            continue
        match = SPEC.match(line)
        assert match is not None, f"не разобрана строка requirements.txt: {line!r}"
        specs[match.group("name")] = match.group("op") + match.group("version")
    return specs


class RequirementsPinTest(unittest.TestCase):
    # --- BR-498: главный сценарий карточки P-69-10 -------------------------------------------

    def test_torch_and_torchaudio_pinned_to_same_pair(self) -> None:
        """Одна пара `torch==X` / `torchaudio==X` (одинаковая минорная версия)."""
        specs = _specs()
        torch_spec = specs.get("torch", "")
        audio_spec = specs.get("torchaudio", "")
        self.assertRegex(torch_spec, r"^==\d+\.\d+\.\d+$", "torch обязан быть зафиксирован через ==")
        self.assertRegex(audio_spec, r"^==\d+\.\d+\.\d+$", "torchaudio обязан быть зафиксирован через ==")
        self.assertEqual(
            torch_spec.removeprefix("=="),
            audio_spec.removeprefix("=="),
            "torchaudio должен совпадать с torch по версии (ABI-привязка), а не разрешаться отдельно",
        )

    def test_transformers_floor_supports_processing_class(self) -> None:
        specs = _specs()
        match = re.match(r"^>=(\d+)\.(\d+)", specs.get("transformers", ""))
        self.assertIsNotNone(match, "у transformers должна быть нижняя граница >=4.46")
        self.assertGreaterEqual((int(match.group(1)), int(match.group(2))), (4, 46))

    def test_readme_requires_perl_for_uroman(self) -> None:
        text = README.read_text(encoding="utf-8").lower()
        self.assertIn("perl", text)
        self.assertIn("uroman", text)


if __name__ == "__main__":
    unittest.main()
