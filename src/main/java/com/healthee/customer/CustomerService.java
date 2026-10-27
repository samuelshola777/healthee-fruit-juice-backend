package com.healthee.customer;

import com.healthee.customer.request.*;
import com.healthee.customer.response.CustomerAuthResponse;
import com.healthee.customer.response.CustomerForgotPasswordResponse;
import com.healthee.customer.response.CustomerResponse;
import com.healthee.userauthmgt.user.*;
import com.healthee.userauthmgt.user.request.PasswordChangeRequest;
import com.healthee.userauthmgt.userotp.UserOtpService;
import com.healthee.userauthmgt.userotp.UserOtpType;
import com.healthee.utils.PaginatedData;
import com.healthee.utils.UtilsService;
import com.healthee.utils.appsecurity.JwtService;
import com.healthee.utils.emailsenderservice.EmailSenderService;
import com.healthee.utils.exception.*;
import com.healthee.utils.fileupload.FileUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final UserOtpService userOtpService;
    private final JwtService jwtService;
    private final EmailSenderService emailSenderService;
    private final FileUploadService fileUploadService;
    private final PasswordEncoder passwordEncoder;
    private final UtilsService utilsService;
    private final MongoTemplate mongoTemplate;

    public void register(CustomerCreateRequest request) {
        if (request == null) {
            throw new InvalidInputException("Request body is required");
        }
        String emailAddress = normalizeEmail(request.emailAddress());
        if (userRepository.existsByEmailAddressIgnoreCase(emailAddress)) {
            throw new ResourceExistsException("An account already exists with this email address");
        }

        User user = User.builder()
                .emailAddress(emailAddress)
                .password(passwordEncoder.encode(request.password()))
                .userType(UserType.CUSTOMER)
                .isActive(false)
                .isVerified(false)
                .isDeleted(false)
                .verifiedAt(null)
                .lastLoginAt(null)
                .deletedAt(null)
                .passwordChangedAt(null)
                .build();
        User savedUser = userRepository.save(user);

        Customer customer = Customer.builder()
                .userId(savedUser.getId())
                .customerNumber(generateCustomerNumber())
                .active(false)
                .deleted(false)
                .lastLoginAt(null)
                .deletedAt(null)
                .build();
        Customer savedCustomer = customerRepository.save(customer);

        Map<String, String> otp = userOtpService.generateOtpForUser(savedUser.getId(), UserOtpType.ACCOUNT_ACTIVATION);

        emailSenderService.sendAccountActivationEmail(
                savedUser.getEmailAddress(),
                savedUser.getId(),
                buildFullName(savedUser),
                otp.get("otp")
        );
    }

    public CustomerResponse verifyAccount(CustomerVerifyOtpRequest request) {
        User user = findCustomerUserByEmail(request.emailAddress());
        if (user.isVerified()) {
            throw new InvalidOperationException("Customer account is already verified");
        }
        userOtpService.validateOtp(request.otp(), user.getId(), UserOtpType.ACCOUNT_ACTIVATION, true);

        user.setVerified(true);
        user.setActive(true);
        user.setVerifiedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        User savedUser = userRepository.save(user);

        Customer customer = findCustomerByUserId(savedUser.getId());
        customer.setActive(true);
        customer.setUpdatedAt(LocalDateTime.now());
        Customer savedCustomer = customerRepository.save(customer);

        emailSenderService.sendAccountSetupCompletionEmail(savedUser.getEmailAddress(), buildFullName(savedUser));
        return CustomerResponse.from(savedCustomer, savedUser);
    }

    public void resendVerificationOtp(CustomerResendOtpRequest request) {
        User user = findCustomerUserByEmail(request.emailAddress());
        Map<String, String> otp = null;
        if(request.userOtpType() == UserOtpType.ACCOUNT_ACTIVATION) {
            if (user.isVerified()) {
                throw new InvalidOperationException("Customer account is already verified");
            }
            otp = userOtpService.generateOtpForUser(user.getId(), UserOtpType.ACCOUNT_ACTIVATION);
            emailSenderService.sendAccountActivationEmail(user.getEmailAddress(), user.getId(), buildFullName(user), otp.get("otp"));
        }

        if(request.userOtpType() == UserOtpType.PASSWORD_RESET) {
            otp = userOtpService.generateOtpForUser(user.getId(), UserOtpType.PASSWORD_RESET);
            emailSenderService.sendPasswordResetEmail(user.getEmailAddress(), user.getId(), otp.get("otp"), buildFullName(user));
        }
    }

    public CustomerAuthResponse login(CustomerLoginRequest request) {
        User user = findCustomerUserByEmail(request.emailAddress());

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new UnauthorizedException("Invalid email address or password");
        }

        if (!user.isVerified()) {
            throw new InvalidOperationException("Customer account has not been verified");
        }

        if (!user.isActive()) {
            throw new UnauthorizedException("Customer account is inactive");
        }

        Customer customer = findCustomerByUserId(user.getId());

        if (!customer.isActive()) {
            throw new UnauthorizedException("Customer account is inactive");
        }

        LocalDateTime loginTime = LocalDateTime.now();

        user.setLastLoginAt(loginTime);
        user.setUpdatedAt(loginTime);
        customer.setLastLoginAt(loginTime);
        customer.setUpdatedAt(loginTime);

        User savedUser = userRepository.save(user);
        Customer savedCustomer = customerRepository.save(customer);

        Map<String, Object> claims = buildJwtClaims(savedCustomer, savedUser);

        String accessToken = jwtService.generateToken(savedUser.getEmailAddress(), claims);
        String refreshToken = jwtService.generateRefreshToken(savedUser.getEmailAddress(), claims);

        return new CustomerAuthResponse(accessToken, refreshToken, CustomerResponse.from(savedCustomer, savedUser));
    }

    public CustomerResponse getCurrentProfile() {
        User user = findCustomerUserByEmail(getCurrentPrincipalEmail());
        Customer customer = findCustomerByUserId(user.getId());
        return CustomerResponse.from(customer, user);
    }

    public CustomerResponse updateCurrentProfile(CustomerUpdateRequest request) {
        if (request == null) {
            throw new InvalidInputException("Request body is required");
        }

        User user = findCustomerUserByEmail(getCurrentPrincipalEmail());
        Customer customer = findCustomerByUserId(user.getId());

        String previousProfilePicture = user.getProfilePictureUrl();

        if (request.firstName() != null) user.setFirstName(requireText(request.firstName(), "First name cannot be blank"));
        if (request.lastName() != null) user.setLastName(requireText(request.lastName(), "Last name cannot be blank"));
        if (request.phoneNumber() != null) user.setPhoneNumber(clean(request.phoneNumber()));
        if (request.dateOfBirth() != null) {
            user.setDateOfBirth(request.dateOfBirth());
            user.setAge(calculateAge(request.dateOfBirth()));
        }
        if (request.gender() != null) user.setGender(request.gender());
        if (request.profilePictureUrl() != null) user.setProfilePictureUrl(clean(request.profilePictureUrl()));
        if (request.marketingEmailsEnabled() != null) customer.setMarketingEmailsEnabled(request.marketingEmailsEnabled());

        user.setUpdatedAt(LocalDateTime.now());
        customer.setUpdatedAt(LocalDateTime.now());

        User savedUser = userRepository.save(user);
        Customer savedCustomer = customerRepository.save(customer);

        fileUploadService.deleteOldFileIfChanged(previousProfilePicture, savedUser.getProfilePictureUrl());

        return CustomerResponse.from(savedCustomer, savedUser);
    }

    public void changePassword(PasswordChangeRequest request) {
        if (request == null) throw new InvalidInputException("Request body is required");

        validatePassword(request.newPassword());

        User user = findCustomerUserByEmail(getCurrentPrincipalEmail());

        if (!passwordEncoder.matches(request.oldPassword(), user.getPassword())) {
            throw new InvalidPasswordException("Current password is incorrect");
        }

        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new InvalidPasswordException("New password cannot be the same as the current password");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        userRepository.save(user);
    }

    public CustomerForgotPasswordResponse forgotPassword(CustomerForgotPasswordRequest request) {
        User user = findCustomerUserByEmail(request.emailAddress());
        Customer customer = findCustomerByUserId(user.getId());

        if (!user.isActive() || !customer.isActive()) throw new InvalidOperationException("Customer account is inactive");
        if (!user.isVerified()) throw new InvalidOperationException("Customer account has not been verified");

        Map<String, String> otp = userOtpService.generateOtpForUser(user.getId(), UserOtpType.PASSWORD_RESET);

        emailSenderService.sendPasswordResetEmail(user.getEmailAddress(), user.getId(), otp.get("otp"), buildFullName(user));

        return new CustomerForgotPasswordResponse(user.getId(), user.getEmailAddress(), otp.get("expiresAt"));
    }

    public void resetPassword(CustomerResetPasswordRequest request) {
        User user = findCustomerUserByEmail(request.emailAddress());
        Customer customer = findCustomerByUserId(user.getId());

        if (!user.isActive() || !customer.isActive()) throw new InvalidOperationException("Customer account is inactive");

        validatePassword(request.newPassword());

        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new InvalidPasswordException("New password cannot be the same as the current password");
        }

        userOtpService.validateOtp(request.otp(), user.getId(), UserOtpType.PASSWORD_RESET, true);

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        userRepository.save(user);
    }

    public void deleteCurrentAccount() {
        User user = findCustomerUserByEmail(getCurrentPrincipalEmail());
        Customer customer = findCustomerByUserId(user.getId());
        softDelete(customer, user);
    }

    public PaginatedData getCustomers(int page, int size, String searchParam) {
        validatePagination(page, size);

        Criteria criteria = Criteria.where("deleted").is(false);

        if (StringUtils.hasText(searchParam)) {
            // 1. Find matching Users
            Query userQuery = new Query();
            userQuery.addCriteria(new Criteria().andOperator(
                    Criteria.where("userType").is(UserType.CUSTOMER),
                    Criteria.where("isDeleted").is(false),
                    new Criteria().orOperator(
                            Criteria.where("firstName").regex(searchParam, "i"),
                            Criteria.where("lastName").regex(searchParam, "i"),
                            Criteria.where("emailAddress").regex(searchParam, "i"),
                            Criteria.where("phoneNumber").regex(searchParam, "i")
                    )
            ));
            List<String> matchingUserIds = mongoTemplate.find(userQuery, User.class).stream().map(User::getId).toList();

            // 2. Filter Customers by matching User IDs OR Customer Number
            criteria = criteria.andOperator(new Criteria().orOperator(
                    Criteria.where("customerNumber").regex(searchParam, "i"),
                    Criteria.where("userId").in(matchingUserIds)
            ));
        }

        return executeCustomerPagination(page, size, criteria);
    }

    public PaginatedData filterCustomers(int page, int size, CustomerFilterRequest filter) {
        validatePagination(page, size);

        CustomerFilterRequest request = filter == null ? new CustomerFilterRequest(null, null, null, null, null, null, null) : filter;
        validateCustomerFilterDates(request);

        Criteria criteria = Criteria.where("deleted").is(false);

        if (request.marketingEmailsEnabled() != null) {
            criteria = criteria.and("marketingEmailsEnabled").is(request.marketingEmailsEnabled());
        }
        if (request.registeredFrom() != null) {
            criteria = criteria.and("createdAt").gte(request.registeredFrom().atStartOfDay());
        }
        if (request.registeredTo() != null) {
            criteria = criteria.and("createdAt").lt(request.registeredTo().plusDays(1).atStartOfDay());
        }

        // Handle User-based Filters (Active, Verified, Gender, Search)
        boolean needsUserFilter = request.active() != null || request.verified() != null ||
                request.gender() != null || StringUtils.hasText(request.searchParam());

        if (needsUserFilter) {
            Query userQuery = new Query();
            Criteria userCriteria = Criteria.where("userType").is(UserType.CUSTOMER).and("isDeleted").is(false);

            if (request.active() != null) userCriteria = userCriteria.and("isActive").is(request.active());
            if (request.verified() != null) userCriteria = userCriteria.and("isVerified").is(request.verified());
            if (request.gender() != null) userCriteria = userCriteria.and("gender").is(request.gender());

            if (StringUtils.hasText(request.searchParam())) {
                userCriteria = userCriteria.andOperator(new Criteria().orOperator(
                        Criteria.where("firstName").regex(request.searchParam(), "i"),
                        Criteria.where("lastName").regex(request.searchParam(), "i"),
                        Criteria.where("emailAddress").regex(request.searchParam(), "i"),
                        Criteria.where("phoneNumber").regex(request.searchParam(), "i")
                ));
            }

            userQuery.addCriteria(userCriteria);
            List<String> matchingUserIds = mongoTemplate.find(userQuery, User.class).stream().map(User::getId).toList();

            criteria = criteria.and("userId").in(matchingUserIds);
        }

        return executeCustomerPagination(page, size, criteria);
    }

    // --- Helper Methods ---

    private PaginatedData executeCustomerPagination(int page, int size, Criteria criteria) {
        Query query = new Query(criteria)
                .with(PageRequest.of(page - 1, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"))));

        long total = mongoTemplate.count(query, Customer.class);
        List<Customer> customers = mongoTemplate.find(query, Customer.class);

        List<String> userIds = customers.stream().map(Customer::getUserId).distinct().toList();
        Map<String, User> usersById = userIds.isEmpty() ? Map.of() :
                userRepository.findAllById(userIds).stream()
                        .filter(user -> user.getUserType() == UserType.CUSTOMER && !user.isDeleted())
                        .collect(Collectors.toMap(User::getId, Function.identity()));

        List<CustomerResponse> responses = customers.stream().map(customer -> {
            User user = usersById.get(customer.getUserId());
            if (user == null) throw new ResourceNotFoundException("Customer account not found");
            return CustomerResponse.from(customer, user);
        }).toList();

        return utilsService.createPaginatedData(responses, page, size, total);
    }

    public CustomerResponse getCustomerById(String customerId) {
        Customer customer = findCustomerById(customerId);
        User user = findCustomerUserById(customer.getUserId());
        return CustomerResponse.from(customer, user);
    }

    public CustomerResponse activateCustomer(String customerId) {
        Customer customer = findCustomerById(customerId);
        User user = findCustomerUserById(customer.getUserId());

        if (!user.isVerified()) throw new InvalidOperationException("Unverified customer accounts cannot be activated");

        customer.setActive(true);
        customer.setUpdatedAt(LocalDateTime.now());
        user.setActive(true);
        user.setUpdatedAt(LocalDateTime.now());

        return CustomerResponse.from(customerRepository.save(customer), userRepository.save(user));
    }

    public CustomerResponse deactivateCustomer(String customerId) {
        Customer customer = findCustomerById(customerId);
        User user = findCustomerUserById(customer.getUserId());

        customer.setActive(false);
        customer.setUpdatedAt(LocalDateTime.now());
        user.setActive(false);
        user.setUpdatedAt(LocalDateTime.now());

        return CustomerResponse.from(customerRepository.save(customer), userRepository.save(user));
    }

    public void deleteCustomer(String customerId) {
        Customer customer = findCustomerById(customerId);
        User user = findCustomerUserById(customer.getUserId());
        softDelete(customer, user);
    }

    private void softDelete(Customer customer, User user) {
        LocalDateTime deletedAt = LocalDateTime.now();

        customer.setDeleted(true);
        customer.setActive(false);
        customer.setDeletedAt(deletedAt);
        customer.setUpdatedAt(deletedAt);

        user.setDeleted(true);
        user.setActive(false);
        user.setDeletedAt(deletedAt);
        user.setUpdatedAt(deletedAt);

        customerRepository.save(customer);
        userRepository.save(user);

        fileUploadService.deleteFileAsync(user.getProfilePictureUrl());
    }

    private Customer findCustomerById(String customerId) {
        if (customerId == null || customerId.isBlank()) throw new InvalidInputException("Customer ID is required");
        return customerRepository.findByIdAndDeletedFalse(customerId.trim()).orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
    }

    private Customer findCustomerByUserId(String userId) {
        return customerRepository.findByUserIdAndDeletedFalse(userId).orElseThrow(() -> new ResourceNotFoundException("Customer profile not found"));
    }

    private User findCustomerUserByEmail(String emailAddress) {
        return userRepository.findByEmailAddressIgnoreCaseAndUserTypeAndIsDeleted(normalizeEmail(emailAddress), UserType.CUSTOMER, false)
                .orElseThrow(() -> new ResourceNotFoundException("Customer account not found"));
    }

    private User findCustomerUserById(String userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("Customer account not found"));
        if (user.getUserType() != UserType.CUSTOMER || user.isDeleted()) throw new ResourceNotFoundException("Customer account not found");
        return user;
    }

    private Map<String, Object> buildJwtClaims(Customer customer, User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("customerId", customer.getId());
        claims.put("email", user.getEmailAddress());
        claims.put("userType", UserType.CUSTOMER.name());
        claims.put("userRole", "CUSTOMER");
        claims.put("auth", List.of("ROLE_CUSTOMER"));
        return claims;
    }

    private String generateCustomerNumber() {
        String customerNumber;
        do {
            customerNumber = "HLT-CUS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        } while (customerRepository.existsByCustomerNumber(customerNumber));
        return customerNumber;
    }

    private String getCurrentPrincipalEmail() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal() == null) {
            throw new UnauthorizedException("Authentication is required");
        }
        return authentication.getName();
    }

    private String normalizeEmail(String emailAddress) {
        if (emailAddress == null || emailAddress.isBlank()) throw new InvalidInputException("Email address is required");
        return emailAddress.trim().toLowerCase(Locale.ROOT);
    }

    private String buildFullName(User user) {
        return (safeText(user.getFirstName()) + " " + safeText(user.getLastName())).trim();
    }

    private String calculateAge(LocalDate dateOfBirth) {
        if (dateOfBirth == null) return "";
        return Period.between(dateOfBirth, LocalDate.now()).getYears() + " years";
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8) throw new InvalidPasswordException("Password must contain at least 8 characters");
    }

    private void validatePagination(int page, int size) {
        if (page < 1) throw new InvalidInputException("Page number must be at least 1");
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new InvalidInputException(message);
        return value.trim();
    }

    private String clean(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private void validateCustomerFilterDates(CustomerFilterRequest filter) {
        if (filter.registeredFrom() != null && filter.registeredTo() != null && filter.registeredFrom().isAfter(filter.registeredTo())) {
            throw new InvalidInputException("Registration start date cannot be after the end date");
        }
        if (LocalDate.MAX.equals(filter.registeredTo())) {
            throw new InvalidInputException("Registration end date is out of range");
        }
    }
}