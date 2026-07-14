from __future__ import annotations


class JsonTextExtractor:
    def extract(
        self,
        text: str,
    ) -> str:
        cleaned = self._strip_code_fence(text or "")

        if not cleaned:
            return ""

        start = cleaned.find("{")
        end = cleaned.rfind("}")

        if start >= 0 and end > start:
            return cleaned[start:end + 1].strip()

        return cleaned.strip()

    def _strip_code_fence(
        self,
        text: str,
    ) -> str:
        cleaned = text.strip()

        if cleaned.startswith("```json"):
            cleaned = cleaned.removeprefix("```json").strip()

        if cleaned.startswith("```"):
            cleaned = cleaned.removeprefix("```").strip()

        if cleaned.endswith("```"):
            cleaned = cleaned.removesuffix("```").strip()

        return cleaned.strip()