package com.codemind.platform.module;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/projects/{projectCode}/modules")
public class ProjectModuleController {

    private final ProjectModuleService projectModuleService;

    public ProjectModuleController(ProjectModuleService projectModuleService) {
        this.projectModuleService = projectModuleService;
    }

    @GetMapping
    public List<ProjectModuleService.ModuleTreeNode> getModuleTree(
            @PathVariable String projectCode
    ) {
        return projectModuleService.getModuleTreeByProjectCode(projectCode);
    }
}