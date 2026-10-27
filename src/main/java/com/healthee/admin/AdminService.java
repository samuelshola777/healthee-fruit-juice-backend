package com.healthee.admin;

import com.healthee.admin.request.*;
import com.healthee.admin.response.AdminAuthResponse;
import com.healthee.admin.response.AdminForgotPasswordResponse;
import com.healthee.admin.response.AdminResponse;
import com.healthee.userauthmgt.user.User;
import com.healthee.userauthmgt.user.UserService;
import com.healthee.utils.appsecurity.JwtService;
import com.healthee.utils.emailsenderservice.EmailSenderService;
import java.util.Locale;
import java.util.UUID;
import com.healthee.utils.exception.*;
import com.healthee.utils.fileupload.FileUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Log4j2
@RequiredArgsConstructor
public class AdminService {

  private final AdminRepository adminRepository;
  private final UserService userService;
  private final JwtService jwtService;
  private final FileUploadService fileUploadService;
  private final EmailSenderService emailSenderService;

  public AdminResponse createFirstAdmin(AdminCreateRequest request) {
    validateCreateRequest(request);
    if (adminRepository.countByDeleted(false) > 0) {
      throw new InvalidOperationException("First admin already exists");
    }
    User user = userService.createAdminUserFromAdminModule(
            clean(request.firstName()),
            clean(request.lastName()),
            cleanEmail(request.emailAddress()),
            request.password(),
            clean(request.phoneNumber()),
            clean(request.profilePictureUrl()),
            clean(request.profileColourCode()),
            request.gender(),
            true
    );

    Admin admin = Admin.builder()
            .userId(user.getId())
            .active(true)
            .deleted(false)
            .isSuperAdmin(true)
            .lastLoginAt(null)
            .deletedAt(null)
            .build();

    admin.setId(UUID.randomUUID().toString());
    Admin savedAdmin = adminRepository.save(admin);
    return mapToResponse(savedAdmin, user);
  }

  public AdminResponse createAdmin(AdminCreateRequest request) {
    requireSuperAdmin();
    validateCreateRequest(request);

    User user = userService.createAdminUserFromAdminModule(
            clean(request.firstName()),
            clean(request.lastName()),
            cleanEmail(request.emailAddress()),
            request.password(),
            clean(request.phoneNumber()),
            clean(request.profilePictureUrl()),
            clean(request.profileColourCode()),
            request.gender(),
            true
    );

    if (adminRepository.existsByUserId(user.getId())) {
      throw new ResourceExistsException("User is already an admin");
    }

    Admin admin = Admin.builder()
            .userId(user.getId())
            .active(true)
            .deleted(false)
            .lastLoginAt(null)
            .deletedAt(null)
            .build();

    admin.setId(UUID.randomUUID().toString());
    Admin savedAdmin = adminRepository.save(admin);



    return mapToResponse(savedAdmin, user);
  }

  public AdminAuthResponse login(AdminLoginRequest request) {
    if (request == null || isBlank(request.emailAddress()) || isBlank(request.password())) {
      throw new InvalidInputException("Email address and password are required");
    }
    User user = userService.authenticateAdminUserFromAdminModule(
            cleanEmail(request.emailAddress()),
            request.password()
    );
    Admin admin = adminRepository.findByUserIdAndDeleted(user.getId(), false)
            .orElseThrow(() -> new UnauthorizedException("Admin account not found"));

    if (!admin.isActive()) {
      throw new UnauthorizedException("Admin account is inactive");
    }
    admin.setLastLoginAt(LocalDateTime.now());
    Admin savedAdmin = adminRepository.save(admin);
    Map<String, Object> claims = buildJwtClaims(savedAdmin, user);
    String accessToken = jwtService.generateToken(user.getEmailAddress(), claims);
    String refreshToken = jwtService.generateRefreshToken(user.getEmailAddress(), claims);

    return new AdminAuthResponse(
            accessToken,
            refreshToken,
            mapToResponse(savedAdmin, user)
    );
  }

  public AdminResponse getCurrentAdminProfile() {
    String emailAddress = getCurrentPrincipalEmail();

    User user = userService.getAdminUserByEmailFromAdminModule(emailAddress);

    Admin admin = adminRepository.findByUserIdAndDeleted(user.getId(), false)
            .orElseThrow(() -> new ResourceNotFoundException("Admin profile not found"));

    if (!admin.isActive() || !user.isActive() || !user.isVerified()) {
      throw new UnauthorizedException("Admin account is inactive or not verified");
    }
    return mapToResponse(admin, user);
  }

  public List<AdminResponse> getAdmins() {
    requireSuperAdmin();
    return adminRepository.findAllByDeleted(false)
            .stream()
            .map(admin -> mapToResponse(admin, userService.getAdminUserByIdFromAdminModule(admin.getUserId())))
            .toList();
  }

