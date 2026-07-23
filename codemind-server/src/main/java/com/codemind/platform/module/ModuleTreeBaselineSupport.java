package com.codemind.platform.module;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ModuleTreeBaselineSupport {

    private ModuleTreeBaselineSupport() {
    }

    public static ModuleTreeBaselineSnapshot capture(
            List<ProjectModuleEntity> modules
    ) {
        List<ProjectModuleEntity> safeModules = modules == null
                ? List.of()
                : modules.stream()
                .filter(Objects::nonNull)
                .sorted(
                        Comparator
                                .comparing(
                                        (ProjectModuleEntity item) -> safeUuid(item.getId())
                                )
                                .thenComparing(item -> safeText(item.getParentId()))
                                .thenComparing(item -> safeText(item.getLevelNo()))
                                .thenComparing(item -> safeText(item.getSortOrder()))
                                .thenComparing(item -> safeText(item.getModuleKey()))
                                .thenComparing(item -> safeText(item.getModuleName()))
                )
                .toList();

        StringBuilder canonical = new StringBuilder();

        for (ProjectModuleEntity module : safeModules) {
            canonical
                    .append(safeText(module.getId()))
                    .append('|')
                    .append(safeText(module.getParentId()))
                    .append('|')
                    .append(safeText(module.getModuleKey()))
                    .append('|')
                    .append(safeText(module.getModuleName()))
                    .append('|')
                    .append(safeText(module.getModuleType()))
                    .append('|')
                    .append(safeText(module.getModulePath()))
                    .append('|')
                    .append(safeText(module.getLevelNo()))
                    .append('|')
                    .append(safeText(module.getSortOrder()))
                    .append('|')
                    .append(safeText(module.getStatus()))
                    .append('\n');
        }

        String hash = sha256Hex(canonical.toString());
        String summary = "模块树节点数 " + safeModules.size() + "，hash=" + hash;

        return new ModuleTreeBaselineSnapshot(
                hash,
                safeModules.size(),
                summary
        );
    }

    public static boolean hasBaseline(
            String hash,
            Integer nodeCount
    ) {
        return (hash != null && !hash.isBlank()) || nodeCount != null;
    }

    public static String buildMismatchMessage(
            String staleMessage,
            String expectedHash,
            Integer expectedNodeCount,
            ModuleTreeBaselineSnapshot current
    ) {
        return staleMessage
                + " 预期模块树基线：nodeCount="
                + (expectedNodeCount == null ? "未知" : expectedNodeCount)
                + (expectedHash == null || expectedHash.isBlank() ? "" : ", hash=" + expectedHash)
                + "；当前模块树：nodeCount="
                + current.nodeCount()
                + ", hash="
                + current.hash();
    }

    private static String sha256Hex(
            String value
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(
                    value.getBytes(StandardCharsets.UTF_8)
            );
            StringBuilder result = new StringBuilder(bytes.length * 2);

            for (byte item : bytes) {
                result.append(String.format("%02x", item));
            }

            return result.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256。", ex);
        }
    }

    private static String safeUuid(
            UUID value
    ) {
        return value == null ? "" : value.toString();
    }

    private static String safeText(
            Object value
    ) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public record ModuleTreeBaselineSnapshot(
            String hash,
            int nodeCount,
            String summary
    ) {
    }
}
