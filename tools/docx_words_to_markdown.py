#!/usr/bin/env python3
"""Convert numbered vocabulary entries in DOCX files to one Markdown file per word.

The source documents use numbered entry headings such as ``1. abandon`` or
``4. be able to``.  Text after a heading is stored with that entry until the
next heading.  Repeated entries are merged and every source file is recorded.
"""

from __future__ import annotations

import argparse
import json
import re
import shutil
import sys
import zipfile
from collections import OrderedDict
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterable
from xml.etree import ElementTree as ET


WORD_HEADING = re.compile(
    r"^\s*\d+\s*[.、．]\s*([A-Za-z][A-Za-z'’\- ]*[A-Za-z)|])(?=\s|$)"
)
NUMBERED_TITLE = re.compile(r"^\s*\d+\s*[.、．]")
KNOWN_PARTS_OF_SPEECH = frozenset({"adj", "adv", "modal", "n", "pre", "v", "vi"})
SPACE = re.compile(r"\s+")
SAFE_FILE_NAME = re.compile(r"[^a-z0-9]+")
WORD_DOCUMENT_XML = "word/document.xml"
W = "{http://schemas.openxmlformats.org/wordprocessingml/2006/main}"


@dataclass
class Entry:
    word: str
    parts_of_speech: list[str] = field(default_factory=list)
    sections: list[tuple[str, list[str]]] = field(default_factory=list)


@dataclass(frozen=True)
class Heading:
    word: str
    part_of_speech: str | None = None
    ambiguous: bool = False


def normalize_word(word: str) -> str:
    return SPACE.sub(" ", word.strip()).lower()


def markdown_file_name(word: str) -> str:
    name = SAFE_FILE_NAME.sub("-", normalize_word(word)).strip("-")
    return f"{name or 'unnamed-word'}.md"


def paragraph_text(paragraph: ET.Element) -> str:
    """Extract visible text while retaining tabs and line breaks."""
    parts: list[str] = []
    for element in paragraph.iter():
        if element.tag == W + "t":
            parts.append(element.text or "")
        elif element.tag == W + "tab":
            parts.append("\t")
        elif element.tag in (W + "br", W + "cr"):
            parts.append("\n")
    return "".join(parts).strip()


def iter_block_text(element: ET.Element) -> Iterable[str]:
    """Yield paragraph text in document order, including text inside tables."""
    for child in element:
        if child.tag == W + "p":
            text = paragraph_text(child)
            if text:
                yield text
        elif child.tag == W + "tbl":
            for row in child.findall(W + "tr"):
                cells = row.findall(W + "tc")
                cell_texts = [" ".join(iter_block_text(cell)).strip() for cell in cells]
                text = " | ".join(value for value in cell_texts if value)
                if text:
                    yield text


def read_docx_paragraphs(path: Path) -> list[str]:
    with zipfile.ZipFile(path) as archive:
        root = ET.fromstring(archive.read(WORD_DOCUMENT_XML))
    body = root.find(W + "body")
    if body is None:
        return []
    return list(iter_block_text(body))


def heading_word(text: str) -> Heading | None:
    match = WORD_HEADING.match(text)
    if not match:
        return None
    word = SPACE.sub(" ", match.group(1).strip(" -\t"))
    # A numbered title such as "1. 高考英语..." must not become a word.
    if not re.fullmatch(r"[A-Za-z][A-Za-z'’\- ]*[A-Za-z)]", word):
        return None
    tokens = word.split()
    if len(tokens) > 1 and tokens[-1].lower() in KNOWN_PARTS_OF_SPEECH:
        return Heading(word=" ".join(tokens[:-1]), part_of_speech=tokens[-1].lower())
    ambiguous = len(tokens) > 1 and text[match.end(1) :].lstrip().startswith(".")
    return Heading(word=word, ambiguous=ambiguous)


