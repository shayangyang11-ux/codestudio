package com.codemind.platform.development;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public enum DevelopmentTargetStack {
    NODEJS_FULLSTACK(
            "NODEJS_FULLSTACK",
            "Node.js 全栈应用",
            "AVAILABLE",
            true,
            "生成可本地运行的 Node.js 全栈系统，包含前端、Express 后端、本地数据库、初始化种子数据和运行脚本。",
            List.of(
                    "Vite + Vue 页面工程",
                    "Node.js + Express API 服务",
                    "SQLite 本地数据库与初始化脚本",
                    "真实前后端调用链路，不以 Mock 作为主业务实现",
                    "README、运行脚本与验收检查清单"
            ),
            List.of(
                    "npm install",
                    "npm run dev"
            ),
            List.of(
                    "当前阶段默认支持 Node.js 全栈分支。",
                    "允许生成 seed data 作为首次运行演示数据，但页面业务逻辑必须请求后端 API。",
                    "生成结果仍是代码提案，后续由用户确认后再写入目标目录。"
            )
    ),
    JAVA_SPRING_BOOT(
            "JAVA_SPRING_BOOT",
            "Java Spring Boot 全栈应用",
            "PLANNED",
            false,
            "预留给后续 Java 技术栈生成器。",
            List.of(
                    "Spring Boot 后端",
                    "关系型数据库迁移",
                    "前端页面或模板工程",
                    "JUnit / 集成测试"
            ),
            List.of(),
            List.of(
                    "当前版本仅作为路线占位，不允许直接生成。"
            )
    ),
    PYTHON_FASTAPI(
            "PYTHON_FASTAPI",
            "Python FastAPI 全栈应用",
            "PLANNED",
            false,
            "预留给后续 Python 技术栈生成器。",
            List.of(
                    "FastAPI 后端",
                    "SQLAlchemy / Alembic 数据层",
                    "前端页面工程",
                    "pytest 验收测试"
            ),
            List.of(),
            List.of(
                    "当前版本仅作为路线占位，不允许直接生成。"
            )
    );

    private final String stackCode;
    private final String displayName;
    private final String lifecycleStatus;
    private final boolean available;
    private final String description;
    private final List<String> capabilities;
    private final List<String> runtimeCommands;
    private final List<String> notes;

    DevelopmentTargetStack(
            String stackCode,
            String displayName,
            String lifecycleStatus,
            boolean available,
            String description,
            List<String> capabilities,
            List<String> runtimeCommands,
            List<String> notes
    ) {
        this.stackCode = stackCode;
        this.displayName = displayName;
        this.lifecycleStatus = lifecycleStatus;
        this.available = available;
        this.description = description;
        this.capabilities = capabilities;
        this.runtimeCommands = runtimeCommands;
        this.notes = notes;
    }

    public String stackCode() {
        return stackCode;
    }

    public String displayName() {
        return displayName;
    }

    public String lifecycleStatus() {
        return lifecycleStatus;
    }

    public boolean available() {
        return available;
    }

    public String description() {
        return description;
    }

    public List<String> capabilities() {
        return capabilities;
    }

    public List<String> runtimeCommands() {
        return runtimeCommands;
    }

    public List<String> notes() {
        return notes;
    }

    public DevelopmentTargetStackResponse toResponse() {
        return new DevelopmentTargetStackResponse(
                stackCode,
                displayName,
                lifecycleStatus,
                available,
                description,
                capabilities,
                runtimeCommands,
                notes
        );
    }

    public static DevelopmentTargetStack resolve(
            String rawValue
    ) {
        if (rawValue == null || rawValue.isBlank()) {
            return NODEJS_FULLSTACK;
        }

        String normalized = rawValue
                .trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);

        for (DevelopmentTargetStack stack : values()) {
            if (stack.stackCode.equals(normalized) || stack.name().equals(normalized)) {
                return stack;
            }
        }

        throw new IllegalArgumentException("Unsupported development target stack: " + rawValue);
    }

    public static List<DevelopmentTargetStackResponse> responses() {
        return Arrays.stream(values())
                .map(DevelopmentTargetStack::toResponse)
                .toList();
    }
}
