import { computed, ref } from 'vue'
import { getModuleDisplayName, getModuleStableKey } from '../api'
import type { ModuleTreeNode } from '../types/codemind'

export function useModuleTreeSelection() {
  const selectedModule = ref<ModuleTreeNode | null>(null)

  const selectedModuleId = computed(() => selectedModule.value?.id || null)

  const selectedModuleName = computed(() =>
    getModuleDisplayName(selectedModule.value),
  )

  const selectedModuleKey = computed(() =>
    getModuleStableKey(selectedModule.value),
  )

  const selectedModuleType = computed(() =>
    selectedModule.value?.moduleType || selectedModule.value?.type || '',
  )

  function selectModule(
    module: ModuleTreeNode | null,
  ) {
    selectedModule.value = module
  }

  function clearSelectedModule() {
    selectedModule.value = null
  }

  function restoreSelectedModuleById(
    moduleList: ModuleTreeNode[],
    moduleId: string | null | undefined,
  ): ModuleTreeNode | null {
    if (!moduleId) {
      selectedModule.value = null
      return null
    }

    const matchedModule = findModuleById(moduleList, moduleId)

    selectedModule.value = matchedModule

    return matchedModule
  }

  return {
    selectedModule,
    selectedModuleId,
    selectedModuleName,
    selectedModuleKey,
    selectedModuleType,
    selectModule,
    clearSelectedModule,
    restoreSelectedModuleById,
  }
}

export function findModuleById(
  moduleList: ModuleTreeNode[],
  moduleId: string,
): ModuleTreeNode | null {
  for (const module of moduleList) {
    if (module.id === moduleId) {
      return module
    }

    const matchedChild = findModuleById(
      module.children || [],
      moduleId,
    )

    if (matchedChild) {
      return matchedChild
    }
  }

  return null
}