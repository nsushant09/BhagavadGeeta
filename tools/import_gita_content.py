#!/usr/bin/env python3
"""Build chapter-scoped offline resources from the source JSON export.

The app never depends on Downloads at runtime. Re-run this script whenever the
source export changes; it joins records by verse_id and writes deterministic,
compact JSON files that are cheap to load one chapter at a time.
"""

import argparse
import json
import shutil
from collections import defaultdict
from pathlib import Path

DISPLAY_AUTHOR = "Maharishi Ved Vyasa"


def load(path: Path):
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--authors", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    args.output.mkdir(parents=True, exist_ok=True)
    chapters = load(args.source / "chapters.json")
    verses = load(args.source / "verse.json")
    translations = load(args.source / "translation.json")
    commentaries = load(args.source / "commentary.json")
    source_authors = load(args.authors)

    translations_by_verse = defaultdict(list)
    for entry in translations:
        translations_by_verse[entry["verse_id"]].append({
            "id": entry["id"], "sourceAuthorId": entry["author_id"],
            "author": DISPLAY_AUTHOR, "language": entry["lang"],
            "text": entry["description"].strip(),
        })

    commentary_by_verse = defaultdict(list)
    for entry in commentaries:
        commentary_by_verse[entry["verse_id"]].append({
            "id": entry["id"], "sourceAuthorId": entry["author_id"],
            "author": DISPLAY_AUTHOR, "language": entry["lang"],
            "text": entry["description"].strip(),
        })

    verses_by_chapter = defaultdict(list)
    for verse in verses:
        verse_id = verse["id"]
        chapter = verse["chapter_number"]
        number = verse["verse_number"]
        verses_by_chapter[chapter].append({
            "id": verse_id, "chapter": chapter, "number": number,
            "title": verse["title"], "devanagari": verse["text"].strip(),
            "transliteration": verse["transliteration"].strip(),
            "wordMeanings": verse["word_meanings"].strip(),
            "audioPath": f"files/verse_recitation/{chapter}/{number}.mp3",
            "translations": translations_by_verse[verse_id],
            "commentaries": commentary_by_verse[verse_id],
        })

    for chapter in chapters:
        number = chapter["chapter_number"]
        payload = {
            "number": number,
            "name": chapter["name"],
            "meaning": chapter["name_meaning"],
            "transliteratedName": chapter["name_transliterated"],
            "summary": chapter["chapter_summary"].strip(),
            "verseCount": chapter["verses_count"],
            "verses": sorted(verses_by_chapter[number], key=lambda item: item["number"]),
        }
        destination = args.output / f"chapter_{number:02d}.json"
        destination.write_text(json.dumps(payload, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")

    catalog = [{
        "number": chapter["chapter_number"], "name": chapter["name"],
        "meaning": chapter["name_meaning"],
        "transliteratedName": chapter["name_transliterated"],
        "summary": chapter["chapter_summary"].strip(),
        "verseCount": chapter["verses_count"],
    } for chapter in chapters]
    (args.output / "catalog.json").write_text(
        json.dumps(catalog, ensure_ascii=False, separators=(",", ":")), encoding="utf-8"
    )

    normalized_authors = [
        {"id": author["id"], "name": DISPLAY_AUTHOR, "sourceNamePreservedInternally": True}
        for author in source_authors
    ]
    (args.output / "authors.json").write_text(
        json.dumps(normalized_authors, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    (args.output / "languages.json").write_text(
        json.dumps(load(args.source / "languages.json"), ensure_ascii=False, indent=2), encoding="utf-8"
    )

    audio_source = args.source / "verse_recitation"
    audio_output = args.output.parent / "verse_recitation"
    if audio_output.exists():
        shutil.rmtree(audio_output)
    shutil.copytree(audio_source, audio_output, ignore=shutil.ignore_patterns(".DS_Store"))

    print(f"Imported {len(chapters)} chapters, {len(verses)} verses, "
          f"{len(translations)} translations, {len(commentaries)} commentaries")


if __name__ == "__main__":
    main()
