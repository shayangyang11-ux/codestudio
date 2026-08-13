package com.codemind.platform.agent.requirement.service;

import com.codemind.platform.agent.requirement.dto.GenerateModuleTreeRequest;
import com.codemind.platform.artifact.ArtifactService;
import com.codemind.platform.artifact.ArtifactVersionEntity;
import com.codemind.platform.audit.AuditService;
import com.codemind.platform.agent.common.gateway.AgentGatewayService;
import com.codemind.platform.context.ContextPackageService;
import com.codemind.platform.identity.CurrentUser;
import com.codemind.platform.identity.CurrentUserProvider;
import com.codemind.platform.memory.MemoryWriterService;
import com.codemind.platform.module.ProjectModuleEntity;
import com.codemind.platform.module.ProjectModuleRepository;
import com.codemind.platform.project.ProjectAccessService;
import com.codemind.platform.project.ProjectEntity;
import com.codemind.platform.task.AgentTaskEntity;
import com.codemind.platform.task.AgentTaskService;
import com.codemind.platform.task.TaskType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.*;

@Service
public class ModuleTreePersistenceService {

    private final ProjectAccessService projectAccessService;
    private final ProjectModuleRepository projectModuleRepository;
    private final AgentGatewayService agentGatewayService;
    private final AgentTaskService agentTaskService;
    private final ArtifactService artifactService;
    private final ContextPackageService contextPackageService;
    private final AuditService auditService;
    private final MemoryWriterService memoryWriterService;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final CurrentUserProvider currentUserProvider;

    public ModuleTreePersistenceService(
            ProjectAccessService projectAccessService,
            ProjectModuleRepository projectModuleRepository,
            AgentGatewayService agentGatewayService,
            AgentTaskService agentTaskService,
            ArtifactService artifactService,
            ContextPackageService contextPackageService,
            AuditService auditService,
            MemoryWriterService memoryWriterService,
            TransactionTemplate transactionTemplate,
            ObjectMapper objectMapper,
            CurrentUserProvider currentUserProvider
    ) {
        this.projectAccessService = projectAccessService;
        this.projectModuleRepository = projectModuleRepository;
        this.agentGatewayService = agentGatewayService;
        this.agentTaskService = agentTaskService;
        this.artifactService = artifactService;
        this.contextPackageService = contextPackageService;
        this.auditService = auditService;
        this.memoryWriterService = memoryWriterService;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
        this.currentUserProvider = currentUserProvider;
    }

    public Map<String, Object> generateAndPersistModuleTree(
            UUID workspaceId,
            String projectCode,
            GenerateModuleTreeRequest request
    ) {
        ProjectEntity project = projectAccessService.resolveAccessibleProjectEntity(
                workspaceId,
                projectCode
        );

        return generateAndPersistModuleTreeWithProject(
                project,
                workspaceId,
                projectCode,
                request
        );
    }

    public Map<String, Object> generateAndPersistModuleTree(
            String projectCode,
            GenerateModuleTreeRequest request
    ) {
        ProjectEntity project = projectAccessService.resolveAccessibleProjectEntityByCode(
                projectCode
        );

        return generateAndPersistModuleTreeWithProject(
                project,
                null,
                projectCode,
                request
        );
    }

    private Map<String, Object> generateAndPersistModuleTreeWithProject(
            ProjectEntity project,
            UUID workspaceId,
            String projectCode,
            GenerateModuleTreeRequest request
    ) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();

        Map<String, Object> requestPayload = buildRequestPayload(
                project,
                workspaceId,
                request
        );

        AgentTaskEntity task = agentTaskService.createRunningTask(
                project.getId(),
                null,
                TaskType.GENERATE_MODULE_TREE,
                requestPayload,
                currentUser.username()
        );

