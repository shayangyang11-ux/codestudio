package com.codemind.platform.artifact;

import com.codemind.platform.audit.AuditService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.ArrayList;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts;
import java.math.BigInteger;
import org.apache.poi.xwpf.usermodel.TableRowAlign;
import org.apache.poi.xwpf.usermodel.TextAlignment;
import org.apache.poi.xwpf.usermodel.XWPFTable.XWPFBorderType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;
import com.codemind.platform.module.ProjectModuleEntity;
import com.codemind.platform.module.ProjectModuleRepository;
import com.codemind.platform.task.AgentTaskEntity;
import com.codemind.platform.task.AgentTaskRepository;

@Service
public class ArtifactService {

    private final ArtifactVersionRepository artifactVersionRepository;
    private final ProjectModuleRepository projectModuleRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final AgentTaskRepository agentTaskRepository;
    private final ArtifactQualityCheckRecordRepository qualityCheckRecordRepository;
    private final ArtifactExportRecordRepository exportRecordRepository;

    public ArtifactService(
            ArtifactVersionRepository artifactVersionRepository,
            ProjectModuleRepository projectModuleRepository,
            AgentTaskRepository agentTaskRepository,
            AuditService auditService,
            ObjectMapper objectMapper,
            ArtifactQualityCheckRecordRepository qualityCheckRecordRepository,
            ArtifactExportRecordRepository exportRecordRepository
    ) {
        this.artifactVersionRepository = artifactVersionRepository;
        this.projectModuleRepository = projectModuleRepository;
        this.agentTaskRepository = agentTaskRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.qualityCheckRecordRepository = qualityCheckRecordRepository;
        this.exportRecordRepository = exportRecordRepository;
    }

    @Transactional
    public ArtifactVersionEntity saveArtifact(
            UUID projectId,
            UUID moduleId,
            UUID taskId,
            String artifactType,
            String title,
            Map<String, Object> content,
            String contentMarkdown,
            String createdBy
    ) {
        int maxVersionNo;

        if (moduleId == null) {
            maxVersionNo = artifactVersionRepository
                    .findMaxProjectArtifactVersionNo(projectId, artifactType);

            artifactVersionRepository
                    .markPreviousProjectArtifactsAsNotCurrent(projectId, artifactType);
        } else {
            maxVersionNo = artifactVersionRepository
                    .findMaxModuleArtifactVersionNo(projectId, moduleId, artifactType);

            artifactVersionRepository
                    .markPreviousModuleArtifactsAsNotCurrent(projectId, moduleId, artifactType);
        }

        ArtifactVersionEntity artifact = new ArtifactVersionEntity();
        artifact.setProjectId(projectId);
        artifact.setModuleId(moduleId);
        artifact.setTaskId(taskId);
        artifact.setArtifactType(artifactType);
        artifact.setVersionNo(maxVersionNo + 1);
        artifact.setTitle(title);
        artifact.setContent(content);
        artifact.setContentMarkdown(contentMarkdown);
        artifact.setStatus("ACTIVE");
        artifact.setCurrent(true);
        artifact.setCreatedBy(createdBy);
        applyPromptTemplateFromTask(
                artifact,
                taskId
        );

        return artifactVersionRepository.save(artifact);
    }

    @Transactional(readOnly = true)
    public List<ArtifactVersionEntity> listModuleArtifactVersions(
            UUID projectId,
            UUID moduleId,
            String artifactType
    ) {
        return artifactVersionRepository
                .findByProjectIdAndModuleIdAndArtifactTypeOrderByVersionNoDesc(
                        projectId,
                        moduleId,
                        artifactType
                );
    }

    @Transactional(readOnly = true)
    public ArtifactVersionEntity getModuleArtifactVersion(
            UUID projectId,
            UUID moduleId,
            String artifactType,
            UUID artifactId
    ) {
        return artifactVersionRepository
                .findByIdAndProjectIdAndModuleIdAndArtifactType(
                        artifactId,
                        projectId,
                        moduleId,
                        artifactType
                )
                .orElseThrow(() -> new IllegalArgumentException(
                        "Artifact version not found. artifactId="
                                + artifactId
                                + ", projectId="
                                + projectId
                                + ", moduleId="
                                + moduleId
                                + ", artifactType="
                                + artifactType
                ));
    }

    @Transactional
    public ArtifactVersionEntity rollbackModuleArtifactToVersion(
            UUID projectId,
            UUID moduleId,
            String artifactType,
            UUID artifactId,
            String username
    ) {
        ArtifactVersionEntity target = getModuleArtifactVersion(
                projectId,
                moduleId,
                artifactType,
                artifactId
        );

        ArtifactVersionEntity previousCurrent = artifactVersionRepository
                .findFirstByProjectIdAndModuleIdAndArtifactTypeAndCurrentTrueOrderByVersionNoDesc(
                        projectId,
                        moduleId,
                        artifactType
                )
                .orElse(null);

        Map<String, Object> beforeJson = previousCurrent == null
                ? null
                : toAuditJson(previousCurrent);

        artifactVersionRepository.markPreviousModuleArtifactsAsNotCurrent(
                projectId,
                moduleId,
                artifactType
        );

        target.setCurrent(true);
        target.setStatus("ACTIVE");

        ArtifactVersionEntity saved = artifactVersionRepository.save(target);

        auditService.record(
                projectId,
                "USER",
                username,
                "ROLLBACK_ARTIFACT_VERSION",
                "ARTIFACT_VERSION",
                saved.getId(),
                beforeJson,
                toAuditJson(saved),
                Map.of(
                        "module_id", moduleId.toString(),
                        "artifact_type", artifactType,
                        "rollback_to_artifact_id", artifactId.toString(),
                        "rollback_to_version_no", String.valueOf(saved.getVersionNo()),
                        "previous_current_artifact_id",
                        previousCurrent == null ? "" : previousCurrent.getId().toString(),
                        "previous_current_version_no",
                        previousCurrent == null ? "" : String.valueOf(previousCurrent.getVersionNo())
                )
        );

        return saved;
    }

    @Transactional
    public ArtifactVersionEntity submitModuleArtifactReview(
            UUID projectId,
            UUID moduleId,
            String artifactType,
            UUID artifactId,
            String username,
            String reason
    ) {
        return changeModuleArtifactReviewStatus(
                projectId,
                moduleId,
                artifactType,
                artifactId,
                "PENDING_REVIEW",
                "SUBMIT_ARTIFACT_REVIEW",
                username,
                reason
        );
    }

    @Transactional
    public ArtifactVersionEntity approveModuleArtifactVersion(
            UUID projectId,
            UUID moduleId,
            String artifactType,
            UUID artifactId,
            String username,
            String reason
    ) {
        return changeModuleArtifactReviewStatus(
                projectId,
                moduleId,
                artifactType,
                artifactId,
                "APPROVED",
                "APPROVE_ARTIFACT_VERSION",
                username,
                reason
        );
    }

    @Transactional
    public ArtifactVersionEntity rejectModuleArtifactVersion(
            UUID projectId,
            UUID moduleId,
            String artifactType,
            UUID artifactId,
            String username,
            String reason
    ) {
        return changeModuleArtifactReviewStatus(
                projectId,
                moduleId,
                artifactType,
                artifactId,
                "REJECTED",
                "REJECT_ARTIFACT_VERSION",
                username,
                reason
        );
    }

    @Transactional
    public ArtifactVersionEntity archiveModuleArtifactVersion(
            UUID projectId,
            UUID moduleId,
            String artifactType,
            UUID artifactId,
            String username,
            String reason
    ) {
        return changeModuleArtifactReviewStatus(
                projectId,
                moduleId,
                artifactType,
                artifactId,
                "ARCHIVED",
                "ARCHIVE_ARTIFACT_VERSION",
                username,
                reason
        );
    }

    @Transactional
    public ArtifactQualityCheckRecordEntity runAndPersistModuleQualityCheck(
            UUID projectId,
            UUID moduleId,
            String checkedBy
    ) {
        ProjectModuleEntity module = resolveQualityGateModule(
                projectId,
                moduleId
        );

        List<ArtifactVersionEntity> currentArtifacts =
                artifactVersionRepository.findByProjectIdAndModuleIdAndCurrentTrue(
                        projectId,
                        moduleId
                );

        Map<String, ArtifactVersionEntity> artifactByType = new LinkedHashMap<>();

        for (ArtifactVersionEntity artifact : currentArtifacts) {
            artifactByType.put(artifact.getArtifactType(), artifact);
        }

        LocalDateTime checkedAt = LocalDateTime.now();

        List<ArtifactExportDefinition> definitions =
                buildRequirementArtifactExportDefinitions(module);

        ArtifactQualityCheckResult result = buildModuleQualityCheckResult(
                definitions,
                artifactByType,
                checkedAt
        );

        return persistQualityCheckRecord(
                projectId,
                moduleId,
                resolveQualityGateScope(module),
                result,
                false,
                "MANUAL_CHECK",
                checkedBy
        );
    }

    @Transactional(readOnly = true)
    public ArtifactQualityCheckRecordEntity getLatestModuleQualityCheck(
            UUID projectId,
            UUID moduleId
    ) {
        return qualityCheckRecordRepository
                .findFirstByProjectIdAndModuleIdOrderByCheckedAtDesc(
                        projectId,
                        moduleId
                )
                .orElseThrow(() -> new IllegalArgumentException(
                        "No quality check record found. projectId="
                                + projectId
                                + ", moduleId="
                                + moduleId
                ));
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public ModuleArtifactZipExport exportCurrentModuleArtifactsZip(
            UUID projectId,
            String projectCode,
            UUID moduleId,
            String moduleKey,
            String moduleName,
            String exportedBy,
            boolean forceExport
    ) {
        ProjectModuleEntity module = resolveQualityGateModule(
                projectId,
                moduleId
        );

        String resolvedModuleKey = firstNonBlank(
                moduleKey,
                module.getModuleKey()
        );

        String resolvedModuleName = firstNonBlank(
                moduleName,
                module.getModuleName()
        );

        List<ArtifactVersionEntity> currentArtifacts =
                artifactVersionRepository.findByProjectIdAndModuleIdAndCurrentTrue(
                        projectId,
                        moduleId
                );

        Map<String, ArtifactVersionEntity> artifactByType = new LinkedHashMap<>();

        for (ArtifactVersionEntity artifact : currentArtifacts) {
            artifactByType.put(artifact.getArtifactType(), artifact);
        }

        LocalDateTime exportedAt = LocalDateTime.now();

        List<ArtifactExportDefinition> definitions =
                buildRequirementArtifactExportDefinitions(module);

        String qualityScope = resolveQualityGateScope(module);

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("schema_version", "1.0");
        manifest.put("export_type", "MODULE_REQUIREMENT_ARTIFACT_ZIP");
        manifest.put("exported_at", exportedAt.toString());
        manifest.put("exported_by", exportedBy);
        manifest.put("project_id", projectId.toString());
        manifest.put("project_code", projectCode);
        manifest.put("module_id", moduleId.toString());
        manifest.put("module_key", resolvedModuleKey);
        manifest.put("module_name", resolvedModuleName);
        manifest.put("module_type", safeText(module.getModuleType()));
        manifest.put("quality_scope", qualityScope);

        List<Map<String, Object>> assetManifestItems = definitions
                .stream()
                .map(definition -> buildExportAssetManifestItem(
                        definition,
                        artifactByType.get(definition.artifactType())
                ))
                .toList();

        manifest.put("assets", assetManifestItems);

        ArtifactQualityCheckResult qualityCheckResult = buildModuleQualityCheckResult(
                definitions,
                artifactByType,
                exportedAt
        );

        ArtifactQualityCheckRecordEntity qualityRecord = persistQualityCheckRecord(
                projectId,
                moduleId,
                qualityScope,
                qualityCheckResult,
                forceExport,
                "EXPORT_MODULE_ZIP",
                exportedBy
        );

        if (!forceExport && !qualityCheckResult.blockers().isEmpty()) {
            persistExportRecord(
                    projectId,
                    moduleId,
                    qualityRecord,
                    "MODULE",
                    "ZIP",
                    "BLOCKED",
                    null,
                    0,
                    false,
                    buildQualityCheckManifest(qualityCheckResult, false),
                    "当前节点存在交付阻断项，未确认强制导出。",
                    exportedBy
            );

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "当前节点存在交付阻断项，请先完成质量检查并修复问题，或确认后强制导出。"
            );
        }

        manifest.put(
                "quality_check",
                buildQualityCheckManifest(
                        qualityCheckResult,
                        forceExport
                )
        );

        byte[] zipContent = buildModuleArtifactZip(
                projectCode,
                resolvedModuleKey,
                resolvedModuleName,
                exportedAt,
                definitions,
                artifactByType,
                manifest,
                qualityCheckResult,
                forceExport
        );

        String fileName = buildExportZipFileName(
                projectCode,
                resolvedModuleKey,
                resolvedModuleName,
                exportedAt
        );

        persistExportRecord(
                projectId,
                moduleId,
                qualityRecord,
                "MODULE",
                "ZIP",
                "SUCCESS",
                fileName,
                zipContent.length,
                forceExport,
                manifest,
                null,
                exportedBy
        );

        return new ModuleArtifactZipExport(
                fileName,
                zipContent
        );
    }