def collect_entries(path: Path, entries: OrderedDict[str, Entry], warnings: list[dict[str, object]]) -> int:
    current_word: str | None = None
    current_part_of_speech: str | None = None
    current_position: int | None = None
    current_lines: list[str] = []
    count = 0

    def save_current() -> None:
        nonlocal count
        if current_word is None:
            return
        key = normalize_word(current_word)
        if key in entries:
            warnings.append(
                {
                    "code": "duplicate_heading",
                    "source": path.name,
                    "position": current_position,
                    "text": current_lines[0],
                }
            )
        entry = entries.setdefault(key, Entry(word=current_word))
        if current_part_of_speech is not None and current_part_of_speech not in entry.parts_of_speech:
            entry.parts_of_speech.append(current_part_of_speech)
        entry.sections.append((path.name, current_lines.copy()))
        count += 1

    for position, text in enumerate(read_docx_paragraphs(path), start=1):
        heading = heading_word(text)
        if heading is not None:
            if heading.ambiguous:
                warnings.append(
                    {
                        "code": "ambiguous_heading",
                        "source": path.name,
                        "position": position,
                        "text": text,
                    }
                )
            save_current()
            current_word = heading.word
            current_part_of_speech = heading.part_of_speech
            current_position = position
            current_lines = [text]
        elif NUMBERED_TITLE.match(text):
            warnings.append(
                {
                    "code": "malformed_heading",
                    "source": path.name,
                    "position": position,
                    "text": text,
                }
            )
            if current_word is not None:
                current_lines.append(text)
        elif current_word is not None:
            current_lines.append(text)
        else:
            warnings.append(
                {
                    "code": "unassigned_content",
                    "source": path.name,
                    "position": position,
                    "text": text,
                }
            )
    save_current()
    return count


def markdown(entry: Entry) -> str:
    sources = list(OrderedDict((source, None) for source, _ in entry.sections))
    lines = ["---", f"word: {entry.word}", "sources:"]
    lines.extend(f'  - "{source}"' for source in sources)
    if entry.parts_of_speech:
        lines.append("parts_of_speech:")
        lines.extend(f"  - {part_of_speech}" for part_of_speech in entry.parts_of_speech)
    lines.extend(["---", "", f"# {entry.word}", "", "## 来源", ""])
    lines.extend(f"- `{source}`" for source in sources)
    for source, content in entry.sections:
        lines.extend(["", "## 内容", "", f"**来源文件：** `{source}`", ""])
        lines.extend(content)
    return "\n".join(lines).rstrip() + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path, help="Directory containing DOCX files")
    parser.add_argument("output", type=Path, help="Directory for generated Markdown files")
    parser.add_argument("--keep-output", action="store_true", help="Do not remove an existing output directory")
    args = parser.parse_args()

    if not args.source.is_dir():
        parser.error(f"Source directory does not exist: {args.source}")
    if args.output.exists() and not args.output.is_dir():
        parser.error(f"Output path is not a directory: {args.output}")
    if args.output.exists() and not args.keep_output:
        shutil.rmtree(args.output)
    args.output.mkdir(parents=True, exist_ok=True)

    entries: OrderedDict[str, Entry] = OrderedDict()
    warnings: list[dict[str, object]] = []
    skipped: list[tuple[str, str]] = []
    files = sorted(path for path in args.source.glob("*.docx") if not path.name.startswith("~$"))
    for path in files:
        try:
            collect_entries(path, entries, warnings)
        except (ET.ParseError, OSError, KeyError, zipfile.BadZipFile) as error:
            skipped.append((path.name, str(error)))

    used_names: dict[str, int] = {}
    for entry in entries.values():
        file_name = markdown_file_name(entry.word)
        number = used_names.get(file_name, 0)
        used_names[file_name] = number + 1
        if number:
            file_name = f"{Path(file_name).stem}-{number + 1}.md"
        (args.output / file_name).write_text(markdown(entry), encoding="utf-8")

    report = ["# 转换报告", "", f"- 已读取 DOCX：{len(files)}", f"- 生成词条：{len(entries)}"]
    if skipped:
        report.extend(["", "## 未解析文件", ""])
        report.extend(f"- `{name}`：{reason}" for name, reason in skipped)
    else:
        report.extend(["", "- 未解析文件：0"])
    (args.output / "README.md").write_text("\n".join(report) + "\n", encoding="utf-8")
    machine_report = {
        "sourceDocuments": len(files),
        "generatedEntries": len(entries),
        "warnings": warnings,
        "skippedFiles": [{"source": name, "reason": reason} for name, reason in skipped],
    }
    (args.output / "conversion-report.json").write_text(
        json.dumps(machine_report, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )

    print(f"Read {len(files)} DOCX files; generated {len(entries)} word files in {args.output}")
    if skipped:
        print(f"Skipped {len(skipped)} file(s); see {args.output / 'README.md'}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
