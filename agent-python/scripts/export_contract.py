"""Export OpenAPI without reading env files or accessing any provider."""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from ready_gym_intake import create_app


if __name__ == "__main__":
    path = Path(__file__).resolve().parents[2] / "docs" / "intake-api.json"
    path.write_text(json.dumps(create_app().openapi(), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Exported OpenAPI to {path}")
