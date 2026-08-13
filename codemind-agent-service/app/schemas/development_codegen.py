from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field


class DevelopmentCodeFilePayload(BaseModel):
    file_path: str = Field(default="")
    file_type: str = Field(default="CONFIG")
    action: str = Field(default="CREATE_OR_UPDATE")
    source: str = Field(default="DEV_HANDOFF_PACKAGE")
    description: str = Field(default="")
    language: str = Field(default="text")
    content: str = Field(default="")
    priority: int = Field(default=100)


class DevelopmentNodejsCodegenRequest(BaseModel):
    task_id: str | None = None
    workspace_id: str | None = None
    project_id: str | None = None
    project_code: str
    module_id: str | None = None
    target_stack: str = "NODEJS_FULLSTACK"
    generation_root: str
    node: dict[str, Any] = Field(default_factory=dict)
    handoff_context: dict[str, Any] = Field(default_factory=dict)
    development_build_context: dict[str, Any] = Field(default_factory=dict)
    deterministic_files: list[DevelopmentCodeFilePayload] = Field(default_factory=list)
    remarks: str | None = None


class DevelopmentNodejsCodegenResponse(BaseModel):
    status: str
    generator_mode: str
    provider: str | None = None
    model: str | None = None
    summary: str
    warnings: list[str] = Field(default_factory=list)
    files: list[DevelopmentCodeFilePayload] = Field(default_factory=list)
    usage: dict[str, Any] = Field(default_factory=dict)
    raw: dict[str, Any] = Field(default_factory=dict)




class DevelopmentNodejsRepairRequest(BaseModel):
    task_id: str | None = None
    workspace_id: str | None = None
    project_id: str | None = None
    project_code: str
    module_id: str | None = None
    target_stack: str = "NODEJS_FULLSTACK"
    generation_root: str
    node: dict[str, Any] = Field(default_factory=dict)
    handoff_context: dict[str, Any] = Field(default_factory=dict)
    development_build_context: dict[str, Any] = Field(default_factory=dict)
    acceptance_summary: str = Field(default="")
    failed_items: list[dict[str, Any]] = Field(default_factory=list)
    log_tail: list[str] = Field(default_factory=list)
    current_files: list[DevelopmentCodeFilePayload] = Field(default_factory=list)
    remarks: str | None = None
