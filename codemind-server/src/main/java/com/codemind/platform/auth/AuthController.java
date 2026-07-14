package com.codemind.platform.auth;

import com.codemind.platform.auth.dto.CurrentUserResponse;
import com.codemind.platform.auth.dto.LoginRequest;
import com.codemind.platform.auth.dto.LoginResponse;
import com.codemind.platform.identity.CurrentUser;
import com.codemind.platform.identity.CurrentUserProvider;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final CurrentUserProvider currentUserProvider;

    public AuthController(
            AuthService authService,
            CurrentUserProvider currentUserProvider
    ) {
        this.authService = authService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/login")
    public LoginResponse login(
            @RequestBody LoginRequest request
    ) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public CurrentUserResponse me() {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();

        return new CurrentUserResponse(
                currentUser.userId(),
                currentUser.username(),
                currentUser.displayName()
        );
    }
}