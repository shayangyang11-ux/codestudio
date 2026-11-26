<template>
  <main class="development-workspace-panel">
    <section class="dev-hero-card">
      <div>
        <p class="eyebrow">Development Workspace</p>
        <h2>按模块 / 功能 / 页面开发真实 Node.js 系统</h2>
        <span>
          左侧仍然是业务模块树；中间查看真实运行页面、代码、接口、数据、测试和日志；
          代码目录与模块树通过 Code Trace Graph 多对多关联，内部诊断默认收起。
        </span>
      </div>

      <div class="hero-actions">
        <button class="secondary-button" :disabled="!selectedModule || loading" @click="$emit('refresh')">
          {{ loading ? '检查中...' : '刷新开发状态' }}
        </button>
        <button class="secondary-button" :disabled="!selectedModule" @click="$emit('open-quality-check')">
          高级诊断
        </button>
      </div>
    </section>

    <section v-if="!selectedModule" class="empty-panel">
      <strong>请选择左侧模块树节点</strong>
      <p>开发阶段只把 MODULE / FEATURE / PAGE 作为用户主导航；组件、字段、按钮留在页面预览和代码上下文里。</p>
    </section>

    <section v-else-if="error" class="error-box">{{ error }}</section>

    <section v-else-if="loading && !readiness" class="empty-panel">
      正在读取开发状态...
    </section>

    <template v-else-if="readiness">
      <section class="dev-node-card" :class="nodeToneClass">
        <div>
          <p class="eyebrow">当前开发节点</p>
          <h3>{{ currentNodeName }}</h3>
          <span>{{ currentNodeDescription }}</span>
        </div>

        <div class="node-badges">
          <b>{{ publicNodeTypeLabel }}</b>
          <span>{{ finalModuleBadge }}</span>
          <span>{{ readiness.ready ? '可开发' : '需补充' }}</span>
        </div>
      </section>

      <section class="dev-status-grid">
        <article>
          <strong>{{ codebaseWorkspace?.codebaseExists ? '已生成' : '未生成' }}</strong>
          <span>代码库</span>
        </article>
        <article>
          <strong>{{ codebaseWorkspace?.selectedNodeHasCode ? '已关联' : '待关联' }}</strong>
          <span>当前节点代码</span>
        </article>
        <article>
          <strong>{{ runtimeStatus?.runtimeStatus || '未启动' }}</strong>
          <span>运行状态</span>
        </article>
        <article>
          <strong>{{ codebaseAcceptanceStatus?.acceptanceStatus || '未验收' }}</strong>
          <span>验收状态</span>
        </article>
      </section>

      <section class="dev-action-card">
        <div>
          <strong>{{ nextActionTitle }}</strong>
          <p>{{ nextActionDescription }}</p>
        </div>

        <div class="dev-action-buttons">
          <button class="primary-button" :disabled="!canGenerateNodejsCode || generatingCode" @click="handleGenerateNodejsCode(false)">
            {{ generatingCode ? '生成中...' : mainGenerateButtonText }}
          </button>
          <button class="secondary-button" :disabled="!codebaseWorkspace?.codebaseExists || runtimeLoading" @click="loadRuntimeStatus">
            刷新运行
          </button>
          <button class="secondary-button" :disabled="!codebaseWorkspace?.codebaseExists || codebaseAcceptanceRunning" @click="handleRunAcceptance">
            {{ codebaseAcceptanceRunning ? '验收中...' : '运行验收' }}
          </button>
        </div>
      </section>

      <nav class="dev-tabs">
        <button v-for="tab in workspaceTabs" :key="tab.key" type="button" :class="{ active: activeWorkspaceTab === tab.key }" @click="activeWorkspaceTab = tab.key">
          {{ tab.label }}
        </button>
      </nav>

      <section v-if="activeWorkspaceTab === 'overview'" class="workspace-section">
        <div class="section-heading">
          <h3>业务节点总览</h3>
          <p>用户只按模块、功能、页面理解系统；组件和工程资产隐藏在页面与代码上下文里。</p>
        </div>

        <div class="overview-grid">
          <article>
            <span>节点职责</span>
            <strong>{{ nodeResponsibilityTitle }}</strong>
            <p>{{ nodeResponsibilityDescription }}</p>
          </article>
          <article>
            <span>代码关联</span>
            <strong>{{ traceSummaryTitle }}</strong>
            <p>{{ traceSummaryText }}</p>
          </article>
          <article>
            <span>真实运行</span>
            <strong>{{ runtimeStatus?.previewUrl ? '已有预览入口' : '等待启动' }}</strong>
            <p>{{ runtimeStatus?.previewUrl || codebaseWorkspace?.previewRoute || '生成并启动后可预览真实业务页面。' }}</p>
          </article>
        </div>

        <details class="advanced-details">
          <summary>查看技术诊断</summary>
          <div class="diagnostic-grid">
            <article>
              <strong>开发准备度</strong>
              <p>{{ readiness.readinessText }} / {{ readiness.score }} 分</p>
              <small>{{ readiness.readinessDescription }}</small>
            </article>
            <article>
              <strong>阻断项</strong>
              <p>{{ readiness.blockers.length }} 项</p>
              <small>{{ readiness.blockers.map((item) => item.title).join('；') || '无' }}</small>
            </article>
            <article>
              <strong>警告项</strong>
              <p>{{ readiness.warnings.length }} 项</p>
              <small>{{ readiness.warnings.map((item) => item.title).join('；') || '无' }}</small>
            </article>
          </div>
        </details>
      </section>

      <section v-if="activeWorkspaceTab === 'preview'" class="workspace-section">
        <div class="section-heading">
          <h3>真实运行预览</h3>
          <p>这里展示生成后的真实业务系统，不是需求阶段的 mock 原型。</p>
        </div>

        <div class="runtime-toolbar">
          <button class="secondary-button" :disabled="runtimeLoading" @click="handleInstallRuntime">安装依赖</button>
          <button class="primary-button" :disabled="runtimeLoading || !codebaseWorkspace?.codebaseExists" @click="handleStartRuntime">启动系统</button>
          <button class="secondary-button" :disabled="runtimeLoading || !codebaseWorkspace?.codebaseExists" @click="handleStopRuntime">停止</button>
          <button class="secondary-button" :disabled="runtimeLoading || !codebaseWorkspace?.codebaseExists" @click="handleRestartRuntime">重启</button>
          <button class="secondary-button" :disabled="!runtimePreviewUrl" @click="openRuntimePreview">浏览器打开</button>
        </div>

        <div v-if="runtimeError" class="error-box">{{ runtimeError }}</div>

        <div class="runtime-summary" :class="runtimeToneClass">
          <strong>{{ runtimeStatus?.runtimeStatus || '未读取运行状态' }}</strong>
          <span>{{ runtimeStatus?.summary || '点击启动系统后，平台会读取前端、后端和健康检查状态。' }}</span>
        </div>

        <iframe
          v-if="runtimePreviewUrl"
          class="runtime-frame"
          :key="runtimePreviewKey"
          :src="runtimePreviewUrl"
          title="Generated Node.js preview"
          sandbox="allow-scripts allow-forms allow-same-origin allow-popups"
        ></iframe>

        <div v-else class="empty-panel">暂无预览地址。请先生成代码并启动系统。</div>
      </section>

      <section v-if="activeWorkspaceTab === 'code'" class="workspace-section code-workspace-section">
        <div class="section-heading code-heading">
          <div>
            <h3>代码工作区</h3>
            <p>按业务节点看相关代码，也可以切换到完整项目目录。新增文件先选用途，系统推荐安全路径。</p>
          </div>
          <div class="code-heading-actions">
            <button class="secondary-button" :disabled="codebaseWorkspaceLoading" @click="loadCodebaseWorkspace">
              {{ codebaseWorkspaceLoading ? '刷新中...' : '刷新代码目录' }}
            </button>
            <button class="secondary-button" type="button" @click="newFileVisible = !newFileVisible">
              {{ newFileVisible ? '收起新增文件' : '新增文件' }}
            </button>
          </div>
        </div>

        <div v-if="codebaseWorkspaceError" class="error-box">{{ codebaseWorkspaceError }}</div>

        <section v-if="newFileVisible" class="new-file-card">
          <div class="new-file-grid">
            <label>
              文件用途
              <select v-model="newFileScope">
                <option value="CURRENT_PAGE">当前页面</option>
                <option value="CURRENT_FEATURE">当前功能</option>
                <option value="CURRENT_MODULE">当前模块</option>
                <option value="SHARED">多个页面共享</option>
                <option value="PROJECT">项目全局</option>
                <option value="TEST">测试</option>
              </select>
            </label>
            <label>
              文件类型
              <select v-model="newFileRole">
                <option value="FRONTEND_COMPONENT">前端组件</option>
                <option value="FRONTEND_API">前端 API</option>
                <option value="BACKEND_ROUTE">后端路由</option>
                <option value="BACKEND_SERVICE">后端服务</option>
                <option value="BACKEND_REPOSITORY">数据仓储</option>
                <option value="VALIDATION">校验规则</option>
                <option value="UTILITY">工具函数</option>
                <option value="TEST">测试用例</option>
                <option value="STYLE">样式文件</option>
              </select>
            </label>
            <label>
              文件名
              <input v-model="newFileName" placeholder="例如 InboundOrderForm.vue" />
            </label>
          </div>

          <div class="suggested-path">
            <span>推荐路径</span>
            <strong>{{ suggestedNewFilePath }}</strong>
          </div>

          <button class="primary-button" :disabled="savingCodeFile || !canSaveSuggestedFile" @click="handleCreateSuggestedFile">
            {{ savingCodeFile ? '创建中...' : '创建并关联当前节点' }}
          </button>
        </section>

        <div class="code-layout">
          <aside class="code-sidebar">
            <div class="code-view-toggle">
              <button type="button" :class="{ active: codebaseViewMode === 'node' }" @click="codebaseViewMode = 'node'">当前节点代码</button>
              <button type="button" :class="{ active: codebaseViewMode === 'tree' }" @click="codebaseViewMode = 'tree'">项目目录</button>
            </div>

            <div v-if="codebaseViewMode === 'node'" class="file-list">
              <button
                v-for="file in nodeFileCards"
                :key="`${file.relationship}-${file.filePath}`"
                type="button"
                :class="{ active: selectedFilePath === file.filePath }"
                @click="openCodeFile(file.filePath)"
              >
                <strong>{{ compactPath(file.filePath) }}</strong>
                <span>{{ relationshipLabel(file.relationship) }} / {{ file.fileType }}</span>
              </button>

              <div v-if="nodeFileCards.length === 0" class="empty-mini">
                当前节点暂无已关联文件。生成代码或手动新增文件后会出现在这里。
              </div>
            </div>

            <div v-else class="file-tree">
              <CodeTree
                v-for="item in codebaseWorkspace?.fileTree || []"
                :key="item.path"
                :item="item"
                :active-path="selectedFilePath"
                @open="openCodeFile"
              />
            </div>
          </aside>

          <section class="code-editor-panel">
            <header>
              <div>
                <strong>{{ selectedFilePath || '请选择或新增代码文件' }}</strong>
                <span>{{ selectedCodeFile?.language || 'text' }} {{ codeEditorDirty ? '· 未保存' : '' }}</span>
              </div>

              <div class="editor-actions">
                <button class="secondary-button" :disabled="!selectedFilePath || codeFileLoading" @click="selectedFilePath && openCodeFile(selectedFilePath)">
                  刷新
                </button>
                <button class="primary-button" :disabled="!selectedFilePath || !codeEditorDirty || savingCodeFile" @click="handleSaveCurrentFile">
                  {{ savingCodeFile ? '保存中...' : '保存代码' }}
                </button>
              </div>
            </header>

            <div v-if="codeFileError" class="error-box">{{ codeFileError }}</div>
            <div v-if="codeFileSaveError" class="error-box">{{ codeFileSaveError }}</div>
            <div v-if="codeFileSaveMessage" class="success-box">{{ codeFileSaveMessage }}</div>

            <textarea
              v-model="codeEditorContent"
              class="code-editor"
              spellcheck="false"
              :placeholder="selectedFilePath ? '正在读取文件...' : '从左侧选择文件，或点击新增文件。'"
              @input="codeEditorDirty = true"
            ></textarea>

            <footer class="file-impact-strip">
              <strong>影响范围</strong>
              <span>{{ selectedFileImpactText }}</span>
            </footer>
          </section>
        </div>
      </section>

      <section v-if="activeWorkspaceTab === 'api'" class="workspace-section">
        <div class="section-heading">
          <h3>接口与后端逻辑</h3>
          <p>系统从 Code Trace Graph 中筛选后端路由、服务和仓储文件，避免用户在完整目录里迷路。</p>
        </div>
        <div class="trace-file-grid">
          <button v-for="file in backendTraceFiles" :key="file.filePath" type="button" @click="openCodeFileAndSwitch(file.filePath)">
            <strong>{{ compactPath(file.filePath) }}</strong>
            <span>{{ file.fileType }} / {{ relationshipLabel(file.relationship) }}</span>
          </button>
        </div>
        <div v-if="backendTraceFiles.length === 0" class="empty-panel">当前节点暂无后端关联文件。</div>
      </section>

      <section v-if="activeWorkspaceTab === 'data'" class="workspace-section">
        <div class="section-heading">
          <h3>数据与本地存储</h3>
          <p>开发阶段页面不再只用前端 mock，业务主链路应经过 Node.js 后端和本地数据层。</p>
        </div>
        <div class="trace-file-grid">
          <button v-for="file in dataTraceFiles" :key="file.filePath" type="button" @click="openCodeFileAndSwitch(file.filePath)">
            <strong>{{ compactPath(file.filePath) }}</strong>
            <span>{{ file.fileType }} / {{ relationshipLabel(file.relationship) }}</span>
          </button>
        </div>
        <div v-if="dataTraceFiles.length === 0" class="empty-panel">当前节点暂无数据层关联文件。</div>
      </section>

      <section v-if="activeWorkspaceTab === 'test'" class="workspace-section">
        <div class="section-heading">
          <h3>测试与验收</h3>
          <p>按当前模块树节点运行 smoke / acceptance；失败后系统会带上当前节点、当前文件、日志和 Code Trace Graph 交给 AI 修复。</p>
        </div>

        <div class="runtime-toolbar">
          <button class="primary-button" :disabled="codebaseAcceptanceRunning || !codebaseWorkspace?.codebaseExists" @click="handleRunAcceptance">
            {{ codebaseAcceptanceRunning ? '验收中...' : '运行当前节点验收' }}
          </button>
          <button class="secondary-button" :disabled="codebaseAcceptanceLoading" @click="loadAcceptanceStatus">
            刷新验收状态
          </button>
          <button class="secondary-button" :disabled="!canRepairCodebase" @click="handleRepair(false)">
            {{ codebaseRepairLoading && !codebaseRepairResult?.applied ? '生成中...' : '生成修复建议' }}
          </button>
          <button class="secondary-button" :disabled="!canRepairCodebase" @click="handleRepair(true)">
            {{ codebaseRepairLoading ? '修复中...' : 'AI 修复并重新验收' }}
          </button>
        </div>

        <div v-if="codebaseAcceptanceError" class="error-box">{{ codebaseAcceptanceError }}</div>
        <div v-if="codebaseRepairError" class="error-box">{{ codebaseRepairError }}</div>

        <div v-if="codebaseAcceptanceStatus" class="acceptance-summary" :class="acceptanceToneClass">
          <strong>{{ codebaseAcceptanceStatus.acceptanceStatus }}</strong>
          <span>{{ codebaseAcceptanceStatus.summary }}</span>
        </div>

        <div v-if="codebaseRepairResult" class="repair-summary" :class="repairToneClass">
          <div>
            <strong>{{ codebaseRepairResult.repairStatus }}</strong>
            <span>{{ codebaseRepairResult.summary }}</span>
          </div>
          <div class="repair-meta">
            <b>{{ codebaseRepairResult.repairFileCount }} 个修复文件</b>
            <span>{{ codebaseRepairResult.provider || 'AI' }} / {{ codebaseRepairResult.model || codebaseRepairResult.generatorMode || 'repair' }}</span>
          </div>
          <div v-if="repairResultFiles.length" class="inline-file-actions">
            <button v-for="file in repairResultFiles" :key="file.filePath" type="button" @click="openCodeFileAndSwitch(file.filePath)">
              {{ compactPath(file.filePath) }}
            </button>
          </div>
        </div>

        <div class="acceptance-list">
          <article v-for="item in codebaseAcceptanceStatus?.items || []" :key="item.id || item.title" :class="`acceptance-${String(item.status || '').toLowerCase()}`">
            <header>
              <strong>{{ item.title }}</strong>
              <span>{{ item.status }} {{ item.command ? `· ${item.command}` : '' }}</span>
            </header>
            <p>{{ item.summary }}</p>
            <div v-if="acceptanceItemFiles(item).length" class="inline-file-actions">
              <button v-for="issue in acceptanceItemFiles(item)" :key="`${item.id}-${issue.filePath}-${issue.lineNo || 0}`" type="button" @click="openLogIssue(issue)">
                打开 {{ compactPath(issue.filePath) }}{{ issue.lineNo ? `:${issue.lineNo}` : '' }}
              </button>
            </div>
            <details v-if="item.details?.length || item.logTail?.length" class="mini-details">
              <summary>查看详情 / 日志</summary>
              <ul v-if="item.details?.length">
                <li v-for="detail in item.details" :key="detail">{{ detail }}</li>
              </ul>
              <pre v-if="item.logTail?.length" class="mini-log"><code>{{ item.logTail.join('\n') }}</code></pre>
            </details>
          </article>
        </div>
      </section>

      <section v-if="activeWorkspaceTab === 'logs'" class="workspace-section">
        <div class="section-heading">
          <h3>日志与 AI 调试上下文</h3>
          <p>日志、当前节点、当前文件、验收失败项和 Code Trace Graph 会一起作为 AI 开发修复上下文。</p>
        </div>

        <div class="runtime-toolbar">
          <button class="secondary-button" :disabled="runtimeLogsLoading" @click="loadRuntimeLogs">
            {{ runtimeLogsLoading ? '读取中...' : '读取运行日志' }}
          </button>
          <button class="secondary-button" :disabled="codebaseAcceptanceRunning || !codebaseWorkspace?.codebaseExists" @click="handleRunAcceptance">
            运行验收并分析日志
          </button>
          <button class="secondary-button" :disabled="!canRepairCodebase" @click="handleRepair(false)">
            根据日志生成修复建议
          </button>
          <button class="secondary-button" :disabled="!canRepairCodebase" @click="handleRepair(true)">
            AI 修复并重新验收
          </button>
        </div>

        <div v-if="runtimeLogsError" class="error-box">{{ runtimeLogsError }}</div>

        <div v-if="runtimeLogs" class="log-diagnostics-grid">
          <article>
            <span>错误行</span>
            <strong>{{ runtimeLogs.errorLineCount || 0 }}</strong>
          </article>
          <article>
            <span>警告行</span>
            <strong>{{ runtimeLogs.warningLineCount || 0 }}</strong>
          </article>
          <article>
            <span>定位文件</span>
            <strong>{{ runtimeLogIssues.length }}</strong>
          </article>
        </div>

        <div v-if="runtimeLogs?.diagnosticSuggestions?.length" class="diagnostic-suggestions">
          <strong>系统诊断建议</strong>
          <ul>
            <li v-for="suggestion in runtimeLogs.diagnosticSuggestions" :key="suggestion">{{ suggestion }}</li>
          </ul>
        </div>

        <div v-if="runtimeLogIssues.length" class="log-issue-list">
          <article v-for="issue in runtimeLogIssues" :key="`${issue.filePath}-${issue.lineNo || 0}-${issue.excerpt}`">
            <div>
              <strong>{{ compactPath(issue.filePath) }}{{ issue.lineNo ? `:${issue.lineNo}` : '' }}</strong>
              <span>{{ issue.source }}</span>
            </div>
            <p>{{ issue.excerpt }}</p>
            <button class="secondary-button" type="button" @click="openLogIssue(issue)">打开相关文件</button>
          </article>
        </div>

        <pre v-if="runtimeLogs?.lines?.length" class="log-panel"><code>{{ runtimeLogs.lines.join('\n') }}</code></pre>
        <div v-else class="empty-panel">暂无日志。启动系统或运行验收后再查看。</div>
      </section>

      <section v-if="activeWorkspaceTab === 'advanced'" class="workspace-section">
        <div class="section-heading">
          <h3>高级诊断</h3>
          <p>这里保留内部上下文、资产和代码库诊断。普通用户不需要理解这些名字。</p>
        </div>

        <div class="diagnostic-grid">
          <article v-for="asset in readiness.assets" :key="asset.assetKey || asset.artifactType">
            <strong>{{ asset.title || asset.artifactType }}</strong>
            <p>{{ asset.statusText || asset.status || (asset.exists ? '已存在' : '未生成') }}</p>
            <small>{{ asset.artifactType }} / v{{ asset.versionNo || '-' }}</small>
          </article>
        </div>

        <details class="advanced-details">
          <summary>查看 Development Build Context</summary>
          <button class="secondary-button" :disabled="contextLoading" @click="loadDevelopmentContext">
            {{ contextLoading ? '读取中...' : '读取上下文' }}
          </button>
          <div v-if="contextError" class="error-box">{{ contextError }}</div>
          <pre v-if="handoffContext" class="json-panel"><code>{{ JSON.stringify(handoffContext.developmentBuildContext || handoffContext, null, 2) }}</code></pre>
        </details>
      </section>
    </template>
  </main>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, ref, watch } from 'vue'
