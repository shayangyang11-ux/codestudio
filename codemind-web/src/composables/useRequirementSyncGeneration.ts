import { ref } from 'vue'
import {
  generateBusinessFlow,
  generateModulePrd,
  generatePageSpec,
} from '../api'
import {
  getRequirementArtifactDefinitionByArtifactType,
  type RequirementArtifactType,
} from '../domain/requirementArtifactDefinitions'
import { buildRequirementArtifactPrompt } from '../domain/requirementGenerationPrompts'
import { getArtifactId } from './useModuleArtifacts'
import type {
  AgentTab,
  ArtifactResponse,
  AssistantStep,
  ModuleTreeNode,
  ProjectWorkspaceState,
} from '../types/codemind'

export interface UseRequirementSyncGenerationOptions {
  resolveWorkspaceId: () => string
  resolveProjectCode: () => string
  resolveActiveAgentTab: () => AgentTab
  resolveSelectedModule: () => ModuleTreeNode | null
  resolveModuleDisplayName: (module: ModuleTreeNode) => string
  setArtifactByType: (
    artifactType: RequirementArtifactType,
    artifact: ArtifactResponse | null,
  ) => void
  clearArtifactsAfterArtifact: (artifactType: RequirementArtifactType) => void
  setStepStatus: (
    stepId: string,
    status: AssistantStep['status'],
  ) => void
  saveCurrentWorkspaceState: (payload: {
    selectedModuleId?: string | null
    selectedArtifactType?: string | null
    selectedArtifactId?: string | null
    activeAgentTab?: AgentTab | string | null
    uiState?: Record<string, unknown>
  }) => Promise<ProjectWorkspaceState | null>

    onArtifactGenerated?: (
    artifactType: Extract<
      RequirementArtifactType,
      'MODULE_PRD' | 'BUSINESS_FLOW' | 'PAGE_SPEC'
    >,
    artifact: ArtifactResponse,
  ) => void | Promise<void>
}

export function useRequirementSyncGeneration(
  options: UseRequirementSyncGenerationOptions,
) {
  const isGeneratingPrd = ref(false)
  const isGeneratingBusinessFlow = ref(false)
  const isGeneratingPageSpec = ref(false)

  async function handleGenerateModulePrd() {
    await startSyncArtifactGeneration('MODULE_PRD')
  }

  async function handleGenerateBusinessFlow() {
    await startSyncArtifactGeneration('BUSINESS_FLOW')
  }

  async function handleGeneratePageSpec() {
    await startSyncArtifactGeneration('PAGE_SPEC')
  }

  async function startSyncArtifactGeneration(
    artifactType: Extract<
      RequirementArtifactType,
      'MODULE_PRD' | 'BUSINESS_FLOW' | 'PAGE_SPEC'
    >,
  ) {
    const module = options.resolveSelectedModule()

    if (!module?.id) {
      return
    }

    const definition = getRequirementArtifactDefinitionByArtifactType(artifactType)
    const moduleName = options.resolveModuleDisplayName(module)

    setGeneratingFlag(artifactType, true)

    try {
      options.setStepStatus('receive', 'success')
      options.setStepStatus(definition.stepId, 'running')
      options.setStepStatus('artifact', 'running')

      const artifact = await generateSyncArtifact(
        artifactType,
        module.id,
        moduleName,
      )

      options.setArtifactByType(artifactType, artifact)
      options.clearArtifactsAfterArtifact(artifactType)

      await options.onArtifactGenerated?.(artifactType, artifact)

      for (const downstreamArtifactType of definition.downstreamArtifactTypes) {
        const downstreamDefinition =
          getRequirementArtifactDefinitionByArtifactType(downstreamArtifactType)

        options.setStepStatus(downstreamDefinition.stepId, 'pending')
      }

      await options.saveCurrentWorkspaceState({
        selectedModuleId: module.id,
        selectedArtifactType: artifactType,
        selectedArtifactId: getArtifactId(artifact),
        activeAgentTab: options.resolveActiveAgentTab(),
        uiState: {
          lastAction: `generate-${artifactType.toLowerCase()}`,
        },
      })

      options.setStepStatus(definition.stepId, 'success')
      options.setStepStatus('artifact', 'success')
    } catch (error) {
      console.error(error)
      options.setStepStatus(definition.stepId, 'failed')
      options.setStepStatus('artifact', 'failed')
    } finally {
      setGeneratingFlag(artifactType, false)
    }
  }

  async function generateSyncArtifact(
    artifactType: Extract<
      RequirementArtifactType,
      'MODULE_PRD' | 'BUSINESS_FLOW' | 'PAGE_SPEC'
    >,
    moduleId: string,
    moduleName: string,
  ): Promise<ArtifactResponse> {
    const payload = {
      requirementText: buildRequirementArtifactPrompt(
        artifactType,
        moduleName,
      ),
    }

    if (artifactType === 'MODULE_PRD') {
      return generateModulePrd(
        options.resolveWorkspaceId(),
        options.resolveProjectCode(),
        moduleId,
        payload,
      )
    }

    if (artifactType === 'BUSINESS_FLOW') {
      return generateBusinessFlow(
        options.resolveWorkspaceId(),
        options.resolveProjectCode(),
        moduleId,
        payload,
      )
    }

    return generatePageSpec(
      options.resolveWorkspaceId(),
      options.resolveProjectCode(),
      moduleId,
      payload,
    )
  }

  function setGeneratingFlag(
    artifactType: RequirementArtifactType,
    value: boolean,
  ) {
    if (artifactType === 'MODULE_PRD') {
      isGeneratingPrd.value = value
      return
    }

    if (artifactType === 'BUSINESS_FLOW') {
      isGeneratingBusinessFlow.value = value
      return
    }

    if (artifactType === 'PAGE_SPEC') {
      isGeneratingPageSpec.value = value
    }
  }

  return {
    isGeneratingPrd,
    isGeneratingBusinessFlow,
    isGeneratingPageSpec,
    handleGenerateModulePrd,
    handleGenerateBusinessFlow,
    handleGeneratePageSpec,
  }
}