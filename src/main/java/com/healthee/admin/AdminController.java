package com.healthee.admin;

import com.healthee.admin.request.AdminCreateRequest;
import com.healthee.admin.request.AdminForgotPasswordRequest;
import com.healthee.admin.request.AdminLoginRequest;
import com.healthee.admin.request.AdminResetPasswordRequest;
import com.healthee.admin.request.AdminUpdateRequest;
import com.healthee.admin.response.AdminAuthResponse;
import com.healthee.admin.response.AdminResponse;
import com.healthee.utils.exception.InvalidOperationException;
import com.healthee.utils.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @PostMapping("/public/login")
    public ResponseEntity<AdminAuthResponse> login(
            @Valid @RequestBody AdminLoginRequest request
    ) {
        return ResponseEntity.ok(adminService.login(request));
    }

    @PostMapping("/public/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @Valid @RequestBody AdminForgotPasswordRequest request
    ) {
        try {
            // The service must deliver the OTP through email.
            // Do not expose the returned OTP in this public response.
            adminService.initiateAdminForgotPassword(request);
        } catch (ResourceNotFoundException | InvalidOperationException ignored) {
            // Keep missing or ineligible accounts indistinguishable.
        }

        return ResponseEntity.ok(
                Map.of(
                        "message",
                        "If the account is eligible, password reset instructions will be sent."
                )
        );
    }

    @PostMapping("/public/reset-password")
    public ResponseEntity<Void> resetPassword(
            @Valid @RequestBody AdminResetPasswordRequest request
    ) {
        adminService.resetAdminPassword(request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/private/me")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<AdminResponse> getCurrentAdminProfile() {
        return ResponseEntity.ok(adminService.getCurrentAdminProfile());
    }

    @PostMapping("/private")
    @PreAuthorize("@adminAccess.isSuperAdmin(authentication)")

    public ResponseEntity<AdminResponse> createAdmin(
            @Valid @RequestBody AdminCreateRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(adminService.createAdmin(request));
    }

    @GetMapping("/private")
    @PreAuthorize("@adminAccess.isSuperAdmin(authentication)")

    public ResponseEntity<List<AdminResponse>> getAdmins() {
        return ResponseEntity.ok(adminService.getAdmins());
    }

    @GetMapping("/private/{adminId}")
    @PreAuthorize("@adminAccess.isSuperAdmin(authentication)")

    public ResponseEntity<AdminResponse> getAdminById(
            @PathVariable("adminId") String adminId
    ) {
        return ResponseEntity.ok(adminService.getAdminById(adminId));
    }

    @PutMapping("/private/{adminId}")
    @PreAuthorize("@adminAccess.isSuperAdmin(authentication)")

    public ResponseEntity<AdminResponse> updateAdmin(
            @PathVariable("adminId") String adminId,
            @Valid @RequestBody AdminUpdateRequest request
    ) {
        return ResponseEntity.ok(
                adminService.updateAdmin(adminId, request)
        );
    }

    @PatchMapping("/private/{adminId}/activate")
    @PreAuthorize("@adminAccess.isSuperAdmin(authentication)")

    public ResponseEntity<AdminResponse> activateAdmin(
            @PathVariable("adminId") String adminId
    ) {
        return ResponseEntity.ok(adminService.activateAdmin(adminId));
    }

    @PatchMapping("/private/{adminId}/deactivate")
    @PreAuthorize("@adminAccess.isSuperAdmin(authentication)")

    public ResponseEntity<AdminResponse> deactivateAdmin(
            @PathVariable("adminId") String adminId
    ) {
        return ResponseEntity.ok(adminService.deactivateAdmin(adminId));
    }

    @DeleteMapping("/private/{adminId}")
    @PreAuthorize("@adminAccess.isSuperAdmin(authentication)")

    public ResponseEntity<Void> deleteAdmin(
            @PathVariable("adminId") String adminId
    ) {
        adminService.deleteAdmin(adminId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/private/count")
    @PreAuthorize("@adminAccess.isSuperAdmin(authentication)")

    public ResponseEntity<Long> countAllAdmins() {
        return ResponseEntity.ok(adminService.countAllAdmins());
    }

    @GetMapping("/private/count/active")
    @PreAuthorize("@adminAccess.isSuperAdmin(authentication)")

    public ResponseEntity<Long> countActiveAdmins() {
        return ResponseEntity.ok(adminService.countActiveAdmins());
    }

    @GetMapping("/private/count/inactive")
    @PreAuthorize("@adminAccess.isSuperAdmin(authentication)")

    public ResponseEntity<Long> countInactiveAdmins() {
        return ResponseEntity.ok(adminService.countInactiveAdmins());
    }
}