import type { ModuleTreeNode } from '../types/codemind'
import {
  applyDevelopmentCodebaseRepair,
  fetchDevelopmentCodebaseAcceptanceStatus,
  fetchDevelopmentCodebaseFileContent,
  fetchDevelopmentCodebaseWorkspace,
  fetchDevelopmentHandoffContext,
  fetchDevelopmentRuntimeLogs,
  fetchDevelopmentRuntimeStatus,
  installDevelopmentRuntimeDependencies,
  materializeDevelopmentCodeProject,
  proposeDevelopmentCodebaseRepair,
  runDevelopmentCodebaseAcceptance,
  saveDevelopmentCodebaseFileContent,
  startDevelopmentRuntime,
  stopDevelopmentRuntime,
  restartDevelopmentRuntime,
} from '../api/developmentHandoffApi'
import type {
  DevelopmentCodeMaterializationResponse,
  DevelopmentCodebaseAcceptanceResponse,
  DevelopmentCodebaseRepairResponse,
  DevelopmentCodebaseFileContentResponse,
  DevelopmentCodebaseFileTreeItem,
  DevelopmentCodebaseFileSaveResponse,
  DevelopmentCodebaseNodeFile,
  DevelopmentCodebaseWorkspaceResponse,
  DevelopmentHandoffContextResponse,
  DevelopmentHandoffReadinessResponse,
  DevelopmentRuntimeLogsResponse,
  DevelopmentRuntimeStatusResponse,
} from '../types/developmentHandoff'

