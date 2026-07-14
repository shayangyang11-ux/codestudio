from __future__ import annotations

import json
import re
from typing import Any

from app.graphs.requirement.json.json_text_extractor import JsonTextExtractor


class JsonRepairService:
    """
    本地 JSON 修复器。

    只做确定性修复：
    1. 去除 markdown code fence。
    2. 提取最外层 JSON 对象。
    3. 修复中文全角引号。
    4. 移除尾随逗号。
    5. 截取到最后一个完整右花括号。
    """

    def __init__(self) -> None:
        self.extractor = JsonTextExtractor()

    def repair(
        self,
        text: str,
    ) -> tuple[str, dict[str, Any], list[str]]:
        attempts: list[str] = []

        candidates = [
            text,
            self.extractor.extract(text),
            self._normalize_common_issues(self.extractor.extract(text)),
            self._cut_to_last_object_end(self._normalize_common_issues(self.extractor.extract(text))),
        ]

        last_error: Exception | None = None

        for index, candidate in enumerate(candidates, start=1):
            if not candidate or not candidate.strip():
                continue

            try:
                parsed = json.loads(candidate)
                if isinstance(parsed, dict):
                    attempts.append(f"repair_attempt_{index}_success")
                    return candidate, parsed, attempts

                attempts.append(f"repair_attempt_{index}_parsed_non_object")

            except Exception as exc:
                last_error = exc
                attempts.append(f"repair_attempt_{index}_failed: {type(exc).__name__}")

        if last_error:
            raise RuntimeError(f"JSON repair failed: {type(last_error).__name__}: {last_error}") from last_error

        raise RuntimeError("JSON repair failed: empty text")

    def _normalize_common_issues(
        self,
        text: str,
    ) -> str:
        normalized = text.strip()
        normalized = normalized.replace("“", '"').replace("”", '"')
        normalized = normalized.replace("‘", "'").replace("’", "'")
        normalized = re.sub(r",\s*}", "}", normalized)
        normalized = re.sub(r",\s*]", "]", normalized)
        return normalized

    def _cut_to_last_object_end(
        self,
        text: str,
    ) -> str:
        start = text.find("{")
        end = text.rfind("}")

        if start >= 0 and end > start:
            return text[start:end + 1].strip()

        return text.strip()