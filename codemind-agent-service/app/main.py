from fastapi import FastAPI

from app.api.task_api import router as task_router
from app.api.runtime_api import router as runtime_router
from app.api.development_codegen_api import router as development_codegen_router
from app.api.style_assistant_api import router as style_assistant_router

app = FastAPI(
    title="CodeMind Agent Service",
    version="1.0.0",
)


@app.get("/health")
async def health():
    return {
        "status": "ok",
        "service": "codemind-agent-service",
    }


app.include_router(runtime_router)
app.include_router(development_codegen_router)
app.include_router(task_router)
app.include_router(style_assistant_router)