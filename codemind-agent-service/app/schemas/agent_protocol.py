from typing import Any, Literal

from pydantic import BaseModel, Field
from typing import Any

class PromptTemplateSnapshot(BaseModel):
    template_id: str | None = None
    template_version_id: str | None = None
    template_key: str
    template_name: str | None = None
    task_type: str | None = None
    artifact_type: str | None = None
    version: str
    checksum: str
    system_prompt: str
    user_prompt_template: str
    output_schema: dict[str, Any] | None = None
    
class AgentTaskRequest(BaseModel):
    task_id: str
    project_id: str
    project_code: str | None = None
    module_id: str | None = None
    task_type: str

    input: dict[str, Any] = Field(default_factory=dict)
    context_package: dict[str, Any] = Field(default_factory=dict)

    requirement_text: str | None = None
    prompt_template: PromptTemplateSnapshot | None = None


class AgentTaskResponse(BaseModel):
    task_id: str
    project_id: str
    project_code: str | None = None
    module_id: str | None = None
    task_type: str

    artifact_type: str
    content: dict[str, Any]
    content_markdown: str | None = None

    status: Literal["SUCCESS", "FAILED"] = "SUCCESS"
    error_code: str | None = None
    error_message: str | None = None

    usage: dict[str, Any] = Field(default_factory=dict)
    metadata: dict[str, Any] = Field(default_factory=dict)