        try {
            Map<String, Object> contextPackage = contextPackageService.buildAndLogContextPackage(
                    task,
                    project,
                    null,
                    TaskType.GENERATE_MODULE_TREE,
                    requestPayload
            );

            Object response = agentGatewayService.generateModuleTree(
                    task,
                    project,
                    request,
                    contextPackage
            );

            Map<String, Object> responseEnvelope = toMap(response);
            Map<String, Object> artifactContent = extractArtifactContent(responseEnvelope);
            String contentMarkdown = extractMarkdown(responseEnvelope, artifactContent);

            ArtifactVersionEntity artifact = transactionTemplate.execute(status -> {
                ArtifactVersionEntity savedArtifact = artifactService.saveArtifact(
                        project.getId(),
                        null,
                        task.getId(),
                        "MODULE_TREE",
                        "项目模块树",
                        artifactContent,
                        contentMarkdown,
                        currentUser.username()
                );

                rebuildProjectModules(
                        project.getId(),
                        savedArtifact.getId(),
                        artifactContent,
                        currentUser.username()
                );

                memoryWriterService.writeModuleTreeMemory(
                        project,
                        task,
                        savedArtifact,
                        artifactContent
                );

                agentTaskService.markSuccess(
                        task.getId(),
                        responseEnvelope,
                        savedArtifact.getId()
                );

                auditService.record(
                        project.getId(),
                        "USER",
                        currentUser.username(),
                        "GENERATE_MODULE_TREE",
                        "ARTIFACT",
                        savedArtifact.getId(),
                        null,
                        Map.of(
                                "artifact_id", savedArtifact.getId().toString(),
                                "artifact_type", "MODULE_TREE",
                                "version_no", savedArtifact.getVersionNo()
                        ),
                        Map.of(
                                "task_id", task.getId().toString(),
                                "project_code", projectCode,
                                "workspace_id", workspaceId == null ? "" : workspaceId.toString()
                        )
                );

                return savedArtifact;
            });

            if (artifact == null) {
                throw new IllegalStateException("Failed to save MODULE_TREE artifact.");
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("task_id", task.getId());
            result.put("artifact_id", artifact.getId());
            result.put("artifact_type", artifact.getArtifactType());
            result.put("version_no", artifact.getVersionNo());
            result.put("content", artifactContent);

            return result;

        } catch (Exception e) {
            agentTaskService.markFailed(
                    task.getId(),
                    "GENERATE_MODULE_TREE_FAILED",
                    e.getMessage()
            );

            auditService.record(
                    project.getId(),
                    "USER",
                    currentUser.username(),
                    "GENERATE_MODULE_TREE_FAILED",
                    "AGENT_TASK",
                    task.getId(),
                    null,
                    Map.of(
                            "status", "FAILED",
                            "error_message", e.getMessage() == null ? "" : e.getMessage()
                    ),
                    Map.of(
                            "project_code", projectCode,
                            "workspace_id", workspaceId == null ? "" : workspaceId.toString(),
                            "task_id", task.getId().toString()
                    )
            );

            throw e;
        }
    }

    private Map<String, Object> buildRequestPayload(
            ProjectEntity project,
            UUID workspaceId,
            GenerateModuleTreeRequest request
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("project_id", project.getId().toString());
        payload.put("project_code", project.getProjectCode());
        payload.put("task_type", TaskType.GENERATE_MODULE_TREE.name());
        payload.put("requirement_text", request.getRequirementText());
        payload.put("modeling_mode", request.getModelingMode());

        if (workspaceId != null) {
            payload.put("workspace_id", workspaceId.toString());
        }

        return payload;
    }

    private void rebuildProjectModules(
            UUID projectId,
            UUID sourceArtifactId,
            Map<String, Object> responseMap,
            String operator
    ) {
        projectModuleRepository.deleteByProjectId(projectId);

        List<?> modules = extractModules(responseMap);

        Map<String, Integer> usedModuleKeys = new HashMap<>();

        int index = 0;
        for (Object item : modules) {
            saveModuleRecursive(
                    projectId,
                    null,
                    sourceArtifactId,
                    item,
                    1,
                    index,
                    "",
                    usedModuleKeys,
                    operator
            );
            index++;
        }

        projectModuleRepository.flush();
    }

