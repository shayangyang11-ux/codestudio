from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from app.agents.requirement.ui_contract_quality_gate import UiContractQualityGate
from app.schemas.ui_contract import UiContractArtifact


def main() -> int:
    if len(sys.argv) != 2:
        print("Usage: python scripts/validate_ui_contract.py <ui-contract.json>")
        return 2

    path = Path(sys.argv[1])
    payload = json.loads(path.read_text(encoding="utf-8"))

    artifact = UiContractArtifact.model_validate(payload)
    report = UiContractQualityGate().audit(artifact)

    if report.blockers:
        print("UI_CONTRACT_INVALID")
        for blocker in report.blockers:
            print(f"- BLOCKER: {blocker}")
        for warning in report.warnings:
            print(f"- WARNING: {warning}")
        return 1

    print("UI_CONTRACT_VALID")
    for warning in report.warnings:
        print(f"- WARNING: {warning}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
