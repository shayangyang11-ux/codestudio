import type {
  ArtifactResponse,
  AsyncAgentTaskResponse,
  GenerateApiContractRequest,
  GenerateBusinessFlowRequest,
  GenerateDataModelRequest,
  GenerateModulePrdRequest,
  GenerateModuleTreeRequest,
  GeneratePageSpecRequest,
  GenerateTestSpecRequest,
  GenerateDevHandoffPackageRequest,
  FullChainAsyncTaskResponse,
  GenerateFullChainRequest,
  BatchFullChainAsyncTaskResponse,
  GenerateBatchFullChainRequest,
} from '../types/codemind'
import { authFetch } from './httpClient'

export async function generateModuleTree(
  workspaceId: string,
  projectCode: string,
  payload: GenerateModuleTreeRequest,
): Promise<unknown> {
  return authFetch<unknown>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/tasks/module-tree`,
    {
      method: 'POST',
      body: JSON.stringify(payload),
    },
  )
}

export async function generateModulePrd(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  payload: GenerateModulePrdRequest,
): Promise<ArtifactResponse> {
  return authFetch<ArtifactResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/tasks/module-prd`,
    {
      method: 'POST',
      body: JSON.stringify(payload),
    },
  )
}

export async function generateBusinessFlow(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  payload: GenerateBusinessFlowRequest,
): Promise<ArtifactResponse> {
  return authFetch<ArtifactResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/tasks/business-flow`,
    {
      method: 'POST',
      body: JSON.stringify(payload),
    },
  )
}

export async function generatePageSpec(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  payload: GeneratePageSpecRequest,
): Promise<ArtifactResponse> {
  return authFetch<ArtifactResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/tasks/page-spec`,
    {
      method: 'POST',
      body: JSON.stringify(payload),
    },
  )
}

export async function createModulePrdTaskAsync(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  payload: GenerateModulePrdRequest,
): Promise<AsyncAgentTaskResponse> {
  return authFetch<AsyncAgentTaskResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/tasks/module-prd/async`,
    {
      method: 'POST',
      body: JSON.stringify(payload),
    },
  )
}

export async function createBusinessFlowTaskAsync(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  payload: GenerateBusinessFlowRequest,
): Promise<AsyncAgentTaskResponse> {
  return authFetch<AsyncAgentTaskResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/tasks/business-flow/async`,
    {
      method: 'POST',
      body: JSON.stringify(payload),
    },
  )
}

export async function createPageSpecTaskAsync(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  payload: GeneratePageSpecRequest,
): Promise<AsyncAgentTaskResponse> {
  return authFetch<AsyncAgentTaskResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/tasks/page-spec/async`,
    {
      method: 'POST',
      body: JSON.stringify(payload),
    },
  )
}

export async function generateDataModel(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  payload: GenerateDataModelRequest,
): Promise<ArtifactResponse> {
  return authFetch<ArtifactResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/tasks/data-model`,
    {
      method: 'POST',
      body: JSON.stringify(payload),
    },
  )
}

export async function generateApiContract(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  payload: GenerateApiContractRequest,
): Promise<ArtifactResponse> {
  return authFetch<ArtifactResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/tasks/api-contract`,
    {
      method: 'POST',
      body: JSON.stringify(payload),
    },
  )
}

export async function generateTestSpec(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  payload: GenerateTestSpecRequest,
): Promise<ArtifactResponse> {
  return authFetch<ArtifactResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/tasks/test-spec`,
    {
      method: 'POST',
      body: JSON.stringify(payload),
    },
  )
}

export async function generateDevHandoffPackage(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  payload: GenerateDevHandoffPackageRequest,
): Promise<ArtifactResponse> {
  return authFetch<ArtifactResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/tasks/dev-handoff-package`,
    {
      method: 'POST',
      body: JSON.stringify(payload),
    },
  )
}

export async function createDataModelTaskAsync(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  payload: GenerateDataModelRequest,
): Promise<AsyncAgentTaskResponse> {
  return authFetch<AsyncAgentTaskResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/tasks/data-model/async`,
    {
      method: 'POST',
      body: JSON.stringify(payload),
    },
  )
}

export async function createApiContractTaskAsync(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  payload: GenerateApiContractRequest,
): Promise<AsyncAgentTaskResponse> {
  return authFetch<AsyncAgentTaskResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/tasks/api-contract/async`,
    {
      method: 'POST',
      body: JSON.stringify(payload),
    },
  )
}

export async function createTestSpecTaskAsync(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  payload: GenerateTestSpecRequest,
): Promise<AsyncAgentTaskResponse> {
  return authFetch<AsyncAgentTaskResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/tasks/test-spec/async`,
    {
      method: 'POST',
      body: JSON.stringify(payload),
    },
  )
}

export async function createDevHandoffPackageTaskAsync(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  payload: GenerateDevHandoffPackageRequest,
): Promise<AsyncAgentTaskResponse> {
  return authFetch<AsyncAgentTaskResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/tasks/dev-handoff-package/async`,
    {
      method: 'POST',
      body: JSON.stringify(payload),
    },
  )
}

export async function createFullChainTaskAsync(
  workspaceId: string,
  projectCode: string,
  moduleId: string,
  payload: GenerateFullChainRequest,
): Promise<FullChainAsyncTaskResponse> {
  return authFetch<FullChainAsyncTaskResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/modules/${encodeURIComponent(moduleId)}` +
      `/tasks/full-chain/async`,
    {
      method: 'POST',
      body: JSON.stringify(payload),
    },
  )
}

export async function createBatchFullChainTasksAsync(
  workspaceId: string,
  projectCode: string,
  payload: GenerateBatchFullChainRequest,
): Promise<BatchFullChainAsyncTaskResponse> {
  return authFetch<BatchFullChainAsyncTaskResponse>(
    `/api/workspaces/${encodeURIComponent(workspaceId)}` +
      `/projects/${encodeURIComponent(projectCode)}` +
      `/tasks/full-chain/batch/async`,
    {
      method: 'POST',
      body: JSON.stringify(payload),
    },
  )
}