const props = defineProps<{
  workspaceId?: string | null
  projectCode?: string | null
  selectedModule: ModuleTreeNode | null
  readiness: DevelopmentHandoffReadinessResponse | null
  loading: boolean
  error: string
}>()

defineEmits<{
  refresh: []
  'open-quality-check': []
  'preview-ui-contract': []
}>()

type WorkspaceTab = 'overview' | 'preview' | 'code' | 'api' | 'data' | 'test' | 'logs' | 'advanced'
type CodeViewMode = 'node' | 'tree'
type DetectedLogIssue = { filePath: string; lineNo?: number | null; source: string; excerpt: string }

const workspaceTabs: { key: WorkspaceTab; label: string }[] = [
  { key: 'overview', label: '总览' },
  { key: 'preview', label: '运行预览' },
  { key: 'code', label: '代码' },
  { key: 'api', label: '接口' },
  { key: 'data', label: '数据' },
  { key: 'test', label: '测试' },
  { key: 'logs', label: '日志' },
  { key: 'advanced', label: '高级' },
]

const activeWorkspaceTab = ref<WorkspaceTab>('overview')
const targetStack = ref('NODEJS_FULLSTACK')

const codebaseWorkspace = ref<DevelopmentCodebaseWorkspaceResponse | null>(null)
const codebaseWorkspaceLoading = ref(false)
const codebaseWorkspaceError = ref('')

const selectedCodeFile = ref<DevelopmentCodebaseFileContentResponse | null>(null)
const selectedFilePath = ref('')
const codeEditorContent = ref('')
const codeEditorOriginalContent = ref('')
const codeEditorDirty = ref(false)
const codeFileLoading = ref(false)
const codeFileError = ref('')
const codeFileSaveError = ref('')
const codeFileSaveMessage = ref('')
const savingCodeFile = ref(false)
const codebaseViewMode = ref<CodeViewMode>('node')

const newFileVisible = ref(false)
const newFileScope = ref('CURRENT_PAGE')
const newFileRole = ref('FRONTEND_COMPONENT')
const newFileName = ref('')

const generatingCode = ref(false)
const materialization = ref<DevelopmentCodeMaterializationResponse | null>(null)
const materializationError = ref('')

const runtimeStatus = ref<DevelopmentRuntimeStatusResponse | null>(null)
const runtimeLogs = ref<DevelopmentRuntimeLogsResponse | null>(null)
const runtimeLoading = ref(false)
const runtimeError = ref('')
const runtimeLogsLoading = ref(false)
const runtimeLogsError = ref('')
const runtimePreviewKey = ref(0)

const codebaseAcceptanceStatus = ref<DevelopmentCodebaseAcceptanceResponse | null>(null)
const codebaseAcceptanceLoading = ref(false)
const codebaseAcceptanceRunning = ref(false)
const codebaseAcceptanceError = ref('')
const codebaseRepairLoading = ref(false)
const codebaseRepairError = ref('')
const codebaseRepairResult = ref<DevelopmentCodebaseRepairResponse | null>(null)

const handoffContext = ref<DevelopmentHandoffContextResponse | null>(null)
const contextLoading = ref(false)
const contextError = ref('')

const canUseDevelopmentApi = computed(() =>
  Boolean(props.workspaceId && props.projectCode && props.selectedModule?.id),
)

const currentNodeType = computed(() =>
  String(props.readiness?.nodeType || props.selectedModule?.moduleType || props.selectedModule?.type || 'MODULE').toUpperCase(),
)

const currentNodeName = computed(() =>
  props.readiness?.moduleName ||
  props.selectedModule?.moduleName ||
  props.selectedModule?.name ||
  props.selectedModule?.title ||
  '当前节点',
)

const nodeKey = computed(() =>
  props.readiness?.moduleKey || props.selectedModule?.moduleKey || props.selectedModule?.modulePath || props.selectedModule?.id || 'node',
)

