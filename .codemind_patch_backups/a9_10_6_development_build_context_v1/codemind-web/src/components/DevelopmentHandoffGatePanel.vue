<template>
  <main class="development-handoff-panel">
    <section class="handoff-card">
      <header class="handoff-header">
        <div>
          <p class="eyebrow">Development Agent Gate</p>
          <h2>开发 Agent 准备度</h2>
          <span>
            进入开发 Agent 前，系统会检查需求资产、确认后的交互原型、开发交接包和目标技术栈是否可用。
          </span>
        </div>

        <div class="header-actions">
          <button
            class="secondary-button"
            :disabled="!selectedModule || loading"
            @click="$emit('refresh')"
          >
            {{ loading ? '检查中...' : '刷新准备度' }}
          </button>

          <button
            class="secondary-button"
            :disabled="!selectedModule"
            @click="$emit('open-quality-check')"
          >
            查看资产质量
          </button>
        </div>
      </header>

      <div v-if="!selectedModule" class="empty-box">
        请先从左侧模块树选择一个业务域、模块、页面或功能点节点。
      </div>

      <div v-else-if="error" class="error-box">
        {{ error }}
      </div>

      <div v-else-if="loading && !readiness" class="empty-box">
        正在检查开发准备度...
      </div>

      <template v-else-if="readiness">
        <section class="summary-card" :class="readinessClass">
          <div
            v-if="readiness.directDevelopmentAllowed"
            class="score-circle"
          >
            <strong>{{ readiness.score }}</strong>
            <span>/ 100</span>
          </div>

          <div
            v-else
            class="scope-circle"
          >
            <strong>结构</strong>
            <span>非直接开发单元</span>
          </div>

          <div class="summary-content">
            <div class="summary-title-row">
              <div>
                <h3>{{ readiness.readinessText }}</h3>
                <p>{{ readiness.readinessDescription }}</p>
              </div>

              <span class="node-badge">
                {{ readiness.nodeTypeLabel }}
              </span>
            </div>

            <div
              v-if="readiness.directDevelopmentAllowed"
              class="summary-grid"
            >
              <div>
                <strong>{{ readiness.assets.length }}</strong>
                <span>开发输入资产</span>
              </div>
              <div>
                <strong>{{ readiness.blockers.length }}</strong>
                <span>阻断项</span>
              </div>
              <div>
                <strong>{{ readiness.warnings.length }}</strong>
                <span>待复核项</span>
              </div>
              <div>
                <strong>{{ readiness.passes.length }}</strong>
                <span>通过项</span>
              </div>
            </div>

            <div
              v-else
              class="scope-summary-grid"
            >
              <div>
                <strong>{{ readiness.nodeTypeLabel }}</strong>
                <span>当前层级</span>
              </div>
              <div>
                <strong>{{ gateModeLabel }}</strong>
                <span>资产角色</span>
              </div>
              <div>
                <strong>MODULE / PAGE</strong>
                <span>正式开发入口</span>
              </div>
            </div>
          </div>
        </section>

        <section class="node-info-card">
          <div>
            <strong>{{ readiness.moduleName || selectedModule.moduleName || '当前节点' }}</strong>
            <span>{{ readiness.moduleKey || selectedModule.moduleKey || selectedModule.id }}</span>
          </div>

          <p>
            {{ readiness.scopeSummary }}
          </p>

          <div
            v-if="readiness.effectiveModuleName && readiness.nodeType === 'PAGE'"
            class="effective-module-strip"
          >
            <span>所属开发模块</span>
            <strong>{{ readiness.effectiveModuleName }}</strong>
            <small>{{ readiness.effectiveModuleKey || readiness.effectiveModuleId }}</small>
          </div>
        </section>

        <section
          v-if="!readiness.directDevelopmentAllowed"
          class="development-scope-card"
        >
          <strong>当前节点不会单独生成代码</strong>
          <p>{{ readiness.readinessDescription }}</p>
          <span>
            Development Agent 的正式入口只保留 MODULE 与 PAGE；
            DOMAIN / SUBMODULE / FEATURE 继续作为上下文、分组和页面内联需求使用。
          </span>

          <div
            v-if="readiness.nodeType === 'FEATURE'"
            class="feature-contribution-card"
          >
            <div>
              <strong>功能代码贡献</strong>
              <p>
                当前功能点不会单独生成一个孤立文件，而是随父 PAGE 写入真实页面代码。
                Development Plan 会把它映射为父页面组件、动作处理、接口调用、状态流和测试点。
              </p>
            </div>

            <div class="feature-contribution-grid">
              <article>
                <span>归属方式</span>
                <strong>父页面内联贡献</strong>
                <small>避免功能点独立生成后再硬拼页面。</small>
              </article>
              <article>
                <span>代码落点</span>
                <strong>父 PAGE 组件 / Action</strong>
                <small>按钮、表单、错误提示、跳转和状态守卫进入父页面。</small>
              </article>
              <article>
                <span>生成入口</span>
                <strong>切到父 PAGE 或 MODULE</strong>
                <small>生成页面代码或模块代码时自动带上该功能点。</small>
              </article>
            </div>
          </div>
        </section>

        <template v-if="readiness.directDevelopmentAllowed">
        <section class="dev-next-step-card" :class="nextStepTone">
          <header>
            <div>
              <p class="eyebrow">Development Agent Step</p>
              <h3>{{ nextStepTitle }}</h3>
              <span>{{ nextStepDescription }}</span>
            </div>
            <strong>{{ nextStepBadge }}</strong>
          </header>

          <div class="dev-next-step-grid">
            <article
              v-for="item in nextStepMetaItems"
              :key="item.label"
            >
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
              <small>{{ item.hint }}</small>
            </article>
          </div>

          <div class="context-actions dev-next-step-actions">
            <button
              v-if="nextStepKey === 'GENERATE_PLAN'"
              class="primary-button"
              type="button"
              :disabled="!canGeneratePlan || planLoading"
              @click="handleGeneratePlan"
            >
              {{ planLoading ? '生成中...' : generatePlanButtonText }}
            </button>

            <button
              v-if="nextStepKey === 'CONFIRM_PLAN'"
              class="primary-button"
              type="button"
              :disabled="!developmentPlan || confirmingPlan || isCurrentPlanConfirmed"
              @click="handleConfirmPlan"
            >
              {{ confirmingPlan ? '确认中...' : '确认开发实施计划' }}
            </button>

            <button
              v-if="nextStepKey === 'GENERATE_HANDOFF_PACKAGE'"
              class="primary-button"
              type="button"
              :disabled="!canGenerateHandoffPackage || handoffPackageLoading"
              @click="handleGenerateHandoffPackage"
            >
              {{ handoffPackageLoading ? '生成中...' : '生成 Development Handoff Package' }}
            </button>

            <button
              v-if="nextStepKey === 'GENERATE_CODE_PROJECT'"
              class="primary-button"
              type="button"
              :disabled="!canGenerateCodeProject || codeProjectLoading"
              @click="handleGenerateCodeProject"
            >
              {{ codeProjectLoading ? '生成中...' : '由 Development Codegen Agent 生成 Node.js 项目代码提案' }}
            </button>

            <button
              v-if="nextStepKey === 'MATERIALIZE_CODE'"
              class="primary-button"
              type="button"
              :disabled="!canMaterializeCodeProject || codeProjectMaterializationLoading"
              @click="handleMaterializeCodeProject(false)"
            >
              {{ codeProjectMaterializationLoading ? '写入中...' : '生成项目主干并写入代码' }}
            </button>

            <button
              v-if="nextStepKey === 'INSTALL_DEPS'"
              class="primary-button"
              type="button"
              :disabled="!canInstallRuntime"
              @click="handleRuntimeInstall"
            >
              安装依赖
            </button>

            <button
              v-if="nextStepKey === 'START_RUNTIME'"
              class="primary-button"
              type="button"
              :disabled="!canStartRuntime"
              @click="handleRuntimeStart"
            >
              启动项目
            </button>

            <button
              v-if="nextStepKey === 'RUN_ACCEPTANCE'"
              class="primary-button"
              type="button"
              :disabled="!canRunCodebaseAcceptance"
              @click="handleRunCodebaseAcceptance"
            >
              运行验收流水线
            </button>

            <button
              class="secondary-button"
              type="button"
              @click="showAdvancedDiagnostics = !showAdvancedDiagnostics"
            >
              {{ showAdvancedDiagnostics ? '收起完整诊断' : '查看完整诊断' }}
            </button>
          </div>
        </section>

        <section class="agent-model-routing-card" :class="agentModelRoutingCardClass">
          <header class="codebase-export-header">
            <div>
              <strong>Agent 模型路由</strong>
              <p>需求 Agent 和开发 Agent 可使用不同模型。当前开发代码生成路由已预留给 Qwen，便于测试阶段优先消耗免费额度。</p>
            </div>
            <button
              class="secondary-button"
              type="button"
              :disabled="agentModelRoutingLoading"
              @click="() => loadAgentModelRoutingStatus()"
            >
              {{ agentModelRoutingLoading ? '刷新中...' : '刷新模型路由' }}
            </button>
          </header>

          <div v-if="agentModelRoutingError" class="error-box">
            {{ agentModelRoutingError }}
          </div>

          <div class="model-routing-grid">
            <article>
              <span>需求 Agent</span>
              <strong>{{ modelProfileLabel(requirementModelProfile) }}</strong>
              <small>{{ requirementModelProfile?.apiKeyConfigured ? 'Key 已配置' : 'Key 未配置' }}</small>
            </article>
            <article>
              <span>开发代码生成</span>
              <strong>{{ modelProfileLabel(developmentCodegenProfile) }}</strong>
              <small>{{ developmentCodegenProfile?.freeQuotaStopEnabled ? '免费额度用尽即停' : '未启用额度保护' }}</small>
            </article>
            <article>
              <span>开发快速修复</span>
              <strong>{{ modelProfileLabel(developmentFastProfile) }}</strong>
              <small>{{ developmentFastProfile?.apiKeyConfigured ? 'Key 已配置' : 'Key 未配置' }}</small>
            </article>
            <article>
              <span>日志诊断</span>
              <strong>{{ modelProfileLabel(developmentDiagnosisProfile) }}</strong>
              <small>{{ developmentDiagnosisProfile?.protocol || '-' }}</small>
            </article>
          </div>

          <p class="muted-line">
            当前 Java 版 Node.js 生成器仍是确定性模板生成；下一阶段接入 LLM Dev Codegen 时会读取这里的 development.codegen 路由。
          </p>
        </section>

        <section class="codebase-workspace-section">
          <div class="section-heading codebase-heading-row">
            <div>
              <h3>Dev Codebase Workspace</h3>
              <p>没生成代码时显示准备度和缺失项；已有代码时按项目目录或模块树节点查看关联代码。</p>
            </div>

            <button
              class="secondary-button"
              :disabled="!canUseDevelopmentApi || codebaseWorkspaceLoading"
              @click="() => loadCodebaseWorkspace()"
            >
              {{ codebaseWorkspaceLoading ? '刷新中...' : '刷新代码工作区' }}
            </button>
          </div>

          <div v-if="codebaseWorkspaceError" class="error-box">
            {{ codebaseWorkspaceError }}
          </div>

          <div v-if="!codebaseWorkspace && codebaseWorkspaceLoading" class="empty-mini">
            正在读取生成代码工作区...
          </div>

          <template v-if="codebaseWorkspace">
            <div class="codebase-status-card" :class="codebaseStatusClass">
              <div>
                <strong>{{ codebaseWorkspaceTitle }}</strong>
                <p>{{ codebaseWorkspace.summary }}</p>
              </div>
              <span>{{ codebaseWorkspace.codebaseStatus }}</span>
            </div>

            <div class="codebase-summary-grid">
              <div>
                <strong>{{ codebaseWorkspace.codebaseExists ? '已存在' : '未初始化' }}</strong>
                <span>项目主干</span>
              </div>
              <div>
                <strong>{{ codebaseWorkspace.selectedNodeHasCode ? '已有代码' : '暂无代码' }}</strong>
                <span>当前节点</span>
              </div>
              <div>
                <strong>{{ codebaseWorkspace.fileTree.length }}</strong>
                <span>顶层目录</span>
              </div>
              <div>
                <strong>{{ codebaseWorkspace.nodeFiles.length }}</strong>
                <span>节点关联文件</span>
              </div>
            </div>

            <article class="context-json-card">
              <strong>生成项目根目录</strong>
              <p>{{ codebaseWorkspace.codebaseRoot }}</p>
              <p class="muted-line">{{ codebaseWorkspace.absoluteCodebaseRoot }}</p>
            </article>

            <details
              class="advanced-dev-diagnostics"
              :open="advancedDiagnosticsDefaultOpen"
            >
              <summary>
                <div>
                  <strong>完整代码库诊断</strong>
                  <span>
                    代码库生成前，运行、验收、导出、快照和部署属于后置阶段，默认收起为诊断信息。
                  </span>
                </div>
                <b>{{ advancedDiagnosticsDefaultOpen ? '已展开' : '按需展开' }}</b>
              </summary>

              <div class="advanced-dev-diagnostics-body">
            <article class="context-json-card storage-registry-card">
              <strong>代码库存储登记</strong>
              <div class="registry-grid">
                <div>
                  <span>登记状态</span>
                  <b>{{ codebaseWorkspace.codebaseRegistry?.registered ? '已登记' : '未登记' }}</b>
                </div>
                <div>
                  <span>物理目录</span>
                  <b>{{ codebaseWorkspace.codebaseRegistry?.physicalExists ? '可访问' : '未找到' }}</b>
                </div>
                <div>
                  <span>存储根</span>
                  <b>{{ codebaseWorkspace.codebaseRegistry?.storageRootKey || 'WORKSPACE_GENERATED_ROOT' }}</b>
                </div>
                <div>
                  <span>代码库状态</span>
                  <b>{{ codebaseWorkspace.codebaseRegistry?.status || codebaseWorkspace.codebaseStatus }}</b>
                </div>
              </div>
              <p class="muted-line">相对路径：{{ codebaseWorkspace.codebaseRegistry?.relativeRootPath || 'nodejs/{projectCode}/app' }}</p>
              <p class="muted-line">Manifest：{{ codebaseWorkspace.codebaseRegistry?.manifestPlatformPath || 'generated/nodejs/{projectCode}/app/codemind.generated.json' }}</p>
            </article>



            <section class="codebase-export-card codebase-binding-card" :class="codebaseBindingClass">
              <header class="codebase-export-header">
                <div>
                  <strong>代码库导入与重新绑定</strong>
                  <p>{{ codebaseBindingStatus?.summary || '用于在服务器迁移、目录移动或导入已有生成项目后，重新建立数据库里的代码库位置登记。' }}</p>
                </div>
                <span>{{ codebaseBindingStatus?.bindingStatus || 'UNKNOWN' }}</span>
              </header>

              <div class="registry-grid export-grid">
                <div>
                  <span>数据库登记</span>
                  <b>{{ codebaseBindingStatus?.registered ? '已登记' : '未登记' }}</b>
                </div>
                <div>
                  <span>目录</span>
                  <b>{{ codebaseBindingStatus?.validation?.directoryExists ? '存在' : '未找到' }}</b>
                </div>
                <div>
                  <span>package.json</span>
                  <b>{{ codebaseBindingStatus?.validation?.packageJsonExists ? '存在' : '缺失' }}</b>
                </div>
                <div>
                  <span>Manifest 节点</span>
                  <b>{{ codebaseBindingStatus?.validation?.manifestNodeCount || 0 }} 个</b>
                </div>
              </div>

              <p class="muted-line">生成存储根：{{ codebaseBindingStatus?.storageRoot || codebaseWorkspace.codebaseRegistry?.absoluteRootPath || '-' }}</p>
              <p class="muted-line">建议路径：{{ codebaseBindingStatus?.suggestedRelativeRootPath || codebaseWorkspace.codebaseRegistry?.relativeRootPath || 'nodejs/{projectCode}/app' }}</p>

              <div class="codebase-binding-form">
                <label>
                  <span>绑定相对路径</span>
                  <input
                    v-model.trim="codebaseRebindPath"
                    class="codebase-path-input"
                    type="text"
                    placeholder="nodejs/P001/app 或 generated/nodejs/P001/app"
                  />
                </label>
                <p class="muted-line">只允许绑定到 configured generated-root 内的目录。跨服务器迁移时，先配置 codemind.development.generated-root，再重新绑定相对路径。</p>
              </div>

              <div v-if="codebaseBindingStatus?.blockers?.length" class="missing-list blocker-list export-list">
                <span v-for="item in codebaseBindingStatus.blockers" :key="item">{{ item }}</span>
              </div>

              <div v-if="codebaseBindingStatus?.warnings?.length" class="missing-list export-list">
                <span v-for="item in codebaseBindingStatus.warnings" :key="item">{{ item }}</span>
              </div>

              <div class="context-actions runtime-actions">
                <button
                  class="secondary-button"
                  type="button"
                  :disabled="codebaseBindingLoading || codebaseRebinding"
                  @click="() => loadCodebaseBindingStatus()"
                >
                  {{ codebaseBindingLoading ? '刷新中...' : '刷新绑定状态' }}
                </button>

                <button
                  class="secondary-button"
                  type="button"
                  :disabled="codebaseBindingLoading || codebaseRebinding"
                  @click="() => useSuggestedCodebaseBindingPath()"
                >
                  使用建议路径
                </button>

                <button
                  class="primary-button"
                  type="button"
                  :disabled="!canRebindCodebase"
                  @click="() => handleRebindCodebase()"
                >
                  {{ codebaseRebinding ? '绑定中...' : '重新绑定代码库' }}
                </button>
              </div>

              <div v-if="codebaseRebindResult" :class="codebaseRebindResult.rebound ? 'success-box' : 'error-box'">
                {{ codebaseRebindResult.summary }}
              </div>

              <div v-if="codebaseBindingError" class="error-box">
                {{ codebaseBindingError }}
              </div>
            </section>

            <section class="codebase-snapshot-card" :class="codebaseSnapshotStatus?.snapshotCount ? 'ready' : 'warning'">
              <header class="codebase-export-header">
                <div>
                  <strong>代码快照与安全再生成</strong>
                  <p>{{ codebaseSnapshotStatus?.summary || '重新生成代码前，平台会先创建快照，并检测文件是否被用户修改。' }}</p>
                </div>
                <span>{{ codebaseSnapshotStatus?.snapshotCount || 0 }} 个快照</span>
              </header>

              <div class="registry-grid export-grid">
                <div>
                  <span>最新快照</span>
                  <b>{{ codebaseSnapshotStatus?.latestSnapshot?.snapshotId || '暂无' }}</b>
                </div>
                <div>
                  <span>快照文件</span>
                  <b>{{ codebaseSnapshotStatus?.latestSnapshot?.fileCount || 0 }} 个</b>
                </div>
                <div>
                  <span>快照体积</span>
                  <b>{{ formatBytes(codebaseSnapshotStatus?.latestSnapshot?.totalBytes) }}</b>
                </div>
                <div>
                  <span>最近创建</span>
                  <b>{{ formatTime(codebaseSnapshotStatus?.latestSnapshot?.createdAt) }}</b>
                </div>
              </div>

              <p class="muted-line">快照目录：{{ codebaseSnapshotStatus?.snapshotRoot || 'generated/nodejs/{projectCode}/app/.codemind/snapshots' }}</p>

              <div v-if="codebaseSnapshotStatus?.warnings?.length" class="missing-list export-list">
                <span v-for="item in codebaseSnapshotStatus.warnings" :key="item">{{ item }}</span>
              </div>

              <div class="context-actions runtime-actions">
                <button
                  class="secondary-button"
                  type="button"
                  :disabled="codebaseSnapshotLoading"
                  @click="loadCodebaseSnapshotStatus()"
                >
                  {{ codebaseSnapshotLoading ? '刷新中...' : '刷新快照状态' }}
                </button>
              </div>

              <div v-if="codebaseSnapshotError" class="error-box">
                {{ codebaseSnapshotError }}
              </div>
            </section>

            <section class="codebase-export-card" :class="codebaseExportClass">
              <header class="codebase-export-header">
                <div>
                  <strong>交付导出</strong>
                  <p>{{ codebaseExportStatus?.summary || '生成项目写入并完成代码库登记后，可下载为交付 ZIP。' }}</p>
                </div>
                <span>{{ codebaseExportStatus?.exportStatus || 'UNKNOWN' }}</span>
              </header>

              <div class="registry-grid export-grid">
                <div>
                  <span>导出状态</span>
                  <b>{{ codebaseExportStatus?.exportable ? '可导出' : '不可导出' }}</b>
                </div>
                <div>
                  <span>源码文件</span>
                  <b>{{ codebaseExportStatus?.fileCount || 0 }} 个</b>
                </div>
                <div>
                  <span>估算体积</span>
                  <b>{{ formatBytes(codebaseExportStatus?.totalBytes) }}</b>
                </div>
                <div>
                  <span>Manifest</span>
                  <b>{{ codebaseExportStatus?.manifestExists ? '已包含' : '缺失' }}</b>
                </div>
              </div>

              <p class="muted-line">ZIP：{{ codebaseExportStatus?.estimatedZipName || 'codemind-generated-codebase.zip' }}</p>
              <p class="muted-line">导出源：{{ codebaseExportStatus?.codebaseRoot || codebaseWorkspace.codebaseRoot }}</p>

              <div v-if="codebaseExportStatus?.blockers?.length" class="missing-list blocker-list export-list">
                <span v-for="item in codebaseExportStatus.blockers" :key="item">{{ item }}</span>
              </div>

              <div v-if="codebaseExportStatus?.warnings?.length" class="missing-list export-list">
                <span v-for="item in codebaseExportStatus.warnings" :key="item">{{ item }}</span>
              </div>

              <div class="context-actions runtime-actions">
                <button
                  class="secondary-button"
                  type="button"
                  :disabled="codebaseExportLoading"
                  @click="loadCodebaseExportStatus()"
                >
                  {{ codebaseExportLoading ? '刷新中...' : '刷新导出状态' }}
                </button>

                <button
                  class="primary-button"
                  type="button"
                  :disabled="!canDownloadCodebaseZip"
                  @click="handleDownloadCodebaseZip"
                >
                  {{ codebaseExportDownloading ? '正在打包...' : '下载代码 ZIP' }}
                </button>
              </div>

              <div v-if="codebaseExportDownloadedFile" class="success-box">
                已开始下载：{{ codebaseExportDownloadedFile }}
              </div>

              <div v-if="codebaseExportError" class="error-box">
                {{ codebaseExportError }}
              </div>
            </section>


            <section class="codebase-export-card codebase-acceptance-card" :class="codebaseAcceptanceClass">
              <header class="codebase-export-header">
                <div>
                  <strong>生成代码验收流水线</strong>
                  <p>{{ codebaseAcceptanceStatus?.summary || '对生成项目执行安装、构建、测试和健康检查，判断是否可作为交付候选代码。' }}</p>
                </div>
                <span>{{ codebaseAcceptanceStatus?.acceptanceStatus || 'NOT_READY' }}</span>
              </header>

              <div class="registry-grid export-grid">
                <div>
                  <span>运行条件</span>
                  <b>{{ codebaseAcceptanceStatus?.readyToRun ? '已就绪' : '未就绪' }}</b>
                </div>
                <div>
                  <span>交付候选</span>
                  <b>{{ codebaseAcceptanceStatus?.deliverable ? '可以' : '暂不能' }}</b>
                </div>
                <div>
                  <span>通过 / 失败</span>
                  <b>{{ codebaseAcceptanceStatus?.passedCount || 0 }} / {{ codebaseAcceptanceStatus?.failedCount || 0 }}</b>
                </div>
                <div>
                  <span>耗时</span>
                  <b>{{ codebaseAcceptanceStatus?.durationMillis || 0 }} ms</b>
                </div>
              </div>

              <p class="muted-line">前端预览：{{ codebaseAcceptanceStatus?.frontendUrl || 'http://127.0.0.1:5178' }}</p>
              <p class="muted-line">后端健康：{{ codebaseAcceptanceStatus?.healthUrl || 'http://127.0.0.1:3101/api/health' }}</p>

              <div v-if="codebaseAcceptanceStatus?.blockers?.length" class="missing-list blocker-list export-list">
                <span v-for="item in codebaseAcceptanceStatus.blockers" :key="item">{{ item }}</span>
              </div>

              <div v-if="codebaseAcceptanceStatus?.warnings?.length" class="missing-list export-list">
                <span v-for="item in codebaseAcceptanceStatus.warnings" :key="item">{{ item }}</span>
              </div>

              <div v-if="codebaseAcceptanceStatus?.nextActions?.length" class="missing-list export-list">
                <span v-for="item in codebaseAcceptanceStatus.nextActions" :key="item">{{ item }}</span>
              </div>

              <div class="context-actions runtime-actions">
                <button
                  class="secondary-button"
                  :disabled="codebaseAcceptanceLoading || codebaseAcceptanceRunning"
                  @click="() => loadCodebaseAcceptanceStatus()"
                >
                  {{ codebaseAcceptanceLoading ? '刷新中...' : '刷新验收状态' }}
                </button>

                <button
                  class="primary-button"
                  :disabled="!canRunCodebaseAcceptance"
                  @click="handleRunCodebaseAcceptance"
                >
                  {{ codebaseAcceptanceRunning ? '验收中...' : '运行验收流水线' }}
                </button>
              </div>

              <div v-if="codebaseAcceptanceError" class="error-box">
                {{ codebaseAcceptanceError }}
              </div>


              <div class="codegen-trace-card repair-card" v-if="codebaseAcceptanceStatus?.executed">
                <header class="codebase-export-header">
                  <div>
                    <strong>验收失败自动修复</strong>
                    <p>验收失败后，提取 build/test/runtime 日志交给 Development Agent 生成受控修复文件。</p>
                  </div>
                  <span>{{ codebaseRepairResult?.repairStatus || 'WAITING' }}</span>
                </header>

                <div class="context-actions runtime-actions">
                  <button
                    class="secondary-button"
                    :disabled="!canRepairCodebaseAcceptance"
                    @click="() => handleCodebaseRepair(false)"
                  >
                    {{ codebaseRepairLoading ? '生成中...' : '生成修复提案' }}
                  </button>
                  <button
                    class="primary-button"
                    :disabled="!canRepairCodebaseAcceptance || !codebaseRepairResult?.aiGenerated"
                    @click="() => handleCodebaseRepair(true)"
                  >
                    {{ codebaseRepairApplying ? '写入中...' : '应用修复并写入' }}
                  </button>
                </div>

                <div v-if="codebaseRepairError" class="error-box">{{ codebaseRepairError }}</div>
                <div v-if="codebaseRepairResult" class="success-box">
                  {{ codebaseRepairResult.summary }}
                  <br />
                  模型：{{ codebaseRepairResult.provider || 'unknown' }} / {{ codebaseRepairResult.model || 'unknown' }}，
                  修复文件：{{ codebaseRepairResult.repairFileCount }} 个。
                </div>
                <div v-if="codebaseRepairResult?.blockers?.length" class="missing-list blocker-list export-list">
                  <span v-for="item in codebaseRepairResult.blockers" :key="item">{{ item }}</span>
                </div>
                <div v-if="codebaseRepairResult?.warnings?.length" class="missing-list export-list">
                  <span v-for="item in codebaseRepairResult.warnings.slice(0, 8)" :key="item">{{ item }}</span>
                </div>
                <div v-if="codebaseRepairResult?.repairCodeProject?.files?.length" class="acceptance-item-grid">
                  <article
                    v-for="file in codebaseRepairResult.repairCodeProject.files.slice(0, 8)"
                    :key="file.filePath"
                    class="acceptance-item-card ready"
                  >
                    <header>
                      <strong>{{ file.filePath }}</strong>
                      <span>{{ file.fileType }}</span>
                    </header>
                    <p>{{ file.description }}</p>
                  </article>
                </div>
              </div>

              <div v-if="codebaseAcceptanceStatus?.items?.length" class="acceptance-item-grid">
                <article
                  v-for="item in codebaseAcceptanceStatus.items"
                  :key="item.id"
                  class="acceptance-item-card"
                  :class="acceptanceItemClass(item.status)"
                >
                  <header>
                    <strong>{{ item.title }}</strong>
                    <span>{{ item.required ? '必需' : '检查' }}</span>
                  </header>
                  <p>{{ item.summary }}</p>
                  <footer>
                    <small>{{ item.phase }}</small>
                    <small>{{ item.status }}</small>
                  </footer>
                  <details v-if="item.logTail?.length" class="runtime-error-lines acceptance-log-details">
                    <summary>查看日志 {{ item.logTail.length }} 行</summary>
                    <pre><code>{{ item.logTail.join('\n') }}</code></pre>
                  </details>
                </article>
              </div>
            </section>


            <section class="codebase-export-card codebase-deployment-card" :class="codebaseDeploymentClass">
              <header class="codebase-export-header">
                <div>
                  <strong>部署配置生成</strong>
                  <p>{{ codebaseDeploymentStatus?.summary || '为生成项目补齐 Dockerfile、docker-compose.yml、.env.example 和部署说明，便于交付到用户服务器本地运行。' }}</p>
                </div>
                <span>{{ codebaseDeploymentStatus?.deploymentStatus || 'NOT_READY' }}</span>
              </header>

              <div class="registry-grid export-grid">
                <div>
                  <span>部署条件</span>
                  <b>{{ codebaseDeploymentStatus?.deployable ? '已就绪' : '未就绪' }}</b>
                </div>
                <div>
                  <span>计划文件</span>
                  <b>{{ codebaseDeploymentStatus?.plannedFileCount || 0 }} 个</b>
                </div>
                <div>
                  <span>缺失 / 需更新</span>
                  <b>{{ codebaseDeploymentStatus?.missingFileCount || 0 }} / {{ codebaseDeploymentStatus?.outdatedFileCount || 0 }}</b>
                </div>
                <div>
                  <span>已有配置</span>
                  <b>{{ codebaseDeploymentStatus?.existingFileCount || 0 }} 个</b>
                </div>
              </div>

              <p class="muted-line">部署源：{{ codebaseDeploymentStatus?.codebaseRoot || codebaseWorkspace.codebaseRoot }}</p>
              <p class="muted-line">推荐命令：{{ codebaseDeploymentStatus?.commands?.[1] || 'docker compose --env-file .env up --build' }}</p>

              <div v-if="codebaseDeploymentStatus?.blockers?.length" class="missing-list blocker-list export-list">
                <span v-for="item in codebaseDeploymentStatus.blockers" :key="item">{{ item }}</span>
              </div>

              <div v-if="codebaseDeploymentStatus?.warnings?.length" class="missing-list export-list">
                <span v-for="item in codebaseDeploymentStatus.warnings" :key="item">{{ item }}</span>
              </div>

              <div class="context-actions runtime-actions">
                <button
                  class="secondary-button"
                  type="button"
                  :disabled="codebaseDeploymentLoading || codebaseDeploymentApplying"
                  @click="() => loadCodebaseDeploymentStatus()"
                >
                  {{ codebaseDeploymentLoading ? '刷新中...' : '刷新部署状态' }}
                </button>

                <label class="deployment-overwrite-toggle">
                  <input v-model="codebaseDeploymentOverwrite" type="checkbox" />
                  <span>覆盖更新已有部署配置</span>
                </label>

                <button
                  class="primary-button"
                  type="button"
                  :disabled="!canApplyCodebaseDeployment"
                  @click="() => handleApplyCodebaseDeployment()"
                >
                  {{ codebaseDeploymentApplying ? '生成中...' : '生成部署配置' }}
                </button>
              </div>

              <div v-if="codebaseDeploymentResult" :class="codebaseDeploymentResult.applied ? 'success-box' : 'error-box'">
                {{ codebaseDeploymentResult.summary }}
              </div>

              <div v-if="codebaseDeploymentError" class="error-box">
                {{ codebaseDeploymentError }}
              </div>

              <div v-if="codebaseDeploymentStatus?.files?.length" class="acceptance-item-grid deployment-file-grid">
                <article
                  v-for="file in codebaseDeploymentStatus.files"
                  :key="file.filePath"
                  class="acceptance-item-card"
                  :class="deploymentFileClass(file.action)"
                >
                  <header>
                    <strong>{{ file.filePath }}</strong>
                    <span>{{ file.action }}</span>
                  </header>
                  <p>{{ file.description }}</p>
                  <footer>
                    <small>{{ file.exists ? '已存在' : '缺失' }}</small>
                    <small>{{ file.upToDate ? '最新' : file.message }}</small>
                  </footer>
                </article>
              </div>
            </section>

            <section class="runtime-manager-card" :class="runtimeStatusClass">
              <header class="runtime-header">
                <div>
                  <strong>运行管理</strong>
                  <p>{{ runtimeStatus?.summary || '生成项目写入后，可在平台内安装依赖、启动、停止并查看日志。' }}</p>
                </div>

                <span>{{ runtimeStatus?.runtimeStatus || 'UNKNOWN' }} / {{ runtimeStatus?.installStatus || 'UNKNOWN' }}</span>
              </header>

              <div class="runtime-meta-grid">
                <div>
                  <strong>{{ runtimeStatus?.dependencyInstalled ? '已安装' : '未安装' }}</strong>
                  <span>依赖状态</span>
                </div>
                <div>
                  <strong>{{ runtimeStatus?.processId || '-' }}</strong>
                  <span>运行进程</span>
                </div>
                <div>
                  <strong>{{ runtimeStatus?.frontendUrl || '-' }}</strong>
                  <span>前端预览</span>
                </div>
                <div>
                  <strong>{{ runtimeStatus?.backendUrl || '-' }}</strong>
                  <span>后端接口</span>
                </div>
                <div>
                  <strong>{{ runtimeStatus?.healthStatus || 'NOT_CHECKED' }}</strong>
                  <span>健康状态</span>
                </div>
                <div>
                  <strong>{{ formatTime(runtimeStatus?.lastHealthCheckedAt) }}</strong>
                  <span>最近检查</span>
                </div>
              </div>

              <div v-if="runtimeStatus" class="runtime-health-strip" :class="runtimeHealthClass">
                <span>前端：{{ runtimeStatus.frontendReachable ? '可访问' : runtimeStatus.frontendHealthMessage || '未访问' }}</span>
                <span>后端：{{ runtimeStatus.backendReachable ? '可访问' : runtimeStatus.backendHealthMessage || '未访问' }}</span>
              </div>

              <section v-if="runtimeStatus" class="runtime-diagnostics-panel" :class="runtimeDiagnosticClass">
                <header>
                  <div>
                    <strong>{{ runtimeStatus.diagnosticTitle || '运行诊断' }}</strong>
                    <p>{{ runtimeStatus.diagnosticSummary || '系统会根据运行状态、健康检查和日志自动给出诊断建议。' }}</p>
                  </div>
                  <span>{{ runtimeStatus.diagnosticLevel || 'INFO' }}</span>
                </header>

                <div v-if="runtimeStatus.detectedProblems?.length" class="runtime-diagnostic-section">
                  <b>检测到的问题</b>
                  <ul>
                    <li v-for="item in runtimeStatus.detectedProblems" :key="item">{{ item }}</li>
                  </ul>
                </div>

                <div v-if="runtimeStatus.diagnosticSuggestions?.length" class="runtime-diagnostic-section">
                  <b>修复建议</b>
                  <ul>
                    <li v-for="item in runtimeStatus.diagnosticSuggestions" :key="item">{{ item }}</li>
                  </ul>
                </div>

                <div v-if="runtimeStatus.nextActions?.length" class="runtime-diagnostic-section">
                  <b>下一步操作</b>
                  <ul>
                    <li v-for="item in runtimeStatus.nextActions" :key="item">{{ item }}</li>
                  </ul>
                </div>

                <details v-if="runtimeStatus.recentErrorLines?.length" class="runtime-error-lines">
                  <summary>最近错误日志 {{ runtimeStatus.recentErrorLines.length }} 条</summary>
                  <pre><code>{{ runtimeStatus.recentErrorLines.join('\n') }}</code></pre>
                </details>

                <button class="secondary-button" type="button" @click="copyRuntimeDiagnostics">
                  {{ runtimeDiagnosticCopied ? '已复制诊断信息' : '复制诊断信息' }}
                </button>
              </section>

              <div class="context-actions runtime-actions">
                <button
                  class="secondary-button"
                  :disabled="runtimeLoading"
                  @click="loadRuntimeStatus()"
                >
                  {{ runtimeLoading ? '刷新中...' : '刷新运行状态' }}
                </button>

                <button
                  class="secondary-button"
                  :disabled="!canInstallRuntime"
                  @click="handleRuntimeInstall"
                >
                  安装依赖
                </button>

                <button
                  class="primary-button"
                  :disabled="!canStartRuntime"
                  @click="handleRuntimeStart"
                >
                  启动项目
                </button>

                <button
                  class="secondary-button"
                  :disabled="!canStopRuntime"
                  @click="handleRuntimeStop"
                >
                  停止项目
                </button>

                <button
                  class="secondary-button"
                  :disabled="!runtimeStatus?.codebaseExists || runtimeLoading"
                  @click="handleRuntimeRestart"
                >
                  重启项目
                </button>

                <button
                  class="secondary-button"
                  :disabled="runtimeStatus?.runtimeStatus !== 'RUNNING'"
                  @click="openRuntimePreview"
                >
                  打开当前节点预览
                </button>

                <button
                  class="secondary-button"
                  :disabled="runtimeStatus?.runtimeStatus !== 'RUNNING' || !runtimePreviewUrl"
                  @click="toggleRuntimePreview"
                >
                  {{ runtimePreviewEnabled ? '收起平台内预览' : '平台内预览' }}
                </button>

                <button
                  class="secondary-button"
                  :disabled="runtimeLogsLoading"
                  @click="loadRuntimeLogs()"
                >
                  {{ runtimeLogsLoading ? '读取中...' : '查看日志' }}
                </button>
              </div>

              <section v-if="runtimePreviewEnabled && runtimePreviewUrl" class="runtime-preview-panel">
                <header>
                  <div>
                    <strong>平台内预览</strong>
                    <span>{{ runtimePreviewUrl }}</span>
                  </div>
                  <button class="secondary-button" @click="reloadRuntimePreview">刷新预览</button>
                </header>
                <iframe
                  :key="runtimePreviewKey"
                  :src="runtimePreviewUrl"
                  title="Generated Node.js preview"
                  sandbox="allow-scripts allow-forms allow-same-origin allow-popups"
                ></iframe>
              </section>

              <div v-if="runtimeError" class="error-box">
                {{ runtimeError }}
              </div>

              <div v-if="runtimeLogsError" class="error-box">
                {{ runtimeLogsError }}
              </div>

              <section v-if="runtimeLogs" class="runtime-log-insights">
                <div>
                  <strong>{{ runtimeLogs.errorLineCount || 0 }}</strong>
                  <span>错误日志</span>
                </div>
                <div>
                  <strong>{{ runtimeLogs.warningLineCount || 0 }}</strong>
                  <span>警告日志</span>
                </div>
                <div>
                  <strong>{{ runtimeLogs.lineCount }}</strong>
                  <span>返回日志行</span>
                </div>
              </section>

              <div v-if="runtimeLogs?.diagnosticSuggestions?.length" class="runtime-log-suggestions">
                <strong>日志诊断建议</strong>
                <ul>
                  <li v-for="item in runtimeLogs.diagnosticSuggestions" :key="item">{{ item }}</li>
                </ul>
              </div>

              <details v-if="runtimeLogs?.errorLines?.length" class="runtime-error-lines open-log-errors" open>
                <summary>最近错误日志</summary>
                <pre><code>{{ runtimeLogs.errorLines.join('\n') }}</code></pre>
              </details>

              <pre v-if="runtimeLogs?.lines?.length" class="runtime-log-preview"><code>{{ runtimeLogs.lines.join('\n') }}</code></pre>
            </section>

              </div>
            </details>

            <div v-if="!codebaseWorkspace.selectedNodeHasCode" class="codebase-readiness-panel">
              <strong>当前节点代码准备度</strong>

              <p v-if="codebaseWorkspace.missingRequiredArtifacts.length === 0 && codebaseWorkspace.ready">
                当前节点开发输入已就绪，可以生成代码贡献。
              </p>
              <p v-else>
                当前节点尚未达到代码生成条件。请先补齐缺失资产或处理阻断项。
              </p>

              <div v-if="codebaseWorkspace.missingRequiredArtifacts.length" class="missing-list">
                <span
                  v-for="item in codebaseWorkspace.missingRequiredArtifacts"
                  :key="item"
                >
                  {{ item }}
                </span>
              </div>

              <div v-if="codebaseWorkspace.blockers.length" class="missing-list blocker-list">
                <span
                  v-for="item in codebaseWorkspace.blockers"
                  :key="item"
                >
                  {{ item }}
                </span>
              </div>

              <div class="context-actions">
                <button
                  class="primary-button"
                  :disabled="!canGenerateCodeFromWorkspace || codeProjectLoading"
                  @click="handleGenerateCodeProject"
                >
                  {{ codeProjectLoading ? '生成中...' : codebaseWorkspace.generateActionLabel }}
                </button>
              </div>
            </div>

            <div v-else class="codebase-browser">
              <aside class="codebase-file-list">
                <div class="codebase-tabs">
                  <button
                    type="button"
                    :class="{ active: codebaseViewMode === 'node' }"
                    @click="codebaseViewMode = 'node'"
                  >
                    当前节点代码
                  </button>
                  <button
                    type="button"
                    :class="{ active: codebaseViewMode === 'tree' }"
                    @click="codebaseViewMode = 'tree'"
                  >
                    项目目录
                  </button>
                </div>

                <div v-if="codebaseViewMode === 'node'" class="codebase-file-items">
                  <button
                    v-for="file in codebaseWorkspace.nodeFiles"
                    :key="file.filePath"
                    type="button"
                    class="code-file-pill"
                    :class="{ active: selectedCodeFile?.filePath === file.filePath }"
                    @click="openCodeFile(file.filePath)"
                  >
                    <strong>{{ compactPath(file.filePath) }}</strong>
                    <span>{{ file.relationship }} / {{ file.fileType }}</span>
                  </button>
                </div>

                <div v-else class="codebase-file-items">
                  <button
                    v-for="item in flatCodebaseFiles"
                    :key="item.path"
                    type="button"
                    class="code-file-pill"
                    :style="{ paddingLeft: `${12 + item.depth * 14}px` }"
                    :class="{ active: selectedCodeFile?.filePath === item.path, directory: item.itemType === 'DIRECTORY' }"
                    :disabled="item.itemType === 'DIRECTORY'"
                    @click="openCodeFile(item.path)"
                  >
                    <strong>{{ item.itemType === 'DIRECTORY' ? '📁' : '📄' }} {{ item.name }}</strong>
                    <span>{{ item.itemType === 'DIRECTORY' ? '目录' : item.language }}</span>
                  </button>
                </div>
              </aside>

              <section class="codebase-code-viewer">
                <div class="code-viewer-header">
                  <div>
                    <strong>{{ selectedCodeFile?.filePath || '请选择文件' }}</strong>
                    <span v-if="selectedCodeFile">{{ selectedCodeFile.language }} / {{ selectedCodeFile.bytes }} bytes</span>
                  </div>
                  <button
                    class="secondary-button"
                    :disabled="!selectedCodeFile || codeFileLoading"
                    @click="selectedCodeFile && openCodeFile(selectedCodeFile.filePath)"
                  >
                    {{ codeFileLoading ? '读取中...' : '刷新文件' }}
                  </button>
                </div>

                <div v-if="codeFileError" class="error-box">
                  {{ codeFileError }}
                </div>

                <pre v-if="selectedCodeFile" class="code-preview"><code>{{ selectedCodeFile.content }}</code></pre>

                <div v-else class="empty-mini">
                  从左侧“当前节点代码”或“项目目录”选择文件查看。
                </div>
              </section>
            </div>
          </template>
        </section>

        <section class="target-stack-section">
          <div class="section-heading">
            <h3>目标技术栈</h3>
            <p>
              Dev Agent 使用同一份语言无关 DEV_HANDOFF_PACKAGE，再按目标技术栈转换成具体项目代码。当前第一条开放分支是 Node.js 全栈系统。
            </p>
          </div>

          <div v-if="targetStackLoading" class="empty-mini">
            正在读取目标技术栈...
          </div>

          <div v-if="targetStackError" class="error-box">
            {{ targetStackError }}
          </div>

          <div v-if="targetStacks.length" class="target-stack-grid">
            <button
              v-for="stack in targetStacks"
              :key="stack.stackCode"
              type="button"
              class="target-stack-card"
              :class="{ active: selectedTargetStackCode === stack.stackCode, disabled: !stack.available }"
              :disabled="!stack.available"
              @click="selectedTargetStackCode = stack.stackCode"
            >
              <header>
                <strong>{{ stack.displayName }}</strong>
                <span>{{ stack.lifecycleStatus }}</span>
              </header>
              <p>{{ stack.description }}</p>
              <div class="stack-capabilities">
                <span
                  v-for="capability in stack.capabilities.slice(0, 4)"
                  :key="`${stack.stackCode}-${capability}`"
                >
                  {{ capability }}
                </span>
              </div>
            </button>
          </div>

          <article v-if="selectedTargetStack" class="stack-detail-card">
            <div>
              <strong>{{ selectedTargetStack.displayName }}</strong>
              <p>{{ selectedTargetStack.description }}</p>
            </div>
            <div class="stack-command-list">
              <span
                v-for="command in selectedTargetStack.runtimeCommands"
                :key="command"
              >
                {{ command }}
              </span>
            </div>
          </article>
        </section>

        <section
          class="phase4-acceptance-section"
          :class="{ ready: phase4Acceptance?.readyForNextPhase }"
        >
          <div class="section-heading">
            <h3>Phase 4 验收清单</h3>
            <p>
              验收 UI_CONTRACT、用户确认、开发计划确认与交接包准备情况。该清单通过后，需求 Agent 的 Phase 4 可以进入 Git 提交与 Phase 5 开发。
            </p>
          </div>

          <div class="context-actions">
            <button
              class="secondary-button"
              :disabled="!canUseDevelopmentApi || phase4AcceptanceLoading"
              @click="loadPhase4Acceptance"
            >
              {{ phase4AcceptanceLoading ? '验收中...' : '刷新 Phase 4 验收' }}
            </button>
          </div>

          <div v-if="phase4AcceptanceError" class="error-box">
            {{ phase4AcceptanceError }}
          </div>

          <div v-if="phase4Acceptance" class="phase4-summary-grid">
            <div>
              <strong>{{ phase4Acceptance.completionPercent }}%</strong>
              <span>完成度</span>
            </div>
            <div>
              <strong>{{ phase4Acceptance.completionLevel }}</strong>
              <span>验收状态</span>
            </div>
            <div>
              <strong>{{ phase4Acceptance.readyForNextPhase ? '可以' : '暂不能' }}</strong>
              <span>进入 Phase 5</span>
            </div>
            <div>
              <strong>{{ formatTime(phase4Acceptance.generatedAt) }}</strong>
              <span>检查时间</span>
            </div>
          </div>

          <p
            v-if="phase4Acceptance"
            class="phase4-summary-text"
          >
            {{ phase4Acceptance.summaryText }}
          </p>

          <div
            v-if="phase4Acceptance"
            class="phase4-item-grid"
          >
            <article
              v-for="item in phase4Acceptance.items"
              :key="item.id"
              class="phase4-item-card"
              :class="item.status.toLowerCase()"
            >
              <header>
                <strong>{{ item.title }}</strong>
                <span>{{ item.required ? '必需' : '可选' }}</span>
              </header>
              <p>{{ item.description }}</p>
              <footer>{{ item.statusText }}</footer>
            </article>
          </div>

          <div
            v-if="phase4Acceptance?.blockers?.length"
            class="phase4-blocker-list"
          >
            <strong>剩余阻断项</strong>
            <p
              v-for="blocker in phase4Acceptance.blockers"
              :key="blocker"
            >
              {{ blocker }}
            </p>
          </div>

          <div
            v-if="phase4Acceptance?.nextPhases?.length"
            class="package-section-list"
          >
            <span
              v-for="phase in phase4Acceptance.nextPhases"
              :key="phase"
            >
              {{ phase }}
            </span>
          </div>
        </section>



        <section
          v-if="readiness.nodeType === 'PAGE'"
          class="snapshot-card"
          :class="{ ready: readiness.uiContractSnapshot?.exists && readiness.uiContractSnapshot?.matchesCurrentArtifact }"
        >
          <div>
            <p class="eyebrow">UI_CONTRACT Confirmation</p>
            <h3>页面交互原型确认快照</h3>
            <span>
              {{ snapshotStatusText }}
            </span>
          </div>

          <button
            class="secondary-button"
            :disabled="!canPreviewUiContract"
            @click="$emit('preview-ui-contract')"
          >
            打开交互预览
          </button>
        </section>

        <section class="asset-section">
          <div class="section-heading">
            <h3>开发输入资产</h3>
            <p>必要资产缺失或不可用会阻断正式开发；建议资产缺失只形成警告。</p>
          </div>

          <div class="asset-grid">
            <article
              v-for="asset in readiness.assets"
              :key="asset.assetKey || `${asset.sourceNodeId || 'node'}:${asset.artifactType}`"
              class="asset-card"
              :class="assetLevelClass(asset)"
            >
              <header>
                <strong>{{ asset.title }}</strong>

                <div class="asset-badges">
                  <span :class="{ required: asset.required }">
                    {{ asset.required ? '必要' : '建议' }}
                  </span>
                  <span
                    class="source-badge"
                    :class="{ inherited: asset.inherited }"
                  >
                    {{ assetSourceText(asset) }}
                  </span>
                </div>
              </header>

              <p>{{ asset.statusText }}</p>

              <small
                v-if="asset.sourceNodeName"
                class="asset-source-detail"
              >
                来源：{{ asset.sourceNodeName }}
                <template v-if="asset.sourceNodeType">
                  · {{ asset.sourceNodeType }}
                </template>
              </small>

              <footer>
                <small>{{ asset.exists ? `v${asset.versionNo || '-'}` : '未生成' }}</small>
                <small>{{ formatTime(asset.updatedAt) }}</small>
              </footer>
            </article>
          </div>
        </section>

        <section class="issue-section">
          <div class="section-heading">
            <h3>阻断项</h3>
            <p>存在阻断项时，不允许正式 Development Agent 代码生成。</p>
          </div>

          <div v-if="readiness.blockers.length === 0" class="empty-mini">
            当前没有阻断项。
          </div>

          <article
            v-for="issue in readiness.blockers"
            :key="issue.id"
            class="issue-card blocker"
          >
            <strong>{{ issue.title }}</strong>
            <p>{{ issue.description }}</p>
          </article>
        </section>

        <section class="issue-section">
          <div class="section-heading">
            <h3>警告项</h3>
            <p>警告项不阻断预开发，但正式交付前建议处理。</p>
          </div>

          <div v-if="readiness.warnings.length === 0" class="empty-mini">
            当前没有警告项。
          </div>

          <article
            v-for="issue in readiness.warnings"
            :key="issue.id"
            class="issue-card warning"
          >
            <strong>{{ issue.title }}</strong>
            <p>{{ issue.description }}</p>
          </article>
        </section>

        <section class="context-section">
          <div class="section-heading">
            <h3>开发上下文预览</h3>
            <p>展示 Dev Agent 将读取的资产、确认后的 UI_CONTRACT、主题快照和目标技术栈。</p>
          </div>

          <div v-if="selectedTargetStack" class="selected-stack-strip">
            <strong>当前目标栈：{{ selectedTargetStack.displayName }}</strong>
            <span>{{ selectedTargetStack.stackCode }}</span>
          </div>

          <div class="context-actions">
            <button
              class="secondary-button"
              :disabled="!canUseDevelopmentApi || contextLoading"
              @click="handleLoadContext"
            >
              {{ contextLoading ? '读取中...' : '查看开发上下文' }}
            </button>

            <button
              class="primary-button"
              :disabled="!canGeneratePlan || planLoading"
              @click="handleGeneratePlan"
            >
              {{ generatePlanButtonText }}
            </button>
          </div>

          <div v-if="!canUseDevelopmentApi" class="empty-mini">
            当前开发面板缺少 workspaceId 或 projectCode。请确认 ProjectWorkspacePage.vue 已向 DevelopmentHandoffGatePanel 传入 workspace-id 和 project-code。
          </div>

          <div v-if="contextError" class="error-box">
            {{ contextError }}
          </div>

          <div v-if="planError" class="error-box">
            {{ planError }}
          </div>

          <div v-if="handoffContext" class="context-grid">
            <article
              v-for="artifact in handoffContext.artifacts"
              :key="artifact.artifactType"
              class="context-asset-card"
              :class="{ required: artifact.required }"
            >
              <strong>{{ artifact.title }}</strong>
              <span>{{ artifact.required ? '必要资产' : '建议资产' }}</span>
              <p>{{ artifact.status || '-' }} / v{{ artifact.versionNo || '-' }}</p>
            </article>
          </div>

          <div
            v-if="handoffContext?.confirmedUiContract"
            class="context-json-card"
          >
            <strong>已确认 UI_CONTRACT</strong>
            <p>已纳入开发上下文，后续真实页面代码应保持布局、字段、动作、状态与确认快照一致。</p>
          </div>

          <div
            v-if="handoffContext?.themeProfileSnapshot"
            class="context-json-card"
          >
            <strong>主题风格快照</strong>
            <p>已纳入开发上下文，后续真实页面代码应沿用确认时的项目级风格方向。</p>
          </div>
        </section>

        <section
          v-if="developmentPlan || planConfirmation"
          class="plan-confirm-section"
          :class="{ confirmed: isCurrentPlanConfirmed, stale: planConfirmation && !isCurrentPlanConfirmed }"
        >
          <div class="section-heading">
            <h3>开发实施计划确认</h3>
            <p>{{ planConfirmationStatusText }}</p>
          </div>

          <div class="plan-confirm-meta">
            <span>当前计划校验值：{{ developmentPlan?.planChecksum || '-' }}</span>
            <span>已确认校验值：{{ planConfirmation?.planChecksum || '-' }}</span>
            <span>确认时间：{{ formatTime(planConfirmation?.confirmedAt) }}</span>
          </div>

          <div class="context-actions">
            <button
              class="primary-button"
              :disabled="!developmentPlan || confirmingPlan || isCurrentPlanConfirmed"
              @click="handleConfirmPlan"
            >
              {{ confirmingPlan ? '确认中...' : isCurrentPlanConfirmed ? '当前计划已确认' : '确认开发实施计划' }}
            </button>

            <button
              class="secondary-button"
              :disabled="!canUseDevelopmentApi || planConfirmationLoading"
              @click="loadCurrentPlanConfirmation"
            >
              {{ planConfirmationLoading ? '读取中...' : '刷新确认状态' }}
            </button>

            <button
              class="primary-button"
              :disabled="!canGenerateHandoffPackage || handoffPackageLoading"
              @click="handleGenerateHandoffPackage"
            >
              {{ handoffPackageLoading ? '生成中...' : '生成开发交接包' }}
            </button>

            <button
              class="primary-button"
              :disabled="!canGenerateCodeProject || codeProjectLoading"
              @click="handleGenerateCodeProject"
            >
              {{ codeProjectLoading ? '生成中...' : '由 Development Codegen Agent 生成 Node.js 项目代码提案' }}
            </button>
          </div>

          <div v-if="planConfirmationError" class="error-box">
            {{ planConfirmationError }}
          </div>

          <div v-if="handoffPackageError" class="error-box">
            {{ handoffPackageError }}
          </div>

          <div v-if="codeProjectError" class="error-box">
            {{ codeProjectError }}
          </div>
        </section>

        <section
          v-if="handoffPackage"
          class="handoff-package-section"
        >
          <div class="section-heading">
            <h3>Development Agent 交接包</h3>
            <p>
              已基于确认后的 Development Plan 生成交接包，后续真实代码生成应读取该包，而不是重新临时推理。
            </p>
          </div>

          <div class="package-summary-grid">
            <div>
              <strong>{{ handoffPackage.packageStatus }}</strong>
              <span>交接包状态</span>
            </div>
            <div>
              <strong>{{ handoffPackage.assets.length }}</strong>
              <span>资产版本</span>
            </div>
            <div>
              <strong>{{ handoffPackage.manifest.includedSections.length }}</strong>
              <span>包内分区</span>
            </div>
            <div>
              <strong>{{ formatTime(handoffPackage.generatedAt) }}</strong>
              <span>生成时间</span>
            </div>
          </div>

          <article class="context-json-card">
            <strong>交接包校验值</strong>
            <p>{{ handoffPackage.packageChecksum }}</p>
          </article>

          <article class="context-json-card">
            <strong>已确认计划校验值</strong>
            <p>{{ handoffPackage.manifest.developmentPlanChecksum }}</p>
          </article>

          <div class="package-section-list">
            <span
              v-for="section in handoffPackage.manifest.includedSections"
              :key="section"
            >
              {{ section }}
            </span>
          </div>

          <article class="plan-card">
            <h4>纳入交接包的资产版本</h4>
            <div
              v-for="asset in handoffPackage.assets"
              :key="`${asset.artifactType}-${asset.artifactId || '-'}`"
              class="plan-item"
            >
              <strong>{{ asset.artifactType }} / v{{ asset.versionNo || '-' }}</strong>
              <p>{{ asset.title || '-' }}</p>
              <span>{{ asset.status || '-' }}</span>
            </div>
          </article>
        </section>


        <section
          v-if="codeProject"
          class="code-project-section"
        >
          <div class="section-heading">
            <h3>Node.js 项目代码提案</h3>
            <p>{{ codeProject.summary }}</p>
          </div>

          <div class="package-summary-grid">
            <div>
              <strong>{{ codeProject.files.length }}</strong>
              <span>生成文件</span>
            </div>
            <div>
              <strong>{{ codeProject.targetStackLabel }}</strong>
              <span>目标技术栈</span>
            </div>
            <div>
              <strong>{{ codeProject.proposalStatus }}</strong>
              <span>提案状态</span>
            </div>
            <div>
              <strong>{{ formatTime(codeProject.generatedAt) }}</strong>
              <span>生成时间</span>
            </div>
          </div>

          <article class="context-json-card">
            <strong>生成根目录</strong>
            <p>{{ codeProject.generationRoot }}</p>
          </article>

          <article class="context-json-card">
            <strong>代码提案校验值</strong>
            <p>{{ codeProject.proposalChecksum }}</p>
          </article>

          <div class="context-actions code-project-actions">
            <button
              class="primary-button"
              :disabled="!canMaterializeCodeProject || codeProjectMaterializationLoading"
              @click="handleMaterializeCodeProject(false)"
            >
              {{ codeProjectMaterializationLoading ? '写入中...' : '写入 generated/nodejs' }}
            </button>

            <button
              class="secondary-button"
              :disabled="!canMaterializeCodeProject || codeProjectMaterializationLoading"
              @click="handleMaterializeCodeProject(true)"
            >
              覆盖写入生成目录
            </button>
          </div>

          <div v-if="codeProjectMaterializationError" class="error-box">
            {{ codeProjectMaterializationError }}
          </div>

          <article
            v-if="codeProjectMaterialization"
            class="context-json-card materialization-summary-card"
          >
            <strong>本地落盘结果</strong>
            <p>
              {{ codeProjectMaterialization.materializationStatus }}，
              已写入 {{ codeProjectMaterialization.writtenCount }} 个文件，
              跳过 {{ codeProjectMaterialization.skippedCount }} 个文件。
            </p>
            <p>生成目录：{{ codeProjectMaterialization.absoluteGenerationRoot }}</p>
          </article>

          <article
            v-if="codeProjectMaterialization?.safetyCheck"
            class="plan-card safety-check-card"
            :class="codeProjectMaterialization.safetyCheck.safeToApply ? 'ready' : 'blocked'"
          >
            <h4>安全再生成检查</h4>
            <div class="runtime-log-insights safety-count-grid">
              <div>
                <strong>{{ codeProjectMaterialization.safetyCheck.addedCount }}</strong>
                <span>新增</span>
              </div>
              <div>
                <strong>{{ codeProjectMaterialization.safetyCheck.modifiedCount }}</strong>
                <span>安全修改</span>
              </div>
              <div>
                <strong>{{ codeProjectMaterialization.safetyCheck.unchangedCount }}</strong>
                <span>未变化</span>
              </div>
              <div>
                <strong>{{ codeProjectMaterialization.safetyCheck.conflictCount }}</strong>
                <span>冲突</span>
              </div>
            </div>

            <div v-if="codeProjectMaterialization.snapshot" class="success-box">
              已创建写入前快照：{{ codeProjectMaterialization.snapshot.snapshotId }}，包含
              {{ codeProjectMaterialization.snapshot.fileCount }} 个文件。
            </div>

            <div v-if="codeProjectMaterialization.safetyCheck.blockers?.length" class="missing-list blocker-list export-list">
              <span v-for="item in codeProjectMaterialization.safetyCheck.blockers" :key="item">{{ item }}</span>
            </div>

            <div v-if="codeProjectMaterialization.safetyCheck.warnings?.length" class="missing-list export-list">
              <span v-for="item in codeProjectMaterialization.safetyCheck.warnings" :key="item">{{ item }}</span>
            </div>

            <div class="safety-file-list">
              <div
                v-for="file in codeProjectMaterialization.safetyCheck.files"
                :key="`${file.filePath}-${file.changeType}`"
                class="safety-file-item"
                :class="safetyChangeClass(file.changeType)"
              >
                <div>
                  <strong>{{ file.filePath }}</strong>
                  <p>{{ file.message }}</p>
                </div>
                <span>{{ file.changeType }}</span>
              </div>
            </div>
          </article>

          <article
            v-if="codeProjectMaterialization"
            class="plan-card materialization-file-card"
          >
            <h4>落盘文件结果</h4>
            <div
              v-for="file in codeProjectMaterialization.files"
              :key="`${file.filePath}-${file.status}`"
              class="materialized-file-item"
              :class="file.status.toLowerCase()"
            >
              <div>
                <strong>{{ file.filePath }}</strong>
                <p>{{ file.absolutePath }}</p>
              </div>
              <span>{{ file.status }} / {{ file.bytes }} bytes</span>
            </div>
          </article>

          <div v-if="codeProject.warnings?.length" class="plan-stack-notes">
            <p
              v-for="warning in codeProject.warnings"
              :key="warning"
            >
              {{ warning }}
            </p>
          </div>

          <article class="plan-card">
            <h4>运行命令</h4>
            <div class="stack-command-list">
              <span
                v-for="command in codeProject.runtimeCommands"
                :key="`code-${command}`"
              >
                {{ command }}
              </span>
            </div>
          </article>

          <article class="plan-card">
            <h4>生成文件清单</h4>
            <div
              v-for="file in codeProject.files"
              :key="file.filePath"
              class="code-file-item"
            >
              <header>
                <div>
                  <strong>{{ file.filePath }}</strong>
                  <p>{{ file.description }}</p>
                </div>
                <span>{{ file.fileType }} / {{ file.language }}</span>
              </header>

              <details>
                <summary>查看代码内容</summary>
                <pre><code>{{ file.content }}</code></pre>
              </details>
            </div>
          </article>
        </section>


        <section
          v-if="developmentPlan"
          class="plan-section"
        >
          <div class="section-heading">
            <h3>开发实施计划</h3>
            <p>{{ developmentPlan.summary }}</p>
          </div>

          <div class="plan-stack-summary">
            <div>
              <strong>{{ developmentPlan.targetStackLabel || developmentPlan.targetStack || '目标技术栈' }}</strong>
              <span>{{ developmentPlan.generatorMode || 'Development generator' }}</span>
            </div>
            <div class="stack-command-list">
              <span
                v-for="command in developmentPlan.runtimeCommands || []"
                :key="`plan-${command}`"
              >
                {{ command }}
              </span>
            </div>
          </div>

          <div v-if="developmentPlan.stackNotes?.length" class="plan-stack-notes">
            <p
              v-for="note in developmentPlan.stackNotes"
              :key="note"
            >
              {{ note }}
            </p>
          </div>

          <div class="plan-principles">
            <span
              v-for="principle in developmentPlan.principles"
              :key="principle"
            >
              {{ principle }}
            </span>
          </div>

          <div class="plan-columns">
            <article class="plan-card">
              <h4>前端文件</h4>
              <div
                v-for="file in developmentPlan.frontendFiles"
                :key="file.filePath"
                class="plan-item"
              >
                <strong>{{ file.filePath }}</strong>
                <p>{{ file.description }}</p>
                <span>{{ file.action }} / {{ file.source }}</span>
              </div>
            </article>

            <article class="plan-card">
              <h4>后端文件</h4>
              <div
                v-for="file in developmentPlan.backendFiles"
                :key="file.filePath"
                class="plan-item"
              >
                <strong>{{ file.filePath }}</strong>
                <p>{{ file.description }}</p>
                <span>{{ file.action }} / {{ file.source }}</span>
              </div>
            </article>
          </div>

          <article class="plan-card">
            <h4>接口计划</h4>
            <div
              v-for="api in developmentPlan.apiItems"
              :key="`${api.method}-${api.path}`"
              class="plan-item"
            >
              <strong>{{ api.method }} {{ api.path }}</strong>
              <p>{{ api.purpose }}</p>
              <span>{{ api.implementationHint }}</span>
            </div>
          </article>

          <article class="plan-card">
            <h4>实施步骤</h4>
            <div
              v-for="step in developmentPlan.implementationSteps"
              :key="step.stepNo"
              class="plan-item"
            >
              <strong>{{ step.stepNo }}. {{ step.title }}</strong>
              <p>{{ step.description }}</p>
              <span>{{ step.requiredBeforeCodeGeneration ? '代码生成前必须完成' : '可在代码生成后细化' }}</span>
            </div>
          </article>

          <article class="plan-card">
            <h4>来源映射</h4>
            <div
              v-for="mapping in developmentPlan.sourceMappings"
              :key="`${mapping.sourceType}-${mapping.usedFor}`"
              class="plan-item"
            >
              <strong>{{ mapping.sourceTitle }}</strong>
              <p>{{ mapping.usedFor }}</p>
              <span>置信度：{{ mapping.confidence }}</span>
            </div>
          </article>
        </section>

        <footer class="handoff-footer">
          <button
            class="primary-button"
            :disabled="!readiness.ready || !isCurrentPlanConfirmed"
            title="由 Development Codegen Agent 生成 Node.js 项目代码提案前，需要先通过门禁并确认开发实施计划。"
          >
            {{
              !readiness.ready
                ? '暂不能进入正式开发 Agent'
                : isCurrentPlanConfirmed
                  ? codeProject ? 'Node.js 项目代码提案已生成' : '开发计划已确认，可由 Development Codegen Agent 生成 Node.js 项目代码提案'
                  : '需确认开发实施计划后进入代码生成'
            }}
          </button>
        </footer>
        </template>
      </template>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import type { ModuleTreeNode } from '../types/codemind'
