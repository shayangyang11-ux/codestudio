from fastapi import FastAPI

from app.api.task_api import router as task_router
from app.api.runtime_api import router as runtime_router

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
app.include_router(task_router)