const publicNodeTypeLabel = computed(() => {
  if (currentNodeType.value === 'PAGE') return '页面'
  if (currentNodeType.value === 'FEATURE') return '功能'
  if (currentNodeType.value === 'MODULE' || currentNodeType.value === 'SUBMODULE' || currentNodeType.value === 'DOMAIN') {
    return isFinalModule.value ? '最终模块' : '模块'
  }
  return '模块'
})

const isFinalModule = computed(() => {
  const extra = props.selectedModule?.extra || {}
  return Boolean(
    extra.isFinalModule ||
    extra.finalModule ||
    extra.developmentScope === 'MODULE' ||
    props.readiness?.gateMode === 'MODULE_AGGREGATE' ||
    props.readiness?.directDevelopmentAllowed,
  )
})

const finalModuleBadge = computed(() => {
  if (currentNodeType.value === 'PAGE') return '真实页面入口'
  if (currentNodeType.value === 'FEATURE') return '业务能力'
  return isFinalModule.value ? '可开发模块' : '组织模块'
})

const currentNodeDescription = computed(() => {
  if (currentNodeType.value === 'PAGE') return '页面节点会打开真实运行页面，并关联前端页面、接口、后端逻辑和测试。'
  if (currentNodeType.value === 'FEATURE') return '功能节点不单独变成一个目录；它通过 Code Trace Graph 关联页面、接口、服务和测试。'
  if (isFinalModule.value) return '最终模块是 Development Agent 的主要模块级开发边界。'
  return '普通模块负责组织和聚合，不强迫用户理解内部工程资产。'
})

const nodeResponsibilityTitle = computed(() => {
  if (currentNodeType.value === 'PAGE') return '真实页面入口'
  if (currentNodeType.value === 'FEATURE') return '业务能力链路'
  if (isFinalModule.value) return '可开发模块'
  return '模块分组'
})

const nodeResponsibilityDescription = computed(() => {
  if (currentNodeType.value === 'PAGE') return '适合运行页面、改页面代码、调接口、修页面验收。'
  if (currentNodeType.value === 'FEATURE') return '适合查看该功能影响哪些页面、接口、服务和测试。'
  if (isFinalModule.value) return '适合生成当前业务模块的前端、后端、数据和测试。'
  return '适合查看整体进度和批量调度下级最终模块。'
})

const canGenerateNodejsCode = computed(() =>
  canUseDevelopmentApi.value && Boolean(props.readiness?.directDevelopmentAllowed || codebaseWorkspace.value?.selectedNodeCanGenerate),
)

const mainGenerateButtonText = computed(() => {
  if (codebaseWorkspace.value?.codebaseExists) return '更新 Node.js 系统代码'
  return '生成 Node.js 系统代码'
})

const nextActionTitle = computed(() => {
  if (!props.readiness?.directDevelopmentAllowed && currentNodeType.value === 'FEATURE') return '当前功能随页面或模块一起开发'
  if (!codebaseWorkspace.value?.codebaseExists) return '先生成真实 Node.js 项目代码'
  if (runtimeStatus.value?.runtimeStatus !== 'RUNNING') return '代码已生成，下一步启动真实系统'
  if (codebaseAcceptanceStatus.value?.acceptanceStatus !== 'PASS') return '系统已运行，下一步运行验收'
  return '当前节点已可交付复核'
})

const nextActionDescription = computed(() => {
  if (!props.readiness?.directDevelopmentAllowed && currentNodeType.value === 'FEATURE') {
    return '功能节点不会单独生成孤立项目；切到父页面或最终模块生成代码时会自动带上这个功能链路。'
  }
  if (!codebaseWorkspace.value?.codebaseExists) {
    return '系统会读取 Development Build Context 和已发布 UI_CONTRACT，生成前端、后端、数据和测试。'
  }
  if (runtimeStatus.value?.runtimeStatus !== 'RUNNING') {
    return '启动后这里会展示真实页面，不再是原型 mock。'
  }
  if (codebaseAcceptanceStatus.value?.acceptanceStatus !== 'PASS') {
    return '验收失败时可直接让 AI 读取当前节点、相关文件和日志进行修复。'
  }
  return '可以继续人工改代码、导出代码或切换到下一个业务节点。'
})

const nodeToneClass = computed(() => {
  if (props.readiness?.ready) return 'ready'
  if (props.readiness?.blockers?.length) return 'blocked'
  return 'warning'
})

const runtimeToneClass = computed(() => {
  const status = String(runtimeStatus.value?.runtimeStatus || '').toUpperCase()
  if (status === 'RUNNING') return 'ready'
  if (status.includes('FAILED') || status.includes('EXITED')) return 'blocked'
  return 'warning'
})

const acceptanceToneClass = computed(() => {
  const status = String(codebaseAcceptanceStatus.value?.acceptanceStatus || '').toUpperCase()
  if (status === 'PASS') return 'ready'
  if (status === 'FAILED') return 'blocked'
  return 'warning'
})

const repairToneClass = computed(() => {
  const status = String(codebaseRepairResult.value?.repairStatus || '').toUpperCase()
  if (status === 'APPLIED') return 'ready'
  if (status.includes('BLOCK') || status.includes('CONFLICT')) return 'blocked'
  return 'warning'
})

const failedAcceptanceItems = computed(() =>
  (codebaseAcceptanceStatus.value?.items || []).filter((item) =>
    item && (!item.passed || /FAILED|TIMEOUT|NOT_READY/i.test(String(item.status || ''))),
  ),
)

const runtimeLogIssues = computed<DetectedLogIssue[]>(() => {
  const lines = [
    ...(runtimeLogs.value?.errorLines || []),
    ...(runtimeLogs.value?.warningLines || []),
    ...(runtimeLogs.value?.lines || []).slice(-120),
  ]
  return collectDetectedIssues(lines, '运行日志')
})

const repairResultFiles = computed(() =>
  (codebaseRepairResult.value?.repairCodeProject?.files || [])
    .filter((file) => Boolean(file?.filePath))
    .map((file) => ({
      filePath: normalizeIssueFilePath(file.filePath),
      fileType: file.fileType || 'CODE',
      action: file.action || 'UPDATE',
    }))
    .slice(0, 12),
)

const runtimePreviewUrl = computed(() =>
  runtimeStatus.value?.previewUrl || runtimeStatus.value?.frontendUrl || '',
)

const nodeFileCards = computed(() => codebaseWorkspace.value?.nodeFiles || [])

const backendTraceFiles = computed(() =>
  nodeFileCards.value.filter((file) =>
    /BACKEND|ROUTE|SERVICE|REPOSITORY/i.test(`${file.fileType} ${file.filePath}`),
  ),
)

const dataTraceFiles = computed(() =>
  nodeFileCards.value.filter((file) =>
    /DATABASE|DATA|REPOSITORY|STORE|SEED|db\/|database\//i.test(`${file.fileType} ${file.filePath}`),
  ),
)

const traceSummaryTitle = computed(() => {
  const graph = codebaseWorkspace.value?.codeTraceGraph
  if (graph?.fileCount) return `${graph.fileCount} 个文件 / ${graph.edgeCount || 0} 条映射`
  if (nodeFileCards.value.length) return `${nodeFileCards.value.length} 个相关文件`
  return '暂无代码映射'
})

const traceSummaryText = computed(() => {
  const graph = codebaseWorkspace.value?.codeTraceGraph
  if (graph?.summary) return graph.summary
  if (nodeFileCards.value.length) return '当前节点已经通过代码映射清单关联到相关文件。'
  return '生成代码或手动新增文件后，系统会自动记录模块树节点与代码文件的关系。'
})

const selectedFileImpactText = computed(() => {
  if (!selectedFilePath.value) return '未选择文件。'

  const graph = codebaseWorkspace.value?.codeTraceGraph
  const graphFile = graph?.files?.find((file) => file.filePath === selectedFilePath.value)
  if (graphFile?.affectedNodes?.length) {
    return `影响 ${graphFile.affectedNodes.length} 个业务节点：${graphFile.affectedNodes.join('、')}`
  }

  const nodeFile = nodeFileCards.value.find((file) => file.filePath === selectedFilePath.value)
  if (nodeFile) {
    return `${relationshipLabel(nodeFile.relationship)}当前节点：${nodeFile.nodeName || currentNodeName.value}`
  }

  return '项目共享或暂未建立节点映射的文件。保存后可通过当前节点建立映射。'
})

const canRepairCodebase = computed(() =>
  canUseDevelopmentApi.value &&
  Boolean(codebaseWorkspace.value?.codebaseExists) &&
  !codebaseRepairLoading.value &&
  (
    failedAcceptanceItems.value.length > 0 ||
    runtimeLogIssues.value.length > 0 ||
    Boolean(selectedFilePath.value)
  ),
)