import {
  confirmDevelopmentPlanSnapshot,
  fetchCurrentDevelopmentPlanConfirmation,
  fetchDevelopmentHandoffContext,
  fetchDevelopmentPhase4Acceptance,
  fetchDevelopmentRuntimeLogs,
  fetchDevelopmentRuntimeStatus,
  fetchDevelopmentCodebaseFileContent,
  fetchDevelopmentCodebaseWorkspace,
  fetchDevelopmentCodebaseExportStatus,
  fetchDevelopmentCodebaseSnapshotStatus,
  fetchDevelopmentCodebaseAcceptanceStatus,
  fetchDevelopmentCodebaseDeploymentStatus,
  fetchDevelopmentCodebaseBindingStatus,
  fetchDevelopmentTargetStacks,
  fetchAgentModelRoutingStatus,
  downloadDevelopmentCodebaseZip,
  applyDevelopmentCodebaseDeploymentConfig,
  applyDevelopmentCodebaseRepair,
  proposeDevelopmentCodebaseRepair,
  installDevelopmentRuntimeDependencies,
  generateDevelopmentCodeProject,
  generateDevelopmentHandoffPackage,
  generateDevelopmentPlan,
  materializeDevelopmentCodeProject,
  restartDevelopmentRuntime,
  rebindDevelopmentCodebase,
  runDevelopmentCodebaseAcceptance,
  startDevelopmentRuntime,
  stopDevelopmentRuntime,
} from '../api/developmentHandoffApi'
import type {
  AgentModelRoutingGatewayResponse,
  AgentModelProfileResponse,
  ConfirmedDevelopmentPlanSnapshot,
  DevelopmentCodeMaterializationResponse,
  DevelopmentCodeProjectResponse,
  DevelopmentCodebaseFileContentResponse,
  DevelopmentCodebaseFileTreeItem,
  DevelopmentCodebaseWorkspaceResponse,
  DevelopmentCodebaseExportStatusResponse,
  DevelopmentCodebaseSnapshotStatusResponse,
  DevelopmentCodebaseAcceptanceResponse,
  DevelopmentCodebaseRepairResponse,
  DevelopmentCodebaseDeploymentStatusResponse,
  DevelopmentCodebaseDeploymentApplyResponse,
  DevelopmentCodebaseBindingStatusResponse,
  DevelopmentCodebaseRebindResponse,
  DevelopmentHandoffAsset,
  DevelopmentHandoffContextResponse,
  DevelopmentRuntimeLogsResponse,
  DevelopmentRuntimeStatusResponse,
  DevelopmentHandoffPackageResponse,
  DevelopmentHandoffReadinessResponse,
  DevelopmentPhase4AcceptanceResponse,
  DevelopmentPlanResponse,
  DevelopmentTargetStackResponse,
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

const showAdvancedDiagnostics = ref(false)

const handoffContext = ref<DevelopmentHandoffContextResponse | null>(null)
const contextLoading = ref(false)
const contextError = ref('')

const developmentPlan = ref<DevelopmentPlanResponse | null>(null)
const planLoading = ref(false)
const planError = ref('')

const planConfirmation = ref<ConfirmedDevelopmentPlanSnapshot | null>(null)
const planConfirmationLoading = ref(false)
const planConfirmationError = ref('')
const confirmingPlan = ref(false)

const handoffPackage = ref<DevelopmentHandoffPackageResponse | null>(null)
const handoffPackageLoading = ref(false)
const handoffPackageError = ref('')

const codeProject = ref<DevelopmentCodeProjectResponse | null>(null)
const codeProjectLoading = ref(false)
const codeProjectError = ref('')

const codeProjectMaterialization = ref<DevelopmentCodeMaterializationResponse | null>(null)
const codeProjectMaterializationLoading = ref(false)
const codeProjectMaterializationError = ref('')

const codebaseWorkspace = ref<DevelopmentCodebaseWorkspaceResponse | null>(null)
const codebaseWorkspaceLoading = ref(false)
const codebaseWorkspaceError = ref('')
const codebaseViewMode = ref<'node' | 'tree'>('node')
const selectedCodeFile = ref<DevelopmentCodebaseFileContentResponse | null>(null)
const codeFileLoading = ref(false)
const codeFileError = ref('')

const codebaseSnapshotStatus = ref<DevelopmentCodebaseSnapshotStatusResponse | null>(null)
const codebaseSnapshotLoading = ref(false)
const codebaseSnapshotError = ref('')

const codebaseExportStatus = ref<DevelopmentCodebaseExportStatusResponse | null>(null)
const codebaseExportLoading = ref(false)
const codebaseExportDownloading = ref(false)
const codebaseExportError = ref('')
const codebaseExportDownloadedFile = ref('')

const codebaseAcceptanceStatus = ref<DevelopmentCodebaseAcceptanceResponse | null>(null)
const codebaseAcceptanceLoading = ref(false)
const codebaseAcceptanceRunning = ref(false)
const codebaseAcceptanceError = ref('')

const codebaseRepairResult = ref<DevelopmentCodebaseRepairResponse | null>(null)
const codebaseRepairLoading = ref(false)
const codebaseRepairApplying = ref(false)
const codebaseRepairError = ref('')

const codebaseDeploymentStatus = ref<DevelopmentCodebaseDeploymentStatusResponse | null>(null)
const codebaseDeploymentResult = ref<DevelopmentCodebaseDeploymentApplyResponse | null>(null)
const codebaseDeploymentLoading = ref(false)
const codebaseDeploymentApplying = ref(false)
const codebaseDeploymentOverwrite = ref(false)
const codebaseDeploymentError = ref('')

const codebaseBindingStatus = ref<DevelopmentCodebaseBindingStatusResponse | null>(null)
const codebaseRebindResult = ref<DevelopmentCodebaseRebindResponse | null>(null)
const codebaseBindingLoading = ref(false)
const codebaseRebinding = ref(false)
const codebaseBindingError = ref('')
const codebaseRebindPath = ref('')

const runtimeStatus = ref<DevelopmentRuntimeStatusResponse | null>(null)
const runtimeLogs = ref<DevelopmentRuntimeLogsResponse | null>(null)
const runtimeLoading = ref(false)
const runtimeError = ref('')
const runtimeLogsLoading = ref(false)
const runtimeLogsError = ref('')
const runtimePreviewEnabled = ref(false)
const runtimePreviewKey = ref(0)
const runtimeDiagnosticCopied = ref(false)
let runtimePollTimer: number | null = null

const phase4Acceptance = ref<DevelopmentPhase4AcceptanceResponse | null>(null)
const phase4AcceptanceLoading = ref(false)
const phase4AcceptanceError = ref('')

const targetStacks = ref<DevelopmentTargetStackResponse[]>([])
const targetStackLoading = ref(false)
const targetStackError = ref('')
const selectedTargetStackCode = ref('NODEJS_FULLSTACK')

const agentModelRoutingStatus = ref<AgentModelRoutingGatewayResponse | null>(null)
const agentModelRoutingLoading = ref(false)
const agentModelRoutingError = ref('')


const agentModelRoutingPayload = computed(() => agentModelRoutingStatus.value?.status || null)

const requirementModelProfile = computed<AgentModelProfileResponse | null>(() =>
  agentModelRoutingPayload.value?.requirement?.default || null,
)

const developmentCodegenProfile = computed<AgentModelProfileResponse | null>(() =>
  agentModelRoutingPayload.value?.development?.codegen || null,
)

const developmentFastProfile = computed<AgentModelProfileResponse | null>(() =>
  agentModelRoutingPayload.value?.development?.fast || null,
)

const developmentDiagnosisProfile = computed<AgentModelProfileResponse | null>(() =>
  agentModelRoutingPayload.value?.development?.diagnosis || null,
)

const agentModelRoutingCardClass = computed(() => {
  if (!agentModelRoutingStatus.value) {
    return 'warning'
  }

  if (!agentModelRoutingStatus.value.available) {
    return 'blocked'
  }

  if (!developmentCodegenProfile.value?.apiKeyConfigured) {
    return 'warning'
  }

  return 'ready'
})

const readinessClass = computed(() => {
  if (!props.readiness) {
    return 'blocked'
  }

  if (
    props.readiness.readinessLevel === 'NOT_APPLICABLE' ||
    !props.readiness.directDevelopmentAllowed
  ) {
    return 'context'
  }

  if (props.readiness.readinessLevel === 'READY') {
    return 'ready'
  }

  if (props.readiness.readinessLevel === 'WARNING') {
    return 'warning'
  }

  return 'blocked'
})

const gateModeLabel = computed(() => {
  switch (props.readiness?.gateMode) {
    case 'MODULE_AGGREGATE':
      return '模块聚合开发'
    case 'PAGE_WITH_MODULE_INHERITANCE':
      return '页面继承开发'
    case 'CONTEXT_ONLY':
      return '上下文索引'
    case 'GROUP_ONLY':
      return '场景分组'
    case 'INLINE_ONLY':
      return '页面内联'
    default:
      return '开发范围'
  }
})

type DevNextStepKey =
  | 'INPUT_BLOCKED'
  | 'GENERATE_PLAN'
  | 'CONFIRM_PLAN'
  | 'GENERATE_HANDOFF_PACKAGE'
  | 'GENERATE_CODE_PROJECT'
  | 'MATERIALIZE_CODE'
  | 'INSTALL_DEPS'
  | 'START_RUNTIME'
  | 'RUN_ACCEPTANCE'

const codebaseExistsForUi = computed(() =>
  Boolean(
    codebaseWorkspace.value?.codebaseExists ||
    codebaseWorkspace.value?.selectedNodeHasCode ||
    codeProjectMaterialization.value?.writtenCount,
  ),
)

const advancedDiagnosticsDefaultOpen = computed(() =>
  showAdvancedDiagnostics.value ||
  codebaseExistsForUi.value ||
  Boolean(codebaseAcceptanceStatus.value?.executed) ||
  String(runtimeStatus.value?.runtimeStatus || '').toUpperCase() === 'RUNNING',
)

const nextStepKey = computed<DevNextStepKey>(() => {
  if (!props.readiness?.ready) {
    return 'INPUT_BLOCKED'
  }

  if (!developmentPlan.value) {
    return 'GENERATE_PLAN'
  }

  if (!isCurrentPlanConfirmed.value) {
    return 'CONFIRM_PLAN'
  }

  if (!handoffPackage.value) {
    return 'GENERATE_HANDOFF_PACKAGE'
  }

  if (!codeProject.value) {
    return 'GENERATE_CODE_PROJECT'
  }

  if (!codebaseExistsForUi.value) {
    return 'MATERIALIZE_CODE'
  }

  if (!runtimeStatus.value?.dependencyInstalled) {
    return 'INSTALL_DEPS'
  }

  if (runtimeStatus.value?.runtimeStatus !== 'RUNNING') {
    return 'START_RUNTIME'
  }

  return 'RUN_ACCEPTANCE'
})

const nextStepTitle = computed(() => {
  switch (nextStepKey.value) {
    case 'INPUT_BLOCKED':
      return '先处理开发输入阻断项'
    case 'GENERATE_PLAN':
      return '生成 Node.js 全栈应用计划'
    case 'CONFIRM_PLAN':
      return '确认开发实施计划'
    case 'GENERATE_HANDOFF_PACKAGE':
      return '生成 Development Handoff Package'
    case 'GENERATE_CODE_PROJECT':
      return '由 Development Codegen Agent 生成 Node.js 项目代码提案'
    case 'MATERIALIZE_CODE':
      return '生成项目主干并写入代码'
    case 'INSTALL_DEPS':
      return '安装生成项目依赖'
    case 'START_RUNTIME':
      return '启动生成项目'
    case 'RUN_ACCEPTANCE':
      return '运行验收流水线'
    default:
      return '继续开发流程'
  }
})

const nextStepDescription = computed(() => {
  switch (nextStepKey.value) {
    case 'INPUT_BLOCKED':
      return '当前节点仍存在真实阻断项。请先处理上方开发输入资产或确认快照问题。'
    case 'GENERATE_PLAN':
      return '开发输入已就绪。下一步先把需求资产、页面契约和目标技术栈固化为可确认的开发计划。'
    case 'CONFIRM_PLAN':
      return '代码生成前必须由用户确认开发计划，避免后续 Dev Agent 按未确认方案施工。'
    case 'GENERATE_HANDOFF_PACKAGE':
      return '确认计划后，生成给 Development Agent 使用的交接包，固定资产版本、UI_CONTRACT 快照和施工上下文。'
    case 'GENERATE_CODE_PROJECT':
      return '交接包准备后，生成 Node.js 全栈项目代码提案，先预览文件清单，再落盘。'
    case 'MATERIALIZE_CODE':
      return '代码提案已经生成。现在可以写入 generated/nodejs，创建项目主干、package.json 和 manifest。'
    case 'INSTALL_DEPS':
      return '代码库已经存在。先安装依赖，再启动前后端开发服务。'
    case 'START_RUNTIME':
      return '依赖已安装或可启动。启动项目后可以进入平台内预览和验收。'
    case 'RUN_ACCEPTANCE':
      return '项目正在运行。现在可以执行安装、构建、测试和健康检查验收流水线。'
    default:
      return '按阶段推进 Development Agent。'
  }
})

const nextStepBadge = computed(() => {
  switch (nextStepKey.value) {
    case 'INPUT_BLOCKED':
      return 'BLOCKED'
    case 'GENERATE_PLAN':
      return 'STEP 1 / PLAN'
    case 'CONFIRM_PLAN':
      return 'STEP 2 / CONFIRM'
    case 'GENERATE_HANDOFF_PACKAGE':
      return 'STEP 3 / PACKAGE'
    case 'GENERATE_CODE_PROJECT':
      return 'STEP 4 / PROPOSAL'
    case 'MATERIALIZE_CODE':
      return 'STEP 5 / WRITE'
    case 'INSTALL_DEPS':
      return 'STEP 6 / INSTALL'
    case 'START_RUNTIME':
      return 'STEP 7 / RUN'
    case 'RUN_ACCEPTANCE':
      return 'STEP 8 / ACCEPT'
    default:
      return 'NEXT'
  }
})

const nextStepTone = computed(() => {
  if (nextStepKey.value === 'INPUT_BLOCKED') {
    return 'blocked'
  }

  if (['INSTALL_DEPS', 'START_RUNTIME', 'RUN_ACCEPTANCE'].includes(nextStepKey.value)) {
    return 'runtime'
  }

  if (nextStepKey.value === 'MATERIALIZE_CODE') {
    return 'code'
  }

  return 'ready'
})

const nextStepMetaItems = computed(() => [
  {
    label: '开发输入',
    value: props.readiness?.ready ? '已就绪' : '待处理',
    hint: props.readiness?.readinessText || '-',
  },
  {
    label: '开发计划',
    value: isCurrentPlanConfirmed.value
      ? '已确认'
      : developmentPlan.value
        ? '待确认'
        : '未生成',
    hint: developmentPlan.value?.targetStackLabel || selectedTargetStack.value?.displayName || 'Node.js 全栈应用',
  },
  {
    label: '交接包',
    value: handoffPackage.value ? '已生成' : '待生成',
    hint: handoffPackage.value?.packageStatus || '确认计划后生成',
  },
  {
    label: '代码库',
    value: codebaseExistsForUi.value ? '已初始化' : '未初始化',
    hint: codebaseWorkspace.value?.codebaseRoot || 'generated/nodejs/{projectCode}/app',
  },
])

const canPreviewUiContract = computed(() =>
  props.readiness?.nodeType === 'PAGE' &&
  props.readiness.assets.some(
    (asset: DevelopmentHandoffAsset) =>
      asset.artifactType === 'UI_CONTRACT' && asset.exists,
  ),
)

const canUseDevelopmentApi = computed(() =>
  Boolean(
    props.workspaceId &&
    props.projectCode &&
    props.selectedModule?.id,
  ),
)

const selectedTargetStack = computed(() =>
  targetStacks.value.find(
    (stack) => stack.stackCode === selectedTargetStackCode.value,
  ) || null,
)

const canGeneratePlan = computed(() =>
  canUseDevelopmentApi.value &&
  Boolean(props.readiness?.directDevelopmentAllowed) &&
  Boolean(props.readiness?.ready) &&
  Boolean(selectedTargetStack.value?.available),
)

const generatePlanButtonText = computed(() => {
  if (planLoading.value) {
    return '生成中...'
  }

  return `生成${selectedTargetStack.value?.displayName || '开发'}计划`
})

const isCurrentPlanConfirmed = computed(() =>
  Boolean(
    developmentPlan.value?.planChecksum &&
    planConfirmation.value?.planChecksum &&
    developmentPlan.value.planChecksum === planConfirmation.value.planChecksum,
  ),
)

const canGenerateHandoffPackage = computed(() =>
  canUseDevelopmentApi.value &&
  Boolean(props.readiness?.directDevelopmentAllowed) &&
  Boolean(planConfirmation.value?.id) &&
  (
    !developmentPlan.value ||
    isCurrentPlanConfirmed.value
  ),
)

const canGenerateCodeProject = computed(() =>
  canUseDevelopmentApi.value &&
  Boolean(props.readiness?.directDevelopmentAllowed) &&
  Boolean(planConfirmation.value?.id) &&
  Boolean(selectedTargetStack.value?.available) &&
  (
    !developmentPlan.value ||
    isCurrentPlanConfirmed.value
  ),
)
const canMaterializeCodeProject = computed(() =>
  canGenerateCodeProject.value &&
  Boolean(codeProject.value),
)


const canGenerateCodeFromWorkspace = computed(() =>
  canGenerateCodeProject.value &&
  Boolean(codebaseWorkspace.value?.selectedNodeCanGenerate) &&
  Boolean(codebaseWorkspace.value?.ready),
)

const codebaseWorkspaceTitle = computed(() => {
  if (!codebaseWorkspace.value) {
    return '代码工作区'
  }

  if (codebaseWorkspace.value.selectedNodeHasCode) {
    return '当前节点已有代码贡献'
  }

  if (!codebaseWorkspace.value.ready) {
    return '开发准备度未就绪'
  }

  if (!codebaseWorkspace.value.selectedNodeCanGenerate) {
    return '当前节点作为开发上下文使用'
  }

  return codebaseWorkspace.value.codebaseExists ? '可生成当前节点代码贡献' : '可初始化项目主干并生成当前节点代码'
})

const codebaseStatusClass = computed(() => {
  if (!codebaseWorkspace.value) {
    return 'pending'
  }

  if (codebaseWorkspace.value.selectedNodeHasCode || codebaseWorkspace.value.codebaseExists) {
    return 'ready'
  }

  if (codebaseWorkspace.value.ready && codebaseWorkspace.value.selectedNodeCanGenerate) {
    return 'pending'
  }

  return 'blocked'
})

const flatCodebaseFiles = computed(() => {
  const result: Array<DevelopmentCodebaseFileTreeItem & { depth: number }> = []

  function walk(items: DevelopmentCodebaseFileTreeItem[], depth: number) {
    for (const item of items || []) {
      result.push({ ...item, depth })

      if (item.children?.length) {
        walk(item.children, depth + 1)
      }
    }
  }

  walk(codebaseWorkspace.value?.fileTree || [], 0)
  return result
})

const runtimeStatusClass = computed(() => {
  if (!runtimeStatus.value || !runtimeStatus.value.codebaseExists) {
    return 'pending'
  }

  if (runtimeStatus.value.runtimeStatus === 'RUNNING') {
    return 'ready'
  }

  if (
    runtimeStatus.value.runtimeStatus === 'EXITED' ||
    runtimeStatus.value.runtimeStatus === 'START_FAILED' ||
    runtimeStatus.value.installStatus === 'INSTALL_FAILED'
  ) {
    return 'blocked'
  }

  return 'warning'
})

const canInstallRuntime = computed(() =>
  canUseDevelopmentApi.value &&
  Boolean(runtimeStatus.value?.codebaseExists) &&
  Boolean(runtimeStatus.value?.packageJsonExists) &&
  runtimeStatus.value?.runtimeStatus !== 'RUNNING' &&
  runtimeStatus.value?.installStatus !== 'INSTALLING' &&
  !runtimeLoading.value,
)

const canStartRuntime = computed(() =>
  canUseDevelopmentApi.value &&
  Boolean(runtimeStatus.value?.codebaseExists) &&
  Boolean(runtimeStatus.value?.packageJsonExists) &&
  runtimeStatus.value?.runtimeStatus !== 'RUNNING' &&
  runtimeStatus.value?.installStatus !== 'INSTALLING' &&
  !runtimeLoading.value,
)

const canStopRuntime = computed(() =>
  canUseDevelopmentApi.value &&
  runtimeStatus.value?.runtimeStatus === 'RUNNING' &&
  !runtimeLoading.value,
)

const runtimeHealthClass = computed(() => {
  if (!runtimeStatus.value) {
    return 'not_checked'
  }

  return String(runtimeStatus.value.healthStatus || 'NOT_CHECKED').toLowerCase()
})

const runtimeDiagnosticClass = computed(() => {
  const level = String(runtimeStatus.value?.diagnosticLevel || 'INFO').toLowerCase()

  if (level === 'error') {
    return 'blocked'
  }

  if (level === 'warning') {
    return 'warning'
  }

  if (level === 'pass') {
    return 'ready'
  }

  return 'info'
})


const canDownloadCodebaseZip = computed(() =>
  Boolean(codebaseExportStatus.value?.exportable) &&
  !codebaseExportLoading.value &&
  !codebaseExportDownloading.value,
)


const codebaseExportClass = computed(() => {
  if (!codebaseExportStatus.value || !codebaseExportStatus.value.codebaseExists) {
    return 'pending'
  }

  if (codebaseExportStatus.value.exportable) {
    return 'ready'
  }

  return codebaseExportStatus.value.blockers?.length ? 'blocked' : 'warning'
})

const codebaseBindingClass = computed(() => {
  const status = String(codebaseBindingStatus.value?.bindingStatus || 'NOT_BOUND').toLowerCase()

  if (status === 'bound_ready') {
    return 'ready'
  }

  if (!codebaseExistsForUi.value && status === 'not_bound') {
    return 'pending'
  }

  if (status === 'bound_warning' || status === 'unregistered_local_exists') {
    return 'warning'
  }

  return 'blocked'
})

const canRebindCodebase = computed(() =>
  canUseDevelopmentApi.value &&
  Boolean(codebaseRebindPath.value.trim()) &&
  !codebaseBindingLoading.value &&
  !codebaseRebinding.value,
)

const codebaseAcceptanceClass = computed(() => {
  const status = String(codebaseAcceptanceStatus.value?.acceptanceStatus || 'NOT_READY').toLowerCase()

  if (!codebaseAcceptanceStatus.value?.codebaseExists && !codebaseAcceptanceStatus.value?.executed) {
    return 'pending'
  }

  if (status === 'pass') {
    return 'ready'
  }

  if (status === 'ready' || status === 'warning') {
    return 'warning'
  }

  return 'blocked'
})

const canRunCodebaseAcceptance = computed(() =>
  canUseDevelopmentApi.value &&
  Boolean(codebaseAcceptanceStatus.value?.readyToRun) &&
  !codebaseAcceptanceLoading.value &&
  !codebaseAcceptanceRunning.value,
)

const canRepairCodebaseAcceptance = computed(() =>
  canUseDevelopmentApi.value &&
  Boolean(props.selectedModule?.id) &&
  Boolean(codebaseAcceptanceStatus.value?.executed) &&
  ['FAILED', 'WARNING'].includes(String(codebaseAcceptanceStatus.value?.acceptanceStatus || '').toUpperCase()) &&
  !codebaseRepairLoading.value &&
  !codebaseRepairApplying.value,
)

const codebaseDeploymentClass = computed(() => {
  const status = String(codebaseDeploymentStatus.value?.deploymentStatus || 'BLOCKED').toLowerCase()

  if (!codebaseDeploymentStatus.value?.codebaseExists) {
    return 'pending'
  }

  if (status === 'ready') {
    return 'ready'
  }

  if (status === 'not_generated' || status === 'needs_update') {
    return 'warning'
  }

  return 'blocked'
})

const canApplyCodebaseDeployment = computed(() =>
  canUseDevelopmentApi.value &&
  Boolean(codebaseDeploymentStatus.value?.deployable) &&
  !codebaseDeploymentLoading.value &&
  !codebaseDeploymentApplying.value,
)

const runtimePreviewUrl = computed(() => {
  const base = runtimeStatus.value?.previewUrl || runtimeStatus.value?.frontendUrl

  if (!base) {
    return ''
  }

  const route = codebaseWorkspace.value?.previewRoute || ''

  if (!route) {
    return base
  }

  try {
    return new URL(route, base).toString()
  } catch {
    return `${base.replace(/\/$/, '')}/${route.replace(/^\//, '')}`
  }
})

const shouldPollRuntime = computed(() =>
  runtimeStatus.value?.runtimeStatus === 'RUNNING' ||
  runtimeStatus.value?.installStatus === 'INSTALLING',
)


const planConfirmationStatusText = computed(() => {
  if (!planConfirmation.value) {
    return '当前节点尚未确认开发实施计划。生成计划并确认后，后续真实代码生成 Agent 才能读取该计划。'
  }

  if (!developmentPlan.value) {
    return `已有确认计划，确认人 ${planConfirmation.value.confirmedBy || '-'}，确认时间 ${formatTime(planConfirmation.value.confirmedAt)}。`
  }

  if (isCurrentPlanConfirmed.value) {
    return '当前页面展示的 Development Plan 与已确认快照一致，可作为后续代码生成依据。'
  }

  return '当前 Development Plan 与已确认快照不一致，说明计划已重新生成或输入资产发生变化，需要重新确认。'
})

const snapshotStatusText = computed(() => {
  const snapshot = props.readiness?.uiContractSnapshot

  if (!snapshot?.exists) {
    return '尚未保存用户确认快照。请先打开交互预览，确认当前页面效果。'
  }

  if (!snapshot.matchesCurrentArtifact) {
    return '确认快照已滞后。当前 UI_CONTRACT 更新后，需要重新预览并确认。'
  }

  return `已确认：${snapshot.pageName || '-'}，版本 v${snapshot.artifactVersionNo || '-'}，确认时间 ${formatTime(snapshot.confirmedAt)}。`
})

watch(
  () => [
    props.workspaceId,
    props.projectCode,
    props.selectedModule?.id,
  ],
  async () => {
    handoffContext.value = null
    developmentPlan.value = null
    contextError.value = ''
    planError.value = ''
    planConfirmation.value = null
    planConfirmationError.value = ''
    handoffPackage.value = null
    handoffPackageError.value = ''
    codeProject.value = null
    codeProjectError.value = ''
    codeProjectMaterialization.value = null
    codeProjectMaterializationError.value = ''
    codebaseWorkspace.value = null
    codebaseWorkspaceError.value = ''
    selectedCodeFile.value = null
    codeFileError.value = ''
    codebaseSnapshotStatus.value = null
    codebaseSnapshotError.value = ''
    codebaseExportStatus.value = null
    codebaseExportError.value = ''
    codebaseExportDownloadedFile.value = ''
    codebaseAcceptanceStatus.value = null
    codebaseAcceptanceError.value = ''
    codebaseRepairResult.value = null
    codebaseRepairError.value = ''
    codebaseDeploymentStatus.value = null
    codebaseDeploymentResult.value = null
    codebaseDeploymentError.value = ''
    codebaseBindingStatus.value = null
    codebaseRebindResult.value = null
    codebaseBindingError.value = ''
    codebaseRebindPath.value = ''
    runtimeStatus.value = null
    runtimeLogs.value = null
    runtimeError.value = ''
    runtimeLogsError.value = ''
    runtimePreviewEnabled.value = false
    stopRuntimePolling()
    phase4Acceptance.value = null
    phase4AcceptanceError.value = ''
    targetStacks.value = []
    targetStackError.value = ''
    agentModelRoutingStatus.value = null
    agentModelRoutingError.value = ''

    await loadTargetStacks()
    await loadAgentModelRoutingStatus()
    await loadCurrentPlanConfirmation()
    await loadPhase4Acceptance()
    await loadCodebaseWorkspace()
    await loadCodebaseBindingStatus()
    await loadCodebaseSnapshotStatus()
    await loadCodebaseExportStatus()
    await loadCodebaseAcceptanceStatus()
    await loadCodebaseDeploymentStatus()
    await loadRuntimeStatus()
  },
  {
    immediate: true,
  },
)

watch(
  () => [runtimeStatus.value?.runtimeStatus, runtimeStatus.value?.installStatus],
  () => {
    reconcileRuntimePolling()
  },
)

onBeforeUnmount(() => {
  stopRuntimePolling()
})


async function loadAgentModelRoutingStatus() {
  agentModelRoutingLoading.value = true
  agentModelRoutingError.value = ''

  try {
    agentModelRoutingStatus.value = await fetchAgentModelRoutingStatus()
  } catch (error) {
    agentModelRoutingStatus.value = null
    agentModelRoutingError.value = resolveErrorMessage(
      error,
      '读取 Agent 模型路由失败。',
    )
  } finally {
    agentModelRoutingLoading.value = false
  }
}

function modelProfileLabel(profile?: AgentModelProfileResponse | null): string {
  if (!profile) {
    return '-'
  }

  return `${profile.provider} / ${profile.model}`
}

async function loadCodebaseWorkspace(options: { silent?: boolean } = {}) {
  const silent = options.silent === true
  if (!canUseDevelopmentApi.value || !props.selectedModule?.id) {
    codebaseWorkspace.value = null
    selectedCodeFile.value = null
    return
  }

  if (!silent) {
    codebaseWorkspaceLoading.value = true
  }
  codebaseWorkspaceError.value = ''

  try {
    codebaseWorkspace.value = await fetchDevelopmentCodebaseWorkspace(
      props.workspaceId || '',
      props.projectCode || '',
      props.selectedModule.id,
      selectedTargetStackCode.value,
    )

    if (codebaseWorkspace.value.selectedNodeHasCode) {
      codebaseViewMode.value = 'node'
    }

    const defaultFile =
      codebaseWorkspace.value.nodeFiles.find((file) => file.exists)?.filePath ||
      codebaseWorkspace.value.defaultFilePath

    if (defaultFile) {
      await openCodeFile(defaultFile)
    } else {
      selectedCodeFile.value = null
    }
  } catch (error) {
    codebaseWorkspace.value = null
    selectedCodeFile.value = null
    codebaseWorkspaceError.value = resolveErrorMessage(
      error,
      '读取生成代码工作区失败。',
    )
  } finally {
    if (!silent) {
      codebaseWorkspaceLoading.value = false
    }
  }
}

async function openCodeFile(filePath: string) {
  if (!canUseDevelopmentApi.value || !props.selectedModule?.id || !filePath) {
    return
  }

  codeFileLoading.value = true
  codeFileError.value = ''

  try {
    selectedCodeFile.value = await fetchDevelopmentCodebaseFileContent(
      props.workspaceId || '',
      props.projectCode || '',
      props.selectedModule.id,
      filePath,
      selectedTargetStackCode.value,
    )
  } catch (error) {
    selectedCodeFile.value = null
    codeFileError.value = resolveErrorMessage(
      error,
      '读取代码文件失败。',
    )
  } finally {
    codeFileLoading.value = false
  }
}

function compactPath(filePath?: string | null): string {
  if (!filePath) {
    return '-'
  }

  const parts = filePath.split('/').filter(Boolean)
  return parts.slice(Math.max(0, parts.length - 3)).join('/')
}

async function loadTargetStacks() {
  if (!canUseDevelopmentApi.value || !props.selectedModule?.id) {
    targetStacks.value = []
    return
  }

  targetStackLoading.value = true
  targetStackError.value = ''

  try {
    targetStacks.value = await fetchDevelopmentTargetStacks(
      props.workspaceId || '',
      props.projectCode || '',
      props.selectedModule.id,
    )

    const currentStack = targetStacks.value.find(
      (stack) => stack.stackCode === selectedTargetStackCode.value && stack.available,
    )

    if (!currentStack) {
      selectedTargetStackCode.value =
        targetStacks.value.find((stack) => stack.available)?.stackCode || 'NODEJS_FULLSTACK'
    }
  } catch (error) {
    targetStacks.value = []
    targetStackError.value = resolveErrorMessage(
      error,
      '读取目标技术栈失败。',
    )
  } finally {
    targetStackLoading.value = false
  }
}

async function loadPhase4Acceptance() {
  if (!canUseDevelopmentApi.value || !props.selectedModule?.id) {
    phase4Acceptance.value = null
    return
  }

  phase4AcceptanceLoading.value = true
  phase4AcceptanceError.value = ''

  try {
    phase4Acceptance.value = await fetchDevelopmentPhase4Acceptance(
      props.workspaceId || '',
      props.projectCode || '',
      props.selectedModule.id,
    )
  } catch (error) {
    phase4Acceptance.value = null
    phase4AcceptanceError.value = resolveErrorMessage(
      error,
      '读取 Phase 4 验收状态失败。',
    )
  } finally {
    phase4AcceptanceLoading.value = false
  }
}

async function handleLoadContext() {
  if (!canUseDevelopmentApi.value || !props.selectedModule?.id) {
    contextError.value = '缺少 workspaceId、projectCode 或当前节点 ID，无法读取开发上下文。'
    return
  }

  contextLoading.value = true
  contextError.value = ''

  try {
    handoffContext.value = await fetchDevelopmentHandoffContext(
      props.workspaceId || '',
      props.projectCode || '',
      props.selectedModule.id,
    )
  } catch (error) {
    contextError.value = resolveErrorMessage(
      error,
      '读取开发上下文失败。',
    )
  } finally {
    contextLoading.value = false
  }
}

async function handleGeneratePlan() {
  if (!canGeneratePlan.value || !props.selectedModule?.id) {
    planError.value = '当前节点尚未通过开发交接门禁，不能生成开发实施计划。'
    return
  }

  planLoading.value = true
  planError.value = ''

  try {
    developmentPlan.value = await generateDevelopmentPlan(
      props.workspaceId || '',
      props.projectCode || '',
      props.selectedModule.id,
      {
        planScope: 'CURRENT_NODE',
        targetStack: selectedTargetStackCode.value,
        remarks: '基于 DEV_HANDOFF_PACKAGE v2 生成语言无关 Dev Blueprint 对应的开发实施计划。',
      },
    )

    handoffContext.value = developmentPlan.value.handoffContext
    await loadCurrentPlanConfirmation()
  } catch (error) {
    planError.value = resolveErrorMessage(
      error,
      '生成开发实施计划失败。',
    )
  } finally {
    planLoading.value = false
  }
}

async function loadCurrentPlanConfirmation() {
  if (!canUseDevelopmentApi.value || !props.selectedModule?.id) {
    planConfirmation.value = null
    return
  }

  planConfirmationLoading.value = true
  planConfirmationError.value = ''

  try {
    planConfirmation.value = await fetchCurrentDevelopmentPlanConfirmation(
      props.workspaceId || '',
      props.projectCode || '',
      props.selectedModule.id,
    )
  } catch (error) {
    planConfirmation.value = null
    planConfirmationError.value = resolveErrorMessage(
      error,
      '读取开发实施计划确认快照失败。',
    )
  } finally {
    planConfirmationLoading.value = false
  }
}

async function handleConfirmPlan() {
  if (!developmentPlan.value || !props.selectedModule?.id) {
    planConfirmationError.value = '请先生成开发实施计划，再确认。'
    return
  }

  const confirmationNote = window.prompt(
    '确认该开发实施计划后，后续代码生成 Agent 将以此计划作为施工依据。可填写确认备注：',
    '',
  )

  if (confirmationNote === null) {
    return
  }

  confirmingPlan.value = true
  planConfirmationError.value = ''

  try {
    planConfirmation.value = await confirmDevelopmentPlanSnapshot(
      props.workspaceId || '',
      props.projectCode || '',
      props.selectedModule.id,
      {
        plan: developmentPlan.value as unknown as Record<string, unknown>,
        planChecksum: developmentPlan.value.planChecksum || '',
        confirmationNote,
      },
    )

    await loadPhase4Acceptance()
  } catch (error) {
    planConfirmationError.value = resolveErrorMessage(
      error,
      '确认开发实施计划失败。',
    )
  } finally {
    confirmingPlan.value = false
  }
}

async function handleGenerateHandoffPackage() {
  if (!canGenerateHandoffPackage.value || !props.selectedModule?.id) {
    handoffPackageError.value = '请先确认开发实施计划，再生成 Development Agent 交接包。'
    return
  }

  handoffPackageLoading.value = true
  handoffPackageError.value = ''

  try {
    handoffPackage.value = await generateDevelopmentHandoffPackage(
      props.workspaceId || '',
      props.projectCode || '',
      props.selectedModule.id,
    )

    await loadPhase4Acceptance()
  } catch (error) {
    handoffPackageError.value = resolveErrorMessage(
      error,
      '生成 Development Agent 交接包失败。',
    )
  } finally {
    handoffPackageLoading.value = false
  }
}

async function handleGenerateCodeProject() {
  if (!canGenerateCodeProject.value || !props.selectedModule?.id) {
    codeProjectError.value = '请先确认开发实施计划，再由 Development Codegen Agent 生成 Node.js 项目代码提案。'
    return
  }

  codeProjectLoading.value = true
  codeProjectError.value = ''
  codeProjectMaterialization.value = null
  codeProjectMaterializationError.value = ''

  try {
    codeProject.value = await withUiTimeout(
      generateDevelopmentCodeProject(
        props.workspaceId || '',
        props.projectCode || '',
        props.selectedModule.id,
        {
          planScope: 'CURRENT_NODE',
          targetStack: selectedTargetStackCode.value,
          remarks: '基于已确认 Development Plan 和 DEV_HANDOFF_PACKAGE v2 生成 Node.js 全栈项目代码提案。',
        },
      ),
      330_000,
      'Development Codegen Agent 超过 330 秒仍未返回，已释放按钮。系统不会回退为模板假代码；请查看模型路由、Key、额度、Python Agent 日志后重试。',
    )
  } catch (error) {
    codeProject.value = null
    codeProjectError.value = resolveErrorMessage(
      error,
      '由 Development Codegen Agent 生成 Node.js 项目代码提案失败。',
    )
  } finally {
    codeProjectLoading.value = false
  }
}

async function handleMaterializeCodeProject(
  allowOverwrite: boolean,
) {
  if (!canMaterializeCodeProject.value || !props.selectedModule?.id) {
    codeProjectMaterializationError.value = '请先由 Development Codegen Agent 生成 Node.js 项目代码提案，再落盘到 generated/nodejs 目录。'
    return
  }

  if (allowOverwrite) {
    const confirmed = window.confirm(
      '确认覆盖写入 generated/nodejs 下已存在的同名文件？该动作不会覆盖主工程源码，但会覆盖生成目录里的同名文件。',
    )

    if (!confirmed) {
      return
    }
  }

  codeProjectMaterializationLoading.value = true
  codeProjectMaterializationError.value = ''

  try {
    codeProjectMaterialization.value = await materializeDevelopmentCodeProject(
      props.workspaceId || '',
      props.projectCode || '',
      props.selectedModule.id,
      {
        planScope: 'CURRENT_NODE',
        targetStack: selectedTargetStackCode.value,
        remarks: '将已生成的 Node.js 全栈项目代码提案写入本地 generated/nodejs 目录。',
        allowOverwrite,
      },
    )

    if (codeProjectMaterialization.value.codeProject) {
      codeProject.value = codeProjectMaterialization.value.codeProject
    }

    await loadCodebaseWorkspace()
    await loadCodebaseBindingStatus()
    await loadCodebaseSnapshotStatus()
    await loadCodebaseExportStatus()
    await loadCodebaseAcceptanceStatus()
    await loadCodebaseDeploymentStatus()
    await loadRuntimeStatus()
  } catch (error) {
    codeProjectMaterialization.value = null
    codeProjectMaterializationError.value = resolveErrorMessage(
      error,
      '写入 Node.js 项目代码失败。',
    )
  } finally {
    codeProjectMaterializationLoading.value = false
  }
}




async function loadCodebaseBindingStatus(options: { silent?: boolean } = {}) {
  if (!canUseDevelopmentApi.value || !props.selectedModule?.id) {
    codebaseBindingStatus.value = null
    return
  }

  if (!options.silent) {
    codebaseBindingLoading.value = true
    codebaseBindingError.value = ''
  }

  try {
    codebaseBindingStatus.value = await fetchDevelopmentCodebaseBindingStatus(
      props.workspaceId || '',
      props.projectCode || '',
      props.selectedModule.id,
      selectedTargetStackCode.value,
    )

    if (!codebaseRebindPath.value.trim()) {
      codebaseRebindPath.value =
        codebaseBindingStatus.value.suggestedRelativeRootPath ||
        codebaseBindingStatus.value.codebaseRegistry?.relativeRootPath ||
        codebaseWorkspace.value?.codebaseRegistry?.relativeRootPath ||
        ''
    }
  } catch (error) {
    codebaseBindingStatus.value = null
    codebaseBindingError.value = resolveErrorMessage(error, '读取代码库绑定状态失败。')
  } finally {
    if (!options.silent) {
      codebaseBindingLoading.value = false
    }
  }
}

function useSuggestedCodebaseBindingPath() {
  codebaseRebindPath.value =
    codebaseBindingStatus.value?.suggestedRelativeRootPath ||
    codebaseWorkspace.value?.codebaseRegistry?.relativeRootPath ||
    codebaseWorkspace.value?.codebaseRegistry?.platformRootPath ||
    ''
}

async function handleRebindCodebase() {
  if (!canRebindCodebase.value || !props.selectedModule?.id) {
    return
  }

  const confirmed = window.confirm(
    '确认重新绑定当前项目的生成代码库目录？该操作不会移动或覆盖代码文件，只会更新数据库登记和模块树索引。',
  )

  if (!confirmed) {
    return
  }

  codebaseRebinding.value = true
  codebaseBindingError.value = ''
  codebaseRebindResult.value = null

  try {
    codebaseRebindResult.value = await rebindDevelopmentCodebase(
      props.workspaceId || '',
      props.projectCode || '',
      props.selectedModule.id,
      {
        targetStack: selectedTargetStackCode.value,
        relativeRootPath: codebaseRebindPath.value.trim(),
        remarks: '用户在 Dev Codebase Workspace 中手动重新绑定生成代码库目录。',
      },
    )

    await loadCodebaseBindingStatus({ silent: true })
    await loadCodebaseWorkspace()
    await loadCodebaseSnapshotStatus({ silent: true })
    await loadCodebaseExportStatus({ silent: true })
    await loadCodebaseAcceptanceStatus({ silent: true })
    await loadCodebaseDeploymentStatus({ silent: true })
    await loadRuntimeStatus({ silent: true })
  } catch (error) {
    codebaseBindingError.value = resolveErrorMessage(error, '重新绑定代码库失败。')
  } finally {
    codebaseRebinding.value = false
  }
}

async function loadCodebaseSnapshotStatus(options: { silent?: boolean } = {}) {
  if (!canUseDevelopmentApi.value || !props.selectedModule?.id) {
    codebaseSnapshotStatus.value = null
    return
  }

  if (!options.silent) {
    codebaseSnapshotLoading.value = true
    codebaseSnapshotError.value = ''
  }

  try {
    codebaseSnapshotStatus.value = await fetchDevelopmentCodebaseSnapshotStatus(
      props.workspaceId || '',
      props.projectCode || '',
      props.selectedModule.id,
      selectedTargetStackCode.value,
    )
  } catch (error) {
    codebaseSnapshotStatus.value = null
    codebaseSnapshotError.value = resolveErrorMessage(error, '读取代码快照状态失败。')
  } finally {
    if (!options.silent) {
      codebaseSnapshotLoading.value = false
    }
  }
}

function safetyChangeClass(changeType?: string | null): string {
  return String(changeType || 'unknown').toLowerCase().replace(/_/g, '-')
}


async function loadCodebaseAcceptanceStatus(options: { silent?: boolean } = {}) {
  if (!canUseDevelopmentApi.value || !props.selectedModule?.id) {
    codebaseAcceptanceStatus.value = null
    return
  }

  if (!options.silent) {
    codebaseAcceptanceLoading.value = true
    codebaseAcceptanceError.value = ''
  }

  try {
    codebaseAcceptanceStatus.value = await fetchDevelopmentCodebaseAcceptanceStatus(
      props.workspaceId || '',
      props.projectCode || '',
      props.selectedModule.id,
      selectedTargetStackCode.value,
    )
  } catch (error) {
    codebaseAcceptanceStatus.value = null
    codebaseAcceptanceError.value = resolveErrorMessage(error, '读取代码验收状态失败。')
  } finally {
    if (!options.silent) {
      codebaseAcceptanceLoading.value = false
    }
  }
}

async function handleRunCodebaseAcceptance() {
  if (!canUseDevelopmentApi.value || !props.selectedModule?.id) {
    return
  }

  codebaseAcceptanceRunning.value = true
  codebaseAcceptanceError.value = ''

  try {
    codebaseAcceptanceStatus.value = await runDevelopmentCodebaseAcceptance(
      props.workspaceId || '',
      props.projectCode || '',
      props.selectedModule.id,
      {
        targetStack: selectedTargetStackCode.value,
        installDependencies: true,
        buildProject: true,
        runTests: true,
        checkRuntimeHealth: true,
      },
    )

    await loadCodebaseExportStatus({ silent: true })
    await loadCodebaseDeploymentStatus({ silent: true })
    await loadRuntimeStatus({ silent: true })
  } catch (error) {
    codebaseAcceptanceError.value = resolveErrorMessage(error, '运行代码验收流水线失败。')
  } finally {
    codebaseAcceptanceRunning.value = false
  }
}


async function handleCodebaseRepair(apply: boolean) {
  if (!canUseDevelopmentApi.value || !props.selectedModule?.id || !codebaseAcceptanceStatus.value) {
    codebaseRepairError.value = '请先运行验收流水线，失败后再生成修复提案。'
    return
  }

  if (apply) {
    codebaseRepairApplying.value = true
  } else {
    codebaseRepairLoading.value = true
  }
  codebaseRepairError.value = ''

  const payload = {
    targetStack: selectedTargetStackCode.value,
    allowOverwrite: false,
    acceptance: codebaseAcceptanceStatus.value,
    extraLogLines: [],
    remarks: apply ? 'APPLY_ACCEPTANCE_REPAIR' : 'PROPOSE_ACCEPTANCE_REPAIR',
  }

  try {
    codebaseRepairResult.value = apply
      ? await applyDevelopmentCodebaseRepair(
          props.workspaceId || '',
          props.projectCode || '',
          props.selectedModule.id,
          payload,
        )
      : await proposeDevelopmentCodebaseRepair(
          props.workspaceId || '',
          props.projectCode || '',
          props.selectedModule.id,
          payload,
        )

    if (codebaseRepairResult.value.repairCodeProject) {
      codeProject.value = codebaseRepairResult.value.repairCodeProject
    }
    if (codebaseRepairResult.value.materialization) {
      codeProjectMaterialization.value = codebaseRepairResult.value.materialization
      await loadCodebaseWorkspace({ silent: true })
      await loadCodebaseSnapshotStatus({ silent: true })
      await loadCodebaseExportStatus({ silent: true })
      await loadCodebaseAcceptanceStatus({ silent: true })
    }
  } catch (error) {
    codebaseRepairError.value = resolveErrorMessage(error, apply ? '应用自动修复失败。' : '生成自动修复提案失败。')
  } finally {
    codebaseRepairLoading.value = false
    codebaseRepairApplying.value = false
  }
}

function acceptanceItemClass(status?: string | null): string {
  return String(status || 'unknown').toLowerCase().replace(/_/g, '-')
}


async function loadCodebaseDeploymentStatus(options: { silent?: boolean } = {}) {
  if (!canUseDevelopmentApi.value || !props.selectedModule?.id) {
    codebaseDeploymentStatus.value = null
    return
  }

  if (!options.silent) {
    codebaseDeploymentLoading.value = true
    codebaseDeploymentError.value = ''
  }

  try {
    codebaseDeploymentStatus.value = await fetchDevelopmentCodebaseDeploymentStatus(
      props.workspaceId || '',
      props.projectCode || '',
      props.selectedModule.id,
      selectedTargetStackCode.value,
    )
  } catch (error) {
    codebaseDeploymentStatus.value = null
    codebaseDeploymentError.value = resolveErrorMessage(error, '读取部署配置状态失败。')
  } finally {
    if (!options.silent) {
      codebaseDeploymentLoading.value = false
    }
  }
}

async function handleApplyCodebaseDeployment() {
  if (!canApplyCodebaseDeployment.value || !props.selectedModule?.id) {
    return
  }

  if (codebaseDeploymentOverwrite.value) {
    const confirmed = window.confirm(
      '确认覆盖更新已有部署配置文件？该操作只影响生成代码库中的 Dockerfile、docker-compose.yml、.env.example 等部署文件，不会修改 CodeMind 平台源码。',
    )

    if (!confirmed) {
      return
    }
  }

  codebaseDeploymentApplying.value = true
  codebaseDeploymentError.value = ''
  codebaseDeploymentResult.value = null

  try {
    codebaseDeploymentResult.value = await applyDevelopmentCodebaseDeploymentConfig(
      props.workspaceId || '',
      props.projectCode || '',
      props.selectedModule.id,
      {
        targetStack: selectedTargetStackCode.value,
        overwriteExisting: codebaseDeploymentOverwrite.value,
        remarks: '用户在 Dev Codebase Workspace 中生成部署配置。',
      },
    )

    await loadCodebaseDeploymentStatus({ silent: true })
    await loadCodebaseExportStatus({ silent: true })
  } catch (error) {
    codebaseDeploymentError.value = resolveErrorMessage(error, '生成部署配置失败。')
  } finally {
    codebaseDeploymentApplying.value = false
  }
}

function deploymentFileClass(action?: string | null): string {
  return String(action || 'unknown').toLowerCase().replace(/_/g, '-')
}

async function loadCodebaseExportStatus(options: { silent?: boolean } = {}) {
  if (!canUseDevelopmentApi.value || !props.selectedModule?.id) {
    codebaseExportStatus.value = null
    return
  }

  if (!options.silent) {
    codebaseExportLoading.value = true
    codebaseExportError.value = ''
  }

  try {
    codebaseExportStatus.value = await fetchDevelopmentCodebaseExportStatus(
      props.workspaceId || '',
      props.projectCode || '',
      props.selectedModule.id,
      selectedTargetStackCode.value,
    )
  } catch (error) {
    codebaseExportStatus.value = null
    codebaseExportError.value = resolveErrorMessage(error, '读取代码库导出状态失败。')
  } finally {
    if (!options.silent) {
      codebaseExportLoading.value = false
    }
  }
}

async function handleDownloadCodebaseZip() {
  if (!canUseDevelopmentApi.value || !props.selectedModule?.id) {
    return
  }

  codebaseExportDownloading.value = true
  codebaseExportError.value = ''
  codebaseExportDownloadedFile.value = ''

  try {
    const fileName = await downloadDevelopmentCodebaseZip(
      props.workspaceId || '',
      props.projectCode || '',
      props.selectedModule.id,
      selectedTargetStackCode.value,
      codebaseExportStatus.value?.estimatedZipName || 'codemind-generated-codebase.zip',
    )

    codebaseExportDownloadedFile.value = fileName
    await loadCodebaseExportStatus({ silent: true })
  } catch (error) {
    codebaseExportError.value = resolveErrorMessage(error, '下载生成项目 ZIP 失败。')
  } finally {
    codebaseExportDownloading.value = false
  }
}

function formatBytes(value?: number | null): string {
  const bytes = Number(value || 0)

  if (!Number.isFinite(bytes) || bytes <= 0) {
    return '0 B'
  }

  const units = ['B', 'KB', 'MB', 'GB']
  let size = bytes
  let unitIndex = 0

  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024
    unitIndex += 1
  }

  return `${size.toFixed(size >= 10 || unitIndex === 0 ? 0 : 1)} ${units[unitIndex]}`
}


