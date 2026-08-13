package com.codemind.platform.development;

import java.time.LocalDateTime;
import java.util.List;

public record DevelopmentCodebaseFileTreeItemResponse(
        String name,
        String path,
        String itemType,
        String language,
        long bytes,
        LocalDateTime updatedAt,
        List<DevelopmentCodebaseFileTreeItemResponse> children
) {
}