const suggestedNewFilePath = computed(() => {
  const root = codebaseWorkspace.value?.codebaseRoot || `generated/nodejs/${safeSegment(String(props.projectCode || 'project'))}/app`
  const segment = safeSegment(String(nodeKey.value || currentNodeName.value || 'module'))
  const rawName = String(newFileName.value || '').trim()
  const fileName = normalizeSuggestedFileName(rawName, newFileRole.value, segment)

  let relative = `frontend/src/modules/${segment}/components/${fileName}`
  if (newFileRole.value === 'FRONTEND_API') relative = `frontend/src/modules/${segment}/api/${stripExt(fileName)}Api.js`
  if (newFileRole.value === 'BACKEND_ROUTE') relative = `backend/src/modules/${segment}/${stripExt(fileName)}.routes.js`
  if (newFileRole.value === 'BACKEND_SERVICE') relative = `backend/src/modules/${segment}/${stripExt(fileName)}.service.js`
  if (newFileRole.value === 'BACKEND_REPOSITORY') relative = `backend/src/modules/${segment}/${stripExt(fileName)}.repository.js`
  if (newFileRole.value === 'VALIDATION') relative = `backend/src/modules/${segment}/${stripExt(fileName)}.validation.js`
  if (newFileRole.value === 'UTILITY') relative = newFileScope.value === 'SHARED' ? `frontend/src/shared/utils/${fileName}` : `frontend/src/modules/${segment}/utils/${fileName}`
  if (newFileRole.value === 'TEST') relative = `tests/${stripExt(fileName)}.smoke.spec.js`
  if (newFileRole.value === 'STYLE') relative = `frontend/src/modules/${segment}/styles/${ensureExt(fileName, '.css')}`

  return `${root.replace(/\/+$/, '')}/${relative.replace(/^\/+/, '')}`
})

const canSaveSuggestedFile = computed(() =>
  canUseDevelopmentApi.value &&
  Boolean(suggestedNewFilePath.value && codebaseWorkspace.value?.codebaseRoot),
)

watch(
  () => props.selectedModule?.id,
  () => {
    resetNodeState()
    if (canUseDevelopmentApi.value) {
      loadCodebaseWorkspace()
      loadRuntimeStatus()
      loadAcceptanceStatus()
    }
  },
  { immediate: true },
)

watch(
  () => codebaseWorkspace.value?.defaultFilePath,
  (filePath) => {
    if (filePath && !selectedFilePath.value && !codeEditorDirty.value) {
      openCodeFile(filePath)
    }
  },
)

async function loadCodebaseWorkspace() {
  if (!canUseDevelopmentApi.value || !props.workspaceId || !props.projectCode || !props.selectedModule?.id) return

  codebaseWorkspaceLoading.value = true
  codebaseWorkspaceError.value = ''
  try {
    codebaseWorkspace.value = await fetchDevelopmentCodebaseWorkspace(
      props.workspaceId,
      props.projectCode,
      props.selectedModule.id,
      targetStack.value,
    )
  } catch (error) {
    codebaseWorkspaceError.value = resolveErrorMessage(error, '读取代码工作区失败。')
  } finally {
    codebaseWorkspaceLoading.value = false
  }
}

async function openCodeFile(filePath: string) {
  if (!canUseDevelopmentApi.value || !props.workspaceId || !props.projectCode || !props.selectedModule?.id || !filePath) return

  codeFileLoading.value = true
  codeFileError.value = ''
  codeFileSaveError.value = ''
  codeFileSaveMessage.value = ''
  selectedFilePath.value = filePath

  try {
    const file = await fetchDevelopmentCodebaseFileContent(
      props.workspaceId,
      props.projectCode,
      props.selectedModule.id,
      filePath,
      targetStack.value,
    )
    selectedCodeFile.value = file
    codeEditorContent.value = file.content || ''
    codeEditorOriginalContent.value = file.content || ''
    codeEditorDirty.value = false
  } catch (error) {
    codeFileError.value = resolveErrorMessage(error, '读取代码文件失败。')
  } finally {
    codeFileLoading.value = false
  }
}

function openCodeFileAndSwitch(filePath: string) {
  activeWorkspaceTab.value = 'code'
  codebaseViewMode.value = 'node'
  openCodeFile(filePath)
}

async function handleSaveCurrentFile() {
  if (!selectedFilePath.value) return
  await saveCodeFile(selectedFilePath.value, codeEditorContent.value, relationshipForScope(newFileScope.value), '用户在开发工作区手动编辑代码。')
}

async function handleCreateSuggestedFile() {
  const path = suggestedNewFilePath.value
  const content = starterContentForRole(newFileRole.value)
  await saveCodeFile(path, content, relationshipForScope(newFileScope.value), '用户通过开发工作区新增代码文件。')
  newFileVisible.value = false
  await openCodeFile(path)
}

async function saveCodeFile(filePath: string, content: string, relationship: string, remarks: string) {
  if (!canUseDevelopmentApi.value || !props.workspaceId || !props.projectCode || !props.selectedModule?.id) return

  savingCodeFile.value = true
  codeFileSaveError.value = ''
  codeFileSaveMessage.value = ''
  try {
    const response: DevelopmentCodebaseFileSaveResponse = await saveDevelopmentCodebaseFileContent(
      props.workspaceId,
      props.projectCode,
      props.selectedModule.id,
      {
        targetStack: targetStack.value,
        filePath,
        content,
        relationship,
        intentScope: newFileScope.value,
        fileRole: newFileRole.value,
        remarks,
      },
    )
    selectedFilePath.value = response.filePath
    codeEditorOriginalContent.value = content
    codeEditorDirty.value = false
    codeFileSaveMessage.value = response.summary || '代码已保存，并已更新当前节点的代码映射。'
    await loadCodebaseWorkspace()
  } catch (error) {
    codeFileSaveError.value = resolveErrorMessage(error, '保存代码文件失败。')
  } finally {
    savingCodeFile.value = false
  }
}

async function handleGenerateNodejsCode(allowOverwrite: boolean) {
  if (!canUseDevelopmentApi.value || !props.workspaceId || !props.projectCode || !props.selectedModule?.id) return

  generatingCode.value = true
  materializationError.value = ''
  try {
    materialization.value = await materializeDevelopmentCodeProject(
      props.workspaceId,
      props.projectCode,
      props.selectedModule.id,
      {
        targetStack: targetStack.value,
        allowOverwrite,
        remarks: 'A9.10.8 Development Workspace：用户点击生成或更新 Node.js 系统代码。',
      },
    )
    await loadCodebaseWorkspace()
    await loadRuntimeStatus()
    await loadAcceptanceStatus()
  } catch (error) {
    materializationError.value = resolveErrorMessage(error, '生成或写入 Node.js 代码失败。')
  } finally {
    generatingCode.value = false
  }
}

async function loadRuntimeStatus() {
  if (!canUseDevelopmentApi.value || !props.workspaceId || !props.projectCode || !props.selectedModule?.id) return

  runtimeLoading.value = true
  runtimeError.value = ''
  try {
    runtimeStatus.value = await fetchDevelopmentRuntimeStatus(
      props.workspaceId,
      props.projectCode,
      props.selectedModule.id,
      targetStack.value,
    )
  } catch (error) {
    runtimeError.value = resolveErrorMessage(error, '读取运行状态失败。')
  } finally {
    runtimeLoading.value = false
  }
}

async function handleInstallRuntime() {
  await runtimeAction(() => installDevelopmentRuntimeDependencies(props.workspaceId!, props.projectCode!, props.selectedModule!.id!, { targetStack: targetStack.value }))
}

async function handleStartRuntime() {
  await runtimeAction(() => startDevelopmentRuntime(props.workspaceId!, props.projectCode!, props.selectedModule!.id!, { targetStack: targetStack.value }))
}

async function handleStopRuntime() {
  await runtimeAction(() => stopDevelopmentRuntime(props.workspaceId!, props.projectCode!, props.selectedModule!.id!, { targetStack: targetStack.value }))
}

async function handleRestartRuntime() {
  await runtimeAction(() => restartDevelopmentRuntime(props.workspaceId!, props.projectCode!, props.selectedModule!.id!, { targetStack: targetStack.value }))
}

async function runtimeAction(action: () => Promise<DevelopmentRuntimeStatusResponse>) {
  if (!canUseDevelopmentApi.value) return
  runtimeLoading.value = true
  runtimeError.value = ''
  try {
    runtimeStatus.value = await action()
    runtimePreviewKey.value += 1
    await loadRuntimeLogs()
  } catch (error) {
    runtimeError.value = resolveErrorMessage(error, '执行运行操作失败。')
  } finally {
    runtimeLoading.value = false
  }
}

function openRuntimePreview() {
  if (runtimePreviewUrl.value) {
    window.open(runtimePreviewUrl.value, '_blank', 'noopener,noreferrer')
  }
}

async function loadRuntimeLogs() {
  if (!canUseDevelopmentApi.value || !props.workspaceId || !props.projectCode || !props.selectedModule?.id) return
  runtimeLogsLoading.value = true
  runtimeLogsError.value = ''
  try {
    runtimeLogs.value = await fetchDevelopmentRuntimeLogs(
      props.workspaceId,
      props.projectCode,
      props.selectedModule.id,
      targetStack.value,
    )
  } catch (error) {
    runtimeLogsError.value = resolveErrorMessage(error, '读取运行日志失败。')
  } finally {
    runtimeLogsLoading.value = false
  }
}