async function loadRuntimeStatus(options: { silent?: boolean } = {}) {
  if (!canUseDevelopmentApi.value || !props.selectedModule?.id) {
    runtimeStatus.value = null
    return
  }

  if (!options.silent) {
    runtimeLoading.value = true
    runtimeError.value = ''
  }

  try {
    runtimeStatus.value = await fetchDevelopmentRuntimeStatus(
      props.workspaceId || '',
      props.projectCode || '',
      props.selectedModule.id,
      selectedTargetStackCode.value,
    )
    runtimeDiagnosticCopied.value = false
  } catch (error) {
    runtimeStatus.value = null
    runtimeError.value = resolveErrorMessage(
      error,
      '读取运行状态失败。',
    )
  } finally {
    if (!options.silent) {
      runtimeLoading.value = false
    }
  }
}

async function loadRuntimeLogs() {
  if (!canUseDevelopmentApi.value || !props.selectedModule?.id) {
    runtimeLogs.value = null
    return
  }

  runtimeLogsLoading.value = true
  runtimeLogsError.value = ''

  try {
    runtimeLogs.value = await fetchDevelopmentRuntimeLogs(
      props.workspaceId || '',
      props.projectCode || '',
      props.selectedModule.id,
      selectedTargetStackCode.value,
      260,
    )
  } catch (error) {
    runtimeLogs.value = null
    runtimeLogsError.value = resolveErrorMessage(
      error,
      '读取运行日志失败。',
    )
  } finally {
    runtimeLogsLoading.value = false
  }
}

