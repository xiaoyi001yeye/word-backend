import json
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
from xml.sax.saxutils import escape


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
CONVERTER = REPOSITORY_ROOT / "tools" / "docx_words_to_markdown.py"
DOCUMENT_XML = "word/document.xml"


def write_docx(path: Path, paragraphs: list[str]) -> None:
    body = "".join(
        f'<w:p><w:r><w:t xml:space="preserve">{escape(text)}</w:t></w:r></w:p>'
        for text in paragraphs
    )
    document = (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">'
        f"<w:body>{body}</w:body></w:document>"
    )
    with zipfile.ZipFile(path, "w") as archive:
        archive.writestr(DOCUMENT_XML, document)


class DocxWordsToMarkdownTest(unittest.TestCase):
    def run_converter(
        self, documents: dict[str, list[str]], repeat: int = 1
    ) -> tuple[Path, subprocess.CompletedProcess[str]]:
        temporary_directory = tempfile.TemporaryDirectory()
        self.addCleanup(temporary_directory.cleanup)
        root = Path(temporary_directory.name)
        source = root / "source"
        output = root / "output"
        source.mkdir()
        for name, paragraphs in documents.items():
            write_docx(source / name, paragraphs)

        for _ in range(repeat):
            result = subprocess.run(
                [sys.executable, str(CONVERTER), str(source), str(output)],
                check=False,
                capture_output=True,
                text=True,
            )
        return output, result

    def test_trailing_part_of_speech_is_not_part_of_canonical_word(self) -> None:
        output, result = self.run_converter(
            {"lesson-53.docx": ["50. weak adj .弱的；软弱的", "word family: weakness"]}
        )

        self.assertEqual(0, result.returncode, result.stderr)
        markdown = (output / "weak.md").read_text(encoding="utf-8")
        self.assertIn("word: weak\n", markdown)
        self.assertIn("  - adj\n", markdown)
        self.assertIn("50. weak adj .弱的；软弱的", markdown)
        self.assertIn("word family: weakness", markdown)
        self.assertFalse((output / "weak-adj.md").exists())

    def test_known_parts_of_speech_are_separated_without_breaking_multiword_expressions(self) -> None:
        output, result = self.run_converter(
            {
                "lesson.docx": [
                    "1. ability 能力",
                    "2. be able to 能够",
                    "3. world n .世界",
                    "4. are v (be的变形) 是",
                    "5. would modal 将会",
                    "6. clone vi & n.克隆",
                    "7. else adv .其它的",
                    "8. within pre p.在里面",
                ]
            }
        )

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertTrue((output / "ability.md").is_file())
        self.assertIn("word: be able to\n", (output / "be-able-to.md").read_text(encoding="utf-8"))
        for word, part_of_speech in (
            ("world", "n"),
            ("are", "v"),
            ("would", "modal"),
            ("clone", "vi"),
            ("else", "adv"),
            ("within", "pre"),
        ):
            markdown = (output / f"{word}.md").read_text(encoding="utf-8")
            self.assertIn(f"word: {word}\n", markdown)
            self.assertIn(f"  - {part_of_speech}\n", markdown)

    def test_ambiguous_and_malformed_numbered_titles_are_reported_with_source_and_position(self) -> None:
        output, result = self.run_converter(
            {
                "lesson-ambiguous.docx": [
                    "1. 高考英语核心词汇",
                    "2. ability 能力",
                    "3. weak xyz 可疑词性",
                    "4. iden tity n .身份",
                ]
            }
        )

        self.assertEqual(0, result.returncode, result.stderr)
        report = json.loads((output / "conversion-report.json").read_text(encoding="utf-8"))
        self.assertEqual(
            [
                {
                    "code": "malformed_heading",
                    "source": "lesson-ambiguous.docx",
                    "position": 1,
                    "text": "1. 高考英语核心词汇",
                },
                {
                    "code": "ambiguous_heading",
                    "source": "lesson-ambiguous.docx",
                    "position": 3,
                    "text": "3. weak xyz 可疑词性",
                },
                {
                    "code": "ambiguous_heading",
                    "source": "lesson-ambiguous.docx",
                    "position": 4,
                    "text": "4. iden tity n .身份",
                },
            ],
            report["warnings"],
        )

    def test_duplicate_canonical_titles_merge_without_losing_source_text(self) -> None:
        output, result = self.run_converter(
            {
                "lesson-a.docx": ["1. weak adj .弱的", "first explanation"],
                "lesson-b.docx": ["8. weak 软弱", "second explanation"],
            }
        )

        self.assertEqual(0, result.returncode, result.stderr)
        markdown = (output / "weak.md").read_text(encoding="utf-8")
        self.assertIn("first explanation", markdown)
        self.assertIn("second explanation", markdown)
        self.assertEqual(1, len(list(output.glob("weak*.md"))))
        report = json.loads((output / "conversion-report.json").read_text(encoding="utf-8"))
        self.assertIn(
            {
                "code": "duplicate_heading",
                "source": "lesson-b.docx",
                "position": 1,
                "text": "8. weak 软弱",
            },
            report["warnings"],
        )

    def test_unassigned_source_text_is_preserved_in_machine_report(self) -> None:
        output, result = self.run_converter(
            {"lesson.docx": ["课程说明：以下为核心词汇", "1. ability 能力"]}
        )

        self.assertEqual(0, result.returncode, result.stderr)
        report = json.loads((output / "conversion-report.json").read_text(encoding="utf-8"))
        self.assertIn(
            {
                "code": "unassigned_content",
                "source": "lesson.docx",
                "position": 1,
                "text": "课程说明：以下为核心词汇",
            },
            report["warnings"],
        )

    def test_repeated_generation_is_idempotent(self) -> None:
        output, result = self.run_converter(
            {"lesson.docx": ["1. weak adj .弱的", "original explanation"]},
            repeat=2,
        )

        self.assertEqual(0, result.returncode, result.stderr)
        generated_markdown = [path.name for path in output.glob("*.md") if path.name != "README.md"]
        self.assertEqual(["weak.md"], generated_markdown)
        self.assertEqual(
            1,
            (output / "weak.md").read_text(encoding="utf-8").count("original explanation"),
        )

    def test_committed_repair_report_matches_generated_corpus(self) -> None:
        material_directory = REPOSITORY_ROOT / "generated" / "gaokao-3500-words-markdown"
        report = json.loads((material_directory / "repair-report.json").read_text(encoding="utf-8"))

        word_files = [path for path in material_directory.glob("*.md") if path.name != "README.md"]
        canonical_words = []
        for path in word_files:
            word_line = next(
                line for line in path.read_text(encoding="utf-8").splitlines()[:20] if line.startswith("word:")
            )
            canonical_words.append(word_line.removeprefix("word:").strip().lower())

        self.assertEqual(3296, len(word_files))
        self.assertEqual(len(canonical_words), len(set(canonical_words)))
        self.assertFalse(report["sourceDocumentsAvailableInRepository"])
        self.assertEqual(15, len(report["repairs"]))
        self.assertEqual(33, len(report["warnings"]))
        self.assertIn("iden tity", {warning["canonicalWord"] for warning in report["warnings"]})
        for repair in report["repairs"]:
            self.assertFalse((material_directory / repair["from"]).exists())
            repaired_file = material_directory / repair["to"]
            self.assertTrue(repaired_file.is_file())
            self.assertIn(
                f"word: {repair['canonicalWord']}\n",
                repaired_file.read_text(encoding="utf-8"),
            )
        for warning in report["warnings"]:
            self.assertEqual("ambiguous_heading", warning["code"])
            self.assertTrue(warning["source"])
            evidence_lines = (material_directory / warning["materialFile"]).read_text(
                encoding="utf-8"
            ).splitlines()
            evidence = evidence_lines[warning["materialLine"] - 1]
            self.assertTrue(evidence.lstrip().startswith(f"{warning['position']}."))


if __name__ == "__main__":
    unittest.main()
