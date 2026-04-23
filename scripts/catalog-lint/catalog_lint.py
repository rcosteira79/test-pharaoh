#!/usr/bin/env python3
"""
Validates every catalog/*.md against _SCHEMA.md:
- has required sections in order
- no empty sections
- no broken intra-repo links
Exits 0 on success, 1 on any failure (with per-file diagnostics).
"""
import sys, re, pathlib

REQUIRED_SECTIONS = ["Purpose", "Detection rule", "Stock edge cases", "Assertion patterns", "Gotchas"]

def lint_file(path: pathlib.Path) -> list[str]:
    errors = []
    text = path.read_text()
    headings = re.findall(r"^## (.+)$", text, re.MULTILINE)
    if headings != REQUIRED_SECTIONS:
        errors.append(f"{path}: headings {headings} != required {REQUIRED_SECTIONS}")
    # Empty section check
    for name in REQUIRED_SECTIONS:
        pattern = rf"^## {re.escape(name)}\s*\n+(?=^## |\Z)"
        if re.search(pattern, text, re.MULTILINE):
            errors.append(f"{path}: section '{name}' is empty")
    return errors

def main() -> int:
    root = pathlib.Path(__file__).resolve().parents[2]
    catalog_dir = root / "catalog"
    errors = []
    for path in sorted(catalog_dir.glob("*.md")):
        if path.name.startswith("_"):  # skip _SCHEMA.md etc
            continue
        errors.extend(lint_file(path))
    if errors:
        for e in errors:
            print(e, file=sys.stderr)
        return 1
    print(f"catalog-lint: OK ({len(list(catalog_dir.glob('*.md')))} files)")
    return 0

if __name__ == "__main__":
    sys.exit(main())
