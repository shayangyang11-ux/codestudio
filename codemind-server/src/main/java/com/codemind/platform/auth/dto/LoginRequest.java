package com.codemind.platform.auth.dto;

public record LoginRequest(
        String account,
        String password
) {
}