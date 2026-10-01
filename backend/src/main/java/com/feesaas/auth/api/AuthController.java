package com.feesaas.auth.api;

import com.feesaas.auth.api.dto.ChangePasswordRequest;
import com.feesaas.auth.api.dto.ForgotPasswordRequest;
import com.feesaas.auth.api.dto.ForgotPasswordResponse;
import com.feesaas.auth.api.dto.LoginRequest;
import com.feesaas.auth.api.dto.LogoutRequest;
import com.feesaas.auth.api.dto.MeResponse;
import com.feesaas.auth.api.dto.RefreshRequest;
import com.feesaas.auth.api.dto.ResetPasswordRequest;
import com.feesaas.auth.api.dto.TokenResponse;
import com.feesaas.auth.api.dto.RegisterRequest;
import com.feesaas.auth.api.dto.RegisterStartResponse;
import com.feesaas.auth.api.dto.VerifyRegisterRequest;
import com.feesaas.auth.application.AuthService;
import com.feesaas.auth.application.AuthService.TokenPair;
import com.feesaas.auth.application.RegisterService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService auth;
    private final RegisterService register;

    public AuthController(AuthService auth, RegisterService register) {
        this.auth = auth;
        this.register = register;
    }

    @PostMapping("/register")
    public RegisterStartResponse register(@Valid @RequestBody RegisterRequest request) {
        return register.startWithPassword(request.fullName(), request.email(), request.phone(), request.password());
    }

    @PostMapping("/register/verify")
    public TokenResponse verifyRegister(@Valid @RequestBody VerifyRegisterRequest request) {
        String deviceId = request.deviceId() == null || request.deviceId().isBlank() ? "web" : request.deviceId();
        return toResponse(register.verify(request.challengeId(), request.otp(), deviceId));
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        String deviceId = request.deviceId() == null || request.deviceId().isBlank() ? "web" : request.deviceId();
        return toResponse(auth.login(request.identifier(), request.password(), deviceId));
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return toResponse(auth.refresh(request.refreshToken(), request.deviceId()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody LogoutRequest request) {
        auth.logout(request.refreshToken());
    }

    @PostMapping("/password/forgot")
    public ForgotPasswordResponse forgot(@Valid @RequestBody ForgotPasswordRequest request) {
        var result = auth.forgotPassword(request.identifier());
        return new ForgotPasswordResponse(true, result.challengeId(), result.resetToken());
    }

    @PostMapping("/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@Valid @RequestBody ResetPasswordRequest request) {
        auth.resetPassword(request.challengeId(), request.resetToken(), request.newPassword());
    }

    @PostMapping("/password/change")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void change(@Valid @RequestBody ChangePasswordRequest request) {
        auth.changePassword(request.currentPassword(), request.newPassword());
    }

    @GetMapping("/me")
    public MeResponse me() {
        var me = auth.me();
        return new MeResponse(me.id(), me.fullName(), me.role(), me.tenantId());
    }

    private static TokenResponse toResponse(TokenPair pair) {
        var user = pair.user();
        return new TokenResponse(
                pair.accessToken(),
                pair.refreshToken(),
                "Bearer",
                pair.expiresIn(),
                new TokenResponse.UserSummary(user.id(), user.fullName(), user.roleCode(), user.tenantId()));
    }
}