async function loadAcceptanceStatus() {
  if (!canUseDevelopmentApi.value || !props.workspaceId || !props.projectCode || !props.selectedModule?.id) return
  codebaseAcceptanceLoading.value = true
  codebaseAcceptanceError.value = ''
  try {
    codebaseAcceptanceStatus.value = await fetchDevelopmentCodebaseAcceptanceStatus(
      props.workspaceId,
      props.projectCode,
      props.selectedModule.id,
      targetStack.value,
    )
  } catch (error) {
    codebaseAcceptanceError.value = resolveErrorMessage(error, '读取验收状态失败。')
  } finally {
    codebaseAcceptanceLoading.value = false
  }
}

async function handleRunAcceptance() {
  if (!canUseDevelopmentApi.value || !props.workspaceId || !props.projectCode || !props.selectedModule?.id) return
  codebaseAcceptanceRunning.value = true
  codebaseAcceptanceError.value = ''
  try {
    codebaseAcceptanceStatus.value = await runDevelopmentCodebaseAcceptance(
      props.workspaceId,
      props.projectCode,
      props.selectedModule.id,
      { targetStack: targetStack.value },
    )
    await loadRuntimeStatus()
    await loadRuntimeLogs()
    if (failedAcceptanceItems.value.length > 0 || runtimeLogIssues.value.length > 0) {
      activeWorkspaceTab.value = 'logs'
    }
  } catch (error) {
    codebaseAcceptanceError.value = resolveErrorMessage(error, '运行验收失败。')
  } finally {
    codebaseAcceptanceRunning.value = false
  }
}

async function handleRepair(apply: boolean) {
  if (!canUseDevelopmentApi.value || !props.workspaceId || !props.projectCode || !props.selectedModule?.id) return
  codebaseRepairLoading.value = true
  codebaseRepairError.value = ''
  codebaseRepairResult.value = null

  try {
    const payload = {
      targetStack: targetStack.value,
      allowOverwrite: apply,
      acceptance: codebaseAcceptanceStatus.value || undefined,
      extraLogLines: [
        ...(runtimeLogs.value?.lines?.slice(-120) || []),
        ...failedAcceptanceItems.value.flatMap((item) => item.logTail || []).slice(-120),
      ],
      context: {
        selectedFilePath: selectedFilePath.value || null,
        selectedFileLanguage: selectedCodeFile.value?.language || null,
        selectedFileImpact: selectedFileImpactText.value,
        nodeName: currentNodeName.value,
        nodeType: currentNodeType.value,
        nodeKey: nodeKey.value,
        nodeFilePaths: nodeFileCards.value.map((file) => file.filePath).filter(Boolean).slice(0, 40),
        backendFilePaths: backendTraceFiles.value.map((file) => file.filePath).filter(Boolean).slice(0, 20),
        dataFilePaths: dataTraceFiles.value.map((file) => file.filePath).filter(Boolean).slice(0, 20),
        logDetectedFilePaths: runtimeLogIssues.value.map((issue) => issue.filePath).filter(Boolean).slice(0, 20),
        failedAcceptanceItems: failedAcceptanceItems.value.map((item) => ({
          id: item.id,
          title: item.title,
          status: item.status,
          command: item.command,
          summary: item.summary,
          files: acceptanceItemFiles(item).map((issue) => issue.filePath),
        })),
      },
      remarks: apply ? '用户在开发工作区请求 AI 修复、写入并重新验收。' : '用户在开发工作区请求 AI 生成可审查修复建议。',
    }

    if (apply) {
      codebaseRepairResult.value = await applyDevelopmentCodebaseRepair(props.workspaceId, props.projectCode, props.selectedModule.id, payload)
      await loadCodebaseWorkspace()
      await loadRuntimeLogs()
      codebaseAcceptanceStatus.value = await runDevelopmentCodebaseAcceptance(
        props.workspaceId,
        props.projectCode,
        props.selectedModule.id,
        { targetStack: targetStack.value },
      )
      await loadRuntimeStatus()
      activeWorkspaceTab.value = codebaseAcceptanceStatus.value?.acceptanceStatus === 'PASS' ? 'test' : 'logs'
      const firstFile = repairResultFiles.value[0]?.filePath
      if (firstFile) {
        await openCodeFile(firstFile)
      }
      return
    }

    codebaseRepairResult.value = await proposeDevelopmentCodebaseRepair(props.workspaceId, props.projectCode, props.selectedModule.id, payload)
    activeWorkspaceTab.value = 'test'
  } catch (error) {
    codebaseRepairError.value = resolveErrorMessage(error, apply ? 'AI 修复写入失败。' : '生成 AI 修复建议失败。')
  } finally {
    codebaseRepairLoading.value = false
  }
}

async function loadDevelopmentContext() {
  if (!canUseDevelopmentApi.value || !props.workspaceId || !props.projectCode || !props.selectedModule?.id) return
  contextLoading.value = true
  contextError.value = ''
  try {
    handoffContext.value = await fetchDevelopmentHandoffContext(
      props.workspaceId,
      props.projectCode,
      props.selectedModule.id,
    )
  } catch (error) {
    contextError.value = resolveErrorMessage(error, '读取开发上下文失败。')
  } finally {
    contextLoading.value = false
  }
}

function resetNodeState() {
  codebaseWorkspace.value = null
  selectedCodeFile.value = null
  selectedFilePath.value = ''
  codeEditorContent.value = ''
  codeEditorOriginalContent.value = ''
  codeEditorDirty.value = false
  codeFileError.value = ''
  codeFileSaveError.value = ''
  codeFileSaveMessage.value = ''
  runtimeStatus.value = null
  runtimeLogs.value = null
  codebaseAcceptanceStatus.value = null
  codebaseRepairResult.value = null
  materialization.value = null
  materializationError.value = ''
}

function compactPath(path: string) {
  if (!path) return ''
  const normalized = path.replace(/\\/g, '/')
  const parts = normalized.split('/')
  if (parts.length <= 4) return normalized
  return `${parts.slice(0, 2).join('/')}/…/${parts.slice(-2).join('/')}`
}

function relationshipLabel(relationship?: string | null) {
  const value = String(relationship || '').toUpperCase()
  if (value === 'OWNED') return '专属'
  if (value === 'SHARED') return '共享'
  return '相关'
}

function relationshipForScope(scope: string) {
  if (scope === 'SHARED' || scope === 'PROJECT') return 'SHARED'
  if (scope === 'CURRENT_PAGE' || scope === 'CURRENT_MODULE') return 'OWNED'
  return 'CONTRIBUTED'
}