async function handleRuntimeInstall() {
  if (!props.selectedModule?.id) {
    return
  }

  runtimeLoading.value = true
  runtimeError.value = ''

  try {
    runtimeStatus.value = await installDevelopmentRuntimeDependencies(
      props.workspaceId || '',
      props.projectCode || '',
      props.selectedModule.id,
      { targetStack: selectedTargetStackCode.value },
    )
    await loadRuntimeLogs()
    await loadCodebaseAcceptanceStatus({ silent: true })
  } catch (error) {
    runtimeError.value = resolveErrorMessage(error, '安装依赖失败。')
  } finally {
    runtimeLoading.value = false
  }
}

async function handleRuntimeStart() {
  if (!props.selectedModule?.id) {
    return
  }

  runtimeLoading.value = true
  runtimeError.value = ''

  try {
    runtimeStatus.value = await startDevelopmentRuntime(
      props.workspaceId || '',
      props.projectCode || '',
      props.selectedModule.id,
      { targetStack: selectedTargetStackCode.value },
    )
    await loadRuntimeLogs()
    await loadCodebaseAcceptanceStatus({ silent: true })
  } catch (error) {
    runtimeError.value = resolveErrorMessage(error, '启动生成项目失败。')
  } finally {
    runtimeLoading.value = false
  }
}

