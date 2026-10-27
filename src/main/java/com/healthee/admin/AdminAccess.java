package com.healthee.admin;

import com.healthee.utils.exception.ResourceNotFoundException;
import com.healthee.utils.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("adminAccess")
@RequiredArgsConstructor
public class AdminAccess {
    private final AdminService adminService;

    public boolean canManage(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getAuthorities().stream()
                .noneMatch(value -> value.getAuthority().equals("ROLE_ADMIN"))) return false;
        try {
            adminService.getCurrentAdminProfile();
            return true;
        } catch (UnauthorizedException | ResourceNotFoundException exception) {
            return false;
        }
    }

    public boolean isSuperAdmin(Authentication authentication) {
        return canManage(authentication) && adminService.getCurrentAdminProfile().active();
    }
}