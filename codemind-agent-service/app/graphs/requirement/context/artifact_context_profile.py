from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class ArtifactContextProfile:
    artifact_type: str
    required_artifacts: tuple[str, ...]
    optional_artifacts: tuple[str, ...]
    max_artifact_chars: int
    max_total_chars: int


_CONTEXT_PROFILES: dict[str, ArtifactContextProfile] = {
    "DATA_MODEL": ArtifactContextProfile(
        artifact_type="DATA_MODEL",
        required_artifacts=("MODULE_TREE", "MODULE_PRD", "BUSINESS_FLOW", "PAGE_SPEC"),
        optional_artifacts=(),
        max_artifact_chars=3500,
        max_total_chars=12000,
    ),
    "API_CONTRACT": ArtifactContextProfile(
        artifact_type="API_CONTRACT",
        required_artifacts=("MODULE_TREE", "MODULE_PRD", "BUSINESS_FLOW", "PAGE_SPEC", "DATA_MODEL"),
        optional_artifacts=(),
        max_artifact_chars=3500,
        max_total_chars=14000,
    ),
    "TEST_SPEC": ArtifactContextProfile(
        artifact_type="TEST_SPEC",
        required_artifacts=("MODULE_TREE", "MODULE_PRD", "BUSINESS_FLOW", "PAGE_SPEC", "DATA_MODEL", "API_CONTRACT"),
        optional_artifacts=(),
        max_artifact_chars=3000,
        max_total_chars=15000,
    ),
    "DEV_HANDOFF_PACKAGE": ArtifactContextProfile(
        artifact_type="DEV_HANDOFF_PACKAGE",
        required_artifacts=(
            "MODULE_TREE",
            "MODULE_PRD",
            "BUSINESS_FLOW",
            "PAGE_SPEC",
            "DATA_MODEL",
            "API_CONTRACT",
            "TEST_SPEC",
        ),
        optional_artifacts=(),
        max_artifact_chars=3000,
        max_total_chars=18000,
    ),
}


def get_context_profile(artifact_type: str) -> ArtifactContextProfile:
    return _CONTEXT_PROFILES.get(
        artifact_type,
        ArtifactContextProfile(
            artifact_type=artifact_type,
            required_artifacts=(),
            optional_artifacts=(),
            max_artifact_chars=2500,
            max_total_chars=10000,
        ),
    )