async function handleRuntimeStop() {
  if (!props.selectedModule?.id) {
    return
  }

  runtimeLoading.value = true
  runtimeError.value = ''

  try {
    runtimeStatus.value = await stopDevelopmentRuntime(
      props.workspaceId || '',
      props.projectCode || '',
      props.selectedModule.id,
      { targetStack: selectedTargetStackCode.value },
    )
    await loadRuntimeLogs()
    await loadCodebaseAcceptanceStatus({ silent: true })
  } catch (error) {
    runtimeError.value = resolveErrorMessage(error, '停止生成项目失败。')
  } finally {
    runtimeLoading.value = false
  }
}

async function handleRuntimeRestart() {
  if (!props.selectedModule?.id) {
    return
  }

  runtimeLoading.value = true
  runtimeError.value = ''

  try {
    runtimeStatus.value = await restartDevelopmentRuntime(
      props.workspaceId || '',
      props.projectCode || '',
      props.selectedModule.id,
      { targetStack: selectedTargetStackCode.value, forceRestart: true },
    )
    await loadRuntimeLogs()
    await loadCodebaseAcceptanceStatus({ silent: true })
  } catch (error) {
    runtimeError.value = resolveErrorMessage(error, '重启生成项目失败。')
  } finally {
    runtimeLoading.value = false
  }
}

