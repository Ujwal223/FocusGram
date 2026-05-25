import os, re
from pathlib import Path

version = os.environ["VERSION"]
text = Path("CHANGELOG.md").read_text(encoding="utf-8")
pattern = rf"(?ms)^##\s+FocusGram\s+{re.escape(version)}\s*$.*?(?=^##\s+|\Z)"
m = re.search(pattern, text)
if not m:
    raise SystemExit(f"Could not find changelog section for version {version}")
Path("release_notes.md").write_text(m.group(0).strip() + "\n", encoding="utf-8")