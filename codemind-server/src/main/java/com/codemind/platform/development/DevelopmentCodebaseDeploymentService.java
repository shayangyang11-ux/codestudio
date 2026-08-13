package com.codemind.platform.development;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DevelopmentCodebaseDeploymentService {

    private final DevelopmentHandoffGateService developmentHandoffGateService;
    private final DevelopmentCodebaseRegistryService codebaseRegistryService;
    private final DevelopmentGeneratedPathService generatedPathService;

    public DevelopmentCodebaseDeploymentService(
            DevelopmentHandoffGateService developmentHandoffGateService,
            DevelopmentCodebaseRegistryService codebaseRegistryService,
            DevelopmentGeneratedPathService generatedPathService
    ) {
        this.developmentHandoffGateService = developmentHandoffGateService;
        this.codebaseRegistryService = codebaseRegistryService;
        this.generatedPathService = generatedPathService;
    }

    public DevelopmentCodebaseDeploymentStatusResponse getStatus(
            UUID workspaceId,
            String projectCode,
            UUID moduleId,
            String targetStack
    ) {
        DeploymentContext context = resolveContext(workspaceId, projectCode, moduleId, targetStack);
        Path root = generatedPathService.resolveRelativeGeneratedPath(context.registry().relativeRootPath());
        boolean codebaseExists = Files.isDirectory(root);
        boolean packageJsonExists = Files.isRegularFile(root.resolve("package.json"));
        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (!codebaseExists) {
            blockers.add("生成代码库目录不存在。请先生成代码，或在代码库导入与重新绑定中恢复目录登记。 ");
        }

        if (codebaseExists && !packageJsonExists) {
            blockers.add("代码库根目录缺少 package.json，暂不能生成可靠的部署配置。 ");
        }

        if (codebaseExists && !Files.isDirectory(root.resolve("frontend"))) {
            warnings.add("未检测到 frontend 目录，Docker Compose 仍会生成，但前端服务可能需要手工调整。 ");
        }

        if (codebaseExists && !Files.isDirectory(root.resolve("backend"))) {
            warnings.add("未检测到 backend 目录，Docker Compose 仍会生成，但后端服务可能需要手工调整。 ");
        }

        List<DevelopmentCodebaseDeploymentFileResponse> files = inspectFiles(root, context.projectCode(), context.targetStack());
        int existingCount = (int) files.stream().filter(DevelopmentCodebaseDeploymentFileResponse::exists).count();
        int missingCount = (int) files.stream().filter(file -> !file.exists()).count();
        int outdatedCount = (int) files.stream().filter(file -> file.exists() && !file.upToDate()).count();
        boolean deployable = blockers.isEmpty();
        String status;

        if (!deployable) {
            status = "BLOCKED";
        } else if (missingCount == 0 && outdatedCount == 0) {
            status = "READY";
        } else if (existingCount == 0) {
            status = "NOT_GENERATED";
        } else {
            status = "NEEDS_UPDATE";
        }

        return new DevelopmentCodebaseDeploymentStatusResponse(
                context.readiness().projectId(),
                context.readiness().moduleId(),
                context.readiness().moduleKey(),
                context.readiness().moduleName(),
                context.projectCode(),
                context.targetStack(),
                context.registry(),
                context.registry().platformRootPath(),
                root.toString(),
                codebaseExists,
                packageJsonExists,
                deployable,
                status,
                files.size(),
                existingCount,
                missingCount,
                outdatedCount,
                blockers,
                warnings,
                deploymentCommands(),
                files,
                summary(status, missingCount, outdatedCount, blockers),
                LocalDateTime.now()
        );
    }

    public DevelopmentCodebaseDeploymentApplyResponse applyDeploymentConfig(
            UUID workspaceId,
            String projectCode,
            UUID moduleId,
            DevelopmentCodebaseDeploymentApplyRequest request
    ) {
        String requestedStack = request == null ? null : request.targetStack();
        boolean overwriteExisting = request != null && Boolean.TRUE.equals(request.overwriteExisting());
        DeploymentContext context = resolveContext(workspaceId, projectCode, moduleId, requestedStack);
        Path root = generatedPathService.resolveRelativeGeneratedPath(context.registry().relativeRootPath());
        boolean codebaseExists = Files.isDirectory(root);
        boolean packageJsonExists = Files.isRegularFile(root.resolve("package.json"));
        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<DevelopmentCodebaseDeploymentFileResponse> results = new ArrayList<>();
        int written = 0;
        int overwritten = 0;
        int skipped = 0;

        if (!codebaseExists) {
            blockers.add("生成代码库目录不存在。请先生成代码，或重新绑定已有代码库。 ");
        }

        if (codebaseExists && !packageJsonExists) {
            blockers.add("代码库根目录缺少 package.json，未写入部署配置。 ");
        }

        if (!blockers.isEmpty()) {
            return new DevelopmentCodebaseDeploymentApplyResponse(
                    context.readiness().projectId(),
                    context.readiness().moduleId(),
                    context.readiness().moduleKey(),
                    context.readiness().moduleName(),
                    context.projectCode(),
                    context.targetStack(),
                    context.registry(),
                    context.registry().platformRootPath(),
                    root.toString(),
                    false,
                    overwriteExisting,
                    "BLOCKED",
                    written,
                    overwritten,
                    skipped,
                    blockers,
                    warnings,
                    results,
                    "代码库未满足部署配置生成条件，未写入任何文件。",
                    LocalDateTime.now()
            );
        }

        for (DeploymentFileTemplate template : templates(context.projectCode(), context.targetStack())) {
            Path target = root.resolve(template.path()).normalize();

            if (!target.startsWith(root)) {
                skipped += 1;
                results.add(fileResult(root, template, "SKIPPED_UNSAFE", false, "部署配置路径越界，已跳过。"));
                continue;
            }

            try {
                boolean exists = Files.isRegularFile(target);
                boolean upToDate = exists && template.content().equals(Files.readString(target, StandardCharsets.UTF_8));

                if (upToDate) {
                    skipped += 1;
                    results.add(fileResult(root, template, "UNCHANGED", true, "文件内容已是最新。"));
                    continue;
                }

                if (exists && !overwriteExisting) {
                    skipped += 1;
                    warnings.add(template.path() + " 已存在且内容不同。默认不覆盖，勾选覆盖更新后可重写。 ");
                    results.add(fileResult(root, template, "SKIPPED_EXISTS", false, "文件已存在且内容不同，未覆盖。"));
                    continue;
                }

                if (target.getParent() != null) {
                    Files.createDirectories(target.getParent());
                }
                Files.writeString(target, template.content(), StandardCharsets.UTF_8);

                if (exists) {
                    overwritten += 1;
                    results.add(fileResult(root, template, "OVERWRITTEN", true, "已覆盖更新部署配置。"));
                } else {
                    written += 1;
                    results.add(fileResult(root, template, "WRITTEN", true, "已生成部署配置。"));
                }
            } catch (IOException e) {
                skipped += 1;
                warnings.add(template.path() + " 写入失败：" + e.getMessage());
                results.add(fileResult(root, template, "WRITE_FAILED", false, "写入失败：" + e.getMessage()));
            }
        }

        String status = blockers.isEmpty() && warnings.isEmpty() ? "READY" : "WARNING";
        String summary = "已处理部署配置：新增 " + written + " 个，覆盖 " + overwritten + " 个，跳过 " + skipped + " 个。";

        return new DevelopmentCodebaseDeploymentApplyResponse(
                context.readiness().projectId(),
                context.readiness().moduleId(),
                context.readiness().moduleKey(),
                context.readiness().moduleName(),
                context.projectCode(),
                context.targetStack(),
                context.registry(),
                context.registry().platformRootPath(),
                root.toString(),
                true,
                overwriteExisting,
                status,
                written,
                overwritten,
                skipped,
                blockers,
                warnings,
                results,
                summary,
                LocalDateTime.now()
        );
    }

    private DeploymentContext resolveContext(
            UUID workspaceId,
            String projectCode,
            UUID moduleId,
            String targetStack
    ) {
        DevelopmentHandoffReadinessResponse readiness = developmentHandoffGateService.getReadiness(
                workspaceId,
                projectCode,
                moduleId
        );
        String resolvedStack = resolveTargetStack(targetStack);
        DevelopmentCodebaseRegistryResponse registry = codebaseRegistryService.resolveCodebase(
                workspaceId,
                readiness.projectId(),
                projectCode,
                resolvedStack
        );

        return new DeploymentContext(readiness, projectCode, resolvedStack, registry);
    }

    private List<DevelopmentCodebaseDeploymentFileResponse> inspectFiles(Path root, String projectCode, String targetStack) {
        return templates(projectCode, targetStack).stream()
                .map(template -> {
                    Path target = root.resolve(template.path()).normalize();
                    boolean exists = Files.isRegularFile(target);
                    boolean upToDate = false;
                    long bytes = 0L;
                    String message = exists ? "已存在" : "待生成";

                    if (exists) {
                        try {
                            bytes = Files.size(target);
                            upToDate = template.content().equals(Files.readString(target, StandardCharsets.UTF_8));
                            message = upToDate ? "已是最新模板" : "已存在但内容不同";
                        } catch (IOException e) {
                            message = "读取失败：" + e.getMessage();
                        }
                    }

                    String action = exists ? (upToDate ? "UNCHANGED" : "UPDATE") : "CREATE";
                    return new DevelopmentCodebaseDeploymentFileResponse(
                            template.path(),
                            template.fileType(),
                            action,
                            exists,
                            upToDate,
                            !exists || upToDate,
                            bytes,
                            template.description(),
                            message
                    );
                })
                .toList();
    }

    private DevelopmentCodebaseDeploymentFileResponse fileResult(
            Path root,
            DeploymentFileTemplate template,
            String action,
            boolean writable,
            String message
    ) {
        Path target = root.resolve(template.path()).normalize();
        boolean exists = Files.isRegularFile(target);
        long bytes = 0L;
        boolean upToDate = false;

        if (exists) {
            try {
                bytes = Files.size(target);
                upToDate = template.content().equals(Files.readString(target, StandardCharsets.UTF_8));
            } catch (IOException ignored) {
                bytes = 0L;
            }
        }

        return new DevelopmentCodebaseDeploymentFileResponse(
                template.path(),
                template.fileType(),
                action,
                exists,
                upToDate,
                writable,
                bytes,
                template.description(),
                message
        );
    }

    private List<DeploymentFileTemplate> templates(String projectCode, String targetStack) {
        return List.of(
                new DeploymentFileTemplate(
                        ".dockerignore",
                        "CONFIG",
                        "Docker 构建排除规则，避免把依赖、构建产物、日志和本地环境文件打入镜像。",
                        dockerIgnore()
                ),
                new DeploymentFileTemplate(
                        "Dockerfile",
                        "CONFIG",
                        "Node.js 全栈生成项目镜像构建文件。",
                        dockerfile()
                ),
                new DeploymentFileTemplate(
                        "docker-compose.yml",
                        "CONFIG",
                        "本地或服务器上启动前端、后端两个容器服务的编排文件。",
                        dockerCompose(projectCode)
                ),
                new DeploymentFileTemplate(
                        ".env.example",
                        "CONFIG",
                        "部署环境变量模板。交付时复制为 .env 后按服务器环境调整。",
                        envExample(projectCode)
                ),
                new DeploymentFileTemplate(
                        "README_DEPLOYMENT.md",
                        "DOC",
                        "生成项目部署说明，包括环境准备、启动命令、健康检查、端口说明和常见问题。",
                        deploymentReadme(projectCode)
                ),
                new DeploymentFileTemplate(
                        "codemind.deployment.json",
                        "CONFIG",
                        "CodeMind 部署配置清单，记录目标技术栈、默认端口和交付命令。",
                        deploymentManifest(projectCode, targetStack)
                )
        );
    }

    private String dockerIgnore() {
        return """
                node_modules
                frontend/node_modules
                backend/node_modules
                dist
                frontend/dist
                backend/dist
                build
                coverage
                .git
                .idea
                .vscode
                .codemind/snapshots
                logs
                *.log
                .env
                .env.*
                !.env.example
                backend/data/local-store.json
                """;
    }

    private String dockerfile() {
        return """
                FROM node:20-alpine

                WORKDIR /app

                COPY package*.json ./
                COPY frontend/package*.json ./frontend/
                COPY backend/package*.json ./backend/

                RUN npm install

                COPY . .

                RUN npm run build

                EXPOSE 5178
                EXPOSE 3101

                CMD ["npm", "run", "dev"]
                """;
    }

    private String dockerCompose(String projectCode) {
        String safeName = generatedPathService.toPathSegment(projectCode, "project");
        return """
                services:
                  %s-backend:
                    build:
                      context: .
                      dockerfile: Dockerfile
                    working_dir: /app
                    command: npm run dev:backend
                    environment:
                      NODE_ENV: ${NODE_ENV:-production}
                      PORT: ${CODEMIND_BACKEND_PORT:-3101}
                    ports:
                      - "${CODEMIND_BACKEND_PORT:-3101}:3101"
                    restart: unless-stopped

                  %s-frontend:
                    build:
                      context: .
                      dockerfile: Dockerfile
                    working_dir: /app
                    command: npm run dev:frontend
                    environment:
                      VITE_API_BASE_URL: ${VITE_API_BASE_URL:-http://127.0.0.1:3101}
                    ports:
                      - "${CODEMIND_FRONTEND_PORT:-5178}:5178"
                    depends_on:
                      - %s-backend
                    restart: unless-stopped
                """.formatted(safeName, safeName, safeName);
    }

    private String envExample(String projectCode) {
        return """
                # CodeMind generated Node.js app deployment env
                COMPOSE_PROJECT_NAME=codemind-%s
                NODE_ENV=production
                CODEMIND_FRONTEND_PORT=5178
                CODEMIND_BACKEND_PORT=3101
                VITE_API_BASE_URL=http://127.0.0.1:3101
                """.formatted(generatedPathService.toPathSegment(projectCode, "project"));
    }

    private String deploymentReadme(String projectCode) {
        return """
                # 部署说明：CodeMind Node.js 全栈生成项目

                本目录是 CodeMind Dev Agent 生成的 Node.js 全栈项目。部署配置由平台生成，适合在用户服务器本地用 Docker Compose 启动交付候选版本。

                ## 1. 环境要求

                - Docker
                - Docker Compose v2

                ## 2. 配置环境变量

                ```bash
                cp .env.example .env
                ```

                默认端口：

                - 前端：5178
                - 后端：3101
                - 健康检查：http://127.0.0.1:3101/api/health

                ## 3. 启动

                ```bash
                docker compose --env-file .env up --build
                ```

                后台启动：

                ```bash
                docker compose --env-file .env up -d --build
                ```

                ## 4. 停止

                ```bash
                docker compose down
                ```

                ## 5. 查看日志

                ```bash
                docker compose logs -f
                ```

                ## 6. 交付前检查

                在 CodeMind 平台中先运行“生成代码验收流水线”。验收通过后再导出 ZIP 并交付部署。

                ## 7. 项目标识

                - 项目编号：%s
                - 技术栈：NODEJS_FULLSTACK

                注意：生成项目中的 `codemind.generated.json` 是模块树节点与代码文件的映射清单，不建议删除。
                """.formatted(projectCode);
    }

    private String deploymentManifest(String projectCode, String targetStack) {
        return """
                {
                  "schemaVersion": "1.0",
                  "source": "CODEMIND_DEV_AGENT_R10",
                  "projectCode": "%s",
                  "targetStack": "%s",
                  "deploymentMode": "DOCKER_COMPOSE_LOCAL_SERVER",
                  "frontendPort": 5178,
                  "backendPort": 3101,
                  "healthPath": "/api/health",
                  "commands": [
                    "cp .env.example .env",
                    "docker compose --env-file .env up --build",
                    "docker compose logs -f",
                    "docker compose down"
                  ]
                }
                """.formatted(projectCode, targetStack == null || targetStack.isBlank() ? "NODEJS_FULLSTACK" : targetStack);
    }

    private List<String> deploymentCommands() {
        return List.of(
                "cp .env.example .env",
                "docker compose --env-file .env up --build",
                "docker compose logs -f",
                "docker compose down"
        );
    }

    private String summary(String status, int missingCount, int outdatedCount, List<String> blockers) {
        if (!blockers.isEmpty()) {
            return "部署配置暂不可生成：" + String.join("；", blockers);
        }

        return switch (status) {
            case "READY" -> "部署配置已齐全且为最新模板，可以随 ZIP 一起交付给用户服务器。";
            case "NOT_GENERATED" -> "尚未生成部署配置。生成后会写入 Dockerfile、docker-compose.yml、.env.example 和部署说明。";
            case "NEEDS_UPDATE" -> "部署配置需要更新：缺失 " + missingCount + " 个，内容不同 " + outdatedCount + " 个。";
            default -> "部署配置状态待确认。";
        };
    }

    private String resolveTargetStack(String targetStack) {
        try {
            return DevelopmentTargetStack.resolve(targetStack).stackCode();
        } catch (IllegalArgumentException e) {
            return "NODEJS_FULLSTACK";
        }
    }

    private record DeploymentContext(
            DevelopmentHandoffReadinessResponse readiness,
            String projectCode,
            String targetStack,
            DevelopmentCodebaseRegistryResponse registry
    ) {
    }

    private record DeploymentFileTemplate(
            String path,
            String fileType,
            String description,
            String content
    ) {
    }
}