function openRuntimePreview() {
  if (runtimePreviewUrl.value) {
    window.open(runtimePreviewUrl.value, '_blank', 'noopener,noreferrer')
  }
}

function toggleRuntimePreview() {
  runtimePreviewEnabled.value = !runtimePreviewEnabled.value
}

function reloadRuntimePreview() {
  runtimePreviewKey.value += 1
}

async function copyRuntimeDiagnostics() {
  if (!runtimeStatus.value) {
    return
  }

  const lines = [
    `运行状态：${runtimeStatus.value.runtimeStatus} / ${runtimeStatus.value.installStatus}`,
    `健康状态：${runtimeStatus.value.healthStatus || 'NOT_CHECKED'}`,
    `诊断级别：${runtimeStatus.value.diagnosticLevel || 'INFO'}`,
    `诊断标题：${runtimeStatus.value.diagnosticTitle || '-'}`,
    `诊断摘要：${runtimeStatus.value.diagnosticSummary || '-'}`,
    '',
    '检测到的问题：',
    ...(runtimeStatus.value.detectedProblems?.length ? runtimeStatus.value.detectedProblems.map((item) => `- ${item}`) : ['- 无']),
    '',
    '修复建议：',
    ...(runtimeStatus.value.diagnosticSuggestions?.length ? runtimeStatus.value.diagnosticSuggestions.map((item) => `- ${item}`) : ['- 无']),
    '',
    '最近错误日志：',
    ...(runtimeStatus.value.recentErrorLines?.length ? runtimeStatus.value.recentErrorLines : ['- 无']),
  ]

  try {
    await navigator.clipboard.writeText(lines.join('\n'))
    runtimeDiagnosticCopied.value = true
    window.setTimeout(() => {
      runtimeDiagnosticCopied.value = false
    }, 1800)
  } catch {
    runtimeError.value = '复制诊断信息失败，请手动选择日志内容复制。'
  }
}