    private void saveModuleRecursive(
            UUID projectId,
            UUID parentId,
            UUID sourceArtifactId,
            Object rawNode,
            int level,
            int sortOrder,
            String parentPath,
            Map<String, Integer> usedModuleKeys,
            String operator
    ) {
        Map<String, Object> node = toMap(rawNode);

        String moduleName = readString(node, "moduleName", "name", "title");
        if (moduleName == null || moduleName.isBlank()) {
            moduleName = "未命名模块";
        }

        String modulePath = parentPath.isBlank()
                ? moduleName
                : parentPath + "/" + moduleName;

        String rawModuleKey = readString(node, "moduleKey", "key", "id");
        String moduleKey = normalizeModuleKey(rawModuleKey, modulePath, usedModuleKeys);

        ProjectModuleEntity entity = new ProjectModuleEntity();
        entity.setProjectId(projectId);
        entity.setParentId(parentId);
        entity.setModuleKey(moduleKey);
        entity.setModuleName(moduleName);
        String moduleType = normalizeModuleType(
                readString(node, "moduleType", "type", "nodeType")
        );

        node.put("moduleType", moduleType);
        node.put("type", moduleType);

        if ("PAGE".equals(moduleType)) {
            node.put("canGenerateUiContract", true);
        } else {
            node.put("canGenerateUiContract", false);
        }

        node.put("requirementMode", resolveGeneratedRequirementMode(moduleType, node));

        entity.setModuleType(moduleType);
        entity.setModulePath(modulePath);
        entity.setLevelNo(level);
        entity.setSortOrder(sortOrder);
        entity.setStatus("ACTIVE");
        entity.setSourceArtifactId(sourceArtifactId);
        entity.setExtra(node);
        entity.setCreatedBy(operator);
        entity.setUpdatedBy(operator);

        entity = projectModuleRepository.save(entity);

        Object childrenObj = node.get("children");
        if (childrenObj instanceof List<?> children) {
            int childIndex = 0;
            for (Object child : children) {
                saveModuleRecursive(
                        projectId,
                        entity.getId(),
                        sourceArtifactId,
                        child,
                        level + 1,
                        childIndex,
                        modulePath,
                        usedModuleKeys,
                        operator
                );
                childIndex++;
            }
        }
    }

    private List<?> extractModules(Map<String, Object> artifactContent) {
        Object modules = artifactContent.get("modules");
        if (modules instanceof List<?> list) {
            return list;
        }

        Object data = artifactContent.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            Object dataModules = dataMap.get("modules");
            if (dataModules instanceof List<?> list) {
                return list;
            }
        }

        Object rootModules = artifactContent.get("rootModules");
        if (rootModules instanceof List<?> list) {
            return list;
        }

        Object content = artifactContent.get("content");
        if (content instanceof Map<?, ?> contentMap) {
            Object contentModules = contentMap.get("modules");
            if (contentModules instanceof List<?> list) {
                return list;
            }
        }

