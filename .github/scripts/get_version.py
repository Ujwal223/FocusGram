from pathlib import Path
import re

text = Path("CHANGELOG.md").read_text(encoding="utf-8")
m = re.search(r"^##\s+FocusGram\s+([0-9]+\.[0-9]+\.[0-9]+)\s*$", text, re.M)
if not m:
    raise SystemExit("Could not find a top changelog heading like: ## FocusGram 2.1.0")
print(m.group(1))