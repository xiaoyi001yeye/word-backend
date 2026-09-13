#!/usr/bin/env python3
"""Create deterministic sample graph data from generated vocabulary Markdown files."""

from __future__ import annotations

import argparse
import json
import random
import re
import uuid
from pathlib import Path


NAMESPACE = uuid.UUID("06c23350-3431-4d4e-8e12-082aa294c4ba")
ENTRY_ORDER = re.compile(r"^\s*(\d+)\s*[.、．]")
SENSE = re.compile(
    r"\b(n\.|v\.|vi\.|vt\.|adj\.|adv\.|prep\.|pron\.|conj\.|art\.|num\.|aux\.)\s*([^【\n]+)"
)
POS = {
    "n.": "NOUN", "v.": "VERB", "vi.": "VERB", "vt.": "VERB",
    "adj.": "ADJECTIVE", "adv.": "ADVERB", "prep.": "PREPOSITION",
    "pron.": "PRONOUN", "conj.": "CONJUNCTION", "art.": "ARTICLE",
    "num.": "NUMERAL", "aux.": "AUXILIARY",
}


def stable_id(kind: str, value: str) -> str:
    return str(uuid.uuid5(NAMESPACE, f"{kind}:{value}"))


def read_markdown(path: Path) -> tuple[str, list[str], str]:
    text = path.read_text(encoding="utf-8")
    front, body = text.split("---", 2)[1:]
    word = re.search(r"^word: (.+)$", front, re.MULTILINE).group(1).strip()
    sources = re.findall(r'^  - "(.+)"$', front, re.MULTILINE)
    raw_content = body.split("**来源文件：**", 1)[-1]
    raw_content = raw_content.split("\n\n", 1)[-1].strip()
    return word, sources, raw_content


def first_sense(raw_content: str) -> tuple[str, str | None]:
    match = SENSE.search(raw_content)
    if not match:
        return "RAW_ONLY", None
    definition = match.group(2).strip(" ，,；;。.")
    return POS[match.group(1)], definition or None


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input_dir", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--count", type=int, default=20)
    parser.add_argument("--seed", type=int, default=20260912)
    args = parser.parse_args()

    files = sorted(path for path in args.input_dir.glob("*.md") if path.name != "README.md")
    if len(files) < args.count:
        raise SystemExit(f"Need {args.count} Markdown files, found {len(files)}")
    selected = random.Random(args.seed).sample(files, args.count)

    nodes: list[dict] = []
    edges: list[dict] = []
    source_ids: dict[str, str] = {}
    for path in selected:
        word, sources, raw_content = read_markdown(path)
        lexeme_id = stable_id("lexeme", word.lower())
        sense_id = stable_id("sense", word.lower())
        pos, definition = first_sense(raw_content)
        nodes.append({
            "id": lexeme_id,
            "node_type": "LEXEME",
            "canonical_form": word.lower(),
            "display_form": word,
            "normalized_key": " ".join(word.lower().split()),
            "form_type": "PHRASE" if " " in word else "WORD",
            "language": "en",
            "status": "ACTIVE",
        })
        nodes.append({
            "id": sense_id,
            "node_type": "WORD_SENSE",
            "lexeme_id": lexeme_id,
            "part_of_speech": pos,
            "definition_zh": definition,
            "sense_order": 1,
            "extraction_status": "EXTRACTED" if definition else "RAW_ONLY",
        })
        edges.append({
            "id": stable_id("edge", f"has-sense:{lexeme_id}:{sense_id}"),
            "from_node_id": lexeme_id,
            "to_node_id": sense_id,
            "relation_type": "HAS_SENSE",
            "display_name_zh": "释义",
            "confidence": 1.0,
            "assertion_status": "EXTRACTED",
        })
        for source in sources:
            source_id = source_ids.get(source)
            if source_id is None:
                source_id = stable_id("source-document", source)
                source_ids[source] = source_id
                lecture = re.search(r"第(\d+)讲", source)
                nodes.append({
                    "id": source_id,
                    "node_type": "SOURCE_DOCUMENT",
                    "file_name": source,
                    "lecture_no": int(lecture.group(1)) if lecture else None,
                })
            excerpt_id = stable_id("excerpt", f"{source}:{word}:{raw_content}")
            entry = ENTRY_ORDER.search(raw_content)
            nodes.append({
                "id": excerpt_id,
                "node_type": "SOURCE_EXCERPT",
                "source_document_id": source_id,
                "lexeme_id": lexeme_id,
                "markdown_file": path.name,
                "entry_order": int(entry.group(1)) if entry else None,
                "raw_content": raw_content,
                "parse_status": "PARSED" if definition else "PARTIAL",
            })
            edges.extend([
                {
                    "id": stable_id("edge", f"has-excerpt:{source_id}:{excerpt_id}"),
                    "from_node_id": source_id,
                    "to_node_id": excerpt_id,
                    "relation_type": "HAS_EXCERPT",
                    "display_name_zh": "收录",
                    "confidence": 1.0,
                    "assertion_status": "EXTRACTED",
                },
                {
                    "id": stable_id("edge", f"described-by:{lexeme_id}:{excerpt_id}"),
                    "from_node_id": lexeme_id,
                    "to_node_id": excerpt_id,
                    "relation_type": "DESCRIBED_BY",
                    "display_name_zh": "讲解",
                    "evidence_excerpt_id": excerpt_id,
                    "confidence": 1.0,
                    "assertion_status": "EXTRACTED",
                },
            ])

    data = {
        "schema_version": "1.0",
        "description": "20 个随机高考英语词条的异构图样例数据",
        "selection": {"count": args.count, "seed": args.seed, "source": str(args.input_dir)},
        "nodes": nodes,
        "edges": edges,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {len(nodes)} nodes and {len(edges)} edges to {args.output}")


if __name__ == "__main__":
    main()
