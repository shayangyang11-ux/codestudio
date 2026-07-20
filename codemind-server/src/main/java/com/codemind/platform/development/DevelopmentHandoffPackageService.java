package com.codemind.platform.development;

import com.codemind.platform.artifact.ArtifactVersionEntity;
import com.codemind.platform.artifact.ArtifactVersionRepository;
import com.codemind.platform.identity.CurrentUser;
import com.codemind.platform.identity.CurrentUserProvider;
import com.codemind.platform.module.ProjectModuleEntity;
import com.codemind.platform.project.ProjectAccessService;
import com.codemind.platform.project.ProjectEntity;
import com.codemind.platform.uicontract.ConfirmedUiContractSnapshotResponse;
import com.codemind.platform.uicontract.ConfirmedUiContractSnapshotService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DevelopmentHandoffPackageService {

    private final ProjectAccessService projectAccessService;
    private final ArtifactVersionRepository artifactVersionRepository;
    private final ConfirmedDevelopmentPlanSnapshotService confirmedPlanSnapshotService;
    private final ConfirmedUiContractSnapshotService confirmedUiContractSnapshotService;
    private final CurrentUserProvider currentUserProvider;
    private final ObjectMapper objectMapper;

    public DevelopmentHandoffPackageService(
            ProjectAccessService projectAccessService,
            ArtifactVersionRepository artifactVersionRepository,
            ConfirmedDevelopmentPlanSnapshotService confirmedPlanSnapshotService,
            ConfirmedUiContractSnapshotService confirmedUiContractSnapshotService,
            CurrentUserProvider currentUserProvider,
            ObjectMapper objectMapper
    ) {
        this.projectAccessService = projectAccessService;
        this.artifactVersionRepository = artifactVersionRepository;
        this.confirmedPlanSnapshotService = confirmedPlanSnapshotService;
        this.confirmedUiContractSnapshotService = confirmedUiContractSnapshotService;
        this.currentUserProvider = currentUserProvider;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public DevelopmentHandoffPackageResponse generatePackage(
            UUID workspaceId,
            String projectCode,
            UUID moduleId
    ) {
        ProjectEntity project = projectAccessService.resolveAccessibleProjectEntity(
                workspaceId,
                projectCode
        );

        ProjectModuleEntity module = projectAccessService.resolveAccessibleModule(
                project.getId(),
                moduleId
        );

        ConfirmedDevelopmentPlanSnapshotResponse confirmedPlan =
                confirmedPlanSnapshotService
                        .getCurrent(
                                workspaceId,
                                projectCode,
                                moduleId
                        )
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "当前节点尚未确认 Development Plan，不能生成 Development Agent 交接包。"
                        ));

        if (!confirmedPlan.current()
                || !"CONFIRMED".equalsIgnoreCase(safeText(confirmedPlan.status()))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "当前 Development Plan 确认快照不是有效确认状态，不能生成 Development Agent 交接包。"
            );
        }

        LocalDateTime generatedAt = LocalDateTime.now();
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        UUID packageId = UUID.randomUUID();

        List<DevelopmentHandoffPackageAssetItem> assets = buildAssetItems(
                project.getId(),
                moduleId
        );

        Map<String, Object> confirmedUiContractSnapshot = resolveUiContractSnapshot(
                workspaceId,
                projectCode,
                moduleId,
                confirmedPlan.confirmedUiContractSnapshotId()
        );

        List<String> includedSections = buildIncludedSections(
                confirmedUiContractSnapshot
        );

        String nodeType = resolveNodeTypeFromPlan(
                confirmedPlan,
                module
        );

        DevelopmentHandoffPackageManifest manifest =
                new DevelopmentHandoffPackageManifest(
                        "1.0",
                        "DEVELOPMENT_AGENT_HANDOFF_PACKAGE",
                        packageId,
                        "READY_FOR_CODE_GENERATION",
                        project.getId(),
                        projectCode,
                        moduleId,
                        module.getModuleKey(),
                        module.getModuleName(),
                        module.getModuleType(),
                        nodeType,
                        generatedAt,
                        currentUser.username(),
                        confirmedPlan.id(),
                        confirmedPlan.planChecksum(),
                        confirmedPlan.confirmedUiContractSnapshotId(),
                        includedSections,
                        nullToEmptyMap(confirmedPlan.sourceArtifactVersions())
                );

        String packageChecksum = calculatePackageChecksum(
                manifest,
                confirmedPlan,
                confirmedUiContractSnapshot,
                assets
        );

        String handoffReadmeMarkdown = buildHandoffReadmeMarkdown(
                manifest,
                packageChecksum,
                assets
        );

        return new DevelopmentHandoffPackageResponse(
                packageId,
                "READY_FOR_CODE_GENERATION",
                packageChecksum,
                generatedAt,
                manifest,
                nullToEmptyMap(confirmedPlan.plan()),
                nullToEmptyMap(confirmedPlan.readinessSnapshot()),
                nullToEmptyMap(confirmedPlan.handoffContextSnapshot()),
                confirmedUiContractSnapshot,
                nullToEmptyMap(confirmedPlan.sourceArtifactVersions()),
                assets,
                handoffReadmeMarkdown
        );
    }

    private List<DevelopmentHandoffPackageAssetItem> buildAssetItems(
            UUID projectId,
            UUID moduleId
    ) {
        List<ArtifactVersionEntity> artifacts =
                artifactVersionRepository.findByProjectIdAndModuleIdAndCurrentTrue(
                        projectId,
                        moduleId
                );

        List<DevelopmentHandoffPackageAssetItem> items = new ArrayList<>();

        for (ArtifactVersionEntity artifact : artifacts) {
            items.add(
                    new DevelopmentHandoffPackageAssetItem(
                            artifact.getArtifactType(),
                            artifact.getTitle(),
                            artifact.getId(),
                            artifact.getVersionNo(),
                            artifact.getStatus(),
                            artifact.getCurrent(),
                            artifact.getChecksum(),
                            artifact.getUpdatedAt()
                    )
            );
        }

        return items;
    }

    private Map<String, Object> resolveUiContractSnapshot(
            UUID workspaceId,
            String projectCode,
            UUID moduleId,
            UUID expectedSnapshotId
    ) {
        return confirmedUiContractSnapshotService
                .getCurrent(
                        workspaceId,
                        projectCode,
                        moduleId
                )
                .filter(snapshot ->
                        expectedSnapshotId == null ||
                                expectedSnapshotId.equals(snapshot.id())
                )
                .map(this::toMap)
                .orElseGet(LinkedHashMap::new);
    }

    private List<String> buildIncludedSections(
            Map<String, Object> confirmedUiContractSnapshot
    ) {
        List<String> sections = new ArrayList<>();

        sections.add("manifest");
        sections.add("confirmedDevelopmentPlan");
        sections.add("readinessSnapshot");
        sections.add("handoffContextSnapshot");
        sections.add("sourceArtifactVersions");
        sections.add("assetVersionList");

        if (confirmedUiContractSnapshot != null && !confirmedUiContractSnapshot.isEmpty()) {
            sections.add("confirmedUiContractSnapshot");
            sections.add("themeProfileSnapshot");
        }

        sections.add("handoffReadmeMarkdown");

        return sections;
    }

    private String resolveNodeTypeFromPlan(
            ConfirmedDevelopmentPlanSnapshotResponse confirmedPlan,
            ProjectModuleEntity module
    ) {
        Object nodeType = nullToEmptyMap(confirmedPlan.plan()).get("nodeType");

        if (nodeType != null && !String.valueOf(nodeType).isBlank()) {
            return String.valueOf(nodeType);
        }

        Object contextNodeType = nullToEmptyMap(
                confirmedPlan.handoffContextSnapshot()
        ).get("nodeType");

        if (contextNodeType != null && !String.valueOf(contextNodeType).isBlank()) {
            return String.valueOf(contextNodeType);
        }

        return safeText(module.getModuleType());
    }

    private String calculatePackageChecksum(
            DevelopmentHandoffPackageManifest manifest,
            ConfirmedDevelopmentPlanSnapshotResponse confirmedPlan,
            Map<String, Object> confirmedUiContractSnapshot,
            List<DevelopmentHandoffPackageAssetItem> assets
    ) {
        Map<String, Object> checksumPayload = new LinkedHashMap<>();
        checksumPayload.put("manifest", manifest);
        checksumPayload.put("planChecksum", confirmedPlan.planChecksum());
        checksumPayload.put("planSnapshotId", confirmedPlan.id());
        checksumPayload.put("confirmedUiContractSnapshot", confirmedUiContractSnapshot);
        checksumPayload.put("sourceArtifactVersions", confirmedPlan.sourceArtifactVersions());
        checksumPayload.put("assets", assets);

        try {
            String json = objectMapper.writeValueAsString(checksumPayload);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(json.getBytes(StandardCharsets.UTF_8));

            StringBuilder builder = new StringBuilder();

            for (byte item : encoded) {
                builder.append(String.format("%02x", item));
            }

            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to calculate Development Handoff Package checksum.",
                    e
            );
        }
    }

    private String buildHandoffReadmeMarkdown(
            DevelopmentHandoffPackageManifest manifest,
            String packageChecksum,
            List<DevelopmentHandoffPackageAssetItem> assets
    ) {
        StringBuilder builder = new StringBuilder();

        builder.append("# Development Agent Handoff Package\n\n");
        builder.append("- Package ID: ").append(manifest.packageId()).append("\n");
        builder.append("- Package Status: ").append(manifest.packageStatus()).append("\n");
        builder.append("- Project Code: ").append(manifest.projectCode()).append("\n");
        builder.append("- Module: ").append(manifest.moduleName()).append(" / ").append(manifest.moduleKey()).append("\n");
        builder.append("- Node Type: ").append(manifest.nodeType()).append("\n");
        builder.append("- Generated At: ").append(manifest.generatedAt()).append("\n");
        builder.append("- Generated By: ").append(manifest.generatedBy()).append("\n");
        builder.append("- Development Plan Snapshot: ").append(manifest.confirmedDevelopmentPlanSnapshotId()).append("\n");
        builder.append("- Development Plan Checksum: ").append(manifest.developmentPlanChecksum()).append("\n");
        builder.append("- Package Checksum: ").append(packageChecksum).append("\n\n");

        builder.append("## Included Sections\n\n");

        for (String section : manifest.includedSections()) {
            builder.append("- ").append(section).append("\n");
        }

        builder.append("\n## Asset Versions\n\n");

        if (assets.isEmpty()) {
            builder.append("- No current assets found.\n");
        } else {
            for (DevelopmentHandoffPackageAssetItem asset : assets) {
                builder.append("- ")
                        .append(asset.artifactType())
                        .append(" v")
                        .append(asset.versionNo() == null ? "-" : asset.versionNo())
                        .append(" / ")
                        .append(asset.status() == null ? "-" : asset.status())
                        .append(" / ")
                        .append(asset.artifactId() == null ? "-" : asset.artifactId())
                        .append("\n");
            }
        }

        builder.append("\n## Development Agent Rules\n\n");
        builder.append("- Read the confirmed Development Plan snapshot as the execution plan.\n");
        builder.append("- Read formal requirement assets as the business source of truth.\n");
        builder.append("- For PAGE nodes, preserve the confirmed UI_CONTRACT layout, component IDs, action IDs, fields and page states.\n");
        builder.append("- Replace mock data and mock actions with real API, Store and Service implementations only during code generation.\n");
        builder.append("- Do not silently redesign the page or rename contract identifiers during Development Agent execution.\n");

        return builder.toString();
    }

    private Map<String, Object> toMap(
            ConfirmedUiContractSnapshotResponse snapshot
    ) {
        return objectMapper.convertValue(
                snapshot,
                new TypeReference<Map<String, Object>>() {
                }
        );
    }

    private Map<String, Object> nullToEmptyMap(
            Map<String, Object> value
    ) {
        if (value == null) {
            return new LinkedHashMap<>();
        }

        return value;
    }

    private String safeText(
            String value
    ) {
        if (value == null || value.isBlank()) {
            return "";
        }

        return value;
    }
}
