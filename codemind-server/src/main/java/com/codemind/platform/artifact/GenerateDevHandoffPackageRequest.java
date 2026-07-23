package com.codemind.platform.artifact;

public record GenerateDevHandoffPackageRequest(
        Boolean forceGenerate,
        Boolean includeUnapproved,
        String note
) {
}