        return List.of();
    }

    private String normalizeModuleKey(
            String rawModuleKey,
            String modulePath,
            Map<String, Integer> usedModuleKeys
    ) {
        String baseKey;

        if (rawModuleKey != null && !rawModuleKey.isBlank()) {
            baseKey = rawModuleKey.trim();
        } else {
            baseKey = "mod_" + Integer.toHexString(modulePath.hashCode());
        }

        baseKey = baseKey
                .replaceAll("[^a-zA-Z0-9_\\-]", "_")
                .replaceAll("_+", "_");

        if (baseKey.isBlank()) {
            baseKey = "mod_" + Integer.toHexString(modulePath.hashCode());
        }

        int count = usedModuleKeys.getOrDefault(baseKey, 0);
        usedModuleKeys.put(baseKey, count + 1);

        if (count == 0) {
            return baseKey;
        }

        return baseKey + "_" + count;
    }

    private Map<String, Object> extractArtifactContent(
            Map<String, Object> responseEnvelope
    ) {
        Object content = responseEnvelope.get("content");

        if (content instanceof Map<?, ?> contentMap) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : contentMap.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }

        return responseEnvelope;
    }

    private String extractMarkdown(
            Map<String, Object> responseEnvelope,
            Map<String, Object> artifactContent
    ) {
        Object contentMarkdown = responseEnvelope.get("content_markdown");
        if (contentMarkdown != null) {
            return String.valueOf(contentMarkdown);
        }

        Object markdown = responseEnvelope.get("markdown");
        if (markdown != null) {
            return String.valueOf(markdown);
        }

        Object artifactMarkdown = artifactContent.get("markdown");
        if (artifactMarkdown != null) {
            return String.valueOf(artifactMarkdown);
        }

        Object artifactContentMarkdown = artifactContent.get("content_markdown");
        if (artifactContentMarkdown != null) {
            return String.valueOf(artifactContentMarkdown);
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object object) {
        if (object instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }

        return objectMapper.convertValue(object, Map.class);
    }

    private String readString(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private String readStringOrDefault(
            Map<String, Object> map,
            String key,
            String defaultValue
    ) {
        Object value = map.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            return defaultValue;
        }
        return String.valueOf(value);
    }


    private String resolveGeneratedRequirementMode(
            String moduleType,
            Map<String, Object> node
    ) {
        String normalizedType = normalizeModuleType(moduleType);

        if ("FEATURE".equals(normalizedType)) {
            return "FUNCTION_POINT";
        }

        if ("PAGE".equals(normalizedType)) {
            return "PAGE_CONTRACT";
        }

        if (!"MODULE".equals(normalizedType)) {
            return normalizedType;
        }

        Object explicit = node.get("requirementMode");
        if (explicit == null) {
            explicit = node.get("requirement_mode");
        }

        if (explicit != null && !String.valueOf(explicit).isBlank()) {
            return String.valueOf(explicit).trim();
        }

        boolean hasChildModule = false;
        boolean hasFeatureOrPage = false;

        Object childrenObj = node.get("children");
        if (childrenObj instanceof List<?> children) {
            for (Object childObj : children) {
                Map<String, Object> child = toMap(childObj);
                String childType = normalizeModuleType(
                        readString(child, "moduleType", "type", "nodeType")
                );

                if ("MODULE".equals(childType)) {
                    hasChildModule = true;
                }

                if ("FEATURE".equals(childType) || "PAGE".equals(childType)) {
                    hasFeatureOrPage = true;
                }
            }
        }

        if (hasChildModule) {
            return "GROUP_MODULE";
        }

        if (hasFeatureOrPage) {
            return "REQUIREMENT_MODULE";
        }

        return "DRAFT_MODULE";
    }

    private String normalizeModuleType(
            String rawType
    ) {
        if (rawType == null || rawType.isBlank()) {
            return "MODULE";
        }

        String value = rawType.trim().toUpperCase(Locale.ROOT);

        return switch (value) {
            case "DOMAIN", "DOMAIN_MODULE", "BUSINESS_DOMAIN" -> "MODULE";
            case "MODULE", "BUSINESS_MODULE", "FEATURE_MODULE", "INTEGRATION_MODULE", "INFRA_MODULE" -> "MODULE";
            case "SUBMODULE", "SUB_MODULE", "SCENE", "BUSINESS_SCENE" -> "MODULE";
            case "PAGE", "PAGE_MODULE", "SCREEN", "VIEW" -> "PAGE";
            case "FEATURE", "FUNCTION", "ACTION", "OPERATION" -> "FEATURE";
            default -> "MODULE";
        };
    }
}