    private List<ArtifactExportDefinition> buildRequirementArtifactExportDefinitions() {
        return buildModuleArtifactExportDefinitions();
    }

    private List<ArtifactExportDefinition> buildRequirementArtifactExportDefinitions(
            ProjectModuleEntity module
    ) {
        String nodeType = resolveQualityGateNodeType(module);

        return switch (nodeType) {
            case "DOMAIN" -> buildDomainArtifactExportDefinitions();
            case "SUBMODULE" -> buildSubmoduleArtifactExportDefinitions();
            case "PAGE" -> buildPageArtifactExportDefinitions();
            case "FEATURE" -> buildFeatureArtifactExportDefinitions();
            default -> buildModuleArtifactExportDefinitions();
        };
    }

    private List<ArtifactExportDefinition> buildDomainArtifactExportDefinitions() {
        return List.of(
                required("01_DOMAIN_OVERVIEW.md", "MODULE_PRD", "业务域概览"),
                required("02_DOMAIN_BUSINESS_FLOW.md", "BUSINESS_FLOW", "业务域流程总览"),
                required("03_DOMAIN_PAGE_MAP.md", "PAGE_SPEC", "模块/页面地图"),
                optional("04_DOMAIN_DATA_TOPICS.md", "DATA_MODEL", "数据主题总览"),
                optional("05_DOMAIN_API_BOUNDARY.md", "API_CONTRACT", "接口域总览"),
                optional("06_DOMAIN_TEST_SCOPE.md", "TEST_SPEC", "业务域测试范围"),
                optional("07_DOMAIN_DELIVERY_NOTE.md", "DEV_HANDOFF_PACKAGE", "业务域交付说明")
        );
    }

    private List<ArtifactExportDefinition> buildModuleArtifactExportDefinitions() {
        return List.of(
                required("01_MODULE_PRD.md", "MODULE_PRD", "模块 PRD"),
                required("02_BUSINESS_FLOW.md", "BUSINESS_FLOW", "业务流程"),
                required("03_PAGE_SPEC.md", "PAGE_SPEC", "页面地图"),
                optional("04_DATA_MODEL.md", "DATA_MODEL", "数据模型"),
                required("05_API_CONTRACT.md", "API_CONTRACT", "接口规划"),
                optional("06_TEST_SPEC.md", "TEST_SPEC", "测试规格"),
                optional("07_DEV_HANDOFF_PACKAGE.md", "DEV_HANDOFF_PACKAGE", "开发交接包")
        );
    }

    private List<ArtifactExportDefinition> buildSubmoduleArtifactExportDefinitions() {
        return List.of(
                required("01_SCENE_REQUIREMENT.md", "MODULE_PRD", "场景需求说明"),
                required("02_SCENE_BUSINESS_FLOW.md", "BUSINESS_FLOW", "场景业务流程"),
                required("03_SCENE_PAGE_LIST.md", "PAGE_SPEC", "场景页面清单"),
                optional("04_SCENE_API_LIST.md", "API_CONTRACT", "场景接口清单"),
                optional("05_SCENE_TEST_SPEC.md", "TEST_SPEC", "场景测试规格"),
                optional("06_SCENE_DEV_NOTE.md", "DEV_HANDOFF_PACKAGE", "场景开发说明")
        );
    }

    private List<ArtifactExportDefinition> buildPageArtifactExportDefinitions() {
        return List.of(
                optional("01_PAGE_REQUIREMENT.md", "MODULE_PRD", "页面需求"),
                optional("02_PAGE_INTERACTION_FLOW.md", "BUSINESS_FLOW", "页面交互流程"),
                required("03_PAGE_SPEC.md", "PAGE_SPEC", "页面规范"),
                optional("04_PAGE_DATA_MODEL.md", "DATA_MODEL", "页面数据模型"),
                required("05_PAGE_API_CONTRACT.md", "API_CONTRACT", "页面接口契约"),
                optional("06_PAGE_TEST_SPEC.md", "TEST_SPEC", "页面测试规格"),
                required("07_UI_CONTRACT.json.md", "UI_CONTRACT", "交互原型"),
                optional("08_PAGE_DEV_HANDOFF.md", "DEV_HANDOFF_PACKAGE", "页面开发交接包")
        );
    }

    private List<ArtifactExportDefinition> buildFeatureArtifactExportDefinitions() {
        return List.of(
                required("01_FEATURE_REQUIREMENT.md", "MODULE_PRD", "功能点需求"),
                optional("02_FEATURE_FLOW.md", "BUSINESS_FLOW", "功能点流程"),
                required("03_FEATURE_API.md", "API_CONTRACT", "功能点接口"),
                required("04_FEATURE_TEST.md", "TEST_SPEC", "功能点测试"),
                optional("05_FEATURE_DEV_NOTE.md", "DEV_HANDOFF_PACKAGE", "功能点开发说明")
        );
    }

    private ArtifactExportDefinition required(
            String fileName,
            String artifactType,
            String title
    ) {
        return new ArtifactExportDefinition(
                fileName,
                artifactType,
                title,
                true
        );
    }

    private ArtifactExportDefinition optional(
            String fileName,
            String artifactType,
            String title
    ) {
        return new ArtifactExportDefinition(
                fileName,
                artifactType,
                title,
                false
        );
    }

    private ProjectModuleEntity resolveQualityGateModule(
            UUID projectId,
            UUID moduleId
    ) {
        ProjectModuleEntity module = projectModuleRepository.findById(moduleId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Project module not found. moduleId=" + moduleId
                ));

        if (
                module.getProjectId() != null &&
                        !module.getProjectId().equals(projectId)
        ) {
            throw new IllegalArgumentException(
                    "Project module does not belong to current project. projectId="
                            + projectId
                            + ", moduleId="
                            + moduleId
            );
        }

