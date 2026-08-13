import { ref } from 'vue'
import { fetchCurrentModuleArtifact, fetchLatestProjectArtifact } from '../api'
import {
  getDownstreamArtifactTypes,
  getRequirementArtifactDefinitionByArtifactType,
  REQUIREMENT_ARTIFACT_DEFINITIONS,
  type RequirementArtifactType,
} from '../domain/requirementArtifactDefinitions'
import type {
  ArtifactResponse,
  AssistantStep,
  ModuleTreeNode,
} from '../types/codemind'

export type SetAssistantStepStatus = (
  stepId: string,
  status: AssistantStep['status'],
) => void

export function useModuleArtifacts(
  resolveWorkspaceId: () => string,
  resolveProjectCode: () => string,
  setStepStatus: SetAssistantStepStatus,
) {
  const prdArtifact = ref<ArtifactResponse | null>(null)
  const modulePrdMarkdown = ref('')

  const businessFlowArtifact = ref<ArtifactResponse | null>(null)
  const businessFlowMarkdown = ref('')

  const pageSpecArtifact = ref<ArtifactResponse | null>(null)
const pageMapArtifact = ref<ArtifactResponse | null>(null)
  const pageSpecMarkdown = ref('')
const pageMapMarkdown = ref('')

  const dataModelArtifact = ref<ArtifactResponse | null>(null)
  const dataModelMarkdown = ref('')

  const apiContractArtifact = ref<ArtifactResponse | null>(null)
  const apiContractMarkdown = ref('')

  const testSpecArtifact = ref<ArtifactResponse | null>(null)
  const testSpecMarkdown = ref('')

  const uiContractArtifact = ref<ArtifactResponse | null>(null)
  const uiContractMarkdown = ref('')

  const devHandoffPackageArtifact = ref<ArtifactResponse | null>(null)
  const devHandoffPackageMarkdown = ref('')

  async function loadCurrentModulePrd(node: ModuleTreeNode): Promise<boolean> {
    return loadCurrentArtifactByType(node, 'MODULE_PRD')
  }

  async function loadCurrentBusinessFlow(node: ModuleTreeNode): Promise<boolean> {
    return loadCurrentArtifactByType(node, 'BUSINESS_FLOW')
  }

  async function loadCurrentPageSpec(node: ModuleTreeNode): Promise<boolean> {
    return loadCurrentArtifactByType(node, 'PAGE_SPEC')
  }

  async function loadCurrentPageMap(node: ModuleTreeNode): Promise<boolean> {
    return loadCurrentArtifactByType(node, 'PAGE_MAP')
  }

  async function loadCurrentDataModel(node: ModuleTreeNode): Promise<boolean> {
    return loadCurrentArtifactByType(node, 'DATA_MODEL')
  }

  async function loadCurrentApiContract(node: ModuleTreeNode): Promise<boolean> {
    return loadCurrentArtifactByType(node, 'API_CONTRACT')
  }

  async function loadCurrentTestSpec(node: ModuleTreeNode): Promise<boolean> {
    return loadCurrentArtifactByType(node, 'TEST_SPEC')
  }

  async function loadCurrentUiContract(node: ModuleTreeNode): Promise<boolean> {
    return loadCurrentArtifactByType(node, 'UI_CONTRACT')
  }

  async function loadCurrentDevHandoffPackage(node: ModuleTreeNode): Promise<boolean> {
    return loadCurrentArtifactByType(node, 'DEV_HANDOFF_PACKAGE')
  }

  async function loadAllCurrentModuleArtifacts(
    node: ModuleTreeNode,
  ): Promise<Record<RequirementArtifactType, boolean>> {
    const result = {} as Record<RequirementArtifactType, boolean>

    for (const definition of REQUIREMENT_ARTIFACT_DEFINITIONS) {
      result[definition.artifactType] = await loadCurrentArtifactByType(
        node,
        definition.artifactType,
      )
    }

    return result
  }

  async function loadCurrentArtifactByType(
    node: ModuleTreeNode,
    artifactType: RequirementArtifactType,
  ): Promise<boolean> {
    if (!node.id) {
      return false
    }

    try {
      const artifact = await fetchCurrentModuleArtifact(
        resolveWorkspaceId(),
        resolveProjectCode(),
        node.id,
        artifactType,
      )

      if (artifact.exists) {
        setArtifactByType(artifactType, artifact)

        const definition =
          getRequirementArtifactDefinitionByArtifactType(artifactType)

        setStepStatus(definition.stepId, 'success')

        return true
      }

      setArtifactByType(artifactType, null)
      return false
    } catch (error) {
      console.error(error)
      return false
    }
  }

  async function loadLatestProjectArtifactByType(
    artifactType: RequirementArtifactType,
  ): Promise<ArtifactResponse | null> {
    try {
      const artifact = await fetchLatestProjectArtifact(
        resolveWorkspaceId(),
        resolveProjectCode(),
        artifactType,
      )

      if (artifact.exists) {
        setArtifactByType(artifactType, artifact)
        return artifact
      }

      return null
    } catch (error) {
      console.error(error)
      return null
    }
  }

  function getArtifactByType(
    artifactType: RequirementArtifactType,
  ): ArtifactResponse | null {
    if (artifactType === 'MODULE_PRD') return prdArtifact.value
    if (artifactType === 'BUSINESS_FLOW') return businessFlowArtifact.value
    if (artifactType === 'PAGE_MAP') return pageMapArtifact.value
    if (artifactType === 'PAGE_SPEC') return pageSpecArtifact.value
    if (artifactType === 'DATA_MODEL') return dataModelArtifact.value
    if (artifactType === 'API_CONTRACT') return apiContractArtifact.value
    if (artifactType === 'TEST_SPEC') return testSpecArtifact.value
    if (artifactType === 'UI_CONTRACT') return uiContractArtifact.value

    return devHandoffPackageArtifact.value
  }

  function setArtifactByType(
    artifactType: RequirementArtifactType,
    artifact: ArtifactResponse | null,
  ) {
    const markdown = artifact?.contentMarkdown || ''

    if (artifactType === 'MODULE_PRD') {
      prdArtifact.value = artifact
      modulePrdMarkdown.value = markdown
      return
    }

    if (artifactType === 'BUSINESS_FLOW') {
      businessFlowArtifact.value = artifact
      businessFlowMarkdown.value = markdown
      return
    }

    if (artifactType === 'PAGE_MAP') {
      pageMapArtifact.value = artifact
      pageMapMarkdown.value = markdown
      return
    }

    if (artifactType === 'PAGE_SPEC') {
      pageSpecArtifact.value = artifact
      pageSpecMarkdown.value = markdown
      return
    }

    if (artifactType === 'DATA_MODEL') {
      dataModelArtifact.value = artifact
      dataModelMarkdown.value = markdown
      return
    }

    if (artifactType === 'API_CONTRACT') {
      apiContractArtifact.value = artifact
      apiContractMarkdown.value = markdown
      return
    }

    if (artifactType === 'TEST_SPEC') {
      testSpecArtifact.value = artifact
      testSpecMarkdown.value = markdown
      return
    }

    if (artifactType === 'UI_CONTRACT') {
      uiContractArtifact.value = artifact
      uiContractMarkdown.value = markdown
      return
    }

    devHandoffPackageArtifact.value = artifact
    devHandoffPackageMarkdown.value = markdown
  }

  function clearArtifacts() {
    for (const definition of REQUIREMENT_ARTIFACT_DEFINITIONS) {
      clearArtifactByType(definition.artifactType)
    }
  }

  function clearArtifactByType(
    artifactType: RequirementArtifactType,
  ) {
    setArtifactByType(artifactType, null)
  }

  function clearArtifactsAfterArtifact(
    artifactType: RequirementArtifactType,
  ) {
    const downstreamArtifactTypes = getDownstreamArtifactTypes(artifactType)

    for (const downstreamArtifactType of downstreamArtifactTypes) {
      clearArtifactByType(downstreamArtifactType)
    }
  }

  function clearArtifactsAfterModulePrd() {
    clearArtifactsAfterArtifact('MODULE_PRD')
  }

  function clearArtifactsAfterBusinessFlow() {
    clearArtifactsAfterArtifact('BUSINESS_FLOW')
  }

  function clearArtifactsAfterPageSpec() {
    clearArtifactsAfterArtifact('PAGE_SPEC')
  }

  function clearArtifactsAfterDataModel() {
    clearArtifactsAfterArtifact('DATA_MODEL')
  }

  function clearArtifactsAfterApiContract() {
    clearArtifactsAfterArtifact('API_CONTRACT')
  }

  function clearArtifactsAfterTestSpec() {
    clearArtifactsAfterArtifact('TEST_SPEC')
  }

  function clearArtifactsAfterUiContract() {
    clearArtifactsAfterArtifact('UI_CONTRACT')
  }

  function getCurrentSelectedArtifactType(): RequirementArtifactType | null {
    const reversedDefinitions = [...REQUIREMENT_ARTIFACT_DEFINITIONS].reverse()

    for (const definition of reversedDefinitions) {
      const artifact = getArtifactByType(definition.artifactType)

      if (artifact?.artifactId || artifact?.exists) {
        return definition.artifactType
      }
    }

    return null
  }

  function getCurrentSelectedArtifactId(): string | null {
    const artifactType = getCurrentSelectedArtifactType()

    if (!artifactType) {
      return null
    }

    return getArtifactId(getArtifactByType(artifactType))
  }

  return {
    prdArtifact,
    modulePrdMarkdown,

    businessFlowArtifact,
    businessFlowMarkdown,

    pageSpecArtifact,
    pageSpecMarkdown,

    pageMapArtifact,
    pageMapMarkdown,

    dataModelArtifact,
    dataModelMarkdown,

    apiContractArtifact,
    apiContractMarkdown,

    testSpecArtifact,
    testSpecMarkdown,

    uiContractArtifact,
    uiContractMarkdown,

    devHandoffPackageArtifact,
    devHandoffPackageMarkdown,

    loadCurrentModulePrd,
    loadCurrentBusinessFlow,
    loadCurrentPageSpec,
    loadCurrentPageMap,
    loadCurrentDataModel,
    loadCurrentApiContract,
    loadCurrentTestSpec,
    loadCurrentUiContract,
    loadCurrentDevHandoffPackage,

    loadCurrentArtifactByType,
    loadAllCurrentModuleArtifacts,
    loadLatestProjectArtifactByType,

    getArtifactByType,
    setArtifactByType,

    clearArtifacts,
    clearArtifactByType,
    clearArtifactsAfterArtifact,
    clearArtifactsAfterModulePrd,
    clearArtifactsAfterBusinessFlow,
    clearArtifactsAfterPageSpec,
    clearArtifactsAfterDataModel,
    clearArtifactsAfterApiContract,
    clearArtifactsAfterTestSpec,
    clearArtifactsAfterUiContract,

    getCurrentSelectedArtifactType,
    getCurrentSelectedArtifactId,
  }
}

export function getArtifactId(
  artifact: ArtifactResponse | null,
): string | null {
  if (!artifact) {
    return null
  }

  const value = (artifact as Record<string, unknown>).artifactId

  if (typeof value !== 'string' || !value.trim()) {
    return null
  }

  return value
}

export function getArtifactModuleId(
  artifact: ArtifactResponse | null,
): string | null {
  if (!artifact) {
    return null
  }

  const value = (artifact as Record<string, unknown>).moduleId

  if (typeof value !== 'string' || !value.trim()) {
    return null
  }

  return value
}