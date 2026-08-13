package com.codemind.platform.artifact;

import com.codemind.platform.identity.CurrentUser;
import com.codemind.platform.identity.CurrentUserProvider;
import com.codemind.platform.project.ProjectAccessService;
import com.codemind.platform.task.AgentTaskEntity;
import com.codemind.platform.task.AgentTaskService;
import com.codemind.platform.task.TaskType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ManualArtifactEditService {

    private static final List<String> MANUAL_EDITABLE_ARTIFACT_TYPES = List.of(
            "MODULE_PRD",
            "BUSINESS_FLOW",
            "PAGE_SPEC",
            "TEST_SPEC",
            "UI_CONTRACT",
            "MODULE_APP_CONTRACT",
            "MODULE_PROTOTYPE_SNAPSHOT"
    );

    private final ProjectAccessService projectAccessService;
    private final ArtifactVersionRepository artifactVersionRepository;
    private final AgentTaskService agentTaskService;
    private final CurrentUserProvider currentUserProvider;
    private final ObjectMapper objectMapper;

    public ManualArtifactEditService(
            ProjectAccessService projectAccessService,
            ArtifactVersionRepository artifactVersionRepository,
            AgentTaskService agentTaskService,
            CurrentUserProvider currentUserProvider,
            ObjectMapper objectMapper
    ) {
        this.projectAccessService = projectAccessService;
        this.artifactVersionRepository = artifactVersionRepository;
        this.agentTaskService = agentTaskService;
        this.currentUserProvider = currentUserProvider;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ManualArtifactEditResponse saveManualDocumentEdit(
            UUID workspaceId,
            String projectCode,
            UUID moduleId,
            ManualArtifactEditRequest request
    ) {
        ProjectAccessService.ProjectAccess access =
                projectAccessService.resolveAccessibleProject(workspaceId, projectCode);

        projectAccessService.resolveAccessibleModule(
                access.projectId(),
                moduleId
        );

        String artifactType = normalizeArtifactType(request.artifactType());

        if (!MANUAL_EDITABLE_ARTIFACT_TYPES.contains(artifactType)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "当前资产类型不允许手动编辑文档视图：" + artifactType
            );
        }

        String contentMarkdown = safeTrim(request.contentMarkdown());
        Map<String, Object> requestedContent = copyContent(request.content());
        boolean hasRequestedContent = !requestedContent.isEmpty();

        if (contentMarkdown.isBlank() && hasRequestedContent) {
            contentMarkdown = serializeContentForMarkdown(requestedContent);
        }

        if (contentMarkdown.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "文档内容不能为空。"
            );
        }

        if ("MODULE_PROTOTYPE_SNAPSHOT".equals(artifactType)) {
            return saveModulePrototypeSnapshot(
                    access.projectId(),
                    moduleId,
                    requestedContent,
                    request.reason()
            );
        }
        // A9.10.14.4.3.1_PRD_MANUAL_BOOTSTRAP_SAVE_FIX: legacy/final modules may expose PRD markdown in the UI
        // before a MODULE_PRD artifact_version row exists. Allow MODULE_PRD to
        // bootstrap its first manual version instead of throwing a 404.
        ArtifactVersionEntity previous = artifactVersionRepository
                .findFirstByProjectIdAndModuleIdAndArtifactTypeAndCurrentTrueOrderByVersionNoDesc(
                        access.projectId(),
                        moduleId,
                        artifactType
                )
                .orElse(null);

        if (previous == null && !canBootstrapManualArtifact(artifactType)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "当前节点没有可编辑的资产版本：" + artifactType
            );
        }

        if (request.artifactId() != null && !request.artifactId().isBlank()) {
            UUID requestedArtifactId = parseUuid(
                    request.artifactId(),
                    "artifactId"
            );

            if (previous == null || previous.getId() == null || !requestedArtifactId.equals(previous.getId())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "当前资产版本已变化或当前节点尚无可编辑版本，请刷新后再编辑。"
                );
            }
        }

        String previousMarkdown = previous == null
                ? ""
                : safeTrim(previous.getContentMarkdown());
        boolean markdownUnchanged = previous != null && normalizeMarkdown(previousMarkdown).equals(
                normalizeMarkdown(contentMarkdown)
        );
        boolean contentUnchanged = previous != null && hasRequestedContent && normalizeContentForCompare(previous.getContent()).equals(
                normalizeContentForCompare(requestedContent)
        );

        if (previous != null && ((!hasRequestedContent && markdownUnchanged) || (hasRequestedContent && contentUnchanged))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "资产内容没有变化，无需生成新版本。"
            );
        }

        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        String username = currentUser.username();
        LocalDateTime now = LocalDateTime.now();

        // A9.10.14.4.3.2_MANUAL_EDIT_TASK_TRACE_FIX: artifact_version.task_id is NOT NULL in the database.
        // Manual saves therefore get their own completed provenance task instead of saving a null task id.
        AgentTaskEntity manualEditTask = createManualEditTraceTask(
                access.projectId(),
                moduleId,
                artifactType,
                requestedContent,
                contentMarkdown,
                request.reason(),
                username
        );

        Map<String, Object> content = hasRequestedContent
                ? copyContent(requestedContent)
                : previous == null ? new LinkedHashMap<>() : copyContent(previous.getContent());

        if (previous == null && "MODULE_PRD".equals(artifactType)) {
            content.putIfAbsent("artifactType", "MODULE_PRD");
            content.putIfAbsent("markdown", contentMarkdown);
            content.putIfAbsent("contentMarkdown", contentMarkdown);
            content.putIfAbsent("prd_contract_version", "MANUAL_BOOTSTRAP");
            Map<String, Object> bootstrap = new LinkedHashMap<>();
            bootstrap.put("schemaVersion", "1.0");
            bootstrap.put("source", "A9.10.14.4.3.1_PRD_MANUAL_BOOTSTRAP_SAVE_FIX");
            bootstrap.put("note", "当前 MODULE_PRD 由已有文档视图首次手动保存为 artifact_version。");
            content.put("_manual_prd_bootstrap", bootstrap);
        }

        if (previous == null && "MODULE_APP_CONTRACT".equals(artifactType)) {
            content.putIfAbsent("artifactType", "MODULE_APP_CONTRACT");
            content.putIfAbsent("schemaVersion", "1.0");
            content.putIfAbsent("source", "MODULE_APP_CONTRACT_MANUAL_BOOTSTRAP");
            Map<String, Object> bootstrap = new LinkedHashMap<>();
            bootstrap.put("schemaVersion", "A9.10.14.4.3.15.4.6.2.2");
            bootstrap.put("source", "MODULE_APP_CONTRACT_CURRENT_ARTIFACT_AUTHORITY_READBACK");
            bootstrap.put("note", "由模块完整原型工作台首次保存 MODULE_APP_CONTRACT current artifact。");
            content.put("_manual_module_app_contract_bootstrap", bootstrap);
        }

        if ("UI_CONTRACT".equals(artifactType)) {
            content = validateAndNormalizeUiContractContent(content);
            contentMarkdown = serializeContentForMarkdown(content);
        }

        // A9.10.14.4.3_PRD_PROTOTYPE_SYNC_LOOP
        content = attachPrdPrototypeSemanticSyncMetadata(
                content,
                previous,
                artifactType,
                now,
                username,
                request.reason()
        );

        content.put(
                "_manual_document_edit",
                buildManualEditMetadata(
                        previous,
                        now,
                        username,
                        request.reason(),
                        artifactType
                )
        );

        // A9.10.14.4.3.15.4.7.6: PAGE_SPEC is a hidden/internal asset.
        // Semantic prototype edits should update it in the background before the
        // new UI_CONTRACT version is saved, so PAGE_SPEC will not appear newer
        // than UI_CONTRACT in Development Handoff.
        if ("UI_CONTRACT".equals(artifactType)) {
            Map<String, Object> pageSpecBackgroundSync = applyPrototypeSemanticPageSpecBackgroundSync(
                    access.projectId(),
                    moduleId,
                    content,
                    now,
                    username,
                    request.reason()
            );

            if (!pageSpecBackgroundSync.isEmpty()) {
                content.put("_page_spec_background_sync", pageSpecBackgroundSync);
            }

            // A9.10.14.4.3.15.4.7.8: semantic prototype edits are authoritative
            // for user-facing requirements as well.  After hidden PAGE_SPEC sync,
            // backfill the relevant MODULE_PRD page requirement section automatically.
            Map<String, Object> modulePrdBackgroundPatch = applyPrototypeSemanticModulePrdBackgroundPatch(
                    access.projectId(),
                    moduleId,
                    content,
                    now,
                    username,
                    request.reason()
            );

            if (!modulePrdBackgroundPatch.isEmpty()) {
                content.put("_module_prd_background_sync", modulePrdBackgroundPatch);
            }
        }

        if ("UI_CONTRACT".equals(artifactType)) {
            contentMarkdown = serializeContentForMarkdown(content);
        }

        int maxVersionNo = artifactVersionRepository.findMaxModuleArtifactVersionNo(
                access.projectId(),
                moduleId,
                artifactType
        );

        artifactVersionRepository.markPreviousModuleArtifactsAsNotCurrent(
                access.projectId(),
                moduleId,
                artifactType
        );

        ArtifactVersionEntity saved = new ArtifactVersionEntity();
        saved.setProjectId(access.projectId());
        saved.setModuleId(moduleId);
        saved.setTaskId(manualEditTask.getId());
        saved.setArtifactType(artifactType);
        saved.setVersionNo(maxVersionNo + 1);
        saved.setTitle(resolveManualEditArtifactTitle(previous, artifactType));
        saved.setContent(content);
        saved.setContentMarkdown(contentMarkdown);
        saved.setStatus("PENDING_REVIEW");
        saved.setCurrent(true);
        saved.setCreatedBy(username);
        copyPromptTemplateMetadata(manualEditTask, saved);

        ArtifactVersionEntity persisted = artifactVersionRepository.save(saved);

        markManualEditTraceTaskSuccess(manualEditTask, persisted, artifactType);

        List<String> downstreamArtifactTypes = resolveDownstreamArtifactTypes(artifactType, content);

        int downstreamMarkedCount = 0;

        if (!downstreamArtifactTypes.isEmpty()) {
            downstreamMarkedCount = artifactVersionRepository.markCurrentModuleArtifactsNeedsReview(
                    access.projectId(),
                    List.of(moduleId),
                    downstreamArtifactTypes
            );
        }

        return toResponse(
                persisted,
                downstreamArtifactTypes,
                downstreamMarkedCount
        );
    }

    private ManualArtifactEditResponse saveModulePrototypeSnapshot(
            UUID projectId,
            UUID moduleId,
            Map<String, Object> requestedContent,
            String reason
    ) {
        if (requestedContent == null || requestedContent.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "MODULE_PROTOTYPE_SNAPSHOT \u5185\u5bb9\u4e0d\u80fd\u4e3a\u7a7a\u3002"
            );
        }

        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        String username = currentUser.username();
        LocalDateTime now = LocalDateTime.now();

        // A9.10.14.4.3.2_MANUAL_EDIT_TASK_TRACE_FIX: baseline confirmation is also stored as artifact_version and needs a trace task.
        AgentTaskEntity manualEditTask = createManualEditTraceTask(
                projectId,
                moduleId,
                "MODULE_PROTOTYPE_SNAPSHOT",
                requestedContent,
                "",
                reason,
                username
        );

        ArtifactVersionEntity previous = artifactVersionRepository
                .findFirstByProjectIdAndModuleIdAndArtifactTypeAndCurrentTrueOrderByVersionNoDesc(
                        projectId,
                        moduleId,
                        "MODULE_PROTOTYPE_SNAPSHOT"
                )
                .orElse(null);

        Map<String, Object> content = copyContent(requestedContent);

        Map<String, Object> versionClosure = validateModulePrototypeSnapshotCurrentBaseline(
                projectId,
                moduleId,
                content,
                now,
                username
        );

        content.put("_module_prototype_version_closure", versionClosure);

        Map<String, Object> confirmation = new LinkedHashMap<>();
        confirmation.put("schemaVersion", "A9.10.13.3.1");
        confirmation.put("editMode", "MODULE_PROTOTYPE_BASELINE_CONFIRM");
        confirmation.put("authority", "DEVELOPMENT_AGENT_CODEGEN_BASELINE");
        confirmation.put("confirmedAt", now.toString());
        confirmation.put("confirmedBy", username);
        confirmation.put("reason", safeTrim(reason));
        confirmation.put("versionClosurePolicy", "MODULE_APP_CONTRACT_AND_PAGE_UI_CONTRACT_CURRENT_VERSION_LOCK");
        confirmation.put("moduleAppArtifact", versionClosure.get("moduleAppArtifact"));
        confirmation.put("pageVersionLocks", versionClosure.get("pages"));

        if (previous != null && previous.getId() != null) {
            confirmation.put("previousSnapshotArtifactId", previous.getId().toString());
            confirmation.put("previousSnapshotVersionNo", previous.getVersionNo());
        }

        content.put("_module_prototype_confirmation", confirmation);
        String contentMarkdown = serializeContentForMarkdown(content);

        int maxVersionNo = artifactVersionRepository.findMaxModuleArtifactVersionNo(
                projectId,
                moduleId,
                "MODULE_PROTOTYPE_SNAPSHOT"
        );

        artifactVersionRepository.markPreviousModuleArtifactsAsNotCurrent(
                projectId,
                moduleId,
                "MODULE_PROTOTYPE_SNAPSHOT"
        );

        ArtifactVersionEntity saved = new ArtifactVersionEntity();
        saved.setProjectId(projectId);
        saved.setModuleId(moduleId);
        saved.setTaskId(manualEditTask.getId());
        saved.setArtifactType("MODULE_PROTOTYPE_SNAPSHOT");
        saved.setVersionNo(maxVersionNo + 1);
        saved.setTitle(
                previous != null && !safeTrim(previous.getTitle()).isBlank()
                        ? previous.getTitle()
                        : "\u6a21\u5757\u6c47\u603b\u539f\u578b\u5f00\u53d1\u57fa\u7ebf"
        );
        saved.setContent(content);
        saved.setContentMarkdown(contentMarkdown);
        saved.setStatus("CONFIRMED");
        saved.setCurrent(true);
        saved.setCreatedBy(username);

        copyPromptTemplateMetadata(manualEditTask, saved);

        ArtifactVersionEntity persisted = artifactVersionRepository.save(saved);

        markManualEditTraceTaskSuccess(manualEditTask, persisted, "MODULE_PROTOTYPE_SNAPSHOT");

        return new ManualArtifactEditResponse(
                persisted.getId(),
                persisted.getProjectId(),
                persisted.getModuleId(),
                persisted.getArtifactType(),
                persisted.getVersionNo(),
                persisted.getTitle(),
                persisted.getStatus(),
                persisted.getCurrent(),
                persisted.getContent(),
                persisted.getContentMarkdown(),
                persisted.getCreatedBy(),
                persisted.getCreatedAt(),
                persisted.getUpdatedAt(),
                List.of(),
                0,
                "模块汇总原型已确认为开发基线 V" + persisted.getVersionNo()
                        + "；已校验当前 MODULE_APP_CONTRACT 与可用 PAGE_UI_CONTRACT 版本锁。"
        );
    }


    private Map<String, Object> validateModulePrototypeSnapshotCurrentBaseline(
            UUID projectId,
            UUID moduleId,
            Map<String, Object> content,
            LocalDateTime validatedAt,
            String username
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", "A9.10.14.4.3.15.4.7.10");
        result.put("source", "MODULE_PROTOTYPE_SNAPSHOT_VERSION_CLOSURE");
        result.put("policy", "确认模块开发基线时，必须锁定 current MODULE_APP_CONTRACT 和 current PAGE_UI_CONTRACT 版本。");
        result.put("validatedAt", validatedAt == null ? null : validatedAt.toString());
        result.put("validatedBy", username);

        Map<String, Object> moduleAppLock = syncMap(content.get("moduleAppArtifact"));
        ArtifactVersionEntity currentModuleApp = artifactVersionRepository
                .findFirstByProjectIdAndModuleIdAndArtifactTypeAndCurrentTrueOrderByVersionNoDesc(
                        projectId,
                        moduleId,
                        "MODULE_APP_CONTRACT"
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "当前模块尚无 current MODULE_APP_CONTRACT，请先保存模块完整原型后再确认开发基线。"
                ));

        validateSnapshotArtifactLock(
                "MODULE_APP_CONTRACT",
                "模块完整原型",
                moduleAppLock,
                currentModuleApp
        );

        result.put(
                "moduleAppArtifact",
                buildModulePrototypeArtifactVersionLock(
                        "MODULE_APP_CONTRACT",
                        currentModuleApp
                )
        );

        List<Object> pages = syncList(content.get("pages"));
        List<Map<String, Object>> pageLocks = new java.util.ArrayList<>();
        int usablePageCount = 0;

        for (Object value : pages) {
            Map<String, Object> page = syncMap(value);
            if (page.isEmpty() || !Boolean.TRUE.equals(page.get("hasUiContract"))) {
                continue;
            }

            usablePageCount++;

            String pageName = firstNonBlankSync(
                    safeText(page.get("pageName")),
                    safeText(page.get("pageKey")),
                    safeText(page.get("nodeKey")),
                    "页面"
            );
            String nodeId = firstNonBlankSync(
                    safeText(page.get("nodeId")),
                    safeText(page.get("moduleId")),
                    safeText(page.get("pageModuleId"))
            );

            if (nodeId.isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        pageName + " 缺少 PAGE 节点 ID，无法锁定 PAGE_UI_CONTRACT 版本；请重新打开模块汇总原型后再确认开发基线。"
                );
            }

            UUID pageModuleId = parseUuid(
                    nodeId,
                    "pages[].nodeId"
            );

            ArtifactVersionEntity currentPageUi = artifactVersionRepository
                    .findFirstByProjectIdAndModuleIdAndArtifactTypeAndCurrentTrueOrderByVersionNoDesc(
                            projectId,
                            pageModuleId,
                            "UI_CONTRACT"
                    )
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            pageName + " 尚无 current PAGE_UI_CONTRACT，请先生成并保存页面交互原型。"
                    ));

            validateSnapshotArtifactLock(
                    "UI_CONTRACT",
                    pageName + " 的页面交互原型",
                    page,
                    currentPageUi
            );

            Map<String, Object> pageLock = buildModulePrototypeArtifactVersionLock(
                    "UI_CONTRACT",
                    currentPageUi
            );
            pageLock.put("pageKey", page.get("pageKey"));
            pageLock.put("pageName", page.get("pageName"));
            pageLock.put("nodeId", pageModuleId.toString());
            pageLock.put("nodeKey", page.get("nodeKey"));
            pageLocks.add(pageLock);
        }

        if (usablePageCount <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "当前模块汇总原型没有任何可用 PAGE_UI_CONTRACT；请先生成并保存至少一个页面交互原型。"
            );
        }

        result.put("pageCount", pages.size());
        result.put("usablePageCount", usablePageCount);
        result.put("validatedPageCount", pageLocks.size());
        result.put("pages", pageLocks);

        return result;
    }

    private void validateSnapshotArtifactLock(
            String artifactType,
            String label,
            Map<String, Object> snapshotLock,
            ArtifactVersionEntity current
    ) {
        if (current == null || current.getId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    label + " 当前版本不存在，请刷新后重新确认开发基线。"
            );
        }

        String lockedArtifactId = firstNonBlankSync(
                safeText(snapshotLock.get("artifactId")),
                safeText(snapshotLock.get("artifact_id"))
        );
        Integer lockedVersionNo = firstNonNullInt(
                snapshotLock.get("artifactVersionNo"),
                snapshotLock.get("artifact_version_no"),
                snapshotLock.get("versionNo"),
                snapshotLock.get("version_no")
        );

        if (lockedArtifactId.isBlank() || lockedVersionNo == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    label + " 缺少 artifactId/versionNo 锁定信息；请重新打开模块汇总原型后再确认开发基线。"
            );
        }

        if (!current.getId().toString().equals(lockedArtifactId)
                || !current.getVersionNo().equals(lockedVersionNo)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    label + " 已有更新版本，不能确认旧模块汇总原型为开发基线。请重新打开模块完整预览并再次确认。"
            );
        }

        String currentType = safeText(current.getArtifactType());
        if (!artifactType.equals(currentType)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    label + " 版本类型不匹配：" + currentType
            );
        }
    }

    private Map<String, Object> buildModulePrototypeArtifactVersionLock(
            String artifactType,
            ArtifactVersionEntity artifact
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("artifactType", artifactType);
        result.put("artifactId", artifact == null || artifact.getId() == null ? null : artifact.getId().toString());
        result.put("artifactVersionNo", artifact == null ? null : artifact.getVersionNo());
        result.put("status", artifact == null ? null : artifact.getStatus());
        result.put("current", artifact == null ? null : artifact.getCurrent());
        result.put("updatedAt", artifact == null || artifact.getUpdatedAt() == null ? null : artifact.getUpdatedAt().toString());
        return result;
    }

    private Integer firstNonNullInt(
            Object... values
    ) {
        if (values == null) {
            return null;
        }

        for (Object value : values) {
            Integer parsed = intValue(value);
            if (parsed != null) {
                return parsed;
            }
        }

        return null;
    }

    private Integer intValue(
            Object value
    ) {
        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return number.intValue();
        }

        String text = safeText(value);
        if (text.isBlank()) {
            return null;
        }

        try {
            return Integer.parseInt(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String normalizeArtifactType(
            String value
    ) {
        String artifactType = safeTrim(value).toUpperCase();

        if (artifactType.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "缺少 artifactType。"
            );
        }

        return artifactType;
    }

    private UUID parseUuid(
            String value,
            String fieldName
    ) {
        try {
            return UUID.fromString(value);
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "非法 " + fieldName + "：" + value
            );
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> copyContent(
            Map<String, Object> content
    ) {
        if (content == null || content.isEmpty()) {
            return new LinkedHashMap<>();
        }

        return objectMapper.convertValue(
                content,
                LinkedHashMap.class
        );
    }



    private Map<String, Object> validateAndNormalizeUiContractContent(
            Map<String, Object> rawContent
    ) {
        if (rawContent == null || rawContent.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "UI_CONTRACT 内容不能为空。"
            );
        }

        Map<String, Object> content = copyContent(rawContent);
        String artifactType = safeText(content.get("artifactType"));
        if (artifactType.isBlank()) {
            content.put("artifactType", "UI_CONTRACT");
        } else if (!"UI_CONTRACT".equals(artifactType)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "当前保存内容不是 UI_CONTRACT：" + artifactType
            );
        }

        String schemaVersion = safeText(content.get("schemaVersion"));
        if (schemaVersion.isBlank()) {
            content.put("schemaVersion", "1.0");
        } else if (!"1.0".equals(schemaVersion)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "当前只允许保存 schemaVersion=1.0 的 UI_CONTRACT。"
            );
        }

        Map<String, Object> page = asMap(
                content.get("page"),
                "page"
        );
        List<Object> componentTree = asList(
                page.get("componentTree"),
                "page.componentTree"
        );
        if (componentTree.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "UI_CONTRACT 至少需要一个页面组件。"
            );
        }

        normalizeRequiredPageText(page, "pageId", "page_id");
        normalizeRequiredPageText(page, "pageKey", "page_key");
        normalizeRequiredPageText(page, "pageName", "页面预览");
        normalizeRequiredPageText(page, "route", "/");
        normalizeRequiredPageText(page, "title", safeText(page.get("pageName")));
        normalizeRequiredPageText(page, "description", "");
        normalizeRequiredPageText(page, "pageKind", "BUSINESS_PAGE");

        Object layoutValue = page.get("layout");
        if (!(layoutValue instanceof Map<?, ?>)) {
            Map<String, Object> layout = new LinkedHashMap<>();
            layout.put("type", safeText(layoutValue).isBlank() ? "CUSTOM_GRID" : safeText(layoutValue));
            page.put("layout", layout);
        }

        UiContractReferenceIndex index = buildUiContractReferenceIndex(componentTree);
        normalizeListField(content, "interactions");
        normalizeListField(content, "validationRules");
        normalizeListField(content, "pageStates");
        normalizeListField(content, "mockScenarios");
        normalizeListField(content, "apiBindings");
        normalizeListField(content, "navigationTargets");
        normalizeListField(content, "featureCoverage");
        normalizeListField(content, "assumptions");

        if (!(content.get("mockData") instanceof Map<?, ?>)) {
            content.put("mockData", new LinkedHashMap<>());
        }
        if (!(content.get("developmentRules") instanceof Map<?, ?>)) {
            content.put("developmentRules", new LinkedHashMap<>());
        }
        if (!(content.get("qualitySummary") instanceof Map<?, ?>)) {
            Map<String, Object> qualitySummary = new LinkedHashMap<>();
            qualitySummary.put("score", 85);
            qualitySummary.put("coverageRate", 100);
            qualitySummary.put("generationMode", "LLM_STRUCTURED");
            qualitySummary.put("blockers", List.of());
            qualitySummary.put("warnings", List.of("A9.5 自然语言原型编辑后端保存校验已自动补齐质量摘要。"));
            content.put("qualitySummary", qualitySummary);
        }

        validateUiContractInteractions(content, index.componentIds());
        validateUiContractFeatureCoverage(content, index.componentIds());
        validateUiContractMockScenarios(content);
        validateUiContractApiBindings(content);

        content.put("page", page);
        return content;
    }

    private UiContractReferenceIndex buildUiContractReferenceIndex(
            List<Object> componentTree
    ) {
        java.util.Set<String> componentIds = new java.util.LinkedHashSet<>();
        collectUiContractComponents(
                componentTree,
                componentIds,
                "page.componentTree"
        );
        return new UiContractReferenceIndex(componentIds);
    }

    private void collectUiContractComponents(
            List<Object> components,
            java.util.Set<String> componentIds,
            String path
    ) {
        for (int index = 0; index < components.size(); index++) {
            Object value = components.get(index);
            Map<String, Object> component = asMap(
                    value,
                    path + "[" + index + "]"
            );

            String componentId = safeText(component.get("componentId"));
            if (componentId.isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "组件缺少 componentId：" + path + "[" + index + "]"
                );
            }
            if (!componentIds.add(componentId)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "组件 componentId 重复：" + componentId
                );
            }

            String type = safeText(component.get("type"));
            if (type.isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "组件缺少 type：" + componentId
                );
            }

            Object childrenValue = component.get("children");
            if (childrenValue == null) {
                component.put("children", List.of());
            } else {
                collectUiContractComponents(
                        asList(childrenValue, path + "[" + index + "].children"),
                        componentIds,
                        path + "[" + index + "].children"
                );
            }
        }
    }

    private void validateUiContractInteractions(
            Map<String, Object> content,
            java.util.Set<String> componentIds
    ) {
        List<Object> interactions = asList(
                content.get("interactions"),
                "interactions"
        );
        java.util.Set<String> actionIds = new java.util.LinkedHashSet<>();

        for (int index = 0; index < interactions.size(); index++) {
            Map<String, Object> action = asMap(
                    interactions.get(index),
                    "interactions[" + index + "]"
            );
            String actionId = safeText(action.get("actionId"));
            if (actionId.isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "交互动作缺少 actionId：interactions[" + index + "]"
                );
            }
            if (!actionIds.add(actionId)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "交互动作 actionId 重复：" + actionId
                );
            }

            String type = safeText(action.get("type"));
            if (type.isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "交互动作缺少 type：" + actionId
                );
            }

            String sourceComponentId = safeText(action.get("sourceComponentId"));
            if (!sourceComponentId.isBlank() && !componentIds.contains(sourceComponentId)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "交互动作 sourceComponentId 不存在：" + sourceComponentId
                );
            }

            String targetId = safeText(action.get("targetId"));
            if (!targetId.isBlank() && !componentIds.contains(targetId)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "交互动作 targetId 不存在：" + targetId
                );
            }
        }

        validateComponentActionIds(
                asList(asMap(content.get("page"), "page").get("componentTree"), "page.componentTree"),
                actionIds
        );
        validateActionChains(interactions, actionIds);
    }

    private void validateComponentActionIds(
            List<Object> components,
            java.util.Set<String> actionIds
    ) {
        for (Object value : components) {
            Map<String, Object> component = asMap(value, "component");
            String componentId = safeText(component.get("componentId"));
            for (String actionId : toStringList(component.get("actionIds"))) {
                if (!actionIds.contains(actionId)) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "组件引用了不存在的 actionId：" + componentId + " -> " + actionId
                    );
                }
            }

            Object children = component.get("children");
            if (children instanceof List<?>) {
                validateComponentActionIds(
                        asList(children, componentId + ".children"),
                        actionIds
                );
            }
        }
    }

    private void validateActionChains(
            List<Object> interactions,
            java.util.Set<String> actionIds
    ) {
        for (Object value : interactions) {
            Map<String, Object> action = asMap(value, "interaction");
            String actionId = safeText(action.get("actionId"));
            for (String chainedActionId : toStringList(action.get("successActionIds"))) {
                if (!actionIds.contains(chainedActionId)) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "动作 successActionIds 引用了不存在的 actionId：" + actionId + " -> " + chainedActionId
                    );
                }
            }
            for (String chainedActionId : toStringList(action.get("failureActionIds"))) {
                if (!actionIds.contains(chainedActionId)) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "动作 failureActionIds 引用了不存在的 actionId：" + actionId + " -> " + chainedActionId
                    );
                }
            }
        }
    }

    private void validateUiContractFeatureCoverage(
            Map<String, Object> content,
            java.util.Set<String> componentIds
    ) {
        java.util.Set<String> actionIds = collectActionIds(content);
        List<Object> featureCoverage = asList(
                content.get("featureCoverage"),
                "featureCoverage"
        );

        for (Object value : featureCoverage) {
            Map<String, Object> coverage = asMap(value, "featureCoverage[]");
            String featureName = safeText(
                    coverage.get("featureName")
            );
            for (String componentId : toStringList(coverage.get("componentIds"))) {
                if (!componentIds.contains(componentId)) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "featureCoverage 引用了不存在的 componentId：" + featureName + " -> " + componentId
                    );
                }
            }
            for (String actionId : toStringList(coverage.get("actionIds"))) {
                if (!actionIds.contains(actionId)) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "featureCoverage 引用了不存在的 actionId：" + featureName + " -> " + actionId
                    );
                }
            }
        }
    }

    private void validateUiContractMockScenarios(
            Map<String, Object> content
    ) {
        java.util.Set<String> actionIds = collectActionIds(content);
        List<Object> mockScenarios = asList(
                content.get("mockScenarios"),
                "mockScenarios"
        );

        for (Object value : mockScenarios) {
            Map<String, Object> scenario = asMap(value, "mockScenarios[]");
            String actionId = safeText(scenario.get("actionId"));
            if (!actionId.isBlank() && !actionIds.contains(actionId)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "mockScenarios 引用了不存在的 actionId：" + actionId
                );
            }
        }
    }

    private void validateUiContractApiBindings(
            Map<String, Object> content
    ) {
        java.util.Set<String> actionIds = collectActionIds(content);
        List<Object> apiBindings = asList(
                content.get("apiBindings"),
                "apiBindings"
        );

        for (Object value : apiBindings) {
            Map<String, Object> binding = asMap(value, "apiBindings[]");
            for (String actionId : toStringList(binding.get("usedByActionIds"))) {
                if (!actionIds.contains(actionId)) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "apiBindings.usedByActionIds 引用了不存在的 actionId：" + actionId
                    );
                }
            }
        }
    }

    private java.util.Set<String> collectActionIds(
            Map<String, Object> content
    ) {
        java.util.Set<String> result = new java.util.LinkedHashSet<>();
        for (Object value : asList(content.get("interactions"), "interactions")) {
            Map<String, Object> action = asMap(value, "interaction");
            String actionId = safeText(action.get("actionId"));
            if (!actionId.isBlank()) {
                result.add(actionId);
            }
        }
        return result;
    }

    private void normalizeListField(
            Map<String, Object> source,
            String key
    ) {
        Object value = source.get(key);
        if (!(value instanceof List<?>)) {
            source.put(key, List.of());
        }
    }

    private void normalizeRequiredPageText(
            Map<String, Object> page,
            String key,
            String fallback
    ) {
        if (safeText(page.get(key)).isBlank()) {
            page.put(key, safeText(fallback));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(
            Object value,
            String path
    ) {
        if (value instanceof Map<?, ?>) {
            return objectMapper.convertValue(value, LinkedHashMap.class);
        }

        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "字段必须是对象：" + path
        );
    }

    @SuppressWarnings("unchecked")
    private List<Object> asList(
            Object value,
            String path
    ) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?>) {
            return objectMapper.convertValue(value, List.class);
        }

        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "字段必须是数组：" + path
        );
    }

    private List<String> toStringList(
            Object value
    ) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }

        return values.stream()
                .map(this::safeText)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private record UiContractReferenceIndex(
            java.util.Set<String> componentIds
    ) {
    }


    private String serializeContentForMarkdown(
            Map<String, Object> content
    ) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(content);
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "资产内容无法序列化为 JSON。"
            );
        }
    }

    private String normalizeContentForCompare(
            Map<String, Object> content
    ) {
        try {
            Map<String, Object> normalized = copyContent(content);
            normalized.remove("_manual_document_edit");

            Object metadataValue = normalized.get("metadata");
            if (metadataValue instanceof Map<?, ?>) {
                @SuppressWarnings("unchecked")
                Map<String, Object> metadata = objectMapper.convertValue(
                        metadataValue,
                        LinkedHashMap.class
                );
                metadata.remove("prototypeSaveTrace");
                normalized.put("metadata", metadata);
            }

            return objectMapper.writeValueAsString(normalized);
        } catch (Exception ex) {
            return String.valueOf(content);
        }
    }

    private Map<String, Object> buildManualEditMetadata(
            ArtifactVersionEntity previous,
            LocalDateTime editedAt,
            String editedBy,
            String reason,
            String artifactType
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("schemaVersion", "1.0");
        metadata.put(
                "editMode",
                "UI_CONTRACT".equals(artifactType)
                        ? "UI_CONTRACT_PROTOTYPE_EDIT"
                        : "MANUAL_DOCUMENT_VIEW"
        );
        metadata.put("sourceArtifactId", previous == null || previous.getId() == null ? null : previous.getId().toString());
        metadata.put("sourceVersionNo", previous == null ? null : previous.getVersionNo());
        metadata.put("editedAt", editedAt.toString());
        metadata.put("editedBy", editedBy);
        metadata.put("reason", safeTrim(reason));
        metadata.put(
                "cardViewPolicy",
                "UI_CONTRACT".equals(artifactType)
                        ? "STRUCTURED_UI_CONTRACT_CONTENT"
                        : "MANUAL_MARKDOWN_SECTIONS"
        );
        metadata.put(
                "note",
                "UI_CONTRACT".equals(artifactType)
                        ? "当前版本由自然语言原型编辑确认保存生成，content 为结构化 UI_CONTRACT。"
                        : "当前版本由文档视图手动编辑生成，卡片视图应根据 contentMarkdown 重新拆分展示。"
        );
        return metadata;
    }


    private Map<String, Object> attachPrdPrototypeSemanticSyncMetadata(
            Map<String, Object> content,
            ArtifactVersionEntity previous,
            String artifactType,
            LocalDateTime editedAt,
            String editedBy,
            String reason
    ) {
        Map<String, Object> result = content == null
                ? new LinkedHashMap<>()
                : content;

        if (!"MODULE_PRD".equals(artifactType)
                && !"BUSINESS_FLOW".equals(artifactType)
                && !"PAGE_SPEC".equals(artifactType)
                && !"UI_CONTRACT".equals(artifactType)
                && !"MODULE_APP_CONTRACT".equals(artifactType)
                && !"MODULE_PROTOTYPE_SNAPSHOT".equals(artifactType)) {
            return result;
        }

        Map<String, Object> sync = new LinkedHashMap<>();
        sync.put("schemaVersion", "A9.10.14.4.3");
        sync.put("source", "MANUAL_ARTIFACT_EDIT_PRD_PROTOTYPE_SYNC");
        sync.put("sourceArtifactType", artifactType);
        sync.put("sourceArtifactId", previous == null || previous.getId() == null ? null : previous.getId().toString());
        sync.put("sourceVersionNo", previous == null ? null : previous.getVersionNo());
        sync.put("editedAt", editedAt == null ? null : editedAt.toString());
        sync.put("editedBy", editedBy);
        sync.put("reason", safeTrim(reason));

        if ("UI_CONTRACT".equals(artifactType)) {
            Map<String, Object> prototypeEditSync = classifyPrototypeLocalEdits(result);
            sync.put("prototypeEditSync", prototypeEditSync);

            if (Boolean.TRUE.equals(prototypeEditSync.get("visualOnly"))) {
                sync.put("syncDirection", "UI_CONTRACT_ONLY");
                sync.put("status", "UI_CONTRACT_ONLY_VISUAL_CHANGE");
                sync.put("targetArtifactTypes", List.of());
                sync.put("proposal", buildVisualOnlyPrototypeSyncProposal(prototypeEditSync, reason));
            } else {
                sync.put("syncDirection", "PROTOTYPE_TO_PRD");
                sync.put("status", "PRD_SYNC_PROPOSAL_REQUIRED");
                sync.put("targetArtifactTypes", List.of("MODULE_PRD", "PAGE_SPEC"));
                sync.put("proposal", buildPrototypeToPrdSyncProposal(result, previous, reason, prototypeEditSync));
            }
        } else if ("MODULE_APP_CONTRACT".equals(artifactType)
                || "MODULE_PROTOTYPE_SNAPSHOT".equals(artifactType)) {
            sync.put("syncDirection", "PROTOTYPE_BASELINE_TO_PRD");
            sync.put("status", "PRD_SYNC_REVIEW_REQUIRED");
            sync.put("targetArtifactTypes", List.of("MODULE_PRD", "PAGE_SPEC", "UI_CONTRACT"));
            sync.put("proposal", Map.of(
                    "summary", "模块级原型或开发基线发生变化，请核对 PRD、PAGE_SPEC 与各 PAGE_UI_CONTRACT 是否需要同步更新。",
                    "reviewFocus", List.of("页面清单", "导航关系", "页面职责", "交互动作", "开发基线版本")
            ));
        } else {
            sync.put("syncDirection", "PRD_TO_PROTOTYPE");
            sync.put("status", "PROTOTYPE_IMPACT_REVIEW_REQUIRED");
            sync.put("targetArtifactTypes", resolveDownstreamArtifactTypes(artifactType));
            sync.put("proposal", Map.of(
                    "summary", artifactType + " 已保存新版本；如果本次需求变化影响页面字段、按钮、表格列、跳转或状态，请重新生成对应页面交互原型。",
                    "reviewFocus", List.of(
                            "相关页面是否需要重新生成交互原型",
                            "页面字段是否变化",
                            "按钮动作是否变化",
                            "表格列是否变化",
                            "弹窗表单是否变化",
                            "导航目标是否变化",
                            "接口绑定和验收规则是否需要同步"
                    ),
                    "userAction", "进入受影响页面，点击“重新生成交互原型”。"
            ));
        }

        result.put("_prd_prototype_semantic_sync", sync);
        return result;
    }


    private Map<String, Object> applyPrototypeSemanticPageSpecBackgroundSync(
            UUID projectId,
            UUID moduleId,
            Map<String, Object> uiContract,
            LocalDateTime now,
            String username,
            String reason
    ) {
        Map<String, Object> sync = syncMap(uiContract.get("_prd_prototype_semantic_sync"));
        String status = safeText(sync.get("status"));

        if (!"PRD_SYNC_PROPOSAL_REQUIRED".equals(status)
                && !"PRD_SYNC_REVIEW_REQUIRED".equals(status)) {
            return Map.of();
        }

        Map<String, Object> prototypeEditSync = syncMap(sync.get("prototypeEditSync"));
        if (Boolean.TRUE.equals(prototypeEditSync.get("visualOnly"))) {
            return Map.of();
        }

        ArtifactVersionEntity previousPageSpec = artifactVersionRepository
                .findFirstByProjectIdAndModuleIdAndArtifactTypeAndCurrentTrueOrderByVersionNoDesc(
                        projectId,
                        moduleId,
                        "PAGE_SPEC"
                )
                .orElse(null);

        Map<String, Object> pageSpecContent = previousPageSpec == null
                ? buildBootstrapPageSpecFromUiContract(uiContract)
                : copyContent(previousPageSpec.getContent());

        if (pageSpecContent.isEmpty()) {
            pageSpecContent = buildBootstrapPageSpecFromUiContract(uiContract);
        }

        mergeUiContractSemanticsIntoPageSpec(
                pageSpecContent,
                uiContract
        );

        Map<String, Object> backgroundSync = buildPageSpecBackgroundSyncMetadata(
                uiContract,
                prototypeEditSync,
                previousPageSpec,
                now,
                username,
                reason
        );
        pageSpecContent.put("_prototype_semantic_background_sync", backgroundSync);

        String pageSpecMarkdown = serializeContentForMarkdown(pageSpecContent);

        AgentTaskEntity pageSpecSyncTask = createManualEditTraceTask(
                projectId,
                moduleId,
                "PAGE_SPEC",
                pageSpecContent,
                pageSpecMarkdown,
                reason,
                username
        );

        int pageSpecMaxVersionNo = artifactVersionRepository.findMaxModuleArtifactVersionNo(
                projectId,
                moduleId,
                "PAGE_SPEC"
        );

        artifactVersionRepository.markPreviousModuleArtifactsAsNotCurrent(
                projectId,
                moduleId,
                "PAGE_SPEC"
        );

        ArtifactVersionEntity savedPageSpec = new ArtifactVersionEntity();
        savedPageSpec.setProjectId(projectId);
        savedPageSpec.setModuleId(moduleId);
        savedPageSpec.setTaskId(pageSpecSyncTask.getId());
        savedPageSpec.setArtifactType("PAGE_SPEC");
        savedPageSpec.setVersionNo(pageSpecMaxVersionNo + 1);
        savedPageSpec.setTitle(resolveManualEditArtifactTitle(previousPageSpec, "PAGE_SPEC"));
        savedPageSpec.setContent(pageSpecContent);
        savedPageSpec.setContentMarkdown(pageSpecMarkdown);
        savedPageSpec.setStatus("ACTIVE");
        savedPageSpec.setCurrent(true);
        savedPageSpec.setCreatedBy(username);
        copyPromptTemplateMetadata(pageSpecSyncTask, savedPageSpec);

        ArtifactVersionEntity persistedPageSpec = artifactVersionRepository.save(savedPageSpec);

        markManualEditTraceTaskSuccess(
                pageSpecSyncTask,
                persistedPageSpec,
                "PAGE_SPEC"
        );

        Map<String, Object> applied = new LinkedHashMap<>(backgroundSync);
        applied.put("pageSpecArtifactId", persistedPageSpec.getId() == null ? null : persistedPageSpec.getId().toString());
        applied.put("pageSpecVersionNo", persistedPageSpec.getVersionNo());
        applied.put("pageSpecStatus", persistedPageSpec.getStatus());
        applied.put("pageSpecUpdatedAt", persistedPageSpec.getUpdatedAt() == null ? null : persistedPageSpec.getUpdatedAt().toString());
        applied.put("previousPageSpecArtifactId", previousPageSpec == null || previousPageSpec.getId() == null ? null : previousPageSpec.getId().toString());
        applied.put("previousPageSpecVersionNo", previousPageSpec == null ? null : previousPageSpec.getVersionNo());

        markUiContractPageSpecBackgroundSyncApplied(
                uiContract,
                applied
        );

        return applied;
    }

    private void markUiContractPageSpecBackgroundSyncApplied(
            Map<String, Object> uiContract,
            Map<String, Object> pageSpecBackgroundSync
    ) {
        Map<String, Object> sync = syncMap(uiContract.get("_prd_prototype_semantic_sync"));
        Map<String, Object> proposal = syncMap(sync.get("proposal"));
        Map<String, Object> metadata = syncMap(uiContract.get("metadata"));

        sync.put("syncDirection", "PROTOTYPE_TO_PAGE_SPEC_BACKGROUND_SYNC");
        sync.put("status", "PAGE_SPEC_BACKGROUND_SYNC_APPLIED");
        sync.put("pageSpecSyncStatus", "APPLIED");
        sync.put("requiresManualConfirmation", false);
        sync.put(
                "pageSpecAutoSyncPolicy",
                Map.of(
                        "mode", "AUTO_BACKGROUND_HIDDEN_PAGE_SPEC_SYNC",
                        "locality", "CURRENT_MODULE_PAGE_SPEC_ARTIFACT",
                        "traceability", "pageSpecBackgroundSync",
                        "visibleToUser", false,
                        "userConfirmation", false
                )
        );
        sync.put("targetArtifactTypes", List.of("MODULE_PRD"));
        sync.put("pageSpecBackgroundSync", pageSpecBackgroundSync);
        sync.put("message", "PAGE_SPEC 已作为隐藏/内部页面规范在后台自动同步；用户无需手动维护 PAGE_SPEC。");

        proposal.put("pageSpecBackgroundSync", pageSpecBackgroundSync);
        proposal.put("pageSpecApplyPolicy", "PAGE_SPEC 已后台自动同步；MODULE_PRD 仅保留轻量复核建议，不阻断 UI_CONTRACT 保存。");
        proposal.put("applyPolicy", "PAGE_SPEC 已后台自动同步为 current artifact；不直接改写 MODULE_PRD 正文。");

        sync.put("proposal", proposal);
        uiContract.put("_prd_prototype_semantic_sync", sync);

        metadata.put("prototypeSemanticSyncAppliedAt", pageSpecBackgroundSync.get("appliedAt"));
        metadata.put("absorbedPageSpecArtifactId", pageSpecBackgroundSync.get("pageSpecArtifactId"));
        metadata.put("absorbedPageSpecVersionNo", pageSpecBackgroundSync.get("pageSpecVersionNo"));
        metadata.put("absorbedPageSpecSyncSource", "PROTOTYPE_SEMANTIC_EDIT_BACKGROUND_SYNC");
        uiContract.put("metadata", metadata);
    }

    private Map<String, Object> buildPageSpecBackgroundSyncMetadata(
            Map<String, Object> uiContract,
            Map<String, Object> prototypeEditSync,
            ArtifactVersionEntity previousPageSpec,
            LocalDateTime now,
            String username,
            String reason
    ) {
        Map<String, Object> page = syncMap(uiContract.get("page"));
        Map<String, Object> metadata = syncMap(uiContract.get("metadata"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", "A9.10.14.4.3.15.4.7.6");
        result.put("source", "PROTOTYPE_SEMANTIC_EDIT_PAGE_SPEC_BACKGROUND_SYNC");
        result.put("mode", "AUTO_SYNC_HIDDEN_PAGE_SPEC_FROM_UI_CONTRACT");
        result.put("appliedAt", now == null ? null : now.toString());
        result.put("appliedBy", username);
        result.put("reason", safeTrim(reason));
        result.put("pageId", page.get("pageId"));
        result.put("pageKey", page.get("pageKey"));
        result.put("pageName", firstNonBlankSync(safeText(page.get("pageName")), safeText(page.get("title"))));
        result.put("route", page.get("route"));
        result.put("prototypeEditSync", prototypeEditSync == null ? Map.of() : prototypeEditSync);
        result.put("prototypeLocalEditRevision", metadata.get("localEditRevision"));
        result.put("previousPageSpecArtifactId", previousPageSpec == null || previousPageSpec.getId() == null ? null : previousPageSpec.getId().toString());
        result.put("previousPageSpecVersionNo", previousPageSpec == null ? null : previousPageSpec.getVersionNo());
        result.put("policy", "视觉修改不更新 PAGE_SPEC；字段、按钮、表格列、页面文案、显隐/必填等语义修改自动同步隐藏 PAGE_SPEC。");
        return result;
    }

    private Map<String, Object> buildBootstrapPageSpecFromUiContract(
            Map<String, Object> uiContract
    ) {
        Map<String, Object> page = syncMap(uiContract.get("page"));

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("artifact_type", "PAGE_SPEC");
        content.put("artifactType", "PAGE_SPEC");
        content.put("schemaVersion", "1.0");

        Map<String, Object> basicInfo = new LinkedHashMap<>();
        basicInfo.put("page_key", firstNonBlankSync(safeText(page.get("pageKey")), safeText(page.get("page_key")), "page"));
        basicInfo.put("page_name", firstNonBlankSync(safeText(page.get("pageName")), safeText(page.get("title")), "页面"));
        basicInfo.put("related_module_key", "");
        basicInfo.put("related_module_name", "");
        basicInfo.put("page_type", inferPageSpecPageType(page));
        basicInfo.put("route_suggestion", firstNonBlankSync(safeText(page.get("route")), safeText(page.get("routePath")), "/"));
        basicInfo.put("page_goal", firstNonBlankSync(safeText(page.get("description")), safeText(page.get("pageGoal")), "由交互原型语义修改后台同步生成。"));
        basicInfo.put("primary_user_role", "业务用户");
        content.put("basic_info", basicInfo);

        content.put("layout_sections", new java.util.ArrayList<>());
        content.put("component_specs", new java.util.ArrayList<>());
        content.put("action_specs", new java.util.ArrayList<>());
        content.put("form_fields", new java.util.ArrayList<>());
        content.put("table_columns", new java.util.ArrayList<>());
        content.put("api_bindings", new java.util.ArrayList<>());
        content.put("page_semantic_contract", new LinkedHashMap<>());
        content.put("page_states", new java.util.ArrayList<>());
        content.put("permission_specs", new java.util.ArrayList<>());
        content.put("exception_specs", new java.util.ArrayList<>());
        content.put("acceptance_criteria", new java.util.ArrayList<>());
        content.put("downstream_hints", Map.of(
                "data_model_hints", List.of(),
                "api_contract_hints", List.of(),
                "test_spec_hints", List.of(),
                "frontend_component_hints", List.of(),
                "dev_handoff_hints", List.of()
        ));
        content.put("development_notes", new java.util.ArrayList<>());
        content.put("testing_notes", new java.util.ArrayList<>());
        content.put("analysis_summary", "PAGE_SPEC 由交互原型语义修改后台同步初始化。");
        content.put("markdown", "");

        return content;
    }

    private void mergeUiContractSemanticsIntoPageSpec(
            Map<String, Object> pageSpecContent,
            Map<String, Object> uiContract
    ) {
        pageSpecContent.putIfAbsent("artifact_type", "PAGE_SPEC");
        pageSpecContent.putIfAbsent("artifactType", "PAGE_SPEC");

        Map<String, Object> page = syncMap(uiContract.get("page"));
        Map<String, Object> basicInfo = syncMap(pageSpecContent.get("basic_info"));
        basicInfo.put("page_key", firstNonBlankSync(safeText(page.get("pageKey")), safeText(page.get("page_key")), safeText(basicInfo.get("page_key")), "page"));
        basicInfo.put("page_name", firstNonBlankSync(safeText(page.get("pageName")), safeText(page.get("title")), safeText(basicInfo.get("page_name")), "页面"));
        basicInfo.put("page_type", firstNonBlankSync(safeText(basicInfo.get("page_type")), inferPageSpecPageType(page)));
        basicInfo.put("route_suggestion", firstNonBlankSync(safeText(page.get("route")), safeText(page.get("routePath")), safeText(basicInfo.get("route_suggestion")), "/"));
        basicInfo.put("page_goal", firstNonBlankSync(safeText(page.get("description")), safeText(page.get("pageGoal")), safeText(basicInfo.get("page_goal")), "由交互原型语义修改后台同步。"));
        basicInfo.putIfAbsent("primary_user_role", "业务用户");
        basicInfo.putIfAbsent("related_module_key", "");
        basicInfo.putIfAbsent("related_module_name", "");
        pageSpecContent.put("basic_info", basicInfo);

        List<Map<String, Object>> components = collectUiContractComponentRecords(uiContract);

        List<Object> uiFields = collectPageSpecFieldsFromUiComponents(components);
        List<Object> uiColumns = collectPageSpecColumnsFromUiComponents(components);
        List<Object> uiActions = collectPageSpecActionsFromUiContract(uiContract);

        pageSpecContent.put(
                "form_fields",
                mergePageSpecObjectsByKey(
                        syncList(pageSpecContent.get("form_fields")),
                        uiFields,
                        "field_key"
                )
        );
        pageSpecContent.put(
                "table_columns",
                mergePageSpecObjectsByKey(
                        syncList(pageSpecContent.get("table_columns")),
                        uiColumns,
                        "column_key"
                )
        );
        pageSpecContent.put(
                "action_specs",
                mergePageSpecObjectsByKey(
                        syncList(pageSpecContent.get("action_specs")),
                        uiActions,
                        "action_key"
                )
        );

        Map<String, Object> semanticContract = syncMap(pageSpecContent.get("page_semantic_contract"));
        semanticContract.put("source", "UI_CONTRACT_PROTOTYPE_SEMANTIC_BACKGROUND_SYNC");
        semanticContract.put("pageId", page.get("pageId"));
        semanticContract.put("pageKey", page.get("pageKey"));
        semanticContract.put("pageName", firstNonBlankSync(safeText(page.get("pageName")), safeText(page.get("title"))));
        semanticContract.put("route", firstNonBlankSync(safeText(page.get("route")), safeText(page.get("routePath"))));
        semanticContract.put("syncedFieldCount", uiFields.size());
        semanticContract.put("syncedColumnCount", uiColumns.size());
        semanticContract.put("syncedActionCount", uiActions.size());
        pageSpecContent.put("page_semantic_contract", semanticContract);

        List<Object> developmentNotes = new java.util.ArrayList<>(syncList(pageSpecContent.get("development_notes")));
        addPageSpecNote(
                developmentNotes,
                "页面规范已根据交互原型语义修改在后台自动同步；当前 UI_CONTRACT 版本已吸收该 PAGE_SPEC 更新。"
        );
        pageSpecContent.put("development_notes", developmentNotes);

        pageSpecContent.put(
                "analysis_summary",
                firstNonBlankSync(
                        safeText(pageSpecContent.get("analysis_summary")),
                        "PAGE_SPEC 已由交互原型语义修改后台同步。"
                )
        );
    }

    private List<Map<String, Object>> collectUiContractComponentRecords(
            Map<String, Object> uiContract
    ) {
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        Map<String, Object> page = syncMap(uiContract.get("page"));
        collectUiContractComponentsFromValue(
                page.get("componentTree"),
                result
        );
        collectUiContractComponentsFromValue(
                page.get("components"),
                result
        );
        collectUiContractComponentsFromValue(
                uiContract.get("componentTree"),
                result
        );
        collectUiContractComponentsFromValue(
                uiContract.get("components"),
                result
        );
        return result;
    }

    private void collectUiContractComponentsFromValue(
            Object value,
            List<Map<String, Object>> result
    ) {
        for (Object item : syncList(value)) {
            Map<String, Object> component = syncMap(item);
            if (component.isEmpty()) {
                continue;
            }

            if (!safeText(component.get("componentId")).isBlank()) {
                result.add(component);
            }

            for (String key : List.of("children", "components", "componentTree", "items", "body", "content")) {
                collectUiContractComponentsFromValue(
                        component.get(key),
                        result
                );
            }

            for (Object tabValue : syncList(component.get("tabs"))) {
                Map<String, Object> tab = syncMap(tabValue);
                collectUiContractComponentsFromValue(
                        tab.get("children"),
                        result
                );
                collectUiContractComponentsFromValue(
                        tab.get("components"),
                        result
                );
            }
        }
    }

    private List<Object> collectPageSpecFieldsFromUiComponents(
            List<Map<String, Object>> components
    ) {
        List<Object> fields = new java.util.ArrayList<>();

        for (Map<String, Object> component : components) {
            for (Object fieldValue : syncList(component.get("fields"))) {
                Map<String, Object> field = syncMap(fieldValue);
                String fieldKey = firstNonBlankSync(
                        safeText(field.get("fieldKey")),
                        safeText(field.get("field_key")),
                        safeText(field.get("key")),
                        safeText(field.get("name"))
                );

                if (fieldKey.isBlank()) {
                    continue;
                }

                Map<String, Object> spec = new LinkedHashMap<>();
                spec.put("field_key", fieldKey);
                spec.put("field_name", firstNonBlankSync(safeText(field.get("label")), safeText(field.get("fieldName")), fieldKey));
                spec.put("field_type", firstNonBlankSync(safeText(field.get("type")), safeText(field.get("fieldType")), "TEXT"));
                spec.put("required", Boolean.TRUE.equals(field.get("required")));
                spec.put("default_value", field.get("defaultValue"));
                spec.put("validation_rules", firstListValue(field.get("validationRules"), field.get("validationRuleIds")));
                spec.put("description", firstNonBlankSync(safeText(field.get("helpText")), safeText(field.get("description")), "由交互原型字段同步。"));
                spec.put("placeholder", safeText(field.get("placeholder")));
                spec.put("hidden", Boolean.TRUE.equals(field.get("hidden")));
                spec.put("disabled", Boolean.TRUE.equals(field.get("disabled")));
                fields.add(spec);
            }
        }

        return fields;
    }

    private List<Object> collectPageSpecColumnsFromUiComponents(
            List<Map<String, Object>> components
    ) {
        List<Object> columns = new java.util.ArrayList<>();

        for (Map<String, Object> component : components) {
            for (Object columnValue : syncList(component.get("columns"))) {
                Map<String, Object> column = syncMap(columnValue);
                String columnKey = firstNonBlankSync(
                        safeText(column.get("columnKey")),
                        safeText(column.get("column_key")),
                        safeText(column.get("key")),
                        safeText(column.get("dataIndex")),
                        safeText(column.get("dataField"))
                );

                if (columnKey.isBlank()) {
                    continue;
                }

                Map<String, Object> spec = new LinkedHashMap<>();
                spec.put("column_key", columnKey);
                spec.put("column_name", firstNonBlankSync(safeText(column.get("label")), safeText(column.get("title")), columnKey));
                spec.put("data_field", firstNonBlankSync(safeText(column.get("dataField")), safeText(column.get("dataIndex")), columnKey));
                spec.put("display_type", firstNonBlankSync(safeText(column.get("displayType")), safeText(column.get("type")), "TEXT"));
                spec.put("sortable", Boolean.TRUE.equals(column.get("sortable")));
                spec.put("filterable", Boolean.TRUE.equals(column.get("filterable")));
                spec.put("hidden", Boolean.TRUE.equals(column.get("hidden")));
                spec.put("description", firstNonBlankSync(safeText(column.get("description")), "由交互原型表格列同步。"));
                columns.add(spec);
            }
        }

        return columns;
    }

    private List<Object> collectPageSpecActionsFromUiContract(
            Map<String, Object> uiContract
    ) {
        List<Object> actions = new java.util.ArrayList<>();

        for (Object actionValue : syncList(uiContract.get("interactions"))) {
            Map<String, Object> action = syncMap(actionValue);
            String actionId = firstNonBlankSync(
                    safeText(action.get("actionId")),
                    safeText(action.get("action_key")),
                    safeText(action.get("key"))
            );

            if (actionId.isBlank()) {
                continue;
            }

            Map<String, Object> spec = new LinkedHashMap<>();
            spec.put("action_key", actionId);
            spec.put("action_name", firstNonBlankSync(safeText(action.get("label")), safeText(action.get("name")), actionId));
            spec.put("trigger_position", firstNonBlankSync(safeText(action.get("sourceComponentId")), safeText(action.get("targetId")), "交互原型按钮/动作"));
            spec.put("actor", "业务用户");
            spec.put("description", firstNonBlankSync(safeText(action.get("description")), "由交互原型动作同步。"));
            spec.put("interaction_type", firstNonBlankSync(safeText(action.get("type")), safeText(action.get("actionType")), "UI_ACTION"));
            spec.put("expected_result", firstNonBlankSync(safeText(action.get("expectedResult")), safeText(action.get("successMessage")), "页面状态、数据或导航按原型动作变化。"));
            spec.put("preconditions", firstListValue(action.get("preconditions")));
            spec.put("business_rules", firstListValue(action.get("businessRules"), action.get("rules")));
            spec.put("success_feedback", firstNonBlankSync(safeText(action.get("successMessage")), "操作成功。"));
            spec.put("failure_feedback", firstNonBlankSync(safeText(action.get("failureMessage")), safeText(action.get("errorMessage")), "操作失败时展示错误提示。"));
            spec.put("target_page_id", firstNonBlankSync(safeText(action.get("targetPageId")), safeText(action.get("targetPageKey"))));
            spec.put("target_id", safeText(action.get("targetId")));
            actions.add(spec);
        }

        return actions;
    }

    private List<Object> mergePageSpecObjectsByKey(
            List<Object> existing,
            List<Object> updates,
            String key
    ) {
        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();

        for (Object item : existing) {
            Map<String, Object> record = syncMap(item);
            String identity = safeText(record.get(key));
            if (!identity.isBlank()) {
                merged.put(identity, record);
            }
        }

        for (Object item : updates) {
            Map<String, Object> update = syncMap(item);
            String identity = safeText(update.get(key));
            if (identity.isBlank()) {
                continue;
            }

            Map<String, Object> current = merged.containsKey(identity)
                    ? new LinkedHashMap<>(merged.get(identity))
                    : new LinkedHashMap<>();

            current.putAll(update);
            merged.put(identity, current);
        }

        return new java.util.ArrayList<>(merged.values());
    }

    private List<Object> firstListValue(
            Object... values
    ) {
        if (values == null) {
            return List.of();
        }

        for (Object value : values) {
            List<Object> list = syncList(value);
            if (!list.isEmpty()) {
                return list;
            }
        }

        return List.of();
    }

    private void addPageSpecNote(
            List<Object> target,
            String note
    ) {
        String text = safeTrim(note);
        if (text.isBlank()) {
            return;
        }

        for (Object item : target) {
            if (text.equals(safeText(item))) {
                return;
            }
        }

        target.add(text);
    }

    private String inferPageSpecPageType(
            Map<String, Object> page
    ) {
        String kind = firstNonBlankSync(
                safeText(page.get("pageKind")),
                safeText(page.get("pageType")),
                safeText(page.get("type"))
        ).toUpperCase();

        if (kind.contains("DASHBOARD")) {
            return "DASHBOARD";
        }

        if (kind.contains("DETAIL")) {
            return "DETAIL";
        }

        if (kind.contains("FORM") || kind.contains("CREATE") || kind.contains("EDIT")) {
            return "FORM";
        }

        if (kind.contains("SETTING")) {
            return "SETTING";
        }

        if (kind.contains("WORKBENCH") || kind.contains("WORKFLOW")) {
            return "WORKBENCH";
        }

        if (kind.contains("WIZARD")) {
            return "WIZARD";
        }

        if (kind.contains("LIST") || kind.contains("TABLE")) {
            return "LIST";
        }

        return "OTHER";
    }


    private Map<String, Object> applyPrototypeSemanticModulePrdBackgroundPatch(
            UUID projectId,
            UUID moduleId,
            Map<String, Object> uiContract,
            LocalDateTime now,
            String username,
            String reason
    ) {
        Map<String, Object> sync = syncMap(uiContract.get("_prd_prototype_semantic_sync"));
        String status = safeText(sync.get("status"));

        if (!"PRD_SYNC_PROPOSAL_REQUIRED".equals(status)
                && !"PRD_SYNC_REVIEW_REQUIRED".equals(status)
                && !"PAGE_SPEC_BACKGROUND_SYNC_APPLIED".equals(status)) {
            return Map.of();
        }

        Map<String, Object> prototypeEditSync = syncMap(sync.get("prototypeEditSync"));
        if (Boolean.TRUE.equals(prototypeEditSync.get("visualOnly"))) {
            return Map.of();
        }

        ArtifactVersionEntity previousModulePrd = artifactVersionRepository
                .findFirstByProjectIdAndModuleIdAndArtifactTypeAndCurrentTrueOrderByVersionNoDesc(
                        projectId,
                        moduleId,
                        "MODULE_PRD"
                )
                .orElse(null);

        Map<String, Object> modulePrdContent = previousModulePrd == null
                ? buildBootstrapModulePrdFromUiContract(uiContract)
                : copyContent(previousModulePrd.getContent());

        if (modulePrdContent.isEmpty()) {
            modulePrdContent = buildBootstrapModulePrdFromUiContract(uiContract);
        }

        mergeUiContractSemanticsIntoModulePrd(
                modulePrdContent,
                uiContract,
                prototypeEditSync,
                now,
                username,
                reason
        );

        String modulePrdMarkdown = buildModulePrdMarkdownAfterPrototypeSync(
                modulePrdContent,
                uiContract,
                previousModulePrd
        );

        modulePrdContent.put("markdown", modulePrdMarkdown);
        modulePrdContent.put("contentMarkdown", modulePrdMarkdown);
        modulePrdContent.put("content_markdown", modulePrdMarkdown);

        AgentTaskEntity modulePrdSyncTask = createManualEditTraceTask(
                projectId,
                moduleId,
                "MODULE_PRD",
                modulePrdContent,
                modulePrdMarkdown,
                reason,
                username
        );

        int modulePrdMaxVersionNo = artifactVersionRepository.findMaxModuleArtifactVersionNo(
                projectId,
                moduleId,
                "MODULE_PRD"
        );

        artifactVersionRepository.markPreviousModuleArtifactsAsNotCurrent(
                projectId,
                moduleId,
                "MODULE_PRD"
        );

        ArtifactVersionEntity savedModulePrd = new ArtifactVersionEntity();
        savedModulePrd.setProjectId(projectId);
        savedModulePrd.setModuleId(moduleId);
        savedModulePrd.setTaskId(modulePrdSyncTask.getId());
        savedModulePrd.setArtifactType("MODULE_PRD");
        savedModulePrd.setVersionNo(modulePrdMaxVersionNo + 1);
        savedModulePrd.setTitle(resolveManualEditArtifactTitle(previousModulePrd, "MODULE_PRD"));
        savedModulePrd.setContent(modulePrdContent);
        savedModulePrd.setContentMarkdown(modulePrdMarkdown);
        savedModulePrd.setStatus("ACTIVE");
        savedModulePrd.setCurrent(true);
        savedModulePrd.setCreatedBy(username);
        copyPromptTemplateMetadata(modulePrdSyncTask, savedModulePrd);

        ArtifactVersionEntity persistedModulePrd = artifactVersionRepository.save(savedModulePrd);

        markManualEditTraceTaskSuccess(
                modulePrdSyncTask,
                persistedModulePrd,
                "MODULE_PRD"
        );

        Map<String, Object> applied = buildModulePrdBackgroundSyncMetadata(
                uiContract,
                prototypeEditSync,
                previousModulePrd,
                persistedModulePrd,
                now,
                username,
                reason
        );

        enrichModulePrdBackgroundSyncReliability(applied);
        modulePrdContent.put("_prototype_semantic_background_prd_sync", applied);
        persistedModulePrd.setContent(modulePrdContent);
        persistedModulePrd.setContentMarkdown(modulePrdMarkdown);
        artifactVersionRepository.save(persistedModulePrd);

        markUiContractModulePrdBackgroundPatchApplied(
                uiContract,
                applied
        );

        return applied;
    }

    private void enrichModulePrdBackgroundSyncReliability(
            Map<String, Object> applied
    ) {
        if (applied == null || applied.isEmpty()) {
            return;
        }

        applied.put("autoSyncReliabilityVersion", "A9.10.14.4.3.15.4.10.1");
        applied.put("autoSyncReliabilityToken", "A9.10.14.4.3.15.4.10.1_MODULE_PRD_AUTO_SYNC_RELIABILITY");
        applied.put("requiresManualConfirmation", false);
        applied.put("syncLocality", "CURRENT_MODULE_PRD_ARTIFACT_ONLY");
        applied.put("syncPatchMode", "APPEND_AND_MERGE_LOCAL_SEMANTIC_SECTIONS");
        applied.put("persistenceMode", "EXPLICIT_RESAVE_AFTER_METADATA_ATTACH");
        applied.put(
                "traceabilityPolicy",
                Map.of(
                        "sourceArtifact", "UI_CONTRACT",
                        "targetArtifact", "MODULE_PRD",
                        "scope", "MODULE_LOCAL_BACKGROUND_SYNC",
                        "userConfirmation", "NOT_REQUIRED",
                        "visibleDocumentRewrite", "NO_FULL_REWRITE"
                )
        );
    }

    private void markUiContractModulePrdBackgroundPatchApplied(
            Map<String, Object> uiContract,
            Map<String, Object> modulePrdBackgroundSync
    ) {
        Map<String, Object> sync = syncMap(uiContract.get("_prd_prototype_semantic_sync"));
        Map<String, Object> proposal = syncMap(sync.get("proposal"));
        Map<String, Object> metadata = syncMap(uiContract.get("metadata"));

        sync.put("syncDirection", "PROTOTYPE_TO_REQUIREMENT_BACKGROUND_SYNC");
        sync.put("status", "MODULE_PRD_BACKGROUND_SYNC_APPLIED");
        sync.put("modulePrdSyncStatus", "APPLIED");
        sync.put("requiresManualConfirmation", false);
        sync.put(
                "modulePrdAutoSyncPolicy",
                Map.of(
                        "mode", "AUTO_BACKGROUND_LOCAL_PATCH",
                        "locality", "CURRENT_MODULE_PRD_ARTIFACT",
                        "traceability", "modulePrdBackgroundSync",
                        "fullRewrite", false,
                        "userConfirmation", false
                )
        );
        sync.put("targetArtifactTypes", List.of());
        sync.put("modulePrdBackgroundSync", modulePrdBackgroundSync);
        sync.put("message", "PAGE_SPEC 与 MODULE_PRD 已根据原型语义修改在后台自动同步；用户无需手动维护中间页面规范。");

        proposal.put("modulePrdBackgroundSync", modulePrdBackgroundSync);
        proposal.put("modulePrdApplyPolicy", "MODULE_PRD 已根据原型语义修改执行局部追加/合并式反写。");
        proposal.put("applyPolicy", "PAGE_SPEC 与 MODULE_PRD 已后台自动同步；后续只需重新确认模块汇总原型基线。");

        sync.put("proposal", proposal);
        uiContract.put("_prd_prototype_semantic_sync", sync);

        metadata.put("prototypeModulePrdSyncAppliedAt", modulePrdBackgroundSync.get("appliedAt"));
        metadata.put("absorbedModulePrdArtifactId", modulePrdBackgroundSync.get("modulePrdArtifactId"));
        metadata.put("absorbedModulePrdVersionNo", modulePrdBackgroundSync.get("modulePrdVersionNo"));
        metadata.put("absorbedModulePrdSyncSource", "PROTOTYPE_SEMANTIC_EDIT_MODULE_PRD_BACKGROUND_PATCH");
        metadata.put("prototypeRequirementAutoSyncVersion", "A9.10.14.4.3.15.4.10.1");
        metadata.put("prototypeRequirementAutoSyncToken", "A9.10.14.4.3.15.4.10.1_MODULE_PRD_AUTO_SYNC_RELIABILITY");
        metadata.put("prototypeRequirementAutoSyncMode", "PAGE_SPEC_AND_MODULE_PRD_AUTO_BACKGROUND_SYNC");
        metadata.put("prototypeRequirementAutoSyncLocality", "LOCAL_MODULE_ARTIFACTS_ONLY");
        metadata.put("prototypeRequirementAutoSyncTraceable", true);
        uiContract.put("metadata", metadata);
    }

    private Map<String, Object> buildModulePrdBackgroundSyncMetadata(
            Map<String, Object> uiContract,
            Map<String, Object> prototypeEditSync,
            ArtifactVersionEntity previousModulePrd,
            ArtifactVersionEntity persistedModulePrd,
            LocalDateTime now,
            String username,
            String reason
    ) {
        Map<String, Object> page = syncMap(uiContract.get("page"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", "A9.10.14.4.3.15.4.10.1");
        result.put("previousSchemaVersion", "A9.10.14.4.3.15.4.7.8");
        result.put("source", "PROTOTYPE_SEMANTIC_EDIT_MODULE_PRD_BACKGROUND_PATCH");
        result.put("mode", "AUTO_PATCH_MODULE_PRD_FROM_UI_CONTRACT");
        result.put("autoSyncPolicy", "LOCAL_TRACEABLE_BACKGROUND_PATCH_WITHOUT_USER_CONFIRMATION");
        result.put("requiresManualConfirmation", false);
        result.put("appliedAt", now == null ? null : now.toString());
        result.put("appliedBy", username);
        result.put("reason", safeTrim(reason));
        result.put("pageId", page.get("pageId"));
        result.put("pageKey", page.get("pageKey"));
        result.put("pageName", firstNonBlankSync(safeText(page.get("pageName")), safeText(page.get("title"))));
        result.put("route", page.get("route"));
        result.put("prototypeEditSync", prototypeEditSync == null ? Map.of() : prototypeEditSync);
        result.put("previousModulePrdArtifactId", previousModulePrd == null || previousModulePrd.getId() == null ? null : previousModulePrd.getId().toString());
        result.put("previousModulePrdVersionNo", previousModulePrd == null ? null : previousModulePrd.getVersionNo());
        result.put("modulePrdArtifactId", persistedModulePrd == null || persistedModulePrd.getId() == null ? null : persistedModulePrd.getId().toString());
        result.put("modulePrdVersionNo", persistedModulePrd == null ? null : persistedModulePrd.getVersionNo());
        result.put("policy", "视觉修改不更新 MODULE_PRD；字段、按钮、表格列、页面文案、显隐/必填等语义修改自动反写 MODULE_PRD。");
        return result;
    }

    private Map<String, Object> buildBootstrapModulePrdFromUiContract(
            Map<String, Object> uiContract
    ) {
        Map<String, Object> page = syncMap(uiContract.get("page"));

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("artifact_type", "MODULE_PRD");
        content.put("artifactType", "MODULE_PRD");
        content.put("prd_contract_version", "2.0");

        Map<String, Object> basicInfo = new LinkedHashMap<>();
        basicInfo.put("module_key", firstNonBlankSync(safeText(page.get("pageKey")), safeText(page.get("pageId")), "prototype_page"));
        basicInfo.put("module_name", firstNonBlankSync(safeText(page.get("pageName")), safeText(page.get("title")), "页面需求"));
        basicInfo.put("module_type", "PAGE_MODULE");
        basicInfo.put("module_position", "由交互原型语义修改自动初始化。");
        basicInfo.put("module_goal", firstNonBlankSync(safeText(page.get("description")), "保持页面需求与交互原型一致。"));
        basicInfo.put("responsibility_boundary", "记录当前页面的字段、动作、表格列、状态和导航要求。");
        content.put("module_basic_info", basicInfo);

        content.put("business_background", "该 PRD 由交互原型语义修改后台反写生成。");
        content.put("user_roles", List.of(Map.of(
                "role_key", "business_user",
                "role_name", "业务用户",
                "description", "使用当前页面完成业务操作。"
        )));
        content.put("functional_requirements", new java.util.ArrayList<>());
        content.put("business_rules", new java.util.ArrayList<>());
        content.put("state_rules", new java.util.ArrayList<>());
        content.put("page_requirements", new java.util.ArrayList<>());
        content.put("data_requirements", new java.util.ArrayList<>());
        content.put("api_requirements", new java.util.ArrayList<>());
        content.put("permission_requirements", new java.util.ArrayList<>());
        content.put("exception_scenarios", new java.util.ArrayList<>());
        content.put("acceptance_criteria", new java.util.ArrayList<>());
        content.put("non_functional_requirements", new java.util.ArrayList<>());
        content.put("downstream_artifact_hints", Map.of(
                "business_flow_hints", List.of(),
                "page_spec_hints", List.of("根据交互原型语义同步页面规范。"),
                "data_model_hints", List.of(),
                "api_contract_hints", List.of(),
                "test_spec_hints", List.of(),
                "dev_handoff_hints", List.of("重新确认模块汇总原型后进入开发。")
        ));
        content.put("development_notes", new java.util.ArrayList<>());
        content.put("testing_notes", new java.util.ArrayList<>());
        content.put("analysis_summary", "MODULE_PRD 由交互原型语义修改后台反写初始化。");
        content.put("markdown", "");

        return content;
    }

    private void mergeUiContractSemanticsIntoModulePrd(
            Map<String, Object> modulePrdContent,
            Map<String, Object> uiContract,
            Map<String, Object> prototypeEditSync,
            LocalDateTime now,
            String username,
            String reason
    ) {
        modulePrdContent.putIfAbsent("artifact_type", "MODULE_PRD");
        modulePrdContent.putIfAbsent("artifactType", "MODULE_PRD");
        modulePrdContent.putIfAbsent("prd_contract_version", "2.0");

        Map<String, Object> page = syncMap(uiContract.get("page"));
        List<Map<String, Object>> components = collectUiContractComponentRecords(uiContract);
        List<Object> fields = collectPageSpecFieldsFromUiComponents(components);
        List<Object> columns = collectPageSpecColumnsFromUiComponents(components);
        List<Object> actions = collectPageSpecActionsFromUiContract(uiContract);

        String pageKey = firstNonBlankSync(safeText(page.get("pageKey")), safeText(page.get("pageId")), "prototype_page");
        String pageName = firstNonBlankSync(safeText(page.get("pageName")), safeText(page.get("title")), pageKey);
        String route = firstNonBlankSync(safeText(page.get("route")), safeText(page.get("routePath")), "/");

        Map<String, Object> pageRequirement = new LinkedHashMap<>();
        pageRequirement.put("page_key", pageKey);
        pageRequirement.put("page_name", pageName);
        pageRequirement.put("description", firstNonBlankSync(safeText(page.get("description")), "由交互原型语义修改自动同步。"));
        pageRequirement.put("main_components", buildModulePrdMainComponents(fields, columns, actions));
        pageRequirement.put("user_actions", extractDisplayValues(actions, "action_name", "action_key"));
        pageRequirement.put("page_type", inferPageSpecPageType(page));
        pageRequirement.put("route_path", route);
        pageRequirement.put("layout_type", firstNonBlankSync(safeText(syncMap(page.get("layout")).get("type")), safeText(page.get("layout")), "CUSTOM_GRID"));
        pageRequirement.put("primary_entity", firstNonBlankSync(safeText(page.get("primaryEntity")), safeText(page.get("entityName"))));
        pageRequirement.put("source_page_id", firstNonBlankSync(safeText(page.get("pageId")), pageKey));
        pageRequirement.put("sync_status", "SYNCED_FROM_UI_CONTRACT_PROTOTYPE_EDIT");
        pageRequirement.put("filter_fields", List.of());
        pageRequirement.put("table_columns", extractDisplayValues(columns, "column_name", "column_key"));
        pageRequirement.put("form_fields", extractDisplayValues(fields, "field_name", "field_key"));
        pageRequirement.put("primary_actions", extractDisplayValues(actions, "action_name", "action_key"));
        pageRequirement.put("row_actions", List.of());
        pageRequirement.put("navigation_targets", extractNavigationTargets(uiContract));

        List<Object> pageRequirements = mergePageSpecObjectsByKey(
                syncList(modulePrdContent.get("page_requirements")),
                List.of(pageRequirement),
                "page_key"
        );
        modulePrdContent.put("page_requirements", pageRequirements);
        modulePrdContent.put("pageRequirements", pageRequirements);

        Map<String, Object> functionalRequirement = buildPrototypeFunctionalRequirement(
                pageKey,
                pageName,
                fields,
                columns,
                actions,
                reason
        );
        modulePrdContent.put(
                "functional_requirements",
                mergePageSpecObjectsByKey(
                        syncList(modulePrdContent.get("functional_requirements")),
                        List.of(functionalRequirement),
                        "requirement_key"
                )
        );

        Map<String, Object> businessRule = buildPrototypeBusinessRule(
                pageKey,
                pageName,
                prototypeEditSync
        );
        modulePrdContent.put(
                "business_rules",
                mergePageSpecObjectsByKey(
                        syncList(modulePrdContent.get("business_rules")),
                        List.of(businessRule),
                        "rule_key"
                )
        );

        Map<String, Object> acceptanceCriterion = buildPrototypeAcceptanceCriterion(
                pageKey,
                pageName
        );
        modulePrdContent.put(
                "acceptance_criteria",
                mergePageSpecObjectsByKey(
                        syncList(modulePrdContent.get("acceptance_criteria")),
                        List.of(acceptanceCriterion),
                        "criterion_key"
                )
        );

        Map<String, Object> semanticPatch = new LinkedHashMap<>();
        semanticPatch.put("schemaVersion", "A9.10.14.4.3.15.4.7.8");
        semanticPatch.put("source", "PROTOTYPE_SEMANTIC_EDIT_MODULE_PRD_BACKGROUND_PATCH");
        semanticPatch.put("appliedAt", now == null ? null : now.toString());
        semanticPatch.put("appliedBy", username);
        semanticPatch.put("reason", safeTrim(reason));
        semanticPatch.put("pageKey", pageKey);
        semanticPatch.put("pageName", pageName);
        semanticPatch.put("semanticChangeTypes", syncList(prototypeEditSync.get("semanticChangeTypes")));
        semanticPatch.put("fieldCount", fields.size());
        semanticPatch.put("columnCount", columns.size());
        semanticPatch.put("actionCount", actions.size());
        modulePrdContent.put("_prototype_semantic_prd_patch", semanticPatch);

        List<Object> developmentNotes = new java.util.ArrayList<>(syncList(modulePrdContent.get("development_notes")));
        addPageSpecNote(
                developmentNotes,
                "MODULE_PRD 已根据交互原型语义修改自动反写；若需进入开发，请重新确认模块汇总原型基线。"
        );
        modulePrdContent.put("development_notes", developmentNotes);

        modulePrdContent.put(
                "analysis_summary",
                firstNonBlankSync(
                        safeText(modulePrdContent.get("analysis_summary")),
                        "MODULE_PRD 已由交互原型语义修改后台反写。"
                )
        );
    }

    private Map<String, Object> buildPrototypeFunctionalRequirement(
            String pageKey,
            String pageName,
            List<Object> fields,
            List<Object> columns,
            List<Object> actions,
            String reason
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requirement_key", "FR_PROTO_" + stableRequirementKey(pageKey));
        result.put("title", pageName + "交互原型语义同步");
        result.put("description", "页面需求应与当前交互原型保持一致：字段 "
                + String.join("、", extractDisplayValues(fields, "field_name", "field_key"))
                + "；表格列 "
                + String.join("、", extractDisplayValues(columns, "column_name", "column_key"))
                + "；动作 "
                + String.join("、", extractDisplayValues(actions, "action_name", "action_key"))
                + "。");
        result.put("priority", "MUST");
        result.put("input_description", "以用户确认保存的 UI_CONTRACT 语义修改为输入。");
        result.put("output_description", "页面字段、动作、表格列、导航与当前交互原型一致。");
        result.put("preconditions", List.of("用户完成语义类原型修改并确认保存。"));
        result.put("postconditions", List.of("MODULE_PRD 与 PAGE_SPEC 已同步该语义修改。", safeTrim(reason)));
        return result;
    }

    private Map<String, Object> buildPrototypeBusinessRule(
            String pageKey,
            String pageName,
            Map<String, Object> prototypeEditSync
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rule_key", "BR_PROTO_" + stableRequirementKey(pageKey));
        result.put("title", pageName + "原型语义同步规则");
        result.put("description", "当交互原型发生字段、按钮、表格列、页面文案、显隐/必填或跳转等语义修改时，MODULE_PRD 和隐藏 PAGE_SPEC 必须同步更新。");
        result.put("trigger_condition", "prototypeEditSync.requiresRequirementSync=true");
        result.put("expected_result", "需求文档、页面规范与用户确认的交互原型语义保持一致。");
        result.put("semantic_change_types", syncList(prototypeEditSync.get("semanticChangeTypes")));
        return result;
    }

    private Map<String, Object> buildPrototypeAcceptanceCriterion(
            String pageKey,
            String pageName
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("criterion_key", "AC_PROTO_" + stableRequirementKey(pageKey));
        result.put("description", pageName + "的 PRD 页面需求、PAGE_SPEC 页面规范和 UI_CONTRACT 交互原型保持语义一致。");
        result.put("verification_method", "保存语义类原型修改后，检查 MODULE_PRD current artifact 中的 page_requirements 与当前 UI_CONTRACT 字段/动作/表格列一致。");
        return result;
    }

    private List<String> buildModulePrdMainComponents(
            List<Object> fields,
            List<Object> columns,
            List<Object> actions
    ) {
        List<String> result = new java.util.ArrayList<>();
        if (!fields.isEmpty()) {
            result.add("表单字段：" + String.join("、", extractDisplayValues(fields, "field_name", "field_key")));
        }
        if (!columns.isEmpty()) {
            result.add("表格列：" + String.join("、", extractDisplayValues(columns, "column_name", "column_key")));
        }
        if (!actions.isEmpty()) {
            result.add("页面动作：" + String.join("、", extractDisplayValues(actions, "action_name", "action_key")));
        }
        if (result.isEmpty()) {
            result.add("页面组件按交互原型定义展示。");
        }
        return result;
    }

    private List<String> extractDisplayValues(
            List<Object> records,
            String labelKey,
            String fallbackKey
    ) {
        List<String> result = new java.util.ArrayList<>();

        for (Object item : records) {
            Map<String, Object> record = syncMap(item);
            addUnique(result, firstNonBlankSync(
                    safeText(record.get(labelKey)),
                    safeText(record.get(fallbackKey))
            ));
        }

        return result;
    }

    private List<String> extractNavigationTargets(
            Map<String, Object> uiContract
    ) {
        List<String> result = new java.util.ArrayList<>();

        for (Object item : syncList(uiContract.get("navigationTargets"))) {
            Map<String, Object> target = syncMap(item);
            addUnique(result, firstNonBlankSync(
                    safeText(target.get("targetPageKey")),
                    safeText(target.get("targetPageId")),
                    safeText(target.get("pageKey")),
                    safeText(target.get("pageId")),
                    safeText(target.get("label"))
            ));
        }

        for (Object item : syncList(uiContract.get("interactions"))) {
            Map<String, Object> action = syncMap(item);
            addUnique(result, firstNonBlankSync(
                    safeText(action.get("targetPageKey")),
                    safeText(action.get("targetPageId"))
            ));
        }

        return result;
    }

    private String buildModulePrdMarkdownAfterPrototypeSync(
            Map<String, Object> modulePrdContent,
            Map<String, Object> uiContract,
            ArtifactVersionEntity previousModulePrd
    ) {
        String baseMarkdown = firstNonBlankSync(
                previousModulePrd == null ? "" : safeText(previousModulePrd.getContentMarkdown()),
                safeText(modulePrdContent.get("markdown")),
                safeText(modulePrdContent.get("contentMarkdown")),
                "# 模块 PRD\n"
        );

        Map<String, Object> page = syncMap(uiContract.get("page"));
        String pageKey = firstNonBlankSync(safeText(page.get("pageKey")), safeText(page.get("pageId")), "prototype_page");
        String normalizedKey = stableRequirementKey(pageKey);
        String startMarker = "<!-- CODEMIND_PROTO_PRD_SYNC_START:" + normalizedKey + " -->";
        String endMarker = "<!-- CODEMIND_PROTO_PRD_SYNC_END:" + normalizedKey + " -->";
        String block = buildModulePrdPrototypeSyncMarkdownBlock(
                modulePrdContent,
                uiContract,
                startMarker,
                endMarker
        );

        int start = baseMarkdown.indexOf(startMarker);
        int end = baseMarkdown.indexOf(endMarker);

        if (start >= 0 && end > start) {
            int endAfterMarker = end + endMarker.length();
            return baseMarkdown.substring(0, start).stripTrailing()
                    + "\n\n"
                    + block
                    + "\n\n"
                    + baseMarkdown.substring(endAfterMarker).stripLeading();
        }

        return baseMarkdown.stripTrailing() + "\n\n" + block + "\n";
    }

    private String buildModulePrdPrototypeSyncMarkdownBlock(
            Map<String, Object> modulePrdContent,
            Map<String, Object> uiContract,
            String startMarker,
            String endMarker
    ) {
        Map<String, Object> page = syncMap(uiContract.get("page"));
        String pageKey = firstNonBlankSync(safeText(page.get("pageKey")), safeText(page.get("pageId")), "prototype_page");
        String pageName = firstNonBlankSync(safeText(page.get("pageName")), safeText(page.get("title")), pageKey);

        List<Map<String, Object>> components = collectUiContractComponentRecords(uiContract);
        List<Object> fields = collectPageSpecFieldsFromUiComponents(components);
        List<Object> columns = collectPageSpecColumnsFromUiComponents(components);
        List<Object> actions = collectPageSpecActionsFromUiContract(uiContract);

        StringBuilder builder = new StringBuilder();
        builder.append("## 原型语义同步（系统自动更新）\n\n");
        builder.append(startMarker).append("\n\n");
        builder.append("### ").append(pageName).append("\n\n");
        builder.append("- 页面 Key：").append(pageKey).append("\n");
        builder.append("- 路由：").append(firstNonBlankSync(safeText(page.get("route")), safeText(page.get("routePath")), "/")).append("\n");
        builder.append("- 同步来源：交互原型语义修改保存\n\n");

        builder.append("#### 字段要求\n\n");
        appendBulletList(builder, extractDisplayValues(fields, "field_name", "field_key"));

        builder.append("\n#### 表格列要求\n\n");
        appendBulletList(builder, extractDisplayValues(columns, "column_name", "column_key"));

        builder.append("\n#### 页面动作与跳转\n\n");
        appendBulletList(builder, extractDisplayValues(actions, "action_name", "action_key"));

        builder.append("\n#### 验收要求\n\n");
        builder.append("- MODULE_PRD、隐藏 PAGE_SPEC 与 UI_CONTRACT 的字段、动作、表格列、页面文案和跳转语义保持一致。\n");
        builder.append("- 纯视觉布局修改不进入本节；业务语义修改会自动更新本节。\n\n");
        builder.append(endMarker);

        return builder.toString();
    }

    private void appendBulletList(
            StringBuilder builder,
            List<String> values
    ) {
        if (values == null || values.isEmpty()) {
            builder.append("- 暂无显式声明，按当前交互原型结构为准。\n");
            return;
        }

        for (String value : values) {
            builder.append("- ").append(safeText(value)).append("\n");
        }
    }

    private String stableRequirementKey(
            String value
    ) {
        String text = safeText(value).toLowerCase();
        if (text.isBlank()) {
            return "prototype_page";
        }

        StringBuilder builder = new StringBuilder();
        boolean lastUnderscore = false;

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            boolean allowed = (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9');

            if (allowed) {
                builder.append(ch);
                lastUnderscore = false;
            } else if (!lastUnderscore) {
                builder.append('_');
                lastUnderscore = true;
            }
        }

        String result = builder.toString();
        while (result.startsWith("_")) {
            result = result.substring(1);
        }
        while (result.endsWith("_")) {
            result = result.substring(0, result.length() - 1);
        }

        return result.isBlank() ? "prototype_page" : result;
    }

    private Map<String, Object> buildPrototypeToPrdSyncProposal(
            Map<String, Object> uiContract,
            ArtifactVersionEntity previous,
            String reason,
            Map<String, Object> prototypeEditSync
    ) {
        Map<String, Object> page = syncMap(uiContract.get("page"));

        List<String> componentIds = new java.util.ArrayList<>();
        List<String> fieldKeys = new java.util.ArrayList<>();
        List<String> columnKeys = new java.util.ArrayList<>();

        collectPrototypeSyncReferences(
                syncList(page.get("componentTree")),
                componentIds,
                fieldKeys,
                columnKeys
        );

        List<String> actionIds = new java.util.ArrayList<>();
        List<String> navigationTargets = new java.util.ArrayList<>();

        for (Object item : syncList(uiContract.get("interactions"))) {
            Map<String, Object> action = syncMap(item);
            addUnique(actionIds, safeText(action.get("actionId")));
            String targetPageId = safeText(action.get("targetPageId"));
            if (!targetPageId.isBlank()) {
                addUnique(navigationTargets, targetPageId);
            }
        }

        for (Object item : syncList(uiContract.get("navigationTargets"))) {
            Map<String, Object> target = syncMap(item);
            addUnique(navigationTargets, firstNonBlankSync(
                    safeText(target.get("targetPageId")),
                    safeText(target.get("pageId")),
                    safeText(target.get("targetPageKey")),
                    safeText(target.get("pageKey"))
            ));
        }

        Map<String, Object> proposal = new LinkedHashMap<>();
        proposal.put("summary", "交互原型已保存为新版本，需要核对 PRD/PAGE_SPEC 是否同步描述页面字段、按钮、表格列、弹窗和跳转。");
        proposal.put("baseUiContractArtifactId", previous == null || previous.getId() == null ? null : previous.getId().toString());
        proposal.put("baseUiContractVersionNo", previous == null ? null : previous.getVersionNo());
        proposal.put("pageId", page.get("pageId"));
        proposal.put("pageKey", page.get("pageKey"));
        proposal.put("pageName", firstNonBlankSync(safeText(page.get("pageName")), safeText(page.get("title"))));
        proposal.put("route", page.get("route"));
        proposal.put("reason", safeTrim(reason));
        proposal.put("prototypeEditSync", prototypeEditSync == null ? Map.of() : prototypeEditSync);
        proposal.put("changedSemanticSurface", Map.of(
                "componentIds", componentIds,
                "fieldKeys", fieldKeys,
                "columnKeys", columnKeys,
                "actionIds", actionIds,
                "navigationTargets", navigationTargets
        ));
        proposal.put("recommendedPrdPatchFocus", List.of(
                "页面职责是否变化",
                "字段/表格列是否需要写入需求说明",
                "新增/修改按钮是否需要补业务规则",
                "弹窗表单是否需要补校验规则",
                "跳转目标是否需要补导航要求",
                "接口绑定和验收标准是否需要同步"
        ));
        proposal.put("applyPolicy", "原型语义修改保存时，系统会后台同步隐藏 PAGE_SPEC，并对 MODULE_PRD 执行局部追加/合并式反写；纯视觉修改不触发需求资产同步。");

        return proposal;
    }


    private Map<String, Object> classifyPrototypeLocalEdits(
            Map<String, Object> uiContract
    ) {
        Map<String, Object> metadata = syncMap(uiContract.get("metadata"));
        List<Object> edits = syncList(metadata.get("prototypeLocalEdits"));

        List<Map<String, Object>> visualOnlyEdits = new java.util.ArrayList<>();
        List<Map<String, Object>> semanticEdits = new java.util.ArrayList<>();
        List<String> semanticChangeTypes = new java.util.ArrayList<>();
        List<String> visualChangeTypes = new java.util.ArrayList<>();

        for (Object item : edits) {
            Map<String, Object> edit = syncMap(item);
            if (edit.isEmpty()) {
                continue;
            }

            Map<String, Object> impact = syncMap(edit.get("requirementSyncImpact"));
            String classification = firstNonBlankSync(
                    safeText(impact.get("classification")),
                    safeText(edit.get("requirementSyncClassification")),
                    classifyPrototypeLocalEditFallback(edit)
            );

            copyStringList(semanticChangeTypes, syncList(impact.get("semanticChangeTypes")));
            copyStringList(visualChangeTypes, syncList(impact.get("visualChangeTypes")));

            if ("VISUAL_ONLY".equals(classification)) {
                visualOnlyEdits.add(edit);
            } else {
                semanticEdits.add(edit);
            }
        }

        boolean hasEdits = !edits.isEmpty();
        boolean visualOnly = hasEdits && semanticEdits.isEmpty();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", "A9.10.14.4.3.15.4.7.3");
        result.put("source", "PROTOTYPE_LOCAL_EDIT_REQUIREMENT_SYNC_CLASSIFIER");
        result.put("mode", visualOnly ? "UI_CONTRACT_ONLY" : "REQUIREMENT_SYNC_REVIEW_REQUIRED");
        result.put("visualOnly", visualOnly);
        result.put("requiresRequirementSync", !visualOnly);
        result.put("totalEditCount", edits.size());
        result.put("visualOnlyEditCount", visualOnlyEdits.size());
        result.put("semanticEditCount", semanticEdits.size());
        result.put("targetArtifactTypes", visualOnly ? List.of() : List.of("MODULE_PRD", "PAGE_SPEC"));
        result.put("semanticChangeTypes", semanticChangeTypes);
        result.put("visualChangeTypes", visualChangeTypes);
        result.put("reviewPolicy", "Visual-only prototype edits remain in UI_CONTRACT. Field/action/navigation/copy/visibility edits require MODULE_PRD/PAGE_SPEC review.");
        return result;
    }

    private String classifyPrototypeLocalEditFallback(
            Map<String, Object> edit
    ) {
        String instruction = safeText(edit.get("instruction"));
        String changes = String.valueOf(edit.getOrDefault("changes", ""));
        Map<String, Object> selection = syncMap(edit.get("targetSelection"));
        String kind = safeText(selection.get("elementKind")).toUpperCase();

        String text = (instruction + " " + changes + " " + kind).toLowerCase();

        boolean semantic = prototypeTextContainsAny(
                text,
                "字段名",
                "输入提示",
                "placeholder",
                "占位",
                "字段说明",
                "必填",
                "非必填",
                "可选",
                "按钮文案",
                "表格列名",
                "列名",
                "标签页名称",
                "页面标题",
                "说明文案",
                "跳转",
                "导航",
                "状态",
                "审批",
                "隐藏",
                "恢复显示",
                "禁用",
                "不可编辑",
                "删除",
                "删掉",
                "移除",
                "新增",
                "添加"
        );

        if (semantic) {
            return "REQUIREMENT_SYNC_REVIEW_REQUIRED";
        }

        boolean visual = prototypeTextContainsAny(
                text,
                "颜色",
                "蓝色",
                "绿色",
                "红色",
                "橙色",
                "紫色",
                "灰色",
                "宽一点",
                "窄一点",
                "长一点",
                "短一点",
                "全宽",
                "铺满",
                "拉满",
                "大一点",
                "小一点",
                "靠右",
                "靠左",
                "居中",
                "右对齐",
                "左对齐",
                "右上角",
                "上方",
                "下方",
                "两列",
                "三列",
                "四列",
                "主按钮",
                "次按钮",
                "危险按钮",
                "警告按钮",
                "链接按钮"
        );

        return visual ? "VISUAL_ONLY" : "REQUIREMENT_SYNC_REVIEW_REQUIRED";
    }

    private boolean prototypeTextContainsAny(
            String text,
            String... keywords
    ) {
        String value = safeText(text).toLowerCase();

        for (String keyword : keywords) {
            if (!safeText(keyword).isBlank() && value.contains(safeText(keyword).toLowerCase())) {
                return true;
            }
        }

        return false;
    }

    private void copyStringList(
            List<String> target,
            List<Object> values
    ) {
        for (Object value : values) {
            addUnique(target, safeText(value));
        }
    }

    private Map<String, Object> buildVisualOnlyPrototypeSyncProposal(
            Map<String, Object> prototypeEditSync,
            String reason
    ) {
        Map<String, Object> proposal = new LinkedHashMap<>();
        proposal.put("summary", "本次原型修改被判定为纯视觉/布局调整，仅保存 UI_CONTRACT，不要求同步 MODULE_PRD 或 PAGE_SPEC。");
        proposal.put("reason", safeTrim(reason));
        proposal.put("prototypeEditSync", prototypeEditSync);
        proposal.put("applyPolicy", "只更新交互原型版本；字段、按钮业务含义、跳转、状态和验收规则未变化，不触发需求资产同步。");
        proposal.put("reviewFocus", List.of("视觉布局", "按钮尺寸", "对齐方式", "颜色样式", "局部位置"));
        return proposal;
    }


    private void collectPrototypeSyncReferences(
            List<Object> components,
            List<String> componentIds,
            List<String> fieldKeys,
            List<String> columnKeys
    ) {
        for (Object value : components) {
            Map<String, Object> component = syncMap(value);
            if (component.isEmpty()) {
                continue;
            }

            addUnique(componentIds, safeText(component.get("componentId")));

            for (Object item : syncList(component.get("fields"))) {
                Map<String, Object> field = syncMap(item);
                addUnique(fieldKeys, firstNonBlankSync(
                        safeText(field.get("fieldKey")),
                        safeText(field.get("key")),
                        safeText(field.get("name")),
                        safeText(field.get("label"))
                ));
            }

            for (Object item : syncList(component.get("columns"))) {
                Map<String, Object> column = syncMap(item);
                addUnique(columnKeys, firstNonBlankSync(
                        safeText(column.get("columnKey")),
                        safeText(column.get("key")),
                        safeText(column.get("fieldKey")),
                        safeText(column.get("label"))
                ));
            }

            collectPrototypeSyncReferences(
                    syncList(component.get("children")),
                    componentIds,
                    fieldKeys,
                    columnKeys
            );
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> syncMap(
            Object value
    ) {
        if (value instanceof Map<?, ?>) {
            return objectMapper.convertValue(value, LinkedHashMap.class);
        }

        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private List<Object> syncList(
            Object value
    ) {
        if (value instanceof List<?>) {
            return objectMapper.convertValue(value, List.class);
        }

        return List.of();
    }

    private void addUnique(
            List<String> target,
            String value
    ) {
        String text = safeTrim(value);

        if (!text.isBlank() && !target.contains(text)) {
            target.add(text);
        }
    }

    private String firstNonBlankSync(
            String... values
    ) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            String text = safeTrim(value);
            if (!text.isBlank()) {
                return text;
            }
        }

        return "";
    }



    private AgentTaskEntity createManualEditTraceTask(
            UUID projectId,
            UUID moduleId,
            String artifactType,
            Map<String, Object> requestedContent,
            String contentMarkdown,
            String reason,
            String username
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", "A9.10.14.4.3.2_MANUAL_EDIT_TASK_TRACE_FIX");
        payload.put("mode", "MANUAL_ARTIFACT_EDIT_TRACE");
        payload.put("artifact_type", artifactType);
        payload.put("module_id", moduleId == null ? null : moduleId.toString());
        payload.put("requirement_text", firstNonBlankSync(
                safeTrim(contentMarkdown),
                safeTrim(reason),
                "Manual edit for " + artifactType
        ));
        payload.put("reason", safeTrim(reason));
        payload.put("has_structured_content", requestedContent != null && !requestedContent.isEmpty());
        payload.put("created_by_username", username);

        return agentTaskService.createRunningTask(
                projectId,
                moduleId,
                manualEditTraceTaskType(artifactType),
                payload,
                username
        );
    }

    private TaskType manualEditTraceTaskType(
            String artifactType
    ) {
        return switch (safeTrim(artifactType)) {
            case "MODULE_PRD" -> TaskType.GENERATE_MODULE_PRD;
            case "BUSINESS_FLOW" -> TaskType.GENERATE_BUSINESS_FLOW;
            case "PAGE_SPEC" -> TaskType.GENERATE_PAGE_SPEC;
            case "TEST_SPEC" -> TaskType.GENERATE_TEST_SPEC;
            case "UI_CONTRACT", "MODULE_APP_CONTRACT", "MODULE_PROTOTYPE_SNAPSHOT" -> TaskType.GENERATE_UI_CONTRACT;
            default -> TaskType.GENERATE_DEV_HANDOFF_PACKAGE;
        };
    }

    private void markManualEditTraceTaskSuccess(
            AgentTaskEntity task,
            ArtifactVersionEntity artifact,
            String artifactType
    ) {
        if (task == null || task.getId() == null || artifact == null || artifact.getId() == null) {
            return;
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("source", "A9.10.14.4.3.2_MANUAL_EDIT_TASK_TRACE_FIX");
        response.put("mode", "MANUAL_ARTIFACT_EDIT_TRACE");
        response.put("artifactType", artifactType);
        response.put("artifactId", artifact.getId().toString());
        response.put("versionNo", artifact.getVersionNo());
        response.put("status", artifact.getStatus());

        agentTaskService.markSuccess(
                task.getId(),
                response,
                artifact.getId()
        );
    }

    private void copyPromptTemplateMetadata(
            AgentTaskEntity source,
            ArtifactVersionEntity target
    ) {
        if (source == null || target == null) {
            return;
        }

        target.setPromptTemplateId(source.getPromptTemplateId());
        target.setPromptTemplateVersionId(source.getPromptTemplateVersionId());
        target.setPromptTemplateKey(source.getPromptTemplateKey());
        target.setPromptTemplateVersion(source.getPromptTemplateVersion());
        target.setPromptTemplateChecksum(source.getPromptTemplateChecksum());
    }

    private void copyPromptTemplateMetadata(
            ArtifactVersionEntity source,
            ArtifactVersionEntity target
    ) {
        target.setPromptTemplateId(source.getPromptTemplateId());
        target.setPromptTemplateVersionId(source.getPromptTemplateVersionId());
        target.setPromptTemplateKey(source.getPromptTemplateKey());
        target.setPromptTemplateVersion(source.getPromptTemplateVersion());
        target.setPromptTemplateChecksum(source.getPromptTemplateChecksum());
    }

    private List<String> resolveDownstreamArtifactTypes(
            String artifactType
    ) {
        return resolveDownstreamArtifactTypes(
                artifactType,
                Map.of()
        );
    }

    private List<String> resolveDownstreamArtifactTypes(
            String artifactType,
            Map<String, Object> content
    ) {
        // A9.10.14.4.3.15.4.10.2.2_PRD_IMPACT_COMPAT_CLOSURE
        // User custom/free PRD sections are document-only. They should create a MODULE_PRD
        // version but must not dirty PAGE_SPEC/UI_CONTRACT/prototype/development assets.
        if ("MODULE_PRD".equals(artifactType)) {
            if (isModulePrdDocumentOnlyEdit(content)) {
                return List.of();
            }
            return resolveModulePrdSectionAwareDownstreamArtifactTypes(content);
        }

        if ("UI_CONTRACT".equals(artifactType)) {
            Map<String, Object> sync = syncMap(content == null ? null : content.get("_prd_prototype_semantic_sync"));
            String status = safeText(sync.get("status"));

            if ("UI_CONTRACT_ONLY_VISUAL_CHANGE".equals(status)) {
                return List.of();
            }

            if ("PAGE_SPEC_BACKGROUND_SYNC_APPLIED".equals(status)
                    || "MODULE_PRD_BACKGROUND_SYNC_APPLIED".equals(status)) {
                return List.of(
                        "MODULE_APP_CONTRACT",
                        "DEV_HANDOFF_PACKAGE"
                );
            }

            return List.of(
                    "MODULE_PRD",
                    "PAGE_SPEC",
                    "MODULE_APP_CONTRACT",
                    "DEV_HANDOFF_PACKAGE"
            );
        }

        return switch (artifactType) {
            case "MODULE_PRD" -> resolveModulePrdSectionAwareDownstreamArtifactTypes(content);
            case "BUSINESS_FLOW" -> List.of(
                    "PAGE_SPEC",
                    "DATA_MODEL",
                    "API_CONTRACT",
                    "UI_CONTRACT",
                    "TEST_SPEC",
                    "DEV_HANDOFF_PACKAGE"
            );
            case "PAGE_SPEC" -> List.of(
                    "DATA_MODEL",
                    "API_CONTRACT",
                    "UI_CONTRACT",
                    "TEST_SPEC",
                    "DEV_HANDOFF_PACKAGE"
            );
            case "TEST_SPEC" -> List.of(
                    "DEV_HANDOFF_PACKAGE"
            );
            case "MODULE_APP_CONTRACT" -> List.of(
                    "MODULE_PROTOTYPE_SNAPSHOT",
                    "DEV_HANDOFF_PACKAGE"
            );
            case "MODULE_PROTOTYPE_SNAPSHOT" -> List.of(
                    "DEV_HANDOFF_PACKAGE"
            );
            default -> List.of();
        };
    }

    // A9.10.14.4.3.15.4.10.4.5_PRD_EDIT_IMPACT_PRECISION
    private List<String> resolveModulePrdSectionAwareDownstreamArtifactTypes(
            Map<String, Object> content
    ) {
        Map<String, Object> sectionEdit = syncMap(content == null ? null : content.get("_prd_section_edit"));
        String sectionKey = safeText(firstNonBlankSync(
                safeText(sectionEdit.get("sectionKey")),
                safeText(sectionEdit.get("section_key"))
        ));

        if (sectionKey.isBlank()) {
            return List.of(
                    "BUSINESS_FLOW",
                    "PAGE_SPEC",
                    "DATA_MODEL",
                    "API_CONTRACT",
                    "UI_CONTRACT",
                    "TEST_SPEC",
                    "DEV_HANDOFF_PACKAGE"
            );
        }

        return switch (sectionKey) {
            case "custom_sections" -> List.of();
            case "module_basic_info", "business_background", "user_roles" -> List.of(
                    "BUSINESS_FLOW",
                    "DEV_HANDOFF_PACKAGE"
            );
            case "functional_requirements" -> List.of(
                    "BUSINESS_FLOW",
                    "PAGE_SPEC",
                    "UI_CONTRACT",
                    "TEST_SPEC",
                    "DEV_HANDOFF_PACKAGE"
            );
            case "business_rules", "state_rules", "permission_requirements", "exception_scenarios" -> List.of(
                    "PAGE_SPEC",
                    "UI_CONTRACT",
                    "TEST_SPEC",
                    "DEV_HANDOFF_PACKAGE"
            );
            case "page_requirements" -> List.of(
                    "PAGE_SPEC",
                    "UI_CONTRACT",
                    "MODULE_APP_CONTRACT",
                    "DEV_HANDOFF_PACKAGE"
            );
            case "data_requirements" -> List.of(
                    "DATA_MODEL",
                    "API_CONTRACT",
                    "TEST_SPEC",
                    "DEV_HANDOFF_PACKAGE"
            );
            case "api_requirements" -> List.of(
                    "API_CONTRACT",
                    "TEST_SPEC",
                    "DEV_HANDOFF_PACKAGE"
            );
            case "acceptance_criteria", "non_functional_requirements" -> List.of(
                    "TEST_SPEC",
                    "DEV_HANDOFF_PACKAGE"
            );
            default -> List.of(
                    "PAGE_SPEC",
                    "UI_CONTRACT",
                    "TEST_SPEC",
                    "DEV_HANDOFF_PACKAGE"
            );
        };
    }

    // A9.10.14.4.3.15.4.10.2.2_PRD_IMPACT_COMPAT_CLOSURE
    private boolean isModulePrdDocumentOnlyEdit(
            Map<String, Object> content
    ) {
        Map<String, Object> sectionEdit = syncMap(content == null ? null : content.get("_prd_section_edit"));
        String sectionKey = safeText(sectionEdit.get("sectionKey"));
        String editorMode = safeText(sectionEdit.get("editorMode"));
        String impactPolicy = firstNonBlankSync(
                safeText(sectionEdit.get("impactPolicy")),
                safeText(sectionEdit.get("impact_policy"))
        );

        if ("DOCUMENT_ONLY".equalsIgnoreCase(impactPolicy)) {
            return true;
        }

        if ("custom_sections".equals(sectionKey) || sectionKey.startsWith("custom_section_")) {
            return true;
        }

        return editorMode.startsWith("CUSTOM_SECTION");
    }


        private ManualArtifactEditResponse toResponse(
            ArtifactVersionEntity artifact,
            List<String> downstreamArtifactTypes,
            int downstreamMarkedCount
    ) {
        return new ManualArtifactEditResponse(
                artifact.getId(),
                artifact.getProjectId(),
                artifact.getModuleId(),
                artifact.getArtifactType(),
                artifact.getVersionNo(),
                artifact.getTitle(),
                artifact.getStatus(),
                artifact.getCurrent(),
                artifact.getContent(),
                artifact.getContentMarkdown(),
                artifact.getCreatedBy(),
                artifact.getCreatedAt(),
                artifact.getUpdatedAt(),
                downstreamArtifactTypes,
                downstreamMarkedCount,
                resolveManualEditResponseMessage(artifact)
        );
    }


    // A9.10.14.4.3.15.4.7.9_PRD_EDIT_PROTOTYPE_REGENERATION_REMINDER
    private String resolveManualEditResponseMessage(
            ArtifactVersionEntity artifact
    ) {
        String artifactType = safeText(artifact.getArtifactType());
        Integer versionNo = artifact.getVersionNo();

        if ("UI_CONTRACT".equals(artifactType)) {
            Map<String, Object> content = syncMap(artifact.getContent());
            Map<String, Object> sync = syncMap(content.get("_prd_prototype_semantic_sync"));
            String status = safeText(sync.get("status"));

            if ("UI_CONTRACT_ONLY_VISUAL_CHANGE".equals(status)) {
                return "原型视觉/布局调整已保存为 UI_CONTRACT 新版本 V" + versionNo + "；本次不要求同步 MODULE_PRD 或 PAGE_SPEC。";
            }

            if ("MODULE_PRD_BACKGROUND_SYNC_APPLIED".equals(status)) {
                Map<String, Object> pageSpecSync = syncMap(sync.get("pageSpecBackgroundSync"));
                Map<String, Object> modulePrdSync = syncMap(sync.get("modulePrdBackgroundSync"));
                String pageSpecVersionNo = safeText(pageSpecSync.get("pageSpecVersionNo"));
                String modulePrdVersionNo = safeText(modulePrdSync.get("modulePrdVersionNo"));

                return "原型语义调整已保存为 UI_CONTRACT 新版本 V" + versionNo
                        + "；隐藏 PAGE_SPEC 已在后台同步"
                        + (pageSpecVersionNo.isBlank() ? "" : "为 V" + pageSpecVersionNo)
                        + "，MODULE_PRD 已自动反写"
                        + (modulePrdVersionNo.isBlank() ? "" : "为 V" + modulePrdVersionNo)
                        + "。";
            }

            if ("PAGE_SPEC_BACKGROUND_SYNC_APPLIED".equals(status)) {
                Map<String, Object> pageSpecSync = syncMap(sync.get("pageSpecBackgroundSync"));
                String pageSpecVersionNo = safeText(pageSpecSync.get("pageSpecVersionNo"));

                return "原型语义调整已保存为 UI_CONTRACT 新版本 V" + versionNo
                        + "；隐藏 PAGE_SPEC 已在后台同步"
                        + (pageSpecVersionNo.isBlank() ? "" : "为 V" + pageSpecVersionNo)
                        + "，用户无需手动处理页面规范。";
            }

            if ("PRD_SYNC_PROPOSAL_REQUIRED".equals(status)
                    || "PRD_SYNC_REVIEW_REQUIRED".equals(status)) {
                return "原型语义调整已保存为 UI_CONTRACT 新版本 V" + versionNo + "；已生成 MODULE_PRD / PAGE_SPEC 同步复核提示。";
            }

            return "原型修改已保存为 UI_CONTRACT 新版本 V" + versionNo + "。";
        }

        if ("MODULE_APP_CONTRACT".equals(artifactType)) {
            return "模块完整原型契约已保存为新版本 V" + versionNo + "，模块开发基线和开发交接包已标记为需更新。";
        }

        if ("MODULE_PROTOTYPE_SNAPSHOT".equals(artifactType)) {
            return "模块汇总原型已确认为开发基线 V" + versionNo + "。";
        }

        Map<String, Object> content = syncMap(artifact.getContent());
        Map<String, Object> sync = syncMap(content.get("_prd_prototype_semantic_sync"));
        String syncStatus = safeText(sync.get("status"));

        if ("MODULE_PRD".equals(artifactType)) {
            if (isModulePrdDocumentOnlyEdit(content)) {
                return "PRD 补充说明已保存为新版本 V" + versionNo
                        + "；本次只影响文档内容，不会标记模块树、页面规范或交互原型为需更新。";
            }

            return "PRD 已保存为新版本 V" + versionNo
                    + "；系统已按编辑章节收敛下游影响范围。页面级重新生成提示由前端根据页面 Key、页面名称、路由和功能归属精确定位。";
        }

        if ("BUSINESS_FLOW".equals(artifactType)
                || "PAGE_SPEC".equals(artifactType)
                || "TEST_SPEC".equals(artifactType)) {
            return artifactType + " 已保存为新版本 V" + versionNo
                    + "；若影响页面交互，请进入对应页面重新生成交互原型，确保 PRD / PAGE_SPEC / UI_CONTRACT 保持一致。";
        }

        if ("PROTOTYPE_IMPACT_REVIEW_REQUIRED".equals(syncStatus)) {
            return "需求资产已保存为新版本 V" + versionNo
                    + "；受影响页面原型已标记为需要重新生成。";
        }

        return "文档编辑已保存为新版本 V" + versionNo
                + "，当前状态为新版本待确认；受影响的原型和开发交接资产已标记为需复核。";
    }

    private boolean canBootstrapManualArtifact(
            String artifactType
    ) {
        return "MODULE_PRD".equals(artifactType)
                || "MODULE_APP_CONTRACT".equals(artifactType);
    }

    private String resolveManualEditArtifactTitle(
            ArtifactVersionEntity previous,
            String artifactType
    ) {
        if (previous != null && previous.getTitle() != null && !previous.getTitle().isBlank()) {
            return previous.getTitle();
        }

        return switch (artifactType) {
            case "MODULE_APP_CONTRACT" -> "模块完整原型契约";
            case "MODULE_PROTOTYPE_SNAPSHOT" -> "模块汇总原型开发基线";
            case "UI_CONTRACT" -> "页面交互原型";
            case "PAGE_SPEC" -> "页面规范";
            case "BUSINESS_FLOW" -> "业务流程";
            case "TEST_SPEC" -> "测试规格";
            default -> "模块 PRD（手动编辑导入）";
        };
    }

    private String normalizeMarkdown(
            String value
    ) {
        return safeTrim(value).replace("\r\n", "\n");
    }


    private String safeText(
            Object value
    ) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String safeTrim(
            String value
    ) {
        return value == null ? "" : value.trim();
    }
}