        return module;
    }

    private String resolveQualityGateScope(
            ProjectModuleEntity module
    ) {
        return resolveQualityGateNodeType(module);
    }

    private String resolveQualityGateNodeType(
            ProjectModuleEntity module
    ) {
        String rawType = normalizeModuleType(
                module == null ? null : module.getModuleType()
        );

        if (
                "DOMAIN".equals(rawType) ||
                        "DOMAIN_MODULE".equals(rawType) ||
                        "BUSINESS_DOMAIN".equals(rawType)
        ) {
            return "DOMAIN";
        }

        if (
                "SUBMODULE".equals(rawType) ||
                        "SUB_MODULE".equals(rawType) ||
                        "SCENE".equals(rawType) ||
                        "BUSINESS_SCENE".equals(rawType)
        ) {
            return "SUBMODULE";
        }

        if (
                "PAGE".equals(rawType) ||
                        "PAGE_MODULE".equals(rawType) ||
                        "SCREEN".equals(rawType) ||
                        "VIEW".equals(rawType)
        ) {
            return "PAGE";
        }

        if (
                "FEATURE".equals(rawType) ||
                        "FUNCTION".equals(rawType) ||
                        "ACTION".equals(rawType) ||
                        "OPERATION".equals(rawType)
        ) {
            return "FEATURE";
        }

        return "MODULE";
    }

    private String normalizeModuleType(
            String value
    ) {
        if (value == null) {
            return "";
        }

        return value.trim().toUpperCase();
    }

    private String firstNonBlank(
            String first,
            String second
    ) {
        if (first != null && !first.isBlank()) {
            return first;
        }

        if (second != null && !second.isBlank()) {
            return second;
        }

        return "-";
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public ModuleArtifactDocxExport exportCurrentModuleArtifactsDocx(
            UUID projectId,
            String projectCode,
            UUID moduleId,
            String moduleKey,
            String moduleName,
            String exportedBy,
            boolean forceExport
    ) {
        ProjectModuleEntity module = resolveQualityGateModule(
                projectId,
                moduleId
        );

        String resolvedModuleKey = firstNonBlank(
                moduleKey,
                module.getModuleKey()
        );

        String resolvedModuleName = firstNonBlank(
                moduleName,
                module.getModuleName()
        );

        List<ArtifactVersionEntity> currentArtifacts =
                artifactVersionRepository.findByProjectIdAndModuleIdAndCurrentTrue(
                        projectId,
                        moduleId
                );

        Map<String, ArtifactVersionEntity> artifactByType = new LinkedHashMap<>();

        for (ArtifactVersionEntity artifact : currentArtifacts) {
            artifactByType.put(artifact.getArtifactType(), artifact);
        }

        LocalDateTime exportedAt = LocalDateTime.now();

        List<ArtifactExportDefinition> definitions =
                buildRequirementArtifactExportDefinitions(module);

        String qualityScope = resolveQualityGateScope(module);

        ArtifactQualityCheckResult qualityCheckResult = buildModuleQualityCheckResult(
                definitions,
                artifactByType,
                exportedAt
        );

        ArtifactQualityCheckRecordEntity qualityRecord = persistQualityCheckRecord(
                projectId,
                moduleId,
                qualityScope,
                qualityCheckResult,
                forceExport,
                "EXPORT_MODULE_DOCX",
                exportedBy
        );

        if (!forceExport && !qualityCheckResult.blockers().isEmpty()) {
            persistExportRecord(
                    projectId,
                    moduleId,
                    qualityRecord,
                    "MODULE",
                    "DOCX",
                    "BLOCKED",
                    null,
                    0,
                    false,
                    buildQualityCheckManifest(qualityCheckResult, false),
                    "当前节点存在交付阻断项，未确认强制导出。",
                    exportedBy
            );

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "当前节点存在交付阻断项，请先完成质量检查并修复问题，或确认后强制导出。"
            );
        }

        byte[] docxContent = buildModuleArtifactDocx(
                projectCode,
                resolvedModuleKey,
                resolvedModuleName,
                exportedAt,
                exportedBy,
                definitions,
                artifactByType,
                qualityCheckResult,
                forceExport
        );

        String fileName = buildExportDocxFileName(
                projectCode,
                resolvedModuleKey,
                resolvedModuleName,
                exportedAt
        );

        Map<String, Object> docxManifest = new LinkedHashMap<>();
        docxManifest.put("export_type", "MODULE_REQUIREMENT_ARTIFACT_DOCX");
        docxManifest.put("project_id", projectId.toString());
        docxManifest.put("project_code", projectCode);
        docxManifest.put("module_id", moduleId.toString());
        docxManifest.put("module_key", resolvedModuleKey);
        docxManifest.put("module_name", resolvedModuleName);
        docxManifest.put("module_type", safeText(module.getModuleType()));
        docxManifest.put("quality_scope", qualityScope);
        docxManifest.put("quality_check", buildQualityCheckManifest(qualityCheckResult, forceExport));

        persistExportRecord(
                projectId,
                moduleId,
                qualityRecord,
                "MODULE",
                "DOCX",
                "SUCCESS",
                fileName,
                docxContent.length,
                forceExport,
                docxManifest,
                null,
                exportedBy
        );

        return new ModuleArtifactDocxExport(
                fileName,
                docxContent
        );
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public ProjectArtifactZipExport exportProjectArtifactsZip(
            UUID projectId,
            String projectCode,
            String projectName,
            String exportedBy,
            boolean forceExport
    ) {
        List<ProjectModuleEntity> modules =
                projectModuleRepository.findByProjectIdOrderByLevelNoAscSortOrderAsc(
                        projectId
                );

        LocalDateTime exportedAt = LocalDateTime.now();

        Map<String, Integer> folderNameCounters = new LinkedHashMap<>();
        List<ProjectModuleExportContext> moduleContexts = new ArrayList<>();

        for (ProjectModuleEntity module : modules) {
            List<ArtifactVersionEntity> currentArtifacts =
                    artifactVersionRepository.findByProjectIdAndModuleIdAndCurrentTrue(
                            projectId,
                            module.getId()
                    );

            Map<String, ArtifactVersionEntity> artifactByType = new LinkedHashMap<>();

            for (ArtifactVersionEntity artifact : currentArtifacts) {
                artifactByType.put(
                        artifact.getArtifactType(),
                        artifact
                );
            }

            List<ArtifactExportDefinition> definitions =
                    buildRequirementArtifactExportDefinitions(module);

            ArtifactQualityCheckResult qualityCheckResult =
                    buildModuleQualityCheckResult(
                            definitions,
                            artifactByType,
                            exportedAt
                    );

            String folderName = buildUniqueModuleFolderName(
                    module,
                    folderNameCounters
            );

            Map<String, Object> moduleManifest = buildModuleExportManifest(
                    projectId,
                    projectCode,
                    module,
                    exportedBy,
                    exportedAt,
                    definitions,
                    artifactByType,
                    qualityCheckResult,
                    forceExport
            );

            moduleContexts.add(
                    new ProjectModuleExportContext(
                            module,
                            folderName,
                            artifactByType,
                            qualityCheckResult,
                            moduleManifest,
                            definitions
                    )
            );
        }

        long blockedModuleCount = moduleContexts
                .stream()
                .filter(context -> !context.qualityCheckResult().blockers().isEmpty())
                .count();

        Map<String, Object> projectQuality = buildProjectQualityManifest(
                moduleContexts,
                forceExport
        );

        ArtifactQualityCheckRecordEntity qualityRecord = persistProjectQualityCheckRecord(
                projectId,
                projectQuality,
                forceExport,
                "EXPORT_PROJECT_ZIP",
                exportedBy
        );

        if (!forceExport && blockedModuleCount > 0) {
            Map<String, Object> blockedManifest = buildProjectExportManifest(
                    projectId,
                    projectCode,
                    projectName,
                    exportedBy,
                    exportedAt,
                    moduleContexts,
                    false
            );

            persistExportRecord(
                    projectId,
                    null,
                    qualityRecord,
                    "PROJECT",
                    "ZIP",
                    "BLOCKED",
                    null,
                    0,
                    false,
                    blockedManifest,
                    "当前项目存在 " + blockedModuleCount + " 个节点包含交付阻断项，未确认强制导出。",
                    exportedBy
            );

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "当前项目存在 " + blockedModuleCount + " 个节点包含交付阻断项，请先修复问题，或确认后强制导出。"
            );
        }

        Map<String, Object> projectManifest = buildProjectExportManifest(
                projectId,
                projectCode,
                projectName,
                exportedBy,
                exportedAt,
                moduleContexts,
                forceExport
        );

        byte[] zipContent = buildProjectArtifactZip(
                projectCode,
                projectName,
                exportedAt,
                moduleContexts,
                projectManifest,
                forceExport
        );

        String fileName = buildProjectExportZipFileName(
                projectCode,
                projectName,
                exportedAt
        );

        persistExportRecord(
                projectId,
                null,
                qualityRecord,
                "PROJECT",
                "ZIP",
                "SUCCESS",
                fileName,
                zipContent.length,
                forceExport,
                projectManifest,
                null,
                exportedBy
        );

        return new ProjectArtifactZipExport(
                fileName,
                zipContent
        );
    }

    private byte[] buildModuleArtifactZip(
            String projectCode,
            String moduleKey,
            String moduleName,
            LocalDateTime exportedAt,
            List<ArtifactExportDefinition> definitions,
            Map<String, ArtifactVersionEntity> artifactByType,
            Map<String, Object> manifest,
            ArtifactQualityCheckResult qualityCheckResult,
            boolean forceExport
    ) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            try (ZipOutputStream zipOutputStream = new ZipOutputStream(
                    outputStream,
                    StandardCharsets.UTF_8
            )) {
                putZipEntry(
                        zipOutputStream,
                        "README.md",
                        buildExportReadme(
                                projectCode,
                                moduleKey,
                                moduleName,
                                exportedAt
                        )
                );

                putZipEntry(
                        zipOutputStream,
                        "QUALITY_REPORT.md",
                        buildQualityReportMarkdown(
                                qualityCheckResult,
                                forceExport
                        )
                );

                for (ArtifactExportDefinition definition : definitions) {
                    ArtifactVersionEntity artifact = artifactByType.get(
                            definition.artifactType()
                    );

                    putZipEntry(
                            zipOutputStream,
                            definition.fileName(),
                            buildArtifactMarkdown(
                                    definition,
                                    artifact
                            )
                    );
                }

                putZipEntry(
                        zipOutputStream,
                        "manifest.json",
                        toPrettyJson(manifest)
                );
            }

            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to export module artifacts zip.", e);
        }
    }

    private byte[] buildProjectArtifactZip(
            String projectCode,
            String projectName,
            LocalDateTime exportedAt,
            List<ProjectModuleExportContext> moduleContexts,
            Map<String, Object> projectManifest,
            boolean forceExport
    ) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            try (ZipOutputStream zipOutputStream = new ZipOutputStream(
                    outputStream,
                    StandardCharsets.UTF_8
            )) {
                putZipEntry(
                        zipOutputStream,
                        "README.md",
                        buildProjectExportReadme(
                                projectCode,
                                projectName,
                                exportedAt,
                                moduleContexts.size(),
                                forceExport
                        )
                );

                putZipEntry(
                        zipOutputStream,
                        "PROJECT_QUALITY_REPORT.md",
                        buildProjectQualityReportMarkdown(
                                moduleContexts,
                                forceExport
                        )
                );

                putZipEntry(
                        zipOutputStream,
                        "project_manifest.json",
                        toPrettyJson(projectManifest)
                );

                for (ProjectModuleExportContext context : moduleContexts) {
                    String moduleBasePath = "modules/"
                            + context.folderName()
                            + "/";

                    putZipEntry(
                            zipOutputStream,
                            moduleBasePath + "QUALITY_REPORT.md",
                            buildQualityReportMarkdown(
                                    context.qualityCheckResult(),
                                    forceExport
                            )
                    );

                    for (ArtifactExportDefinition definition : context.definitions()) {
                        ArtifactVersionEntity artifact = context.artifactByType()
                                .get(definition.artifactType());

                        putZipEntry(
                                zipOutputStream,
                                moduleBasePath + definition.fileName(),
                                buildArtifactMarkdown(
                                        definition,
                                        artifact
                                )
                        );
                    }

                    putZipEntry(
                            zipOutputStream,
                            moduleBasePath + "manifest.json",
                            toPrettyJson(context.manifest())
                    );
                }
            }

            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to export project artifacts zip.", e);
        }
    }

    private Map<String, Object> buildProjectExportManifest(
            UUID projectId,
            String projectCode,
            String projectName,
            String exportedBy,
            LocalDateTime exportedAt,
            List<ProjectModuleExportContext> moduleContexts,
            boolean forceExport
    ) {
        Map<String, Object> manifest = new LinkedHashMap<>();

        manifest.put("schema_version", "1.0");
        manifest.put("export_type", "PROJECT_REQUIREMENT_ARTIFACT_ZIP");
        manifest.put("exported_at", exportedAt.toString());
        manifest.put("exported_by", exportedBy);
        manifest.put("project_id", projectId.toString());
        manifest.put("project_code", projectCode);
        manifest.put("project_name", projectName);
        manifest.put("module_count", moduleContexts.size());
        manifest.put("force_export", forceExport);

        manifest.put(
                "project_quality",
                buildProjectQualityManifest(
                        moduleContexts,
                        forceExport
                )
        );

        List<Map<String, Object>> moduleItems = moduleContexts
                .stream()
                .map(context -> {
                    ProjectModuleEntity module = context.module();
                    ArtifactQualityCheckResult quality = context.qualityCheckResult();

                    Map<String, Object> item = new LinkedHashMap<>();

                    item.put("module_id", module.getId().toString());
                    item.put("parent_id", module.getParentId() == null ? null : module.getParentId().toString());
                    item.put("module_key", module.getModuleKey());
                    item.put("module_name", module.getModuleName());
                    item.put("module_type", module.getModuleType());
                    item.put("module_path", module.getModulePath());
                    item.put("level_no", module.getLevelNo());
                    item.put("sort_order", module.getSortOrder());
                    item.put("folder_name", context.folderName());
                    item.put("quality_score", quality.score());
                    item.put("blocker_count", quality.blockers().size());
                    item.put("warning_count", quality.warnings().size());
                    item.put("pass_count", quality.passes().size());

                    return item;
                })
                .toList();

        manifest.put("modules", moduleItems);

        return manifest;
    }

    private Map<String, Object> buildModuleExportManifest(
            UUID projectId,
            String projectCode,
            ProjectModuleEntity module,
            String exportedBy,
            LocalDateTime exportedAt,
            List<ArtifactExportDefinition> definitions,
            Map<String, ArtifactVersionEntity> artifactByType,
            ArtifactQualityCheckResult qualityCheckResult,
            boolean forceExport
    ) {
        Map<String, Object> manifest = new LinkedHashMap<>();

        manifest.put("schema_version", "1.0");
        manifest.put("export_type", "MODULE_REQUIREMENT_ARTIFACT_ZIP_IN_PROJECT_PACKAGE");
        manifest.put("exported_at", exportedAt.toString());
        manifest.put("exported_by", exportedBy);
        manifest.put("project_id", projectId.toString());
        manifest.put("project_code", projectCode);
        manifest.put("module_id", module.getId().toString());
        manifest.put("parent_id", module.getParentId() == null ? null : module.getParentId().toString());
        manifest.put("module_key", module.getModuleKey());
        manifest.put("module_name", module.getModuleName());
        manifest.put("module_type", module.getModuleType());
        manifest.put("quality_scope", resolveQualityGateScope(module));
        manifest.put("module_path", module.getModulePath());
        manifest.put("level_no", module.getLevelNo());
        manifest.put("sort_order", module.getSortOrder());

        List<Map<String, Object>> assetManifestItems = definitions
                .stream()
                .map(definition -> buildExportAssetManifestItem(
                        definition,
                        artifactByType.get(definition.artifactType())
                ))
                .toList();

        manifest.put("assets", assetManifestItems);
        manifest.put(
                "quality_check",
                buildQualityCheckManifest(
                        qualityCheckResult,
                        forceExport
                )
        );

        return manifest;
    }

    private Map<String, Object> buildProjectQualityManifest(
            List<ProjectModuleExportContext> moduleContexts,
            boolean forceExport
    ) {
        int moduleCount = moduleContexts.size();

        int blockerModuleCount = 0;
        int warningModuleCount = 0;
        int totalBlockerCount = 0;
        int totalWarningCount = 0;
        int totalPassCount = 0;
        int scoreSum = 0;

        for (ProjectModuleExportContext context : moduleContexts) {
            ArtifactQualityCheckResult quality = context.qualityCheckResult();

            scoreSum += quality.score();
            totalBlockerCount += quality.blockers().size();
            totalWarningCount += quality.warnings().size();
            totalPassCount += quality.passes().size();

            if (!quality.blockers().isEmpty()) {
                blockerModuleCount++;
            }

            if (!quality.warnings().isEmpty()) {
                warningModuleCount++;
            }
        }

        int score = moduleCount == 0
                ? 0
                : Math.max(
                0,
                Math.min(
                        100,
                        Math.round((float) scoreSum / moduleCount)
                )
        );

        Map<String, Object> quality = new LinkedHashMap<>();

        quality.put("score", score);
        quality.put("module_count", moduleCount);
        quality.put("blocker_module_count", blockerModuleCount);
        quality.put("warning_module_count", warningModuleCount);
        quality.put("ready_module_count", moduleCount - blockerModuleCount);
        quality.put("total_blocker_count", totalBlockerCount);
        quality.put("total_warning_count", totalWarningCount);
        quality.put("total_pass_count", totalPassCount);
        quality.put("forced_export", forceExport);
        quality.put(
                "export_decision",
                blockerModuleCount == 0
                        ? "READY"
                        : forceExport ? "FORCED_WITH_BLOCKERS" : "BLOCKED"
        );

        return quality;
    }

    private String buildProjectExportReadme(
            String projectCode,
            String projectName,
            LocalDateTime exportedAt,
            int moduleCount,
            boolean forceExport
    ) {
        return """
            # CodeMind 项目级需求资产交付包

            ## 导出说明

            本 ZIP 文件由 CodeMind Studio 自动生成，用于项目级需求资产整体交付。
            每个模块树节点会按自己的层级执行 Quality Gate v2 检查，业务域、模块、子模块、页面和功能点的资产清单可能不同。

            ## 基础信息

            - 项目编码：%s
            - 项目名称：%s
            - 节点数量：%s
            - 导出时间：%s
            - 是否强制导出：%s

            ## 文件结构

            - README.md：项目级导出说明
            - PROJECT_QUALITY_REPORT.md：项目级质量检查报告
            - project_manifest.json：项目级导出元数据
            - modules/：各节点需求资产目录

            ## 节点目录说明

            每个节点目录下包含：
            - QUALITY_REPORT.md：当前节点质量检查报告
            - manifest.json：当前节点导出元数据和动态资产清单
            - 其他资产文件：根据节点层级动态生成，具体以该节点 manifest.json 中的 assets 为准
            """.formatted(
                safeText(projectCode),
                safeText(projectName),
                String.valueOf(moduleCount),
                exportedAt.toString(),
                forceExport ? "是" : "否"
        );
    }

    private String buildProjectQualityReportMarkdown(
            List<ProjectModuleExportContext> moduleContexts,
            boolean forceExport
    ) {
        Map<String, Object> quality = buildProjectQualityManifest(
                moduleContexts,
                forceExport
        );

        StringBuilder builder = new StringBuilder();

        builder.append("# 项目级交付质量检查报告\n\n");

        builder.append("## 总览\n\n");
        builder.append("- 项目质量评分：").append(quality.get("score")).append(" / 100\n");
        builder.append("- 模块总数：").append(quality.get("module_count")).append("\n");
        builder.append("- 存在阻断项模块数：").append(quality.get("blocker_module_count")).append("\n");
        builder.append("- 存在警告项模块数：").append(quality.get("warning_module_count")).append("\n");
        builder.append("- 可交付模块数：").append(quality.get("ready_module_count")).append("\n");
        builder.append("- 阻断项总数：").append(quality.get("total_blocker_count")).append("\n");
        builder.append("- 警告项总数：").append(quality.get("total_warning_count")).append("\n");
        builder.append("- 是否强制导出：").append(forceExport ? "是" : "否").append("\n");
        builder.append("- 交付结论：").append(quality.get("export_decision")).append("\n\n");

        builder.append("## 模块质量汇总\n\n");
        builder.append("| 模块 Key | 模块名称 | 质量评分 | 阻断项 | 警告项 | 目录 |\n");
        builder.append("|---|---|---:|---:|---:|---|\n");

        for (ProjectModuleExportContext context : moduleContexts) {
            ProjectModuleEntity module = context.module();
            ArtifactQualityCheckResult moduleQuality = context.qualityCheckResult();

            builder.append("| ")
                    .append(safeText(module.getModuleKey()))
                    .append(" | ")
                    .append(safeText(module.getModuleName()))
                    .append(" | ")
                    .append(moduleQuality.score())
                    .append(" | ")
                    .append(moduleQuality.blockers().size())
                    .append(" | ")
                    .append(moduleQuality.warnings().size())
                    .append(" | modules/")
                    .append(context.folderName())
                    .append(" |\n");
        }

        builder.append("\n## 阻断项明细\n\n");

        boolean hasBlocker = false;

        for (ProjectModuleExportContext context : moduleContexts) {
            if (context.qualityCheckResult().blockers().isEmpty()) {
                continue;
            }

            hasBlocker = true;

            builder.append("### ")
                    .append(safeText(context.module().getModuleName()))
                    .append("\n\n");

            for (ArtifactQualityIssue issue : context.qualityCheckResult().blockers()) {
                builder.append("- **")
                        .append(issue.title())
                        .append("**：")
                        .append(issue.description())
                        .append("\n");
            }

            builder.append("\n");
        }

        if (!hasBlocker) {
            builder.append("无。\n");
        }

        builder.append("\n## 警告项明细\n\n");

        boolean hasWarning = false;

        for (ProjectModuleExportContext context : moduleContexts) {
            if (context.qualityCheckResult().warnings().isEmpty()) {
                continue;
            }

            hasWarning = true;

            builder.append("### ")
                    .append(safeText(context.module().getModuleName()))
                    .append("\n\n");

            for (ArtifactQualityIssue issue : context.qualityCheckResult().warnings()) {
                builder.append("- **")
                        .append(issue.title())
                        .append("**：")
                        .append(issue.description())
                        .append("\n");
            }

            builder.append("\n");
        }

        if (!hasWarning) {
            builder.append("无。\n");
        }

        return builder.toString();
    }

    private byte[] buildModuleArtifactDocx(
            String projectCode,
            String moduleKey,
            String moduleName,
            LocalDateTime exportedAt,
            String exportedBy,
            List<ArtifactExportDefinition> definitions,
            Map<String, ArtifactVersionEntity> artifactByType,
            ArtifactQualityCheckResult qualityCheckResult,
            boolean forceExport
    ) {
        try (
                XWPFDocument document = new XWPFDocument();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()
        ) {
            appendCoverPage(
                    document,
                    projectCode,
                    moduleKey,
                    moduleName,
                    exportedAt,
                    exportedBy,
                    forceExport
            );

            appendHeading(document, "一、交付质量检查摘要", 1);
            appendQualitySummary(
                    document,
                    qualityCheckResult,
                    forceExport
            );

            appendHeading(document, "二、需求资产状态", 1);
            appendAssetStatusTable(
                    document,
                    qualityCheckResult
            );

            int index = 1;

            for (ArtifactExportDefinition definition : definitions) {
                ArtifactVersionEntity artifact = artifactByType.get(
                        definition.artifactType()
                );

                appendPageBreak(document);

                appendHeading(
                        document,
                        "三." + index + " " + definition.title(),
                        1
                );

                appendArtifactSection(
                        document,
                        definition,
                        artifact
                );

                index++;
            }

            appendPageBreak(document);
            appendHeading(document, "附录：导出元信息", 1);

            appendNormalParagraph(
                    document,
                    "项目编码：" + safeText(projectCode)
            );
            appendNormalParagraph(
                    document,
                    "模块 Key：" + safeText(moduleKey)
            );
            appendNormalParagraph(
                    document,
                    "模块名称：" + safeText(moduleName)
            );
            appendNormalParagraph(
                    document,
                    "导出人：" + safeText(exportedBy)
            );
            appendNormalParagraph(
                    document,
                    "导出时间：" + exportedAt
            );
            appendNormalParagraph(
                    document,
                    "是否强制导出：" + (forceExport ? "是" : "否")
            );

            document.write(outputStream);

            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to export module artifacts docx.", e);
        }
    }

    private void appendCoverPage(
            XWPFDocument document,
            String projectCode,
            String moduleKey,
            String moduleName,
            LocalDateTime exportedAt,
            String exportedBy,
            boolean forceExport
    ) {
        XWPFParagraph titleParagraph = document.createParagraph();
        titleParagraph.setAlignment(ParagraphAlignment.CENTER);
        titleParagraph.setSpacingBefore(240);
        titleParagraph.setSpacingAfter(260);

        XWPFRun titleRun = titleParagraph.createRun();
        applyRunFont(titleRun, 22, true);
        titleRun.setText("CodeMind Studio");
        titleRun.addBreak();
        titleRun.setText("模块需求资产交付文档");

        XWPFParagraph subTitleParagraph = document.createParagraph();
        subTitleParagraph.setAlignment(ParagraphAlignment.CENTER);
        subTitleParagraph.setSpacingAfter(360);

        XWPFRun subTitleRun = subTitleParagraph.createRun();
        applyRunFont(subTitleRun, 14, false);
        subTitleRun.setText("Requirement Agent Delivery Document");

        XWPFTable table = document.createTable(6, 2);
        styleKeyValueTable(table);
        setTableCellText(table.getRow(0).getCell(0), "项目编码", true);
        setTableCellText(table.getRow(0).getCell(1), safeText(projectCode), false);

        setTableCellText(table.getRow(1).getCell(0), "模块 Key", true);
        setTableCellText(table.getRow(1).getCell(1), safeText(moduleKey), false);

        setTableCellText(table.getRow(2).getCell(0), "模块名称", true);
        setTableCellText(table.getRow(2).getCell(1), safeText(moduleName), false);

        setTableCellText(table.getRow(3).getCell(0), "导出人", true);
        setTableCellText(table.getRow(3).getCell(1), safeText(exportedBy), false);

        setTableCellText(table.getRow(4).getCell(0), "导出时间", true);
        setTableCellText(table.getRow(4).getCell(1), exportedAt.toString(), false);

        setTableCellText(table.getRow(5).getCell(0), "导出方式", true);
        setTableCellText(
                table.getRow(5).getCell(1),
                forceExport ? "强制导出" : "质量检查通过后导出",
                false
        );

        appendPageBreak(document);
    }

    private void appendQualitySummary(
            XWPFDocument document,
            ArtifactQualityCheckResult result,
            boolean forceExport
    ) {
        XWPFTable table = document.createTable(6, 2);

        setTableCellText(table.getRow(0).getCell(0), "质量评分", true);
        setTableCellText(table.getRow(0).getCell(1), result.score() + " / 100", false);

        setTableCellText(table.getRow(1).getCell(0), "阻断项数量", true);
        setTableCellText(table.getRow(1).getCell(1), String.valueOf(result.blockers().size()), false);

        setTableCellText(table.getRow(2).getCell(0), "警告项数量", true);
        setTableCellText(table.getRow(2).getCell(1), String.valueOf(result.warnings().size()), false);

        setTableCellText(table.getRow(3).getCell(0), "通过项数量", true);
        setTableCellText(table.getRow(3).getCell(1), String.valueOf(result.passes().size()), false);

        setTableCellText(table.getRow(4).getCell(0), "是否强制导出", true);
        setTableCellText(table.getRow(4).getCell(1), forceExport ? "是" : "否", false);

        setTableCellText(table.getRow(5).getCell(0), "交付结论", true);
        setTableCellText(
                table.getRow(5).getCell(1),
                result.blockers().isEmpty()
                        ? "满足交付条件"
                        : forceExport
                        ? "存在阻断项，用户确认后强制导出"
                        : "存在阻断项，暂不建议交付",
                false
        );

        appendIssueList(document, "阻断项", result.blockers());
        appendIssueList(document, "警告项", result.warnings());
        appendIssueList(document, "通过项", result.passes());
    }

    private void appendAssetStatusTable(
            XWPFDocument document,
            ArtifactQualityCheckResult result
    ) {
        XWPFTable table = document.createTable(
                result.assets().size() + 1,
                5
        );
        styleAssetStatusTable(table);

        XWPFTableRow header = table.getRow(0);
        setTableCellText(header.getCell(0), "资产", true);
        setTableCellText(header.getCell(1), "版本", true);
        setTableCellText(header.getCell(2), "状态", true);
        setTableCellText(header.getCell(3), "检查结果", true);
        setTableCellText(header.getCell(4), "更新时间", true);

        int rowIndex = 1;

        for (ArtifactQualityAsset asset : result.assets()) {
            XWPFTableRow row = table.getRow(rowIndex);

            setTableCellText(row.getCell(0), asset.title(), false);
            setTableCellText(row.getCell(1), asset.versionNo() == null ? "-" : "v" + asset.versionNo(), false);
            setTableCellText(row.getCell(2), asset.statusText(), false);
            setTableCellText(row.getCell(3), asset.level(), false);
            setTableCellText(row.getCell(4), asset.updatedAt() == null ? "-" : asset.updatedAt(), false);

            rowIndex++;
        }
    }

    private void appendArtifactSection(
            XWPFDocument document,
            ArtifactExportDefinition definition,
            ArtifactVersionEntity artifact
    ) {
        appendHeading(document, "导出元信息", 2);

        if (artifact == null) {
            appendNormalParagraph(document, "资产类型：" + definition.artifactType());
            appendNormalParagraph(document, "资产名称：" + definition.title());
            appendNormalParagraph(document, "生成状态：未生成");
            appendHeading(document, "内容", 2);
            appendNormalParagraph(document, "当前模块尚未生成该资产。");
            return;
        }

        XWPFTable metaTable = document.createTable(8, 2);
        styleKeyValueTable(metaTable);

        setTableCellText(metaTable.getRow(0).getCell(0), "资产类型", true);
        setTableCellText(metaTable.getRow(0).getCell(1), definition.artifactType(), false);

        setTableCellText(metaTable.getRow(1).getCell(0), "资产名称", true);
        setTableCellText(metaTable.getRow(1).getCell(1), definition.title(), false);

        setTableCellText(metaTable.getRow(2).getCell(0), "资产 ID", true);
        setTableCellText(metaTable.getRow(2).getCell(1), artifact.getId() == null ? "-" : artifact.getId().toString(), false);

        setTableCellText(metaTable.getRow(3).getCell(0), "版本号", true);
        setTableCellText(metaTable.getRow(3).getCell(1), "v" + artifact.getVersionNo(), false);

        setTableCellText(metaTable.getRow(4).getCell(0), "状态", true);
        setTableCellText(metaTable.getRow(4).getCell(1), safeText(artifact.getStatus()), false);

        setTableCellText(metaTable.getRow(5).getCell(0), "是否当前版本", true);
        setTableCellText(metaTable.getRow(5).getCell(1), Boolean.TRUE.equals(artifact.getCurrent()) ? "是" : "否", false);

        setTableCellText(metaTable.getRow(6).getCell(0), "创建时间", true);
        setTableCellText(metaTable.getRow(6).getCell(1), artifact.getCreatedAt() == null ? "-" : artifact.getCreatedAt().toString(), false);

        setTableCellText(metaTable.getRow(7).getCell(0), "更新时间", true);
        setTableCellText(metaTable.getRow(7).getCell(1), artifact.getUpdatedAt() == null ? "-" : artifact.getUpdatedAt().toString(), false);

        appendHeading(document, "内容", 2);

        String markdown = artifact.getContentMarkdown();

        if (markdown != null && !markdown.isBlank()) {
            appendMarkdownLikeContent(
                    document,
                    markdown
            );
            return;
        }

        appendCodeBlock(
                document,
                toPrettyJson(artifact.getContent())
        );
    }

    private void appendIssueList(
            XWPFDocument document,
            String title,
            List<ArtifactQualityIssue> issues
    ) {
        appendHeading(document, title, 2);

        if (issues.isEmpty()) {
            appendNormalParagraph(document, "无。");
            return;
        }

        for (ArtifactQualityIssue issue : issues) {
            appendBulletParagraph(
                    document,
                    issue.title() + "：" + issue.description()
            );
        }
    }
    private void appendMarkdownLikeContent(
            XWPFDocument document,
            String markdown
    ) {
        String[] lines = markdown.split("\\R");
        boolean inCodeBlock = false;
        StringBuilder codeBlock = new StringBuilder();

        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine.strip();

            if (line.startsWith("```")) {
                if (!inCodeBlock) {
                    inCodeBlock = true;
                    codeBlock.setLength(0);
                } else {
                    inCodeBlock = false;
                    appendCodeBlock(
                            document,
                            codeBlock.toString()
                    );
                }

                continue;
            }

            if (inCodeBlock) {
                codeBlock.append(rawLine).append("\n");
                continue;
            }

            if (line.isBlank()) {
                continue;
            }

            if (line.startsWith("# ")) {
                appendHeading(document, line.substring(2).trim(), 2);
            } else if (line.startsWith("## ")) {
                appendHeading(document, line.substring(3).trim(), 2);
            } else if (line.startsWith("### ")) {
                appendHeading(document, line.substring(4).trim(), 3);
            } else if (line.startsWith("- ") || line.startsWith("* ")) {
                appendBulletParagraph(document, line.substring(2).trim());
            } else {
                appendNormalParagraph(document, cleanInlineMarkdown(line));
            }
        }

        if (inCodeBlock && codeBlock.length() > 0) {
            appendCodeBlock(
                    document,
                    codeBlock.toString()
            );
        }
    }

    private String cleanInlineMarkdown(
            String value
    ) {
        if (value == null) {
            return "";
        }

        return value
                .replace("**", "")
                .replace("__", "")
                .replace("`", "");
    }

    private void appendHeading(
            XWPFDocument document,
            String text,
            int level
    ) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingBefore(level == 1 ? 260 : 160);
        paragraph.setSpacingAfter(level == 1 ? 160 : 100);
        paragraph.setKeepNext(true);

        XWPFRun run = paragraph.createRun();

        if (level == 1) {
            applyRunFont(run, 16, true);
            run.setColor("0F172A");
        } else if (level == 2) {
            applyRunFont(run, 13, true);
            run.setColor("1E293B");
        } else {
            applyRunFont(run, 11, true);
            run.setColor("334155");
        }

        run.setText(text);
    }

    private void appendNormalParagraph(
            XWPFDocument document,
            String text
    ) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.BOTH);
        paragraph.setSpacingBefore(20);
        paragraph.setSpacingAfter(80);
        paragraph.setFirstLineIndent(420);

        XWPFRun run = paragraph.createRun();
        applyRunFont(run, 11, false);
        run.setColor("334155");
        run.setText(text == null ? "" : text);
    }

    private void appendBulletParagraph(
            XWPFDocument document,
            String text
    ) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingBefore(20);
        paragraph.setSpacingAfter(60);
        paragraph.setIndentationLeft(420);

        XWPFRun run = paragraph.createRun();
        applyRunFont(run, 11, false);
        run.setColor("334155");
        run.setText("• " + (text == null ? "" : cleanInlineMarkdown(text)));
    }

    private void appendCodeBlock(
            XWPFDocument document,
            String text
    ) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingBefore(80);
        paragraph.setSpacingAfter(80);

        XWPFRun run = paragraph.createRun();
        run.setFontFamily("Consolas");
        run.setFontSize(9);
        run.setText(text == null || text.isBlank() ? "-" : text);
    }

    private void appendPageBreak(
            XWPFDocument document
    ) {
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun run = paragraph.createRun();
        run.addBreak(BreakType.PAGE);
    }

    private void setTableCellText(
            XWPFTableCell cell,
            String text,
            boolean bold
    ) {
        while (cell.getParagraphs().size() > 0) {
            cell.removeParagraph(0);
        }

        cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);

        XWPFParagraph paragraph = cell.addParagraph();
        paragraph.setSpacingBefore(0);
        paragraph.setSpacingAfter(0);
        paragraph.setAlignment(ParagraphAlignment.LEFT);
        paragraph.setVerticalAlignment(TextAlignment.CENTER);

        XWPFRun run = paragraph.createRun();
        applyRunFont(run, 10, bold);
        run.setText(text == null || text.isBlank() ? "-" : text);
    }

    private void styleBaseTable(
            XWPFTable table
    ) {
        table.setWidth("100%");
        table.setTableAlignment(TableRowAlign.CENTER);
        table.setCellMargins(90, 140, 90, 140);

        table.setTopBorder(XWPFBorderType.SINGLE, 4, 0, "CBD5E1");
        table.setBottomBorder(XWPFBorderType.SINGLE, 4, 0, "CBD5E1");
        table.setLeftBorder(XWPFBorderType.SINGLE, 4, 0, "CBD5E1");
        table.setRightBorder(XWPFBorderType.SINGLE, 4, 0, "CBD5E1");
        table.setInsideHBorder(XWPFBorderType.SINGLE, 4, 0, "E2E8F0");
        table.setInsideVBorder(XWPFBorderType.SINGLE, 4, 0, "E2E8F0");
    }

    private void styleKeyValueTable(
            XWPFTable table
    ) {
        styleBaseTable(table);

        for (XWPFTableRow row : table.getRows()) {
            if (row.getTableCells().size() >= 2) {
                setCellWidth(row.getCell(0), 2600);
                setCellWidth(row.getCell(1), 6800);

                row.getCell(0).setColor("F8FAFC");
                row.getCell(1).setColor("FFFFFF");
            }
        }
    }

    private void styleAssetStatusTable(
            XWPFTable table
    ) {
        styleBaseTable(table);

        int[] widths = {
                2500,
                1200,
                1800,
                1800,
                3000
        };

        for (int rowIndex = 0; rowIndex < table.getRows().size(); rowIndex++) {
            XWPFTableRow row = table.getRow(rowIndex);

            for (int cellIndex = 0; cellIndex < row.getTableCells().size(); cellIndex++) {
                XWPFTableCell cell = row.getCell(cellIndex);

                if (cellIndex < widths.length) {
                    setCellWidth(cell, widths[cellIndex]);
                }

                cell.setColor(rowIndex == 0 ? "EFF6FF" : "FFFFFF");
            }
        }
    }

    private void setCellWidth(
            XWPFTableCell cell,
            int widthTwips
    ) {
        CTTcPr tcPr = cell.getCTTc().isSetTcPr()
                ? cell.getCTTc().getTcPr()
                : cell.getCTTc().addNewTcPr();

        CTTblWidth cellWidth = tcPr.isSetTcW()
                ? tcPr.getTcW()
                : tcPr.addNewTcW();

        cellWidth.setW(BigInteger.valueOf(widthTwips));
        cellWidth.setType(STTblWidth.DXA);
    }

    private void applyRunFont(
            XWPFRun run,
            int fontSize,
            boolean bold
    ) {
        run.setFontFamily("Times New Roman");
        run.setFontSize(fontSize);
        run.setBold(bold);

        CTRPr rPr = run.getCTR().isSetRPr()
                ? run.getCTR().getRPr()
                : run.getCTR().addNewRPr();

        CTFonts fonts = rPr.sizeOfRFontsArray() > 0
                ? rPr.getRFontsArray(0)
                : rPr.addNewRFonts();

        fonts.setAscii("Times New Roman");
        fonts.setHAnsi("Times New Roman");
        fonts.setEastAsia("宋体");
        fonts.setCs("Times New Roman");
    }

    private String buildExportReadme(
            String projectCode,
            String moduleKey,
            String moduleName,
            LocalDateTime exportedAt
    ) {
        return """
            # CodeMind 节点需求资产交付包

            ## 导出说明

            本 ZIP 文件由 CodeMind Studio 自动生成，用于当前模块树节点的需求资产交付。
            资产清单会根据节点层级动态生成；业务域、模块、子模块、页面和功能点不会强制套用同一套交付项。

            ## 基础信息

            - 项目编码：%s
            - 节点 Key：%s
            - 节点名称：%s
            - 导出时间：%s

            ## 文件说明

            - README.md：导出说明
            - QUALITY_REPORT.md：当前节点质量检查报告
            - manifest.json：导出元数据和动态资产清单
            - 其他资产文件：根据当前节点层级动态生成，具体以 manifest.json 中的 assets 为准

            ## 版本说明

            每个资产文件均导出当前版本。若某个核心资产尚未生成，质量检查会标记为阻断项。
            补充资产缺失时会标记为警告项，允许后续按需补齐。
            历史版本不会被删除，可在 CodeMind Studio 的版本历史中继续查看和回滚。
            """.formatted(
                safeText(projectCode),
                safeText(moduleKey),
                safeText(moduleName),
                exportedAt.toString()
        );
    }

    private String buildArtifactMarkdown(
            ArtifactExportDefinition definition,
            ArtifactVersionEntity artifact
    ) {
        StringBuilder builder = new StringBuilder();

        builder.append("# ")
                .append(definition.title())
                .append("\n\n");

        builder.append("## 导出元信息\n\n");
        builder.append("- 资产类型：").append(definition.artifactType()).append("\n");
        builder.append("- 资产名称：").append(definition.title()).append("\n");
        builder.append("- 核心检查项：").append(definition.required() ? "是" : "否").append("\n");

        if (artifact == null) {
            builder.append("- 生成状态：未生成\n\n");
            builder.append("## 内容\n\n");
            builder.append("当前模块尚未生成该资产。\n");
            return builder.toString();
        }

        builder.append("- 资产 ID：").append(artifact.getId()).append("\n");
        builder.append("- 版本号：v").append(artifact.getVersionNo()).append("\n");
        builder.append("- 状态：").append(safeText(artifact.getStatus())).append("\n");
        builder.append("- 是否当前版本：")
                .append(Boolean.TRUE.equals(artifact.getCurrent()) ? "是" : "否")
                .append("\n");
        builder.append("- 创建人：").append(safeText(artifact.getCreatedBy())).append("\n");
        builder.append("- 创建时间：")
                .append(artifact.getCreatedAt() == null ? "-" : artifact.getCreatedAt())
                .append("\n");
        builder.append("- 更新时间：")
                .append(artifact.getUpdatedAt() == null ? "-" : artifact.getUpdatedAt())
                .append("\n\n");

        builder.append("- Prompt 模板 Key：").append(safeText(artifact.getPromptTemplateKey())).append("\n");
        builder.append("- Prompt 模板版本：").append(safeText(artifact.getPromptTemplateVersion())).append("\n");
        builder.append("- Prompt 模板校验值：").append(safeText(artifact.getPromptTemplateChecksum())).append("\n");

        builder.append("## 内容\n\n");

        String markdown = artifact.getContentMarkdown();

        if (markdown != null && !markdown.isBlank()) {
            builder.append(markdown.trim()).append("\n");
            return builder.toString();
        }

        builder.append("```json\n");
        builder.append(toPrettyJson(artifact.getContent()));
        builder.append("\n```\n");

        return builder.toString();
    }

    private Map<String, Object> buildExportAssetManifestItem(
            ArtifactExportDefinition definition,
            ArtifactVersionEntity artifact
    ) {
        Map<String, Object> item = new LinkedHashMap<>();

        item.put("file_name", definition.fileName());
        item.put("artifact_type", definition.artifactType());
        item.put("title", definition.title());
        item.put("required", definition.required());
        item.put("exists", artifact != null);
        item.put("prompt_template", buildArtifactPromptTemplateManifest(artifact));

        if (artifact == null) {
            item.put("prompt_template", null);
            item.put("artifact_id", null);
            item.put("version_no", null);
            item.put("status", "MISSING");
            item.put("current", false);
            item.put("created_by", null);
            item.put("created_at", null);
            item.put("updated_at", null);
            return item;
        }

        item.put("artifact_id", artifact.getId() == null ? null : artifact.getId().toString());
        item.put("version_no", artifact.getVersionNo());
        item.put("status", artifact.getStatus());
        item.put("current", artifact.getCurrent());
        item.put("created_by", artifact.getCreatedBy());
        item.put("created_at", artifact.getCreatedAt() == null ? null : artifact.getCreatedAt().toString());
        item.put("updated_at", artifact.getUpdatedAt() == null ? null : artifact.getUpdatedAt().toString());

        return item;
    }

    private ArtifactQualityCheckResult buildModuleQualityCheckResult(
            List<ArtifactExportDefinition> definitions,
            Map<String, ArtifactVersionEntity> artifactByType,
            LocalDateTime generatedAt
    ) {
        List<ArtifactQualityAsset> assets = new ArrayList<>();
        List<ArtifactQualityIssue> blockers = new ArrayList<>();
        List<ArtifactQualityIssue> warnings = new ArrayList<>();
        List<ArtifactQualityIssue> passes = new ArrayList<>();

        List<String> expectedArtifactTypes = definitions
                .stream()
                .map(ArtifactExportDefinition::artifactType)
                .toList();

        for (ArtifactExportDefinition definition : definitions) {
            ArtifactVersionEntity artifact = artifactByType.get(definition.artifactType());
            boolean exists = artifact != null;
            boolean contentOk = hasMeaningfulArtifactContent(artifact);

            String status = artifact == null ? "MISSING" : safeText(artifact.getStatus());

            String level = "pass";
            String statusText = "已生成";

            if (!exists) {
                level = definition.required() ? "blocker" : "warning";
                statusText = definition.required() ? "缺失" : "未生成（可后补）";
            } else if (!contentOk) {
                level = definition.required() ? "blocker" : "warning";
                statusText = "内容为空";
            } else if ("REJECTED".equals(status)) {
                level = definition.required() ? "blocker" : "warning";
                statusText = "已退回";
            } else if ("DRAFT".equals(status)
                    || "ACTIVE".equals(status)
                    || "PENDING_REVIEW".equals(status)) {
                level = "warning";
                statusText = "PENDING_REVIEW".equals(status) ? "待审核" : "未确认";
            } else if ("APPROVED".equals(status)) {
                level = "pass";
                statusText = "已确认";
            } else {
                level = "warning";
                statusText = status;
            }

            if (artifact != null && Boolean.FALSE.equals(artifact.getCurrent()) && !"blocker".equals(level)) {
                level = "warning";
                statusText = "非当前版本";
            }

            ArtifactQualityAsset asset = new ArtifactQualityAsset(
                    definition.artifactType(),
                    definition.title(),
                    definition.required(),
                    exists,
                    artifact == null ? null : artifact.getVersionNo(),
                    status,
                    artifact == null ? false : artifact.getCurrent(),
                    artifact == null || artifact.getUpdatedAt() == null ? null : artifact.getUpdatedAt().toString(),
                    level,
                    statusText
            );

            assets.add(asset);

            if (!exists) {
                ArtifactQualityIssue issue = new ArtifactQualityIssue(
                        "missing-" + definition.artifactType(),
                        level,
                        definition.title() + "缺失",
                        definition.required()
                                ? "当前节点尚未生成核心资产“" + definition.title() + "”，会影响正式交付。"
                                : "当前节点尚未生成补充资产“" + definition.title() + "”，建议在正式交付或进入开发 Agent 前补齐。",
                        definition.artifactType()
                );

                if (definition.required()) {
                    blockers.add(issue);
                } else {
                    warnings.add(issue);
                }

                continue;
            }

            if ("REJECTED".equals(status)) {
                ArtifactQualityIssue issue = new ArtifactQualityIssue(
                        "rejected-" + definition.artifactType(),
                        level,
                        definition.title() + "已退回",
                        definition.title() + "当前处于已退回状态，建议修改并重新提交审核。",
                        definition.artifactType()
                );

                if (definition.required()) {
                    blockers.add(issue);
                } else {
                    warnings.add(issue);
                }

                continue;
            }

            if (!contentOk) {
                ArtifactQualityIssue issue = new ArtifactQualityIssue(
                        "empty-" + definition.artifactType(),
                        level,
                        definition.title() + "内容不可用",
                        definition.title() + "存在空内容或占位内容，无法作为正式交付依据。",
                        definition.artifactType()
                );

                if (definition.required()) {
                    blockers.add(issue);
                } else {
                    warnings.add(issue);
                }

                continue;
            }

            if ("warning".equals(level)) {
                warnings.add(new ArtifactQualityIssue(
                        "status-" + definition.artifactType(),
                        "warning",
                        definition.title() + "尚未确认",
                        definition.title() + "当前状态为“" + statusText + "”，建议确认通过后再交付。",
                        definition.artifactType()
                ));
            } else {
                passes.add(new ArtifactQualityIssue(
                        "pass-" + definition.artifactType(),
                        "pass",
                        definition.title() + "已生成",
                        definition.title() + "存在有效内容，可以进入交付检查。",
                        definition.artifactType()
                ));
            }
        }

        for (ArtifactExportDefinition definition : definitions) {
            ArtifactVersionEntity artifact = artifactByType.get(definition.artifactType());

            if (artifact == null || artifact.getUpdatedAt() == null) {
                continue;
            }

            for (String upstreamType : upstreamArtifactTypesOf(definition.artifactType())) {
                if (!expectedArtifactTypes.contains(upstreamType)) {
                    continue;
                }

                ArtifactVersionEntity upstreamArtifact = artifactByType.get(upstreamType);

                if (upstreamArtifact == null || upstreamArtifact.getUpdatedAt() == null) {
                    continue;
                }

                if (upstreamArtifact.getUpdatedAt().isAfter(artifact.getUpdatedAt())) {
                    String upstreamTitle = titleOfArtifactType(
                            definitions,
                            upstreamType
                    );

                    warnings.add(new ArtifactQualityIssue(
                            "stale-" + definition.artifactType() + "-" + upstreamType,
                            "warning",
                            definition.title() + "可能已滞后",
                            upstreamTitle + "的更新时间晚于" + definition.title()
                                    + "，建议重新生成" + definition.title() + "以保持链路一致。",
                            definition.artifactType()
                    ));
                }
            }
        }

        int score = Math.max(
                0,
                100 - blockers.size() * 18 - warnings.size() * 6
        );

        return new ArtifactQualityCheckResult(
                score,
                generatedAt.toString(),
                assets,
                blockers,
                warnings,
                passes
        );
    }

    private boolean hasMeaningfulArtifactContent(
            ArtifactVersionEntity artifact
    ) {
        if (artifact == null) {
            return false;
        }

        String markdown = artifact.getContentMarkdown();

        if (markdown != null && markdown.trim().length() > 20) {
            String normalized = markdown.trim();

            return !normalized.contains("当前模块尚未生成")
                    && !normalized.contains("暂无内容")
                    && !normalized.contains("TODO")
                    && !normalized.contains("占位");
        }

        if (artifact.getContent() == null || artifact.getContent().isEmpty()) {
            return false;
        }

        String contentText = toPrettyJson(artifact.getContent());

        return contentText.length() > 20
                && !"{}".equals(contentText.trim())
                && !contentText.contains("当前模块尚未生成")
                && !contentText.contains("暂无内容");
    }

    private List<String> upstreamArtifactTypesOf(
            String artifactType
    ) {
        return switch (artifactType) {
            case "BUSINESS_FLOW" -> List.of("MODULE_PRD");
            case "PAGE_SPEC" -> List.of("MODULE_PRD", "BUSINESS_FLOW");
            case "DATA_MODEL" -> List.of("MODULE_PRD", "BUSINESS_FLOW", "PAGE_SPEC");
            case "API_CONTRACT" -> List.of("PAGE_SPEC", "DATA_MODEL");
            case "TEST_SPEC" -> List.of("PAGE_SPEC", "DATA_MODEL", "API_CONTRACT");
            case "UI_CONTRACT" -> List.of(
                    "MODULE_PRD",
                    "BUSINESS_FLOW",
                    "PAGE_SPEC",
                    "DATA_MODEL",
                    "API_CONTRACT",
                    "TEST_SPEC"
            );
            case "DEV_HANDOFF_PACKAGE" -> List.of(
                    "MODULE_PRD",
                    "BUSINESS_FLOW",
                    "PAGE_SPEC",
                    "DATA_MODEL",
                    "API_CONTRACT",
                    "TEST_SPEC",
                    "UI_CONTRACT"
            );
            default -> List.of();
        };
    }

    private String titleOfArtifactType(
            List<ArtifactExportDefinition> definitions,
            String artifactType
    ) {
        return definitions.stream()
                .filter(definition -> definition.artifactType().equals(artifactType))
                .findFirst()
                .map(ArtifactExportDefinition::title)
                .orElse(artifactType);
    }

    private Map<String, Object> buildQualityCheckManifest(
            ArtifactQualityCheckResult result,
            boolean forceExport
    ) {
        Map<String, Object> quality = new LinkedHashMap<>();

        quality.put("score", result.score());
        quality.put("generated_at", result.generatedAt());
        quality.put("blocker_count", result.blockers().size());
        quality.put("warning_count", result.warnings().size());
        quality.put("pass_count", result.passes().size());
        quality.put("forced_export", forceExport);
        quality.put(
                "export_decision",
                result.blockers().isEmpty()
                        ? "READY"
                        : forceExport ? "FORCED_WITH_BLOCKERS" : "BLOCKED"
        );
        quality.put("assets", result.assets());
        quality.put("blockers", result.blockers());
        quality.put("warnings", result.warnings());
        quality.put("passes", result.passes());

        return quality;
    }

    private ArtifactQualityCheckRecordEntity persistQualityCheckRecord(
            UUID projectId,
            UUID moduleId,
            String scope,
            ArtifactQualityCheckResult result,
            boolean forceExport,
            String source,
            String checkedBy
    ) {
        Map<String, Object> resultJson = buildQualityCheckManifest(
                result,
                forceExport
        );

        ArtifactQualityCheckRecordEntity record = new ArtifactQualityCheckRecordEntity();

        record.setProjectId(projectId);
        record.setModuleId(moduleId);
        record.setCheckScope(scope);
        record.setScore(result.score());
        record.setAssetCount(result.assets().size());
        record.setBlockerCount(result.blockers().size());
        record.setWarningCount(result.warnings().size());
        record.setPassCount(result.passes().size());
        record.setForcedExport(forceExport);
        record.setSource(source);
        record.setExportDecision(exportDecisionOf(result.blockers().size(), forceExport));
        record.setResultJson(resultJson);
        record.setCheckedBy(checkedBy);

        ArtifactQualityCheckRecordEntity saved =
                qualityCheckRecordRepository.save(record);

        auditService.record(
                projectId,
                "USER",
                checkedBy,
                "RUN_ARTIFACT_QUALITY_CHECK",
                "ARTIFACT_QUALITY_CHECK",
                saved.getId(),
                null,
                qualityCheckAuditJson(saved),
                Map.of(
                        "scope", scope,
                        "module_id", moduleId == null ? "" : moduleId.toString(),
                        "source", source,
                        "score", String.valueOf(result.score()),
                        "blocker_count", String.valueOf(result.blockers().size()),
                        "warning_count", String.valueOf(result.warnings().size())
                )
        );

        return saved;
    }

    private ArtifactQualityCheckRecordEntity persistProjectQualityCheckRecord(
            UUID projectId,
            Map<String, Object> projectQuality,
            boolean forceExport,
            String source,
            String checkedBy
    ) {
        int score = intValue(projectQuality.get("score"));
        int moduleCount = intValue(projectQuality.get("module_count"));
        int blockerCount = intValue(projectQuality.get("total_blocker_count"));
        int warningCount = intValue(projectQuality.get("total_warning_count"));
        int passCount = intValue(projectQuality.get("total_pass_count"));

        ArtifactQualityCheckRecordEntity record = new ArtifactQualityCheckRecordEntity();

        record.setProjectId(projectId);
        record.setModuleId(null);
        record.setCheckScope("PROJECT");
        record.setScore(score);
        record.setAssetCount(moduleCount);
        record.setBlockerCount(blockerCount);
        record.setWarningCount(warningCount);
        record.setPassCount(passCount);
        record.setForcedExport(forceExport);
        record.setSource(source);
        record.setExportDecision(blockerCount == 0 ? "READY" : forceExport ? "FORCED_WITH_BLOCKERS" : "BLOCKED");
        record.setResultJson(projectQuality);
        record.setCheckedBy(checkedBy);

        ArtifactQualityCheckRecordEntity saved =
                qualityCheckRecordRepository.save(record);

        auditService.record(
                projectId,
                "USER",
                checkedBy,
                "RUN_PROJECT_QUALITY_CHECK",
                "ARTIFACT_QUALITY_CHECK",
                saved.getId(),
                null,
                qualityCheckAuditJson(saved),
                Map.of(
                        "scope", "PROJECT",
                        "source", source,
                        "score", String.valueOf(score),
                        "blocker_count", String.valueOf(blockerCount),
                        "warning_count", String.valueOf(warningCount)
                )
        );

        return saved;
    }

    private ArtifactExportRecordEntity persistExportRecord(
            UUID projectId,
            UUID moduleId,
            ArtifactQualityCheckRecordEntity qualityRecord,
            String exportScope,
            String exportFormat,
            String exportStatus,
            String fileName,
            long fileSizeBytes,
            boolean forceExport,
            Map<String, Object> manifest,
            String failureReason,
            String exportedBy
    ) {
        ArtifactExportRecordEntity record = new ArtifactExportRecordEntity();

        record.setProjectId(projectId);
        record.setModuleId(moduleId);
        record.setQualityCheckId(qualityRecord == null ? null : qualityRecord.getId());
        record.setExportScope(exportScope);
        record.setExportFormat(exportFormat);
        record.setExportStatus(exportStatus);
        record.setFileName(fileName);
        record.setFileSizeBytes(fileSizeBytes);
        record.setForcedExport(forceExport);
        record.setQualityScore(qualityRecord == null ? null : qualityRecord.getScore());
        record.setBlockerCount(qualityRecord == null ? 0 : qualityRecord.getBlockerCount());
        record.setWarningCount(qualityRecord == null ? 0 : qualityRecord.getWarningCount());
        record.setExportDecision(qualityRecord == null ? null : qualityRecord.getExportDecision());
        record.setManifestJson(manifest);
        record.setFailureReason(failureReason);
        record.setExportedBy(exportedBy);

        ArtifactExportRecordEntity saved = exportRecordRepository.save(record);

        String action = exportAuditAction(exportScope, exportFormat, exportStatus, forceExport);

        auditService.record(
                projectId,
                "USER",
                exportedBy,
                action,
                "ARTIFACT_EXPORT",
                saved.getId(),
                null,
                exportAuditJson(saved),
                Map.of(
                        "scope", exportScope,
                        "format", exportFormat,
                        "status", exportStatus,
                        "module_id", moduleId == null ? "" : moduleId.toString(),
                        "quality_check_id", qualityRecord == null ? "" : qualityRecord.getId().toString(),
                        "forced_export", String.valueOf(forceExport)
                )
        );

        return saved;
    }

    private String exportDecisionOf(
            int blockerCount,
            boolean forceExport
    ) {
        if (blockerCount == 0) {
            return "READY";
        }

        return forceExport ? "FORCED_WITH_BLOCKERS" : "BLOCKED";
    }

    private String exportAuditAction(
            String scope,
            String format,
            String status,
            boolean forceExport
    ) {
        if ("BLOCKED".equals(status)) {
            return "BLOCKED_" + scope + "_" + format + "_EXPORT";
        }

        if (forceExport) {
            return "FORCE_EXPORT_" + scope + "_" + format;
        }

        return "EXPORT_" + scope + "_" + format;
    }

    private Map<String, Object> qualityCheckAuditJson(
            ArtifactQualityCheckRecordEntity record
    ) {
        Map<String, Object> json = new LinkedHashMap<>();

        json.put("id", record.getId() == null ? null : record.getId().toString());
        json.put("project_id", record.getProjectId() == null ? null : record.getProjectId().toString());
        json.put("module_id", record.getModuleId() == null ? null : record.getModuleId().toString());
        json.put("check_scope", record.getCheckScope());
        json.put("score", record.getScore());
        json.put("asset_count", record.getAssetCount());
        json.put("blocker_count", record.getBlockerCount());
        json.put("warning_count", record.getWarningCount());
        json.put("pass_count", record.getPassCount());
        json.put("export_decision", record.getExportDecision());
        json.put("forced_export", record.getForcedExport());
        json.put("source", record.getSource());
        json.put("checked_by", record.getCheckedBy());
        json.put("checked_at", record.getCheckedAt() == null ? null : record.getCheckedAt().toString());

        return json;
    }

    private Map<String, Object> exportAuditJson(
            ArtifactExportRecordEntity record
    ) {
        Map<String, Object> json = new LinkedHashMap<>();

        json.put("id", record.getId() == null ? null : record.getId().toString());
        json.put("project_id", record.getProjectId() == null ? null : record.getProjectId().toString());
        json.put("module_id", record.getModuleId() == null ? null : record.getModuleId().toString());
        json.put("quality_check_id", record.getQualityCheckId() == null ? null : record.getQualityCheckId().toString());
        json.put("export_scope", record.getExportScope());
        json.put("export_format", record.getExportFormat());
        json.put("export_status", record.getExportStatus());
        json.put("file_name", record.getFileName());
        json.put("file_size_bytes", record.getFileSizeBytes());
        json.put("forced_export", record.getForcedExport());
        json.put("quality_score", record.getQualityScore());
        json.put("blocker_count", record.getBlockerCount());
        json.put("warning_count", record.getWarningCount());
        json.put("export_decision", record.getExportDecision());
        json.put("failure_reason", record.getFailureReason());
        json.put("exported_by", record.getExportedBy());
        json.put("exported_at", record.getExportedAt() == null ? null : record.getExportedAt().toString());

        return json;
    }

    private int intValue(
            Object value
    ) {
        if (value instanceof Number number) {
            return number.intValue();
        }

        if (value == null) {
            return 0;
        }

        return Integer.parseInt(String.valueOf(value));
    }

    private String buildQualityReportMarkdown(
            ArtifactQualityCheckResult result,
            boolean forceExport
    ) {
        StringBuilder builder = new StringBuilder();

        builder.append("# 交付质量检查报告\n\n");

        builder.append("## 总览\n\n");
        builder.append("- 检查时间：").append(result.generatedAt()).append("\n");
        builder.append("- 质量评分：").append(result.score()).append(" / 100\n");
        builder.append("- 阻断项数量：").append(result.blockers().size()).append("\n");
        builder.append("- 警告项数量：").append(result.warnings().size()).append("\n");
        builder.append("- 通过项数量：").append(result.passes().size()).append("\n");
        builder.append("- 是否强制导出：").append(forceExport ? "是" : "否").append("\n");
        builder.append("- 交付结论：")
                .append(result.blockers().isEmpty()
                        ? "满足交付条件"
                        : forceExport ? "存在阻断项，用户确认后强制导出" : "存在阻断项，暂不建议交付")
                .append("\n\n");

        builder.append("## 资产状态\n\n");
        builder.append("| 资产 | 范围 | 状态 | 版本 | 检查结果 | 更新时间 |\n");
        builder.append("|---|---|---|---:|---|---|\n");

        for (ArtifactQualityAsset asset : result.assets()) {
            builder.append("| ")
                    .append(asset.title())
                    .append(" | ")
                    .append(asset.required() ? "核心" : "补充")
                    .append(" | ")
                    .append(asset.statusText())
                    .append(" | ")
                    .append(asset.versionNo() == null ? "-" : "v" + asset.versionNo())
                    .append(" | ")
                    .append(asset.level())
                    .append(" | ")
                    .append(asset.updatedAt() == null ? "-" : asset.updatedAt())
                    .append(" |\n");
        }

        appendQualityIssueSection(builder, "阻断项", result.blockers());
        appendQualityIssueSection(builder, "警告项", result.warnings());
        appendQualityIssueSection(builder, "通过项", result.passes());

        return builder.toString();
    }

    private void appendQualityIssueSection(
            StringBuilder builder,
            String title,
            List<ArtifactQualityIssue> issues
    ) {
        builder.append("\n## ").append(title).append("\n\n");

        if (issues.isEmpty()) {
            builder.append("无。\n");
            return;
        }

        for (ArtifactQualityIssue issue : issues) {
            builder.append("- **")
                    .append(issue.title())
                    .append("**：")
                    .append(issue.description())
                    .append("\n");
        }
    }

    private void putZipEntry(
            ZipOutputStream zipOutputStream,
            String entryName,
            String content
    ) throws Exception {
        ZipEntry entry = new ZipEntry(entryName);
        zipOutputStream.putNextEntry(entry);
        zipOutputStream.write(content.getBytes(StandardCharsets.UTF_8));
        zipOutputStream.closeEntry();
    }

    private String toPrettyJson(
            Object value
    ) {
        try {
            return objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize export manifest.", e);
        }
    }

    private String buildExportZipFileName(
            String projectCode,
            String moduleKey,
            String moduleName,
            LocalDateTime exportedAt
    ) {
        String timestamp = exportedAt.format(
                DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
        );

        String baseName = "codemind_"
                + safeFileNamePart(projectCode)
                + "_"
                + safeFileNamePart(moduleKey == null || moduleKey.isBlank() ? moduleName : moduleKey)
                + "_requirements_"
                + timestamp
                + ".zip";

        return baseName;
    }

    private String buildExportDocxFileName(
            String projectCode,
            String moduleKey,
            String moduleName,
            LocalDateTime exportedAt
    ) {
        String timestamp = exportedAt.format(
                DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
        );

        return "codemind_"
                + safeFileNamePart(projectCode)
                + "_"
                + safeFileNamePart(moduleKey == null || moduleKey.isBlank() ? moduleName : moduleKey)
                + "_requirements_"
                + timestamp
                + ".docx";
    }

    private String buildProjectExportZipFileName(
            String projectCode,
            String projectName,
            LocalDateTime exportedAt
    ) {
        String timestamp = exportedAt.format(
                DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
        );

        return "codemind_"
                + safeFileNamePart(projectCode)
                + "_"
                + safeFileNamePart(projectName)
                + "_project_delivery_"
                + timestamp
                + ".zip";
    }

    private String buildUniqueModuleFolderName(
            ProjectModuleEntity module,
            Map<String, Integer> folderNameCounters
    ) {
        String rawName = safeText(module.getModuleKey())
                + "_"
                + safeText(module.getModuleName());

        String baseName = safeFileNamePart(rawName);

        if (baseName == null || baseName.isBlank()) {
            baseName = "module_" + module.getId();
        }

        int count = folderNameCounters.getOrDefault(
                baseName,
                0
        ) + 1;

        folderNameCounters.put(
                baseName,
                count
        );

        if (count == 1) {
            return baseName;
        }

        return baseName + "_" + count;
    }

    private String safeFileNamePart(
            String value
    ) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }

        return value
                .trim()
                .replaceAll("[\\\\/:*?\"<>|\\s]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
    }

    private String safeText(
            String value
    ) {
        if (value == null || value.isBlank()) {
            return "-";
        }

        return value;
    }

    public record ModuleArtifactZipExport(
            String fileName,
            byte[] content
    ) {
    }

    public record ModuleArtifactDocxExport(
            String fileName,
            byte[] content
    ) {
    }

    public record ProjectArtifactZipExport(
            String fileName,
            byte[] content
    ) {
    }

    private record ArtifactExportDefinition(
            String fileName,
            String artifactType,
            String title,
            boolean required
    ) {
    }

    private record ArtifactQualityCheckResult(
            int score,
            String generatedAt,
            List<ArtifactQualityAsset> assets,
            List<ArtifactQualityIssue> blockers,
            List<ArtifactQualityIssue> warnings,
            List<ArtifactQualityIssue> passes
    ) {
    }

    private record ArtifactQualityAsset(
            String artifactType,
            String title,
            boolean required,
            boolean exists,
            Integer versionNo,
            String status,
            Boolean current,
            String updatedAt,
            String level,
            String statusText
    ) {
    }

    private record ArtifactQualityIssue(
            String id,
            String level,
            String title,
            String description,
            String artifactType
    ) {
    }

    private record ProjectModuleExportContext(
            ProjectModuleEntity module,
            String folderName,
            Map<String, ArtifactVersionEntity> artifactByType,
            ArtifactQualityCheckResult qualityCheckResult,
            Map<String, Object> manifest,
            List<ArtifactExportDefinition> definitions
    ) {
    }

    private ArtifactVersionEntity changeModuleArtifactReviewStatus(
            UUID projectId,
            UUID moduleId,
            String artifactType,
            UUID artifactId,
            String nextStatus,
            String auditAction,
            String username,
            String reason
    ) {
        ArtifactVersionEntity artifact = getModuleArtifactVersion(
                projectId,
                moduleId,
                artifactType,
                artifactId
        );

        String currentStatus = artifact.getStatus() == null
                ? "ACTIVE"
                : artifact.getStatus();

        validateReviewStatusTransition(
                artifact,
                currentStatus,
                nextStatus
        );

        Map<String, Object> beforeJson = toAuditJson(artifact);

        artifact.setStatus(nextStatus);

        ArtifactVersionEntity saved = artifactVersionRepository.save(artifact);

        auditService.record(
                projectId,
                "USER",
                username,
                auditAction,
                "ARTIFACT_VERSION",
                saved.getId(),
                beforeJson,
                toAuditJson(saved),
                Map.of(
                        "module_id", moduleId.toString(),
                        "artifact_type", artifactType,
                        "artifact_id", artifactId.toString(),
                        "from_status", currentStatus,
                        "to_status", nextStatus,
                        "reason", reason == null ? "" : reason
                )
        );

        return saved;
    }

    private void validateReviewStatusTransition(
            ArtifactVersionEntity artifact,
            String currentStatus,
            String nextStatus
    ) {
        boolean currentVersion = Boolean.TRUE.equals(artifact.getCurrent());

        if ("ARCHIVED".equals(nextStatus)) {
            if (currentVersion) {
                throw new IllegalStateException("当前版本不能直接归档。请先回滚到其他版本，再归档该历史版本。");
            }

            if ("ARCHIVED".equals(currentStatus)) {
                throw new IllegalStateException("该版本已经归档。");
            }

            return;
        }

        if (!currentVersion) {
            throw new IllegalStateException("只能对当前版本执行审核操作。如需审核历史版本，请先回滚为当前版本。");
        }

        if ("PENDING_REVIEW".equals(nextStatus)) {
            if ("ACTIVE".equals(currentStatus)
                    || "DRAFT".equals(currentStatus)
                    || "REJECTED".equals(currentStatus)) {
                return;
            }

            throw new IllegalStateException("当前状态不允许提交审核：" + currentStatus);
        }

        if ("APPROVED".equals(nextStatus)) {
            if ("PENDING_REVIEW".equals(currentStatus)) {
                return;
            }

            throw new IllegalStateException("只有待审核版本才能确认通过。当前状态：" + currentStatus);
        }

        if ("REJECTED".equals(nextStatus)) {
            if ("PENDING_REVIEW".equals(currentStatus)) {
                return;
            }

            throw new IllegalStateException("只有待审核版本才能退回修改。当前状态：" + currentStatus);
        }

        throw new IllegalStateException("不支持的资产状态流转：" + currentStatus + " -> " + nextStatus);
    }

    private Map<String, Object> toAuditJson(
            ArtifactVersionEntity artifact
    ) {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("artifact_id", artifact.getId() == null ? null : artifact.getId().toString());
        result.put("project_id", artifact.getProjectId() == null ? null : artifact.getProjectId().toString());
        result.put("module_id", artifact.getModuleId() == null ? null : artifact.getModuleId().toString());
        result.put("task_id", artifact.getTaskId() == null ? null : artifact.getTaskId().toString());
        result.put("artifact_type", artifact.getArtifactType());
        result.put("version_no", artifact.getVersionNo());
        result.put("title", artifact.getTitle());
        result.put("status", artifact.getStatus());
        result.put("current", artifact.getCurrent());
        result.put("created_by", artifact.getCreatedBy());
        result.put("created_at", artifact.getCreatedAt() == null ? null : artifact.getCreatedAt().toString());
        result.put("updated_at", artifact.getUpdatedAt() == null ? null : artifact.getUpdatedAt().toString());
        result.put("prompt_template_id", artifact.getPromptTemplateId() == null ? null : artifact.getPromptTemplateId().toString());
        result.put("prompt_template_version_id", artifact.getPromptTemplateVersionId() == null ? null : artifact.getPromptTemplateVersionId().toString());
        result.put("prompt_template_key", artifact.getPromptTemplateKey());
        result.put("prompt_template_version", artifact.getPromptTemplateVersion());
        result.put("prompt_template_checksum", artifact.getPromptTemplateChecksum());

        return result;
    }

    private void applyPromptTemplateFromTask(
            ArtifactVersionEntity artifact,
            UUID taskId
    ) {
        if (taskId == null) {
            throw new IllegalStateException(
                    "需求资产必须绑定生成任务，禁止保存不可追溯资产。artifactType="
                            + artifact.getArtifactType()
            );
        }

        AgentTaskEntity task = agentTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException(
                        "需求资产绑定的生成任务不存在，taskId=" + taskId
                ));

        if (task.getPromptTemplateId() == null
                || task.getPromptTemplateVersionId() == null
                || task.getPromptTemplateKey() == null
                || task.getPromptTemplateVersion() == null
                || task.getPromptTemplateChecksum() == null) {
            throw new IllegalStateException(
                    "生成任务缺少 Prompt 模板版本信息，禁止保存不可追溯资产。taskId="
                            + taskId
            );
        }

        artifact.setPromptTemplateId(task.getPromptTemplateId());
        artifact.setPromptTemplateVersionId(task.getPromptTemplateVersionId());
        artifact.setPromptTemplateKey(task.getPromptTemplateKey());
        artifact.setPromptTemplateVersion(task.getPromptTemplateVersion());
        artifact.setPromptTemplateChecksum(task.getPromptTemplateChecksum());
    }

    private Map<String, Object> buildArtifactPromptTemplateManifest(
            ArtifactVersionEntity artifact
    ) {
        if (artifact == null) {
            return null;
        }

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("template_id", artifact.getPromptTemplateId() == null ? null : artifact.getPromptTemplateId().toString());
        result.put("template_version_id", artifact.getPromptTemplateVersionId() == null ? null : artifact.getPromptTemplateVersionId().toString());
        result.put("template_key", artifact.getPromptTemplateKey());
        result.put("template_version", artifact.getPromptTemplateVersion());
        result.put("template_checksum", artifact.getPromptTemplateChecksum());

        return result;
    }
}