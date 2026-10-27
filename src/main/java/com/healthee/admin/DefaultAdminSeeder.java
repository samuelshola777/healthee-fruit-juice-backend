package com.healthee.admin;

import com.healthee.userauthmgt.user.User;
import com.healthee.userauthmgt.user.UserService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import java.util.Locale;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DefaultAdminSeeder {
    private static final Logger log = LoggerFactory.getLogger(DefaultAdminSeeder.class);
    private final AdminRepository adminRepository;
    private final UserService userService;
    @Value("${app.bootstrap-email:healtheefruitjuices@gmail.com}") private String email;
    @Value("${app.bootstrap-password:password}") private String
            password;
    @Value("${app.bootstrap-name:Healthee Admin}") private String name;

    @EventListener(ApplicationReadyEvent.class)
    public void seedDefaultSuperAdmin() {
        if (adminRepository.existsByIsSuperAdminAndDeleted(true, false)) {
            log.info("Bootstrap super admin already exists; no account changes made");
            return;
        }
        String address = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (!address.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            log.info("Set BOOTSTRAP_ADMIN_EMAIL to configure the bootstrap super admin");
            return;
        }
        // Promote only the operator-configured bootstrap address; retain its password.
        if (userService.existsByEmailAddressIgnoreCase(address)) {
            User user = userService.getAdminUserByEmailFromAdminModule(address);
            Admin admin = adminRepository.findByUserIdAndDeleted(user.getId(), false)
                    .orElseThrow(() -> new IllegalStateException("Bootstrap admin profile is missing"));
            if (!admin.isActive() || !user.isActive() || !user.isVerified()) {
                throw new IllegalStateException("The configured bootstrap admin must be active and verified");
            }
            admin.setSuperAdmin(true);
            adminRepository.save(admin);
            log.info("Configured existing bootstrap account marked as super admin");
            return;
        }
        if (password == null || password.length() < 8 || password.length() > 100) {
            throw new IllegalStateException("Set BOOTSTRAP_ADMIN_PASSWORD to 8-100 characters for a new bootstrap account");
        }
        User user = userService.createAdminUserFromAdminModule(
                name == null || name.isBlank() ? "Healthee" : name.trim(),
                "Admin", address, password, null, null, "#018543", null, true);
        Admin admin = Admin.builder().userId(user.getId()).active(true)
                .deleted(false).isSuperAdmin(true).build();
        admin.setId(UUID.randomUUID().toString());
        adminRepository.save(admin);
        log.info("Configured bootstrap super admin created");
    }
}