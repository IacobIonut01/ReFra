#!/usr/bin/env python3
"""Snapshot the CLDR data the OCR coverage test needs.

Writes app/ml-models/catalog/coverage/cldr-exemplars.json with, for every locale an OCR pack was
trained on, the CLDR main exemplar set and the English display name. The coverage test
(com.dot.gallery.core.ocr.catalog.OcrCatalogCoverageTest) derives each pack's locale list from these
sets and the pack dictionaries, so the catalog never claims a language whose letters are missing.

Usage: python3 scripts/ocr/cldr-exemplars.py [cldr-json tag]
"""
import json
import os
import sys
import urllib.error
import urllib.request

TAG = sys.argv[1] if len(sys.argv) > 1 else "49.0.0"
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
CATALOG = os.path.join(ROOT, "app", "ml-models", "catalog", "runtime", "ocr", "model-catalog-v1.json")
OUT = os.path.join(ROOT, "app", "ml-models", "catalog", "coverage", "cldr-exemplars.json")
BASE = f"https://raw.githubusercontent.com/unicode-org/cldr-json/{TAG}/cldr-json"


def fetch(path):
    with urllib.request.urlopen(f"{BASE}/{path}") as response:
        return json.load(response)


def main():
    catalog = json.load(open(CATALOG, encoding="utf-8"))
    locales = sorted({tag for pack in catalog["packs"] for tag in pack["trainedLocales"]} |
                     set(catalog.get("unsupportedLocales", [])))
    names = fetch("cldr-localenames-full/main/en/languages.json")["main"]["en"]["localeDisplayNames"]["languages"]
    scripts = fetch("cldr-localenames-full/main/en/scripts.json")["main"]["en"]["localeDisplayNames"]["scripts"]

    def english_name(tag):
        # CLDR's own qualified names: zh is Simplified Chinese, a script subtag becomes "Serbian (Latin)".
        if tag == "zh":
            return names["zh-Hans"]
        if "-" in tag:
            language, script = tag.split("-", 1)
            return names.get(tag) or f"{names[language]} ({scripts[script]})"
        return names.get(tag, tag)

    exemplars, display, missing = {}, {}, []
    for tag in locales:
        try:
            data = fetch(f"cldr-misc-full/main/{tag}/characters.json")
        except urllib.error.HTTPError:
            missing.append(tag)
            continue
        exemplars[tag] = data["main"][tag]["characters"]["exemplarCharacters"]
        display[tag] = english_name(tag)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        json.dump({"cldrVersion": TAG, "exemplars": exemplars, "englishNames": display,
                   "noCldrData": missing}, f, ensure_ascii=False, indent=1, sort_keys=True)
        f.write("\n")
    print(f"{len(exemplars)} locales, no CLDR data for: {', '.join(missing) or 'none'}")


if __name__ == "__main__":
    main()