function reconcileRuntimePolling() {
  if (shouldPollRuntime.value) {
    startRuntimePolling()
  } else {
    stopRuntimePolling()
  }
}

function startRuntimePolling() {
  if (runtimePollTimer !== null) {
    return
  }

  runtimePollTimer = window.setInterval(() => {
    if (!shouldPollRuntime.value) {
      stopRuntimePolling()
      return
    }

    void loadRuntimeStatus({ silent: true })
  }, 3500)
}

function stopRuntimePolling() {
  if (runtimePollTimer === null) {
    return
  }

  window.clearInterval(runtimePollTimer)
  runtimePollTimer = null
}

function assetLevelClass(
  asset: DevelopmentHandoffAsset,
): string {
  const status = String(asset.status || '').toUpperCase()

  if (
    !asset.exists ||
    !asset.contentReady ||
    ['REJECTED', 'ARCHIVED', 'SUPERSEDED'].includes(status) ||
    asset.current === false
  ) {
    return asset.required ? 'blocker' : 'warning'
  }

  if (
    ['DRAFT', 'PENDING_REVIEW', 'NEEDS_REVIEW'].includes(status)
  ) {
    return 'warning'
  }

  return 'pass'
}

function assetSourceText(
  asset: DevelopmentHandoffAsset,
): string {
  switch (asset.sourceScope) {
    case 'LOCAL_MODULE':
      return '模块本地'
    case 'LOCAL_PAGE':
      return '页面本地'
    case 'INHERITED_MODULE':
      return '继承模块'
    case 'DESCENDANT_PAGE':
      return '子页面契约'
    default:
      return asset.inherited ? '继承资产' : '当前范围'
  }
}

function formatTime(
  value?: string | null,
): string {
  if (!value) {
    return '-'
  }

  return String(value).replace('T', ' ').slice(0, 19)
}

async function withUiTimeout<T>(
  promise: Promise<T>,
  timeoutMs: number,
  message: string,
): Promise<T> {
  let timer: number | undefined

  const timeoutPromise = new Promise<never>((_, reject) => {
    timer = window.setTimeout(() => {
      reject(new Error(message))
    }, timeoutMs)
  })

  try {
    return await Promise.race([promise, timeoutPromise])
  } finally {
    if (timer !== undefined) {
      window.clearTimeout(timer)
    }
  }
}

function resolveErrorMessage(
  error: unknown,
  fallback: string,
): string {
  if (error instanceof Error && error.message) {
    return error.message
  }

  return fallback
}
</script>

<style scoped>
.development-handoff-panel {
  flex: 1 1 0;
  min-width: 0;
  min-height: 0;
  height: 100%;
  overflow: hidden;
  background: #f8fafc;
  box-sizing: border-box;
  display: flex;
}

.handoff-card {
  flex: 1 1 0;
  min-height: 0;
  width: 100%;
  box-sizing: border-box;
  padding: 22px;
  overflow-y: auto;
  overflow-x: hidden;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
  display: grid;
  gap: 16px;
  align-content: start;
}

.handoff-header,
.summary-card,
.node-info-card,
.snapshot-card,
.asset-section,
.issue-section,
.context-section,
.plan-confirm-section,
.handoff-package-section,
.phase4-acceptance-section,
.target-stack-section,
.plan-section,
.codebase-workspace-section {
  border: 1px solid #e2e8f0;
  border-radius: 18px;
  background: #ffffff;
}

.handoff-header {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  padding: 20px;
}

.eyebrow {
  margin: 0 0 6px;
  color: #2563eb;
  font-size: 12px;
  font-weight: 900;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.handoff-header h2 {
  margin: 0 0 8px;
  color: #0f172a;
  font-size: 24px;
}

.handoff-header span,
.node-info-card p,
.section-heading p,
.snapshot-card span {
  color: #64748b;
  font-size: 13px;
  line-height: 1.7;
}

.header-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-content: flex-start;
  justify-content: flex-end;
}

.summary-card {
  display: flex;
  gap: 18px;
  padding: 20px;
  background: linear-gradient(135deg, #eff6ff, #ffffff);
}

.summary-card.ready {
  border-color: #bbf7d0;
  background: linear-gradient(135deg, #f0fdf4, #ffffff);
}

.summary-card.warning {
  border-color: #fde68a;
  background: linear-gradient(135deg, #fffbeb, #ffffff);
}

.summary-card.blocked {
  border-color: #fecaca;
  background: linear-gradient(135deg, #fef2f2, #ffffff);
}

.summary-card.context {
  border-color: #cbd5e1;
  background: linear-gradient(135deg, #f8fafc, #ffffff);
}

.scope-circle {
  width: 112px;
  height: 112px;
  flex: 0 0 auto;
  border-radius: 28px;
  border: 1px solid #cbd5e1;
  background: #ffffff;
  color: #334155;
  display: grid;
  place-content: center;
  gap: 5px;
  text-align: center;
  box-sizing: border-box;
}

.scope-circle strong {
  font-size: 24px;
}

.scope-circle span {
  max-width: 80px;
  color: #64748b;
  font-size: 11px;
  line-height: 1.4;
}

.scope-summary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-top: 16px;
}

.scope-summary-grid div {
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 12px;
  background: #ffffff;
}

.scope-summary-grid strong,
.scope-summary-grid span {
  display: block;
}

.scope-summary-grid strong {
  color: #0f172a;
  font-size: 15px;
}

.scope-summary-grid span {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
}

.score-circle {
  width: 112px;
  height: 112px;
  flex: 0 0 auto;
  border-radius: 999px;
  background: #2563eb;
  color: #ffffff;
  display: flex;
  align-items: baseline;
  justify-content: center;
  padding-top: 30px;
  box-sizing: border-box;
}

.ready .score-circle {
  background: #16a34a;
}

.warning .score-circle {
  background: #d97706;
}

.blocked .score-circle {
  background: #dc2626;
}

.score-circle strong {
  font-size: 42px;
  line-height: 1;
}

.score-circle span {
  font-size: 15px;
}

.summary-content {
  flex: 1;
  min-width: 0;
}

.summary-title-row {
  display: flex;
  justify-content: space-between;
  gap: 14px;
}

.summary-title-row h3 {
  margin: 0 0 8px;
  color: #0f172a;
  font-size: 20px;
}

.summary-title-row p {
  margin: 0;
  color: #475569;
  line-height: 1.7;
  font-size: 13px;
}

.node-badge {
  align-self: flex-start;
  border-radius: 999px;
  padding: 6px 12px;
  background: #ffffff;
  color: #2563eb;
  font-size: 13px;
  font-weight: 900;
  white-space: nowrap;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-top: 16px;
}

.summary-grid div {
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 12px;
  background: #ffffff;
}

.summary-grid strong,
.summary-grid span {
  display: block;
}

.summary-grid strong {
  color: #0f172a;
  font-size: 20px;
}

.summary-grid span {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
}

.node-info-card,
.snapshot-card {
  padding: 16px;
}

.node-info-card div {
  display: flex;
  gap: 10px;
  align-items: center;
}

.node-info-card strong {
  color: #0f172a;
}

.node-info-card span {
  border-radius: 999px;
  padding: 3px 8px;
  background: #f1f5f9;
  color: #475569;
  font-size: 12px;
}

.effective-module-strip {
  margin-top: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  border-top: 1px solid #e2e8f0;
  padding-top: 12px;
}

.effective-module-strip strong {
  color: #1d4ed8;
}

.effective-module-strip small {
  color: #64748b;
}

.development-scope-card {
  border: 1px solid #cbd5e1;
  border-radius: 18px;
  padding: 18px;
  background: linear-gradient(135deg, #f8fafc, #ffffff);
}

.development-scope-card strong {
  display: block;
  color: #0f172a;
  font-size: 16px;
}

.development-scope-card p,
.development-scope-card span {
  display: block;
  margin: 8px 0 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.7;
}

.snapshot-card {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  border-color: #fecaca;
  background: #fef2f2;
}

.snapshot-card.ready {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.snapshot-card h3 {
  margin: 0 0 6px;
  color: #0f172a;
}

.asset-section,
.issue-section,
.context-section,
.plan-confirm-section,
.handoff-package-section,
.phase4-acceptance-section,
.target-stack-section,
.plan-section,
.codebase-workspace-section {
  padding: 18px;
}

.section-heading {
  margin-bottom: 12px;
}

.section-heading h3 {
  margin: 0 0 5px;
  color: #0f172a;
  font-size: 17px;
}

.asset-grid,
.context-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
  gap: 10px;
}

.asset-card,
.context-asset-card,
.context-json-card,
.plan-card {
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 12px;
  background: #ffffff;
}

.asset-card header,
.asset-card footer {
  display: flex;
  justify-content: space-between;
  gap: 10px;
}

.asset-badges {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 5px;
}

.asset-card header .source-badge {
  background: #f8fafc;
  color: #475569;
}

.asset-card header .source-badge.inherited {
  background: #eef2ff;
  color: #4338ca;
}

.asset-source-detail {
  display: block;
  margin: -2px 0 9px;
  color: #64748b;
  font-size: 11px;
}

.asset-card strong,
.context-asset-card strong,
.context-json-card strong,
.plan-card h4 {
  color: #0f172a;
  font-size: 14px;
}

.asset-card header span,
.context-asset-card span,
.plan-item span {
  border-radius: 999px;
  padding: 2px 8px;
  background: #f1f5f9;
  color: #64748b;
  font-size: 11px;
  font-weight: 800;
}

.asset-card header span.required,
.context-asset-card.required span {
  background: #eff6ff;
  color: #1d4ed8;
}

.asset-card p,
.context-asset-card p,
.context-json-card p,
.plan-item p {
  margin: 8px 0;
  color: #334155;
  font-size: 13px;
  line-height: 1.6;
}

.asset-card small {
  color: #64748b;
  font-size: 12px;
}

.asset-card.pass {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.asset-card.warning {
  border-color: #fde68a;
  background: #fffbeb;
}

.asset-card.blocker {
  border-color: #fecaca;
  background: #fef2f2;
}

.issue-card {
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 12px 14px;
  margin-bottom: 10px;
}

.issue-card strong {
  display: block;
  margin-bottom: 6px;
  color: #0f172a;
  font-size: 14px;
}

.issue-card p {
  margin: 0;
  color: #475569;
  line-height: 1.7;
  font-size: 13px;
}

.issue-card.blocker {
  border-color: #fecaca;
  background: #fef2f2;
}

.issue-card.warning {
  border-color: #fde68a;
  background: #fffbeb;
}

.empty-box,
.error-box,
.empty-mini {
  border: 1px dashed #cbd5e1;
  border-radius: 16px;
  padding: 18px;
  background: #ffffff;
  color: #64748b;
  line-height: 1.7;
}

.error-box {
  border-color: #fecaca;
  background: #fef2f2;
  color: #991b1b;
}

.empty-mini {
  padding: 14px;
  background: #f8fafc;
}

.context-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 12px;
}

.context-json-card {
  margin-top: 10px;
  background: #f8fafc;
}

.package-summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}

.package-summary-grid div {
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 12px;
  background: #ffffff;
}

.package-summary-grid strong,
.package-summary-grid span {
  display: block;
}

.package-summary-grid strong {
  color: #0f172a;
  font-size: 15px;
  word-break: break-all;
}

.package-summary-grid span {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
}

.package-section-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 12px 0;
}

.package-section-list span {
  border-radius: 999px;
  padding: 5px 10px;
  background: #f1f5f9;
  color: #334155;
  font-size: 12px;
  font-weight: 800;
}

.plan-confirm-section.confirmed {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.plan-confirm-section.stale {
  border-color: #fde68a;
  background: #fffbeb;
}

.plan-confirm-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.plan-confirm-meta span,
.plan-principles span {
  border-radius: 999px;
  padding: 5px 10px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 800;
}

.plan-principles {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 14px;
}

.plan-columns {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.plan-card {
  margin-bottom: 12px;
}

.plan-card h4 {
  margin: 0 0 10px;
  font-size: 15px;
}

.plan-item {
  border-top: 1px solid #e2e8f0;
  padding: 10px 0;
}

.plan-item:first-of-type {
  border-top: 0;
  padding-top: 0;
}

.plan-item strong {
  display: block;
  color: #0f172a;
  font-size: 13px;
}

.handoff-footer {
  display: flex;
  justify-content: flex-end;
}

.primary-button,
.secondary-button {
  border-radius: 999px;
  padding: 9px 14px;
  font-size: 13px;
  cursor: pointer;
}

.primary-button {
  border: 1px solid #2563eb;
  background: #2563eb;
  color: #ffffff;
}

.secondary-button {
  border: 1px solid #cbd5e1;
  background: #ffffff;
  color: #334155;
}

.primary-button:disabled,
.secondary-button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}


.codebase-binding-form {
  display: grid;
  gap: 8px;
  margin-top: 12px;
}

.codebase-binding-form label {
  display: grid;
  gap: 6px;
}

.codebase-binding-form label span {
  color: #475569;
  font-size: 12px;
  font-weight: 800;
}

.codebase-path-input {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid #cbd5e1;
  border-radius: 12px;
  padding: 10px 12px;
  color: #0f172a;
  font-size: 13px;
  outline: none;
}

.codebase-path-input:focus {
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.12);
}



.phase4-acceptance-section.ready {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.phase4-summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}

.phase4-summary-grid div {
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 12px;
  background: #ffffff;
}

.phase4-summary-grid strong,
.phase4-summary-grid span {
  display: block;
}

.phase4-summary-grid strong {
  color: #0f172a;
  font-size: 18px;
  word-break: break-word;
}

.phase4-summary-grid span {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
}

.phase4-summary-text {
  margin: 0 0 12px;
  color: #334155;
  font-size: 13px;
  line-height: 1.7;
}

.phase4-item-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(230px, 1fr));
  gap: 10px;
}

.phase4-item-card {
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 12px;
  background: #ffffff;
}

.phase4-item-card.pass,
.phase4-item-card.ready {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.phase4-item-card.blocked {
  border-color: #fecaca;
  background: #fef2f2;
}

.phase4-item-card.warning {
  border-color: #fde68a;
  background: #fffbeb;
}

.phase4-item-card header {
  display: flex;
  justify-content: space-between;
  gap: 10px;
}

.phase4-item-card strong {
  color: #0f172a;
  font-size: 14px;
}

.phase4-item-card header span {
  border-radius: 999px;
  padding: 2px 8px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 11px;
  font-weight: 800;
}

.phase4-item-card p {
  margin: 8px 0;
  color: #334155;
  font-size: 13px;
  line-height: 1.6;
}

.phase4-item-card footer {
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.phase4-blocker-list {
  margin-top: 12px;
  border: 1px solid #fecaca;
  border-radius: 14px;
  padding: 12px;
  background: #fef2f2;
}

.phase4-blocker-list strong {
  display: block;
  margin-bottom: 6px;
  color: #991b1b;
  font-size: 13px;
}

.phase4-blocker-list p {
  margin: 4px 0;
  color: #7f1d1d;
  font-size: 13px;
  line-height: 1.6;
}

@media (max-width: 820px) {
  .handoff-header,
  .summary-card,
  .snapshot-card {
    flex-direction: column;
  }

  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .plan-columns {
    grid-template-columns: 1fr;
  }
}

.target-stack-section {
  display: grid;
  gap: 14px;
  padding: 18px;
}

.target-stack-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 12px;
}

.target-stack-card {
  border: 1px solid #dbeafe;
  border-radius: 16px;
  background: #f8fafc;
  padding: 14px;
  text-align: left;
  cursor: pointer;
}

.target-stack-card header,
.stack-detail-card,
.plan-stack-summary,
.selected-stack-strip {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.target-stack-card header strong {
  color: #0f172a;
  font-size: 14px;
}

.target-stack-card header span {
  border-radius: 999px;
  background: #e0f2fe;
  padding: 3px 8px;
  color: #0369a1;
  font-size: 11px;
  font-weight: 800;
}

.target-stack-card p,
.stack-detail-card p,
.plan-stack-notes p {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

.target-stack-card.active {
  border-color: #2563eb;
  background: #eff6ff;
  box-shadow: 0 12px 26px rgba(37, 99, 235, 0.12);
}

.target-stack-card.disabled {
  cursor: not-allowed;
  opacity: 0.56;
}

.stack-capabilities,
.stack-command-list,
.plan-stack-notes {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.stack-capabilities span,
.stack-command-list span,
.plan-stack-notes p,
.selected-stack-strip span {
  border: 1px solid #e2e8f0;
  border-radius: 999px;
  background: #ffffff;
  padding: 5px 9px;
  color: #475569;
  font-size: 11px;
  font-weight: 700;
}

.stack-detail-card,
.plan-stack-summary,
.selected-stack-strip {
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  background: #ffffff;
  padding: 14px;
}

.stack-detail-card strong,
.plan-stack-summary strong,
.selected-stack-strip strong {
  color: #0f172a;
  font-size: 14px;
}

.plan-stack-summary {
  margin-bottom: 12px;
  background: #f8fafc;
}

.plan-stack-notes {
  margin-bottom: 12px;
}

.plan-stack-notes p {
  margin: 0;
  border-radius: 12px;
}


.code-project-section {
  display: grid;
  gap: 14px;
}

.code-file-item {
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 12px;
  background: #ffffff;
  display: grid;
  gap: 10px;
}

.code-file-item header {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  align-items: flex-start;
}

.code-file-item strong {
  font-size: 13px;
  color: #0f172a;
  word-break: break-all;
}

.code-file-item p {
  margin: 4px 0 0;
  font-size: 12px;
  color: #64748b;
}

.code-file-item header span {
  flex: 0 0 auto;
  border-radius: 999px;
  padding: 4px 8px;
  font-size: 11px;
  color: #1d4ed8;
  background: #eff6ff;
}

.code-file-item summary {
  cursor: pointer;
  font-size: 12px;
  color: #2563eb;
  font-weight: 700;
}

.code-file-item pre {
  margin: 10px 0 0;
  max-height: 360px;
  overflow: auto;
  border-radius: 12px;
  padding: 12px;
  background: #0f172a;
  color: #e2e8f0;
  font-size: 12px;
  line-height: 1.55;
  white-space: pre-wrap;
}


.code-project-actions {
  margin: 4px 0 12px;
}

.materialization-summary-card p + p {
  margin-top: 4px;
  word-break: break-all;
}

.materialization-file-card {
  display: grid;
  gap: 8px;
}

.materialized-file-item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 10px 12px;
  background: #ffffff;
}

.materialized-file-item.written,
.materialized-file-item.overwritten {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.materialized-file-item.skipped_exists {
  border-color: #fde68a;
  background: #fffbeb;
}

.materialized-file-item strong {
  display: block;
  color: #0f172a;
  font-size: 13px;
  word-break: break-all;
}

.materialized-file-item p {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 12px;
  word-break: break-all;
}

.materialized-file-item span {
  flex: 0 0 auto;
  border-radius: 999px;
  padding: 4px 8px;
  background: #ffffff;
  color: #475569;
  font-size: 11px;
  font-weight: 800;
}


.codebase-heading-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.codebase-status-card {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  padding: 14px;
  background: #f8fafc;
}

.codebase-status-card.ready {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.codebase-status-card.warning {
  border-color: #fde68a;
  background: #fffbeb;
}

.codebase-status-card.blocked {
  border-color: #fecaca;
  background: #fef2f2;
}

.codebase-status-card strong {
  color: #0f172a;
  font-size: 15px;
}

.codebase-status-card p {
  margin: 6px 0 0;
  color: #475569;
  font-size: 13px;
  line-height: 1.6;
}

.codebase-status-card > span {
  align-self: flex-start;
  border-radius: 999px;
  padding: 5px 10px;
  background: #ffffff;
  color: #334155;
  font-size: 12px;
  font-weight: 900;
  white-space: nowrap;
}

.codebase-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin: 12px 0;
}

.codebase-summary-grid div {
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 12px;
  background: #ffffff;
}

.codebase-summary-grid strong,
.codebase-summary-grid span {
  display: block;
}

.codebase-summary-grid strong {
  color: #0f172a;
  font-size: 15px;
}

.codebase-summary-grid span {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
}

.muted-line {
  color: #64748b !important;
  font-size: 12px !important;
}

.codebase-readiness-panel {
  border: 1px dashed #cbd5e1;
  border-radius: 16px;
  padding: 14px;
  background: #f8fafc;
}

.codebase-readiness-panel strong {
  color: #0f172a;
}

.codebase-readiness-panel p {
  margin: 8px 0;
  color: #475569;
  font-size: 13px;
  line-height: 1.6;
}

.missing-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin: 10px 0;
}

.missing-list span {
  border-radius: 10px;
  padding: 8px 10px;
  background: #fff7ed;
  color: #9a3412;
  font-size: 12px;
}

.blocker-list span {
  background: #fef2f2;
  color: #b91c1c;
}

.codebase-browser {
  display: grid;
  grid-template-columns: minmax(260px, 360px) minmax(0, 1fr);
  gap: 12px;
  margin-top: 12px;
}

.codebase-file-list,
.codebase-code-viewer {
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  background: #ffffff;
  overflow: hidden;
}

.codebase-tabs {
  display: grid;
  grid-template-columns: 1fr 1fr;
  border-bottom: 1px solid #e2e8f0;
}

.codebase-tabs button {
  border: 0;
  background: #f8fafc;
  padding: 10px;
  color: #475569;
  font-weight: 900;
  cursor: pointer;
}

.codebase-tabs button.active {
  background: #eff6ff;
  color: #1d4ed8;
}

.codebase-file-items {
  max-height: 560px;
  overflow: auto;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.code-file-pill {
  width: 100%;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 9px 10px;
  background: #ffffff;
  text-align: left;
  cursor: pointer;
}

.code-file-pill.directory {
  cursor: default;
  background: #f8fafc;
}

.code-file-pill.active {
  border-color: #93c5fd;
  background: #eff6ff;
}

.code-file-pill strong,
.code-file-pill span {
  display: block;
}

.code-file-pill strong {
  color: #0f172a;
  font-size: 12px;
  word-break: break-all;
}

.code-file-pill span {
  margin-top: 3px;
  color: #64748b;
  font-size: 11px;
}

.code-viewer-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  padding: 12px;
  border-bottom: 1px solid #e2e8f0;
  background: #f8fafc;
}

.code-viewer-header strong,
.code-viewer-header span {
  display: block;
}

.code-viewer-header strong {
  color: #0f172a;
  font-size: 13px;
  word-break: break-all;
}

.code-viewer-header span {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
}

.code-preview {
  margin: 0;
  max-height: 620px;
  overflow: auto;
  padding: 14px;
  background: #020617;
  color: #e2e8f0;
  font-size: 12px;
  line-height: 1.6;
}

.registry-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-top: 10px;
}

.registry-grid div {
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #f8fafc;
  padding: 10px;
}

.registry-grid span {
  display: block;
  margin-bottom: 4px;
  color: #64748b;
  font-size: 12px;
}

.registry-grid b {
  color: #0f172a;
  font-size: 13px;
}

@media (max-width: 960px) {
  .registry-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}


.codebase-export-card {
  margin: 12px 0;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  background: #ffffff;
  padding: 14px;
}

.codebase-export-card.ready {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.codebase-export-card.blocked {
  border-color: #fecaca;
  background: #fff7f7;
}

.codebase-export-header {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  align-items: flex-start;
}

.codebase-export-header strong,
.codebase-export-header p {
  display: block;
}

.codebase-export-header strong {
  color: #0f172a;
  font-size: 14px;
}

.codebase-export-header p {
  margin: 5px 0 0;
  color: #475569;
  font-size: 13px;
  line-height: 1.5;
}

.codebase-export-header > span {
  flex: 0 0 auto;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.08);
  color: #334155;
  padding: 5px 9px;
  font-size: 12px;
  font-weight: 900;
}

.export-grid {
  margin-bottom: 8px;
}

.export-list {
  margin-top: 10px;
}


.codebase-export-card.warning {
  border-color: #fde68a;
  background: #fffbeb;
}

.acceptance-item-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 10px;
  margin-top: 12px;
}

.acceptance-item-card {
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  background: #ffffff;
  padding: 12px;
}

.acceptance-item-card.pass,
.acceptance-item-card.ready {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.acceptance-item-card.warning,
.acceptance-item-card.skipped {
  border-color: #fde68a;
  background: #fffbeb;
}

.acceptance-item-card.failed,
.acceptance-item-card.timeout,
.acceptance-item-card.not-ready {
  border-color: #fecaca;
  background: #fef2f2;
}

.acceptance-item-card header,
.acceptance-item-card footer {
  display: flex;
  justify-content: space-between;
  gap: 10px;
}

.acceptance-item-card strong {
  color: #0f172a;
  font-size: 13px;
}

.acceptance-item-card header span {
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.08);
  color: #475569;
  padding: 2px 7px;
  font-size: 11px;
  font-weight: 900;
}

.acceptance-item-card p {
  margin: 8px 0;
  color: #334155;
  font-size: 12px;
  line-height: 1.6;
}

.acceptance-item-card small {
  color: #64748b;
  font-size: 11px;
}

.acceptance-log-details {
  margin-top: 8px;
}

.runtime-manager-card {
  margin: 12px 0;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  background: #ffffff;
  padding: 14px;
}

.runtime-manager-card.ready {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.runtime-manager-card.warning {
  border-color: #fde68a;
  background: #fffbeb;
}

.runtime-manager-card.blocked {
  border-color: #fecaca;
  background: #fef2f2;
}

.runtime-header {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  align-items: flex-start;
}

.runtime-header strong {
  color: #0f172a;
  font-size: 15px;
}

.runtime-header p {
  margin: 6px 0 0;
  color: #475569;
  font-size: 13px;
  line-height: 1.6;
}

.runtime-header > span {
  flex: 0 0 auto;
  border-radius: 999px;
  padding: 5px 10px;
  background: #ffffff;
  color: #334155;
  font-size: 11px;
  font-weight: 900;
}

.runtime-meta-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 10px;
  margin: 12px 0;
}

.runtime-meta-grid div {
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  background: #ffffff;
  padding: 10px 12px;
}

.runtime-meta-grid strong,
.runtime-meta-grid span {
  display: block;
}

.runtime-meta-grid strong {
  color: #0f172a;
  font-size: 13px;
  word-break: break-all;
}

.runtime-meta-grid span {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
}


.runtime-health-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 8px 0 4px;
}

.runtime-health-strip span {
  border-radius: 999px;
  padding: 5px 10px;
  background: #ffffff;
  color: #475569;
  font-size: 11px;
  font-weight: 800;
}

.runtime-health-strip.healthy span {
  background: #dcfce7;
  color: #166534;
}

.runtime-health-strip.starting span {
  background: #fef3c7;
  color: #92400e;
}

.runtime-health-strip.unhealthy span {
  background: #fee2e2;
  color: #991b1b;
}

.runtime-preview-panel {
  margin-top: 12px;
  border: 1px solid #bfdbfe;
  border-radius: 16px;
  background: #eff6ff;
  overflow: hidden;
}

.runtime-preview-panel header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
  padding: 10px 12px;
  border-bottom: 1px solid #bfdbfe;
}