  public AdminResponse getAdminById(String adminId) {
    requireSuperAdmin();
    Admin admin = findAdminById(adminId);
    User user = userService.getAdminUserByIdFromAdminModule(admin.getUserId());

    return mapToResponse(admin, user);
  }

  public AdminResponse updateAdmin(String adminId, AdminUpdateRequest request) {
    requireSuperAdmin();
    validateUpdateRequest(request);
    if (request == null) {
      throw new InvalidInputException("Request body is required");
    }

    Admin admin = findAdminById(adminId);
    User oldUser = userService.getAdminUserByIdFromAdminModule(admin.getUserId());

    String oldProfilePictureUrl = oldUser.getProfilePictureUrl();

    User updatedUser = userService.updateAdminUserFromAdminModule(admin.getUserId(), request);



    admin.setUpdatedAt(LocalDateTime.now());

    Admin savedAdmin = adminRepository.save(admin);

    fileUploadService.deleteOldFileIfChanged(oldProfilePictureUrl, updatedUser.getProfilePictureUrl());
    return mapToResponse(savedAdmin, updatedUser);
  }

  public AdminResponse activateAdmin(String adminId) {
    requireSuperAdmin();
    Admin admin = findAdminById(adminId);

    admin.setActive(true);
    admin.setUpdatedAt(LocalDateTime.now());

    Admin savedAdmin = adminRepository.save(admin);

    User user = userService.toggleAdminUserActiveStatusFromAdminModule(admin.getUserId(), true);


    return mapToResponse(savedAdmin, user);
  }

  public AdminResponse deactivateAdmin(String adminId) {
    requireSuperAdmin();
    Admin admin = findAdminById(adminId);
    protectSuperAdmin(admin);
    admin.setActive(false);
    admin.setUpdatedAt(LocalDateTime.now());
    Admin savedAdmin = adminRepository.save(admin);
    User user = userService.toggleAdminUserActiveStatusFromAdminModule(admin.getUserId(), false);
    return mapToResponse(savedAdmin, user);
  }

  public void deleteAdmin(String adminId) {
    requireSuperAdmin();
    Admin admin = findAdminById(adminId);
    protectSuperAdmin(admin);
    User user = userService.getAdminUserByIdFromAdminModule(admin.getUserId());
    String profilePictureUrl = user.getProfilePictureUrl();
    admin.setDeleted(true);
    admin.setActive(false);
    admin.setDeletedAt(LocalDateTime.now());
    admin.setUpdatedAt(LocalDateTime.now());
    Admin savedAdmin = adminRepository.save(admin);
    userService.softDeleteAdminUserFromAdminModule(admin.getUserId());
    fileUploadService.deleteFileAsync(profilePictureUrl);
  }

  public long countAllAdmins() {
    requireSuperAdmin();
    return adminRepository.countByDeleted(false);
  }

  public long countActiveAdmins() {
    requireSuperAdmin();
    return adminRepository.countByDeletedAndActive(false, true);
  }

  public long countInactiveAdmins() {
    requireSuperAdmin();
    return adminRepository.countByDeletedAndActive(false, false);
  }