function safeSegment(value: string) {
  const normalized = String(value || 'node')
    .replace(/([a-z])([A-Z])/g, '$1-$2')
    .replace(/[^A-Za-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .toLowerCase()
  return normalized || 'node'
}

function stripExt(fileName: string) {
  return fileName.replace(/\.[A-Za-z0-9]+$/, '')
}

function ensureExt(fileName: string, ext: string) {
  return fileName.toLowerCase().endsWith(ext) ? fileName : `${stripExt(fileName)}${ext}`
}

function normalizeSuggestedFileName(raw: string, role: string, segment: string) {
  const base = raw || (
    role === 'FRONTEND_COMPONENT' ? `${toPascalCase(segment)}Panel.vue` :
    role === 'TEST' ? `${segment}.smoke.spec.js` :
    `${segment}.js`
  )

  if (role === 'FRONTEND_COMPONENT') return ensureExt(base, '.vue')
  if (role === 'STYLE') return ensureExt(base, '.css')
  if (role === 'TEST') return base.endsWith('.js') ? base : `${stripExt(base)}.smoke.spec.js`
  return ensureExt(base, '.js')
}

function toPascalCase(value: string) {
  return value
    .split(/[-_\s]+/)
    .filter(Boolean)
    .map((item) => item.charAt(0).toUpperCase() + item.slice(1))
    .join('') || 'Generated'
}

function acceptanceItemFiles(item: { logTail?: string[] | null; details?: string[] | null; command?: string | null; summary?: string | null }) {
  return collectDetectedIssues([
    ...(item.details || []),
    ...(item.logTail || []),
    item.command || '',
    item.summary || '',
  ], '验收日志').slice(0, 6)
}

function collectDetectedIssues(lines: string[], source: string) {
  const seen = new Set<string>()
  const issues: DetectedLogIssue[] = []

  for (const line of lines || []) {
    const excerpt = String(line || '').trim()
    if (!excerpt) continue

    const patterns = [
      /((?:[A-Za-z]:)?[\\/][^\s'"<>]+?\.(?:vue|js|mjs|cjs|json|css|html|md))(?:[:(](\d+))?/gi,
      /((?:generated\/)?(?:nodejs\/[^:\s'"<>]+\/app\/)?(?:frontend|backend|database|tests)\/[^:\s'"<>]+?\.(?:vue|js|mjs|cjs|json|css|html|md))(?:[:(](\d+))?/gi,
      /((?:package\.json|README\.md|codemind\.generated\.json))(?:[:(](\d+))?/gi,
    ]

    for (const pattern of patterns) {
      let match: RegExpExecArray | null
      while ((match = pattern.exec(excerpt)) !== null) {
        const filePath = normalizeIssueFilePath(match[1] || '')
        if (!filePath) continue
        const lineNo = match[2] ? Number(match[2]) : null
        const key = `${filePath}:${lineNo || 0}`
        if (seen.has(key)) continue
        seen.add(key)
        issues.push({
          filePath,
          lineNo,
          source,
          excerpt: excerpt.length > 280 ? `${excerpt.slice(0, 280)}...` : excerpt,
        })
      }
    }
  }

  return issues.slice(0, 18)
}

function normalizeIssueFilePath(raw: string) {
  const root = String(codebaseWorkspace.value?.codebaseRoot || '')
    .replace(/\\/g, '/')
    .replace(/\/+$/, '')
  let clean = String(raw || '')
    .replace(/\\/g, '/')
    .replace(/^file:\/+/i, '')
    .replace(/^[`'"(\[]+/, '')
    .replace(/[`'"),;\]]+$/, '')
    .trim()

  const generatedIndex = clean.indexOf('generated/')
  if (generatedIndex >= 0) {
    clean = clean.slice(generatedIndex)
  }

  const rootIndex = root ? clean.indexOf(root) : -1
  if (rootIndex >= 0) {
    clean = clean.slice(rootIndex)
  }

  clean = clean.replace(/:(\d+)(:\d+)?$/, '')

  if (root && /^(frontend|backend|database|tests)\//i.test(clean)) {
    clean = `${root}/${clean}`
  }

  if (root && /^(package\.json|README\.md|codemind\.generated\.json)$/i.test(clean)) {
    clean = `${root}/${clean}`
  }

  return clean
}

function openLogIssue(issue: DetectedLogIssue) {
  if (!issue?.filePath) return
  openCodeFileAndSwitch(issue.filePath)
}

function starterContentForRole(role: string) {
  if (role === 'FRONTEND_COMPONENT') {
    return `<template>\n  <section class="generated-panel">\n    <h3>${currentNodeName.value}</h3>\n    <p>TODO: 在这里实现当前业务节点的界面片段。</p>\n  </section>\n</template>\n\n<script setup>\n${'</'}script>\n`
  }
  if (role.startsWith('BACKEND')) {
    return `// ${currentNodeName.value}\n// TODO: 在这里实现当前业务节点的后端逻辑。\n\nexport function handle${toPascalCase(String(nodeKey.value))}(req, res) {\n  res.json({ ok: true })\n}\n`
  }
  if (role === 'TEST') {
    return `import assert from 'node:assert/strict'\n\ndescribe('${currentNodeName.value}', () => {\n  it('should keep generated business flow testable', () => {\n    assert.equal(true, true)\n  })\n})\n`
  }
  if (role === 'STYLE') {
    return `.generated-panel {\n  display: grid;\n  gap: 12px;\n}\n`
  }
  return `// ${currentNodeName.value}\n// TODO: 在这里补充当前业务节点的代码。\n`
}

function resolveErrorMessage(error: unknown, fallback: string) {
  if (error instanceof Error && error.message) return error.message
  if (typeof error === 'string' && error) return error
  return fallback
}

const CodeTree = defineComponent({
  name: 'CodeTree',
  props: {
    item: {
      type: Object as () => DevelopmentCodebaseFileTreeItem,
      required: true,
    },
    activePath: {
      type: String,
      default: '',
    },
  },
  emits: ['open'],
  setup(componentProps, { emit }) {
    const isFile = () => String(componentProps.item.itemType).toUpperCase() === 'FILE'

    return () => h(
      'div',
      { class: ['code-tree-node', isFile() ? 'file' : 'directory'] },
      [
        h(
          'button',
          {
            type: 'button',
            class: { active: componentProps.activePath === componentProps.item.path },
            onClick: () => isFile() && emit('open', componentProps.item.path),
          },
          [
            h('strong', componentProps.item.name),
            isFile() ? h('span', componentProps.item.language || 'file') : h('span', `${componentProps.item.children?.length || 0} 项`),
          ],
        ),
        !isFile() && componentProps.item.children?.length
          ? h('div', { class: 'code-tree-children' }, componentProps.item.children.map((child) =>
              h(CodeTree, {
                key: child.path,
                item: child,
                activePath: componentProps.activePath,
                onOpen: (path: string) => emit('open', path),
              }),
            ))
          : null,
      ],
    )
  },
})
</script>

<style scoped>
.development-workspace-panel {
  display: grid;
  gap: 18px;
  color: #172033;
}

.dev-hero-card,
.dev-node-card,
.dev-action-card,
.workspace-section,
.empty-panel {
  background: #fff;
  border: 1px solid #dce5f5;
  border-radius: 22px;
  box-shadow: 0 18px 45px rgba(20, 41, 88, 0.07);
}

.dev-hero-card,
.dev-node-card,
.dev-action-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 22px;
}

.dev-hero-card h2,
.dev-node-card h3,
.section-heading h3 {
  margin: 0;
  color: #101828;
}

.dev-hero-card span,
.dev-node-card span,
.dev-action-card p,
.section-heading p,
.empty-panel p {
  color: #667085;
  line-height: 1.7;
}

.eyebrow {
  margin: 0 0 6px;
  font-size: 12px;
  font-weight: 800;
  color: #315fdc;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.hero-actions,
.dev-action-buttons,
.runtime-toolbar,
.editor-actions,
.code-heading-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.primary-button,
.secondary-button {
  border: 0;
  border-radius: 12px;
  padding: 10px 14px;
  font-weight: 800;
  cursor: pointer;
}

.primary-button {
  background: #2563eb;
  color: #fff;
}

.secondary-button {
  background: #eef4ff;
  color: #1d4ed8;
}

.primary-button:disabled,
.secondary-button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.empty-panel,
.error-box,
.success-box,
.empty-mini {
  padding: 16px;
}

.error-box {
  border: 1px solid #fecaca;
  background: #fff1f2;
  color: #b91c1c;
  border-radius: 14px;
}

.success-box {
  border: 1px solid #bbf7d0;
  background: #f0fdf4;
  color: #166534;
  border-radius: 14px;
}

.empty-mini {
  border: 1px dashed #cbd5e1;
  border-radius: 12px;
  background: #f8fafc;
  color: #64748b;
}

.dev-node-card.ready {
  border-color: #bbf7d0;
}

.dev-node-card.warning {
  border-color: #fde68a;
}

.dev-node-card.blocked {
  border-color: #fecaca;
}

.node-badges {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.node-badges b,
.node-badges span {
  display: inline-flex;
  border-radius: 999px;
  padding: 7px 10px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 800;
}

.dev-status-grid,
.overview-grid,
.diagnostic-grid,
.trace-file-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
  gap: 12px;
}

.dev-status-grid article,
.overview-grid article,
.diagnostic-grid article,
.trace-file-grid button {
  border: 1px solid #e2e8f0;
  background: #fff;
  border-radius: 16px;
  padding: 16px;
  text-align: left;
}

.dev-status-grid strong,
.overview-grid strong,
.diagnostic-grid strong,
.trace-file-grid strong {
  display: block;
  color: #0f172a;
  font-size: 18px;
}

.dev-status-grid span,
.overview-grid span,
.diagnostic-grid small,
.trace-file-grid span {
  color: #64748b;
}

.dev-tabs {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding: 6px;
  background: #edf3ff;
  border-radius: 18px;
}

.dev-tabs button {
  border: 0;
  background: transparent;
  border-radius: 12px;
  padding: 10px 14px;
  color: #475569;
  font-weight: 800;
  cursor: pointer;
  white-space: nowrap;
}

.dev-tabs button.active {
  background: #fff;
  color: #1d4ed8;
  box-shadow: 0 10px 25px rgba(37, 99, 235, 0.14);
}

.workspace-section {
  padding: 20px;
  display: grid;
  gap: 16px;
}

.section-heading,
.code-heading {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.advanced-details {
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  padding: 14px;
  background: #f8fafc;
}

.advanced-details summary {
  cursor: pointer;
  font-weight: 800;
  color: #334155;
}

.runtime-summary,
.acceptance-summary,
.file-impact-strip,
.suggested-path {
  border-radius: 16px;
  padding: 14px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  display: grid;
  gap: 4px;
}

.runtime-summary.ready,
.acceptance-summary.ready {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.runtime-summary.warning,
.acceptance-summary.warning {
  border-color: #fde68a;
  background: #fffbeb;
}

.runtime-summary.blocked,
.acceptance-summary.blocked {
  border-color: #fecaca;
  background: #fff1f2;
}

.runtime-frame {
  width: 100%;
  min-height: 720px;
  border: 1px solid #dbe4f0;
  border-radius: 18px;
  background: #fff;
}

.code-layout {
  display: grid;
  grid-template-columns: minmax(260px, 340px) minmax(0, 1fr);
  gap: 16px;
}

.code-sidebar {
  border: 1px solid #e2e8f0;
  border-radius: 18px;
  background: #f8fafc;
  padding: 12px;
  max-height: 780px;
  overflow: auto;
}

.code-view-toggle {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
  margin-bottom: 10px;
}

.code-view-toggle button,
.file-list button,
.code-tree-node > button,
.trace-file-grid button {
  border: 0;
  background: transparent;
  cursor: pointer;
}

.code-view-toggle button {
  border-radius: 10px;
  padding: 9px;
  font-weight: 800;
  color: #64748b;
}

.code-view-toggle button.active {
  background: #fff;
  color: #1d4ed8;
}

.file-list {
  display: grid;
  gap: 8px;
}

.file-list button,
.code-tree-node > button {
  width: 100%;
  display: grid;
  gap: 4px;
  text-align: left;
  border-radius: 12px;
  padding: 10px;
  color: #334155;
}

.file-list button.active,
.code-tree-node > button.active {
  background: #dbeafe;
  color: #1d4ed8;
}

.file-list span,
.code-tree-node span {
  font-size: 12px;
  color: #64748b;
}

.code-tree-children {
  margin-left: 14px;
  border-left: 1px dashed #cbd5e1;
  padding-left: 8px;
}

.code-editor-panel {
  border: 1px solid #e2e8f0;
  border-radius: 18px;
  overflow: hidden;
  background: #fff;
  display: grid;
  grid-template-rows: auto auto 1fr auto;
  min-height: 780px;
}

.code-editor-panel header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border-bottom: 1px solid #e2e8f0;
  background: #f8fafc;
}

.code-editor-panel header span {
  display: block;
  color: #64748b;
  font-size: 12px;
  margin-top: 4px;
}

.code-editor {
  width: 100%;
  min-height: 620px;
  resize: vertical;
  border: 0;
  outline: none;
  padding: 18px;
  font-family: "JetBrains Mono", "Fira Code", Consolas, monospace;
  font-size: 13px;
  line-height: 1.65;
  color: #0f172a;
  background: #fff;
}

.new-file-card {
  display: grid;
  gap: 12px;
  border: 1px solid #bfdbfe;
  background: #eff6ff;
  border-radius: 18px;
  padding: 16px;
}

.new-file-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(160px, 1fr));
  gap: 12px;
}

.new-file-grid label {
  display: grid;
  gap: 6px;
  font-size: 12px;
  color: #475569;
  font-weight: 800;
}

.new-file-grid select,
.new-file-grid input {
  border: 1px solid #cbd5e1;
  border-radius: 10px;
  padding: 9px;
  background: #fff;
}

.acceptance-list {
  display: grid;
  gap: 10px;
}

.acceptance-list article {
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 14px;
  background: #fff;
}

.log-panel,
.json-panel {
  max-height: 680px;
  overflow: auto;
  border-radius: 16px;
  background: #0f172a;
  color: #e2e8f0;
  padding: 16px;
  font-size: 12px;
}

@media (max-width: 1180px) {
  .dev-hero-card,
  .dev-node-card,
  .dev-action-card,
  .section-heading,
  .code-heading,
  .code-editor-panel header {
    flex-direction: column;
    align-items: stretch;
  }

  .code-layout {
    grid-template-columns: 1fr;
  }

  .new-file-grid {
    grid-template-columns: 1fr;
  }
}

/* A9.10.8.1 DEVELOPMENT WORKSPACE COMPACT DENSITY POLISH */
.development-workspace-panel {
  gap: 14px;
  box-sizing: border-box;
  color: #1f2937;
  font-size: 13px;
  line-height: 1.56;
}

.development-workspace-panel * {
  box-sizing: border-box;
}

.dev-hero-card,
.dev-node-card,
.dev-action-card,
.workspace-section,
.empty-panel {
  border-radius: 18px;
  box-shadow: 0 12px 30px rgba(20, 41, 88, 0.055);
}

.dev-hero-card,
.dev-node-card,
.dev-action-card {
  gap: 14px;
  padding: 18px 20px;
}

.dev-hero-card h2 {
  font-size: 22px;
  line-height: 1.25;
}

.dev-node-card h3,
.section-heading h3 {
  font-size: 18px;
  line-height: 1.3;
}

.dev-hero-card span,
.dev-node-card span,
.dev-action-card p,
.section-heading p,
.empty-panel p {
  font-size: 13px;
  line-height: 1.58;
}

.eyebrow {
  margin-bottom: 4px;
  font-size: 11px;
  letter-spacing: 0.07em;
}

.primary-button,
.secondary-button {
  min-height: 34px;
  border-radius: 11px;
  padding: 8px 12px;
  font-size: 13px;
  line-height: 1.2;
}

.node-badges b,
.node-badges span {
  padding: 6px 9px;
  font-size: 11px;
}

.dev-status-grid,
.overview-grid,
.diagnostic-grid,
.trace-file-grid {
  gap: 10px;
}

.dev-status-grid article,
.overview-grid article,
.diagnostic-grid article,
.trace-file-grid button {
  border-radius: 14px;
  padding: 14px;
}

.dev-status-grid strong,
.overview-grid strong,
.diagnostic-grid strong,
.trace-file-grid strong {
  font-size: 16px;
  line-height: 1.3;
}

.dev-tabs {
  gap: 6px;
  padding: 5px;
  border-radius: 15px;
}

.dev-tabs button {
  border-radius: 10px;
  padding: 8px 12px;
  font-size: 13px;
}

.workspace-section {
  gap: 14px;
  padding: 18px;
}

.runtime-frame {
  min-height: 620px;
}

.code-layout {
  grid-template-columns: minmax(230px, 310px) minmax(0, 1fr);
  gap: 14px;
}

.code-sidebar {
  max-height: 640px;
  border-radius: 16px;
}

.code-editor-panel {
  min-height: 640px;
  border-radius: 16px;
}

.code-editor {
  min-height: 480px;
  padding: 14px;
  font-size: 12.5px;
  line-height: 1.58;
}

.log-panel,
.json-panel {
  max-height: 560px;
  font-size: 12px;
  line-height: 1.58;
}

.new-file-card,
.runtime-summary,
.acceptance-summary,
.file-impact-strip,
.suggested-path,
.advanced-details {
  border-radius: 14px;
}

/* A9.10.9 DEVELOPMENT RUNTIME AI REPAIR LOOP */
.repair-summary,
.diagnostic-suggestions {
  border: 1px solid #dbeafe;
  background: #eff6ff;
  border-radius: 14px;
  padding: 14px;
  display: grid;
  gap: 10px;
}

.repair-summary.ready {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.repair-summary.blocked {
  border-color: #fecaca;
  background: #fff1f2;
}

.repair-summary strong,
.diagnostic-suggestions strong {
  color: #0f172a;
}

.repair-summary span,
.diagnostic-suggestions li {
  color: #475569;
}

.repair-meta {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  align-items: center;
  color: #64748b;
  font-size: 12px;
}

.inline-file-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.inline-file-actions button {
  border: 1px solid #bfdbfe;
  background: #fff;
  color: #1d4ed8;
  border-radius: 999px;
  padding: 6px 10px;
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
}

.acceptance-list article header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.acceptance-list article header span {
  color: #64748b;
  font-size: 12px;
}

.acceptance-failed,
.acceptance-timeout,
.acceptance-not_ready {
  border-color: #fecaca !important;
  background: #fff7f7 !important;
}

.acceptance-pass,
.acceptance-ready {
  border-color: #bbf7d0 !important;
}

.mini-details {
  margin-top: 10px;
  border-top: 1px dashed #e2e8f0;
  padding-top: 8px;
}

.mini-details summary {
  cursor: pointer;
  font-weight: 800;
  color: #334155;
}

.mini-log {
  max-height: 240px;
  overflow: auto;
  border-radius: 12px;
  background: #111827;
  color: #e5e7eb;
  padding: 10px;
  font-size: 12px;
  line-height: 1.55;
}

.log-diagnostics-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(120px, 1fr));
  gap: 10px;
}

.log-diagnostics-grid article {
  border: 1px solid #e2e8f0;
  background: #fff;
  border-radius: 14px;
  padding: 12px;
}

.log-diagnostics-grid span {
  display: block;
  color: #64748b;
  font-size: 12px;
}

.log-diagnostics-grid strong {
  display: block;
  margin-top: 4px;
  color: #0f172a;
  font-size: 18px;
}

.log-issue-list {
  display: grid;
  gap: 10px;
}

.log-issue-list article {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  border: 1px solid #fecaca;
  background: #fff7f7;
  border-radius: 14px;
  padding: 12px;
}

.log-issue-list strong {
  display: block;
  color: #991b1b;
}

.log-issue-list span,
.log-issue-list p {
  margin: 0;
  color: #64748b;
  font-size: 12px;
}

.log-issue-list p {
  grid-column: 1 / -1;
  font-family: "JetBrains Mono", "Fira Code", Consolas, monospace;
  color: #334155;
}

@media (max-width: 1180px) {
  .log-diagnostics-grid {
    grid-template-columns: 1fr;
  }

  .log-issue-list article {
    grid-template-columns: 1fr;
  }
}

</style>

