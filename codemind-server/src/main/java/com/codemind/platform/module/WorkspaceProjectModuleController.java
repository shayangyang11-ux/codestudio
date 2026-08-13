package com.codemind.platform.module;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/projects/{projectCode}/modules")
public class WorkspaceProjectModuleController {

    private final ProjectModuleService projectModuleService;
    private final ModuleTreePatchApplyService moduleTreePatchApplyService;
    private final ModuleTreePatchProposalService moduleTreePatchProposalService;

    public WorkspaceProjectModuleController(
            ProjectModuleService projectModuleService,
            ModuleTreePatchApplyService moduleTreePatchApplyService,
            ModuleTreePatchProposalService moduleTreePatchProposalService
    ) {
        this.projectModuleService = projectModuleService;
        this.moduleTreePatchApplyService = moduleTreePatchApplyService;
        this.moduleTreePatchProposalService = moduleTreePatchProposalService;
    }

    @GetMapping
    public List<ProjectModuleService.ModuleTreeNode> getModuleTree(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode
    ) {
        return projectModuleService.getModuleTreeByWorkspaceAndProjectCode(
                workspaceId,
                projectCode
        );
    }

    @PostMapping
    public ProjectModuleMutationResponse createModuleNode(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @RequestBody CreateProjectModuleNodeRequest request
    ) {
        return projectModuleService.createModuleNode(
                workspaceId,
                projectCode,
                request
        );
    }

    /**
     * AI Tree Patch：生成模块树变更提案。
     * 只生成提案，不直接修改模块树。
     */
    @PostMapping("/tree-patch/propose")
    public ModuleTreePatchProposalResponse proposeModuleTreePatch(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @RequestBody ProposeModuleTreePatchRequest request
    ) {
        return moduleTreePatchProposalService.propose(
                workspaceId,
                projectCode,
                request
        );
    }

    /**
     * Tree Patch：应用模块树变更。
     * 用户确认 AI 提案后调用该接口真正修改模块树。
     */
    @PostMapping("/tree-patch/apply")
    public ModuleTreePatchApplyResponse applyModuleTreePatch(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @RequestBody ApplyModuleTreePatchRequest request
    ) {
        return moduleTreePatchApplyService.applyPatch(
                workspaceId,
                projectCode,
                request
        );
    }

    /**
     * 推荐前端使用这个 POST 接口修改节点，避免部分开发环境下 PATCH 预检失败。
     */

    // A9.10.14.4.3.15.4.10.4.4_PRD_TREE_MANUAL_COMPLETION
    @PostMapping("/{moduleId}/complete-from-module-prd")
    public Map<String, Object> completeModuleChildrenFromModulePrd(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestBody Map<String, Object> request
    ) {
        return projectModuleService.completeRequirementModuleChildrenFromModulePrd(
                workspaceId,
                projectCode,
                moduleId,
                request
        );
    }

    @PostMapping("/{moduleId}/update")
    public ProjectModuleMutationResponse updateModuleNodeByPost(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestBody UpdateProjectModuleNodeRequest request
    ) {
        return projectModuleService.updateModuleNode(
                workspaceId,
                projectCode,
                moduleId,
                request
        );
    }

    /**
     * 保留 PATCH，兼容标准 REST 调用。
     */
    @PatchMapping("/{moduleId}")
    public ProjectModuleMutationResponse updateModuleNode(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestBody UpdateProjectModuleNodeRequest request
    ) {
        return projectModuleService.updateModuleNode(
                workspaceId,
                projectCode,
                moduleId,
                request
        );
    }

    /**
     * 推荐前端使用这个 POST 接口移动节点，避免部分开发环境下 PATCH 预检失败。
     */
    @PostMapping("/{moduleId}/move")
    public ProjectModuleMutationResponse moveModuleNodeByPost(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestBody MoveProjectModuleNodeRequest request
    ) {
        return projectModuleService.moveModuleNode(
                workspaceId,
                projectCode,
                moduleId,
                request
        );
    }

    /**
     * 保留 PATCH，兼容标准 REST 调用。
     */
    @PatchMapping("/{moduleId}/move")
    public ProjectModuleMutationResponse moveModuleNode(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId,
            @RequestBody MoveProjectModuleNodeRequest request
    ) {
        return projectModuleService.moveModuleNode(
                workspaceId,
                projectCode,
                moduleId,
                request
        );
    }

    /**
     * 推荐前端使用这个 POST 接口删除节点，避免部分开发环境下 DELETE 预检失败。
     */
    @PostMapping("/{moduleId}/delete")
    public ProjectModuleMutationResponse deleteModuleNodeByPost(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId
    ) {
        return projectModuleService.deleteModuleNode(
                workspaceId,
                projectCode,
                moduleId
        );
    }

    /**
     * 保留 DELETE，兼容标准 REST 调用。
     */
    @DeleteMapping("/{moduleId}")
    public ProjectModuleMutationResponse deleteModuleNode(
            @PathVariable UUID workspaceId,
            @PathVariable String projectCode,
            @PathVariable UUID moduleId
    ) {
        return projectModuleService.deleteModuleNode(
                workspaceId,
                projectCode,
                moduleId
        );
    }
}