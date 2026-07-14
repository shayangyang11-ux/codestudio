package com.codemind.platform.auth;

import com.codemind.platform.auth.dto.LoginRequest;
import com.codemind.platform.auth.dto.LoginResponse;
import com.codemind.platform.identity.CurrentUser;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.codemind.platform.common.web.ApiBadRequestException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuthService {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthService(
            NamedParameterJdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    public LoginResponse login(LoginRequest request) {
        if (request == null) {
            throw new ApiBadRequestException("login request body is required.");
        }

        String account = normalizeRequired(request.account(), "account");
        String password = normalizeRequired(request.password(), "password");

        UserCredential credential = findCredential(account);

        if (!"ACTIVE".equalsIgnoreCase(credential.status())) {
            throw new BadCredentialsException("Invalid account or password.");
        }

        if (!passwordEncoder.matches(password, credential.passwordHash())) {
            throw new BadCredentialsException("Invalid account or password.");
        }

        CurrentUser currentUser = new CurrentUser(
                credential.userId(),
                credential.username(),
                credential.displayName()
        );

        updateLastLoginAt(credential.userId());

        String accessToken = jwtTokenService.createAccessToken(currentUser);

        return new LoginResponse(
                accessToken,
                "Bearer",
                jwtTokenService.getAccessTokenSeconds(),
                new LoginResponse.UserInfo(
                        currentUser.userId(),
                        currentUser.username(),
                        currentUser.displayName()
                )
        );
    }

    private UserCredential findCredential(String account) {
        String sql = """
                SELECT
                    id,
                    username,
                    display_name,
                    password_hash,
                    status
                FROM user_account
                WHERE username = :account
                   OR email = :account
                LIMIT 1
                """;

        try {
            return jdbcTemplate.queryForObject(
                    sql,
                    new MapSqlParameterSource()
                            .addValue("account", account),
                    (rs, rowNum) -> new UserCredential(
                            rs.getObject("id", UUID.class),
                            rs.getString("username"),
                            rs.getString("display_name"),
                            rs.getString("password_hash"),
                            rs.getString("status")
                    )
            );
        } catch (EmptyResultDataAccessException e) {
            throw new BadCredentialsException("Invalid account or password.");
        }
    }

    private void updateLastLoginAt(UUID userId) {
        String sql = """
                UPDATE user_account
                SET
                    last_login_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :userId
                """;

        jdbcTemplate.update(
                sql,
                new MapSqlParameterSource()
                        .addValue("userId", userId)
        );
    }

    private String normalizeRequired(
            String value,
            String fieldName
    ) {
        if (value == null || value.trim().isEmpty()) {
            throw new ApiBadRequestException(fieldName + " is required.");
        }

        return value.trim();
    }

    private record UserCredential(
            UUID userId,
            String username,
            String displayName,
            String passwordHash,
            String status
    ) {
    }
}