  private Admin findAdminById(String adminId) {
    if (isBlank(adminId)) {
      throw new ResourceNotFoundException("Admin not found");
    }

    return adminRepository.findByIdAndDeleted(adminId, false)
            .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));
  }

  private Map<String, Object> buildJwtClaims(Admin admin, User user) {
    Map<String, Object> claims = new HashMap<>();
    claims.put("userId", user.getId());
    claims.put("adminId", admin.getId());
    claims.put("email", user.getEmailAddress());
    claims.put("userType", "ADMIN");
    claims.put("userRole", admin.isSuperAdmin() ? "SUPER_ADMIN" : "ADMIN");
    claims.put("auth", admin.isSuperAdmin()
            ? List.of("ROLE_ADMIN", "ROLE_SUPER_ADMIN")
            : List.of("ROLE_ADMIN"));
    return claims;
  }

  private AdminResponse mapToResponse(Admin admin, User user) {
    return new AdminResponse(
            admin.getId(),
            admin.getUserId(),
            user.getFirstName(),
            user.getLastName(),
            user.getEmailAddress(),
            user.getPhoneNumber(),
            user.getProfilePictureUrl(),
            user.getDateOfBirth(),
            user.getGender(),
            user.getAge(),
            user.getUserType(),
            admin.isActive(),
            user.isActive(),
            user.isVerified(),
            admin.getCreatedAt(),
            admin.getUpdatedAt(),
            admin.getLastLoginAt(),
            admin.isSuperAdmin()
    );
  }

  private void validateCreateRequest(AdminCreateRequest request) {
    if (request == null) {
      throw new InvalidInputException("Request body is required");
    }

    if (isBlank(request.firstName())) {
      throw new InvalidInputException("First name is required");
    }

    if (isBlank(request.lastName())) {
      throw new InvalidInputException("Last name is required");
    }

    if (isBlank(request.emailAddress())) {
      throw new InvalidInputException("Email address is required");
    }

    if (isBlank(request.password())) {
      throw new InvalidInputException("Password is required");
    }

    if (request.password().length() < 8 || request.password().length() > 100) {
      throw new InvalidInputException("Password must be between 8 and 100 characters");
    }
    validateText(request.firstName(), 100, "First name");
    validateText(request.lastName(), 100, "Last name");
    if (request.phoneNumber() != null && request.phoneNumber().length() > 30) throw new InvalidInputException("Phone number must not exceed 30 characters");
    if (!cleanEmail(request.emailAddress()).matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
            || request.emailAddress().length() > 254) {
      throw new InvalidInputException("Enter a valid email address");
    }
  }



  private String buildFullName(User user) {
    if (user == null) {
      return "Unknown admin";
    }

    String firstName = user.getFirstName() == null ? "" : user.getFirstName().trim();
    String lastName = user.getLastName() == null ? "" : user.getLastName().trim();

    String fullName = (firstName + " " + lastName).trim();

    return fullName.isBlank() ? user.getEmailAddress() : fullName;
  }

  private String getCurrentPrincipalEmail() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new UnauthorizedException("Authentication is required");
    }
    Object principal = authentication.getPrincipal();

    if (principal == null) {
      throw new UnauthorizedException("Authentication is required");
    }

    return principal.toString();
  }

  public AdminForgotPasswordResponse initiateAdminForgotPassword(AdminForgotPasswordRequest request) {
    if (request == null || isBlank(request.emailAddress())) {
      throw new InvalidInputException("Email address is required");
    }

    User user = userService.getAdminUserByEmailFromAdminModule(
            cleanEmail(request.emailAddress())
    );

    Admin admin = adminRepository.findByUserIdAndDeleted(user.getId(), false)
            .orElseThrow(() -> new ResourceNotFoundException("Admin account not found"));

    if (!admin.isActive() || !user.isActive()) {
      throw new InvalidOperationException("Admin account is not active");
    }

    if (!user.isVerified()) {
      throw new InvalidOperationException("Admin account is not verified");
    }

    Map<String, String> otp = userService.generatePasswordResetOtpForAdmin(user.getId());
    emailSenderService.sendAdminPasswordResetEmail(
            user.getEmailAddress(), user.getId(), otp.get("otp"), buildFullName(user));

    return new AdminForgotPasswordResponse(
            user.getId(),
            user.getEmailAddress(),
            otp.get("otp"),
            otp.get("expiresAt")
    );
  }

  public void resetAdminPassword(AdminResetPasswordRequest request) {
    if (request == null) {
      throw new InvalidInputException("Request body is required");
    }

    if (isBlank(request.emailAddress())) {
      throw new InvalidInputException("Email address is required");
    }

    if (isBlank(request.otp())) {
      throw new InvalidInputException("OTP is required");
    }

    if (isBlank(request.newPassword())) {
      throw new InvalidInputException("New password is required");
    }

    if (request.newPassword().length() < 8 || request.newPassword().length() > 100) {
      throw new InvalidInputException("Password must be between 8 and 100 characters");
    }

    User user = userService.getAdminUserByEmailFromAdminModule(
            cleanEmail(request.emailAddress())
    );

    Admin admin = adminRepository.findByUserIdAndDeleted(user.getId(), false)
            .orElseThrow(() -> new ResourceNotFoundException("Admin account not found"));

    if (!admin.isActive() || !user.isActive()) {
      throw new InvalidOperationException("Admin account is not active");
    }

    if (!user.isVerified()) {
      throw new InvalidOperationException("Admin account is not verified");
    }

    userService.resetAdminPasswordFromAdminModule(
            user.getId(),
            request.otp(),
            request.newPassword()
    );

  }

  private void requireSuperAdmin() {
    if (!getCurrentAdminProfile().superAdmin()) {
      throw new UnauthorizedException("Super admin access is required");
    }
  }

  private void protectSuperAdmin(Admin admin) {
    if (admin.isSuperAdmin()) {
      throw new InvalidOperationException("The bootstrap super admin cannot be deactivated or deleted");
    }
  }

  private void validateText(String value, int max, String field) {
    if (value != null && (value.trim().isEmpty() || value.length() > max)) {
      throw new InvalidInputException(field + " is empty or too long");
    }
  }

  private void validateUpdateRequest(AdminUpdateRequest request) {
    if (request == null) throw new InvalidInputException("Request body is required");
    validateText(request.firstName(), 100, "First name");
    validateText(request.lastName(), 100, "Last name");
    if (request.phoneNumber() != null && request.phoneNumber().length() > 30) {
      throw new InvalidInputException("Phone number must not exceed 30 characters");
    }
    if (request.dateOfBirth() != null && !request.dateOfBirth().isBefore(java.time.LocalDate.now())) {
      throw new InvalidInputException("Date of birth must be in the past");
    }
  }

  private String clean(String value) {
    return value == null ? null : value.trim();
  }

  private String cleanEmail(String value) {
    return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
  }

  private boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }
}