.runtime-preview-panel strong,
.runtime-preview-panel span {
  display: block;
}

.runtime-preview-panel strong {
  color: #0f172a;
  font-size: 13px;
}

.runtime-preview-panel span {
  margin-top: 4px;
  color: #475569;
  font-size: 12px;
  word-break: break-all;
}

.runtime-preview-panel iframe {
  display: block;
  width: 100%;
  min-height: 520px;
  border: 0;
  background: #ffffff;
}

.runtime-diagnostics-panel {
  margin: 12px 0 6px;
  border: 1px solid #dbeafe;
  border-radius: 16px;
  background: #f8fafc;
  padding: 12px;
}

.runtime-diagnostics-panel.ready {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.runtime-diagnostics-panel.warning {
  border-color: #fde68a;
  background: #fffbeb;
}

.runtime-diagnostics-panel.blocked {
  border-color: #fecaca;
  background: #fff1f2;
}

.runtime-diagnostics-panel.info {
  border-color: #bfdbfe;
  background: #eff6ff;
}

.runtime-diagnostics-panel header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.runtime-diagnostics-panel header strong,
.runtime-diagnostics-panel header span {
  color: #0f172a;
  font-size: 13px;
}

.runtime-diagnostics-panel header span {
  flex: 0 0 auto;
  border-radius: 999px;
  background: #ffffff;
  padding: 5px 9px;
  font-size: 11px;
  font-weight: 900;
}

.runtime-diagnostics-panel header p {
  margin: 4px 0 0;
  color: #475569;
  font-size: 12px;
  line-height: 1.55;
}

.runtime-diagnostic-section {
  margin-top: 10px;
  border-top: 1px dashed #cbd5e1;
  padding-top: 10px;
}

.runtime-diagnostic-section b,
.runtime-log-suggestions strong {
  color: #0f172a;
  font-size: 12px;
}

.runtime-diagnostic-section ul,
.runtime-log-suggestions ul {
  margin: 6px 0 0;
  padding-left: 18px;
  color: #334155;
  font-size: 12px;
  line-height: 1.65;
}

.runtime-error-lines {
  margin-top: 10px;
}

.runtime-error-lines summary {
  cursor: pointer;
  color: #334155;
  font-size: 12px;
  font-weight: 800;
}

.runtime-error-lines pre {
  margin: 8px 0 0;
  max-height: 220px;
  overflow: auto;
  border-radius: 12px;
  padding: 10px;
  background: #450a0a;
  color: #fee2e2;
  font-size: 11px;
  line-height: 1.55;
  white-space: pre-wrap;
}

.runtime-log-insights {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 8px;
  margin-top: 12px;
}

.runtime-log-insights div {
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #ffffff;
  padding: 9px 10px;
}

.runtime-log-insights strong,
.runtime-log-insights span {
  display: block;
}

.runtime-log-insights strong {
  color: #0f172a;
  font-size: 13px;
}

.runtime-log-insights span {
  margin-top: 3px;
  color: #64748b;
  font-size: 11px;
}

.runtime-log-suggestions {
  margin-top: 10px;
  border: 1px solid #fde68a;
  border-radius: 12px;
  background: #fffbeb;
  padding: 10px;
}

.runtime-actions {
  margin: 8px 0 0;
}

.runtime-log-preview {
  margin: 12px 0 0;
  max-height: 320px;
  overflow: auto;
  border-radius: 12px;
  padding: 12px;
  background: #020617;
  color: #e2e8f0;
  font-size: 12px;
  line-height: 1.55;
  white-space: pre-wrap;
}

.codebase-snapshot-card {
  border: 1px solid rgba(148, 163, 184, 0.28);
  border-radius: 18px;
  padding: 16px;
  background: rgba(255, 255, 255, 0.88);
  box-shadow: 0 14px 34px rgba(15, 23, 42, 0.06);
}

.codebase-snapshot-card.ready {
  border-color: rgba(34, 197, 94, 0.32);
}

.codebase-snapshot-card.warning {
  border-color: rgba(245, 158, 11, 0.32);
}

.safety-check-card {
  border-color: rgba(148, 163, 184, 0.28);
}

.safety-check-card.ready {
  border-color: rgba(34, 197, 94, 0.32);
}

.safety-check-card.blocked {
  border-color: rgba(239, 68, 68, 0.32);
}

.safety-count-grid {
  margin-top: 12px;
}

.safety-file-list {
  display: grid;
  gap: 8px;
  margin-top: 12px;
}

.safety-file-item {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  border: 1px solid rgba(148, 163, 184, 0.24);
  border-radius: 12px;
  padding: 10px 12px;
  background: rgba(248, 250, 252, 0.82);
}

.safety-file-item strong {
  display: block;
  font-size: 12px;
  color: #0f172a;
}

.safety-file-item p {
  margin: 4px 0 0;
  font-size: 12px;
  color: #64748b;
}

.safety-file-item span {
  align-self: flex-start;
  border-radius: 999px;
  padding: 3px 8px;
  font-size: 11px;
  font-weight: 700;
  background: rgba(15, 23, 42, 0.06);
  color: #475569;
}

.safety-file-item.added span,
.safety-file-item.modified span {
  background: rgba(34, 197, 94, 0.12);
  color: #15803d;
}

.safety-file-item.unchanged span,
.safety-file-item.skipped-keep span {
  background: rgba(148, 163, 184, 0.16);
  color: #475569;
}

.safety-file-item.conflict span,
.safety-file-item.overwrite-requested span {
  background: rgba(239, 68, 68, 0.12);
  color: #b91c1c;
}


.deployment-overwrite-toggle {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 40px;
  padding: 0 12px;
  border: 1px solid #dbeafe;
  border-radius: 999px;
  background: #eff6ff;
  color: #1e3a8a;
  font-size: 13px;
  font-weight: 800;
}

.deployment-overwrite-toggle input {
  width: 14px;
  height: 14px;
  accent-color: #2563eb;
}

.deployment-file-grid .acceptance-item-card.create,
.deployment-file-grid .acceptance-item-card.written {
  border-color: #bfdbfe;
  background: #eff6ff;
}

.deployment-file-grid .acceptance-item-card.update,
.deployment-file-grid .acceptance-item-card.overwritten {
  border-color: #fde68a;
  background: #fffbeb;
}

.deployment-file-grid .acceptance-item-card.unchanged {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.deployment-file-grid .acceptance-item-card.skipped-exists,
.deployment-file-grid .acceptance-item-card.write-failed {
  border-color: #fecaca;
  background: #fef2f2;
}


.agent-model-routing-card {
  border: 1px solid #dbeafe;
  border-radius: 18px;
  padding: 16px;
  background: #eff6ff;
  display: grid;
  gap: 14px;
}

.agent-model-routing-card.ready {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.agent-model-routing-card.warning {
  border-color: #fde68a;
  background: #fffbeb;
}

.agent-model-routing-card.blocked {
  border-color: #fecaca;
  background: #fef2f2;
}

.model-routing-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.model-routing-grid article {
  border: 1px solid #dbe3ef;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.78);
  padding: 12px;
  display: grid;
  gap: 6px;
}

.model-routing-grid span {
  color: #64748b;
  font-size: 12px;
}

.model-routing-grid strong {
  color: #0f172a;
  font-size: 13px;
  word-break: break-word;
}

.model-routing-grid small {
  color: #475569;
  font-size: 12px;
}

@media (max-width: 1280px) {
  .model-routing-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}


/* Phase 10-A6: Dev Agent staged workspace cleanup */
.score-circle {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding-top: 0;
  white-space: nowrap;
}

.score-circle strong {
  font-size: 40px;
  white-space: nowrap;
}

.score-circle span {
  white-space: nowrap;
}

.dev-next-step-card {
  border: 1px solid #bbf7d0;
  border-radius: 20px;
  padding: 18px;
  background:
    radial-gradient(circle at 0% 0%, rgba(34, 197, 94, 0.10), transparent 34%),
    linear-gradient(135deg, #f0fdf4, #ffffff);
  display: grid;
  gap: 14px;
}

.dev-next-step-card.blocked {
  border-color: #fecaca;
  background: linear-gradient(135deg, #fff1f2, #ffffff);
}

.dev-next-step-card.code {
  border-color: #bfdbfe;
  background: linear-gradient(135deg, #eff6ff, #ffffff);
}

.dev-next-step-card.runtime {
  border-color: #fde68a;
  background: linear-gradient(135deg, #fffbeb, #ffffff);
}

.dev-next-step-card header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.dev-next-step-card h3 {
  margin: 0 0 6px;
  color: #0f172a;
  font-size: 20px;
}

.dev-next-step-card header span {
  display: block;
  color: #475569;
  font-size: 13px;
  line-height: 1.7;
}

.dev-next-step-card header > strong {
  flex: 0 0 auto;
  border-radius: 999px;
  padding: 6px 10px;
  background: #ffffff;
  color: #166534;
  font-size: 12px;
  font-weight: 950;
}

.dev-next-step-card.blocked header > strong {
  color: #b91c1c;
}

.dev-next-step-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 10px;
}

.dev-next-step-grid article {
  border: 1px solid rgba(226, 232, 240, 0.95);
  border-radius: 14px;
  padding: 12px;
  background: rgba(255, 255, 255, 0.88);
}

.dev-next-step-grid span,
.dev-next-step-grid strong,
.dev-next-step-grid small {
  display: block;
}

.dev-next-step-grid span {
  color: #64748b;
  font-size: 12px;
}

.dev-next-step-grid strong {
  margin-top: 4px;
  color: #0f172a;
  font-size: 15px;
}

.dev-next-step-grid small {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
}

.dev-next-step-actions {
  margin-bottom: 0;
}

.feature-contribution-card {
  margin-top: 14px;
  border: 1px solid #bfdbfe;
  border-radius: 16px;
  padding: 14px;
  background: #eff6ff;
}

.feature-contribution-card > div:first-child strong {
  display: block;
  color: #0f172a;
  font-size: 15px;
}

.feature-contribution-card > div:first-child p {
  margin: 6px 0 0;
  color: #475569;
  font-size: 13px;
  line-height: 1.7;
}

.feature-contribution-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
  gap: 10px;
  margin-top: 12px;
}

.feature-contribution-grid article {
  border: 1px solid #dbeafe;
  border-radius: 14px;
  padding: 12px;
  background: #ffffff;
}

.feature-contribution-grid span,
.feature-contribution-grid strong,
.feature-contribution-grid small {
  display: block;
}

.feature-contribution-grid span {
  color: #64748b;
  font-size: 12px;
}

.feature-contribution-grid strong {
  margin-top: 4px;
  color: #0f172a;
  font-size: 14px;
}

.feature-contribution-grid small {
  margin-top: 5px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.55;
}

.advanced-dev-diagnostics {
  margin: 12px 0;
  border: 1px solid #dbeafe;
  border-radius: 18px;
  background: #f8fafc;
  overflow: hidden;
}

.advanced-dev-diagnostics > summary {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 14px;
  padding: 14px 16px;
  cursor: pointer;
  list-style: none;
}

.advanced-dev-diagnostics > summary::-webkit-details-marker {
  display: none;
}

.advanced-dev-diagnostics > summary strong {
  display: block;
  color: #0f172a;
  font-size: 15px;
}

.advanced-dev-diagnostics > summary span {
  display: block;
  margin-top: 5px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

.advanced-dev-diagnostics > summary b {
  flex: 0 0 auto;
  border-radius: 999px;
  padding: 5px 9px;
  background: #ffffff;
  color: #2563eb;
  font-size: 12px;
}

.advanced-dev-diagnostics-body {
  display: grid;
  gap: 12px;
  border-top: 1px solid #e2e8f0;
  padding: 14px;
}

.codebase-status-card.pending,
.codebase-export-card.pending,
.runtime-manager-card.pending {
  border-color: #bfdbfe;
  background: #eff6ff;
}

.codebase-status-card.pending > span,
.codebase-export-card.pending .codebase-export-header > span,
.runtime-manager-card.pending .runtime-header > span {
  background: #ffffff;
  color: #2563eb;
}

@media (max-width: 820px) {
  .dev-next-step-card header {
    flex-direction: column;
  }
}

</style>
