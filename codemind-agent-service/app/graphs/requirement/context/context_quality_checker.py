from __future__ import annotations

from typing import Any


class ContextQualityChecker:
    def check(
        self,
        compact_context: dict[str, Any],
    ) -> dict[str, Any]:
        dependency_status = compact_context.get("dependency_status", {})
        context_quality = compact_context.get("context_quality", {})

        missing = dependency_status.get("missing_required_artifacts", [])
        available_count = int(context_quality.get("available_required_artifact_count", 0))
        required_count = int(context_quality.get("required_artifact_count", 0))

        score = 100

        if missing:
            score -= min(50, len(missing) * 10)

        if required_count > 0:
            ratio = available_count / required_count
            if ratio < 0.5:
                score -= 25
            elif ratio < 0.8:
                score -= 10

        if context_quality.get("budget_trimmed"):
            score -= 5

        score = max(0, min(100, score))

        return {
            "score": score,
            "passed": score >= 60,
            "missing_required_artifacts": missing,
            "notes": self._build_notes(
                missing=missing,
                score=score,
                budget_trimmed=bool(context_quality.get("budget_trimmed")),
            ),
        }

    def _build_notes(
        self,
        *,
        missing: list[str],
        score: int,
        budget_trimmed: bool,
    ) -> list[str]:
        notes: list[str] = []

        if missing:
            notes.append(f"缺少上游资产：{', '.join(missing)}。")

        if budget_trimmed:
            notes.append("上下文超过预算，已进行二次裁剪。")

        if score < 60:
            notes.append("上下文完整度偏低，本次生成结果可能偏保守。")

        if not notes:
            notes.append("上下文质量满足当前资产生成要求。")

        return notes