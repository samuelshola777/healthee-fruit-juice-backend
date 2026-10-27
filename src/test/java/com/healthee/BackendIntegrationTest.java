package com.healthee;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.healthee.admin.*;
import com.healthee.cart.*;
import com.healthee.common.*;
import com.healthee.config.Bootstrap;
import com.healthee.mail.*;
import com.healthee.order.*;
import com.healthee.order.Order;
import com.healthee.order.request.CheckoutRequest;
import com.healthee.payment.*;
import com.healthee.product.*;
import com.healthee.product.request.ProductRequest;
import com.healthee.settings.*;
import com.healthee.upload.*;
import com.healthee.user.*;
import com.healthee.userotp.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(
    properties = {
      "app.jwt-secret=test-only-secret-with-at-least-32-bytes",
          "app.bootstrap-email=admin@example.test",
      "app.bootstrap-password=Testing-password-123", "app.paystack-secret=test-paystack-secret",
      "app.flutterwave-webhook-hash=test-flutterwave-hash", "app.seed-catalogue=true",
      "app.admin-notify-email=admin@example.test", "jobs.initial-delay=3600000"
    })
@AutoConfigureMockMvc
class BackendIntegrationTest {
  static final de.bwaldvogel.mongo.MongoServer memory =
      System.getenv("TEST_MONGO_URI") == null
          ? new de.bwaldvogel.mongo.MongoServer(
              new de.bwaldvogel.mongo.backend.memory.MemoryBackend())
          : null;
  static final String testUri = uri();

  static String uri() {
    String value =
        memory == null
            ? System.getenv("TEST_MONGO_URI")
            : memory.bindAndGetConnectionString() + "/healthee_test";
    if (!value.matches(".*/healthee_test(?:\\?.*)?$"))
      throw new IllegalStateException(
          "TEST_MONGO_URI must select the dedicated healthee_test database");
    return value;
  }

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    r.add("spring.data.mongodb.uri", () -> testUri);
  }

  @AfterAll
  static void stopDatabase() {
    if (memory != null) memory.shutdownNow();
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired MongoTemplate mongo;
  @Autowired Bootstrap bootstrap;
  @Autowired UserRepository users;
  @Autowired UserService userService;
  @Autowired AdminRepository admins;
  @Autowired CartService carts;
  @Autowired ProductRepository products;
  @Autowired ProductService productService;
  @Autowired OrderRepository orders;
  @Autowired OrderService orderService;
  @Autowired PaymentService payments;
  @Autowired PaymentRepository paymentRepository;
  @Autowired SettingsService settings;
  @Autowired CloudinaryFileUploadService files;
  @Autowired JwtEncoder encoder;
  @MockitoBean GatewayClient gateway;
  @MockitoBean CloudinaryService cloudinary;
  @MockitoBean MailService mail;
  static final String passwordHash = new BCryptPasswordEncoder(12).encode("Customer-password-123");
  User customer, other, superUser, regularAdmin;
  String customerToken, otherToken, superToken, adminToken;
  Product orange;

  @BeforeEach
  void prepare() {
    for (String name : mongo.getCollectionNames())
      if (!name.startsWith("system."))
        mongo.getCollection(name).deleteMany(new org.bson.Document());
    reset(gateway, cloudinary, mail);
    when(mail.available()).thenReturn(true);
    bootstrap.run(null);
    var s = settings.business();
    s.setPickupAddress("Healthee pickup counter, Lagos");
    s.setPhone("08012345678");
    s.setCheckoutEnabled(true);
    settings.save(s);
    customer = fixture("customer@example.test", User.UserType.CUSTOMER);
    other = fixture("other@example.test", User.UserType.CUSTOMER);
    regularAdmin = fixture("staff@example.test", User.UserType.ADMIN);
    Admin a = new Admin();
    a.setUserId(regularAdmin.getId());
    a.setActive(true);
    a.setInvitationAccepted(true);
    admins.insert(a);
    superUser = users.findByEmailAddress("admin@example.test").orElseThrow();
    customerToken = token(customer);
    otherToken = token(other);
    superToken = token(superUser);
    adminToken = token(regularAdmin);
    orange = products.findBySku("orange").orElseThrow();
    when(gateway.configured(any())).thenReturn(true);
    when(gateway.initialize(any(), any())).thenReturn("https://checkout.paystack.com/test");
    doReturn(new GatewayClient.Verification(false, "", "", 0, "", "")).when(gateway).verify(any());
    when(cloudinary.upload(any(), anyString()))
        .thenAnswer(
            inv -> {
              String id = inv.getArgument(1);
              return new CloudinaryService.Uploaded(
                  "https://res.cloudinary.com/demo/image/upload/" + id + ".png", id, "png");
            });
  }

  User fixture(String email, User.UserType type) {
    User u = new User();
    u.setFirstName("Test");
    u.setLastName("Customer");
    u.setEmailAddress(email);
    u.setPhoneNumber("08012345678");
    u.setUserType(type);
    u.setPasswordHash(passwordHash);
    u.setVerified(true);
    u.setVerifiedAt(Instant.now());
    return users.insert(u);
  }

  String token(User u) {
    Instant now = Instant.now();
    return encoder
        .encode(
            JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder()
                    .issuer("healthee")
                    .subject(u.getId())
                    .issuedAt(now)
                    .expiresAt(now.plusSeconds(3600))
                    .claim("ver", u.getTokenVersion())
                    .build()))
        .getTokenValue();
  }

  String key() {
    return UUID.randomUUID().toString();
  }

  JsonNode call(MockHttpServletRequestBuilder request, String token, int statusCode)
      throws Exception {
    if (token != null) request.header("Authorization", "Bearer " + token);
    var result = mvc.perform(request).andExpect(status().is(statusCode)).andReturn();
    return json.readTree(result.getResponse().getContentAsString()).path("data");
  }

  MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request, Object data)
      throws Exception {
    return request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(data));
  }

  Cart add(Cart c, String product, String variant, int quantity) {
    return carts.change(
        c,
        c.getVersion(),
        "add",
        new CartController.Item(c.getVersion(), product, variant, quantity),
        null);
  }

  Cart selected() {
    return add(carts.mine(customer.getId()), orange.getId(), "50cl", 2);
  }

  Order checkout(Cart c, String key) {
    return orderService.checkout(
        customer.getId(),
        key,
        new CheckoutRequest(c.getId(), c.getVersion(), "Call before handover"),
        "test");
  }

  Order checkout() {
    return checkout(selected(), key());
  }

  Payment payment(Order o) {
    return payments.initialize(
        customer.getId(),
        key(),
        new PaymentController.Initialize(o.getId(), Payment.Provider.PAYSTACK));
  }

  Payment paid(Payment p, String transaction) {
    doAnswer(
            inv -> {
              Payment actual = inv.getArgument(0);
              return new GatewayClient.Verification(
                  true,
                  actual.getReference(),
                  "NGN",
                  actual.getAmountMinor(),
                  transaction,
                  actual.getEmail());
            })
        .when(gateway)
        .verify(any());
    return payments.verify(p);
  }

  Map<String, Object> productBody(Product p, String url) {
    return Map.of(
        "version",
        p.getVersion(),
        "name",
        p.getName(),
        "category",
        p.getCategory(),
        "visible",
        p.isVisible(),
        "imageUrl",
        url,
        "variants",
        p.getVariants());
  }

  ManagedFile upload(ManagedFile.UsageType usage, String target, String actor) {
    return files.uploadFile(
        new MockMultipartFile("file", "bottle.png", "image/png", new byte[20]),
        usage,
        target,
        actor);
  }

  @Test
  void catalogueAndEveryPersistedModelUseUuidIds() {
    assertThat(products.count()).isEqualTo(18);
    assertThat(orange.getVariants().get(1).priceMinor()).isEqualTo(300000);
    assertThat(UUID.fromString(orange.getId()).toString()).isEqualTo(orange.getId());
    assertThat(UUID.fromString(settings.business().getId())).isNotNull();
    bootstrap.run(null);
    assertThat(admins.count()).isEqualTo(2);
    assertThat(users.count()).isEqualTo(4);
    var p = productService.get(orange.getId());
    p.setName("Edited orange");
    products.save(p);
    bootstrap.run(null);
    assertThat(products.findBySku("orange").orElseThrow().getName()).isEqualTo("Edited orange");
    var schema = mongo.getCollectionNames();
    assertThat(schema).hasSize(13).contains("user", "admin", "payment", "cart", "managed_file");
  }

  @Test
  void anonymousCannotCheckoutOrReadPrivateRecords() throws Exception {
    for (String model :
        List.of(
            "user/private/me",
            "admin/private",
            "product/private",
            "order/private",
            "payment/private",
            "managed-file/private",
            "business-settings/private",
            "dashboard/private")) call(get("/api/v1/" + model), null, 401);
    call(
        body(post("/api/v1/order/private/checkout"), Map.of("cartId", key(), "cartVersion", 0)),
        null,
        401);
    call(get("/api/v1/product/public"), null, 200);
  }

  @Test
  void customersCannotCallAdministrativeFunctions() throws Exception {
    for (String path :
        List.of(
            "product/private",
            "admin/private",
            "business-settings/private",
            "dashboard/private",
            "user/private/customer")) call(get("/api/v1/" + path), customerToken, 403);
    call(get("/api/v1/admin/private"), adminToken, 403);
    call(get("/api/v1/product/private"), adminToken, 200);
  }

  @Test
  void realSharedLoginLogoutRevokesJwt() throws Exception {
    var login =
        call(
            body(
                post("/api/v1/user/public/login"),
                Map.of(
                    "emailAddress",
                    customer.getEmailAddress(),
                    "password",
                    "Customer-password-123")),
            null,
            200);
    String jwt = login.path("accessToken").asText();
    assertThat(login.path("user").has("passwordHash")).isFalse();
    call(get("/api/v1/user/private/me"), jwt, 200);
    call(post("/api/v1/user/private/logout"), jwt, 200);
    call(get("/api/v1/user/private/me"), jwt, 401);
    var admin =
        call(
            body(
                post("/api/v1/user/public/login"),
                Map.of(
                    "emailAddress",
                    superUser.getEmailAddress(),
                    "password",
                    "Testing-password-123")),
            null,
            200);
    assertThat(admin.path("adminRole").asText()).isEqualTo("SUPER_ADMIN");
  }

  @Test
  void signupCannotSelectAdminAndOtpIsOneUse() throws Exception {
    var result =
        call(
            body(
                post("/api/v1/user/public/signup"),
                Map.of(
                    "firstName",
                    "New",
                    "lastName",
                    "Customer",
                    "emailAddress",
                    "new@example.test",
                    "phoneNumber",
                    "08012345678",
                    "password",
                    "New-customer-password-123",
                    "userType",
                    "ADMIN")),
            null,
            200);
    assertThat(result.path("userType").asText()).isEqualTo("CUSTOMER");
    assertThat(result.path("verified").asBoolean()).isFalse();
    User u = users.findById(result.path("id").asText()).orElseThrow();
    assertThatThrownBy(() -> userService.requireCustomer(u.getId()))
        .isInstanceOf(ApiException.class);
    var text = ArgumentCaptor.forClass(String.class);
    verify(mail).queue(anyString(), eq("new@example.test"), anyString(), text.capture());
    String code = text.getValue().split("code is ")[1].substring(0, 6);
    var request = Map.of("emailAddress", "new@example.test", "code", code);
    call(body(post("/api/v1/user-otp/public/verify"), request), null, 200);
    call(body(post("/api/v1/user-otp/public/verify"), request), null, 400);
    assertThat(userService.requireCustomer(u.getId()).isVerified()).isTrue();
    assertThat(mongo.findOne(new Query(), UserOtp.class).getTokenHash()).doesNotContain(code);
  }

  @Test
  void expiredOtpDoesNotVerifyAccount() throws Exception {
    customer.setVerified(false);
    users.save(customer);
    userService.resend(customer.getEmailAddress(), "test");
    var text = ArgumentCaptor.forClass(String.class);
    verify(mail).queue(anyString(), anyString(), anyString(), text.capture());
    String code = text.getValue().split("code is ")[1].substring(0, 6);
    mongo.updateMulti(new Query(), new Update().set("expiresAt", Instant.EPOCH), UserOtp.class);
    call(
        body(
            post("/api/v1/user-otp/public/verify"),
            Map.of("emailAddress", customer.getEmailAddress(), "code", code)),
        null,
        400);
  }

  @Test
  void passwordResetIsOneUseAndRevokesExistingSessions() throws Exception {
    userService.forgot(customer.getEmailAddress(), "test");
    var text = ArgumentCaptor.forClass(String.class);
    verify(mail).queue(anyString(), anyString(), anyString(), text.capture());
    String resetToken = text.getValue().split("&token=")[1].split("\n")[0];
    var r =
        Map.of(
            "userId",
            customer.getId(),
            "token",
            resetToken,
            "password",
            "Updated-customer-password-123");
    call(body(post("/api/v1/user/public/reset-password"), r), null, 200);
    call(body(post("/api/v1/user/public/reset-password"), r), null, 400);
    call(get("/api/v1/user/private/me"), customerToken, 401);
    call(
        body(
            post("/api/v1/user/public/login"),
            Map.of(
                "emailAddress",
                customer.getEmailAddress(),
                "password",
                "Updated-customer-password-123")),
        null,
        200);
  }

  @Test
  void superAdminInvitesStaffAndDisabledStaffTokenStopsWorking() throws Exception {
    var request =
        Map.of(
            "firstName",
            "Invited",
            "lastName",
            "Staff",
            "emailAddress",
            "invited@example.test",
            "phoneNumber",
            "08012345678");
    call(body(post("/api/v1/admin/private/invite"), request), adminToken, 403);
    var invitation = call(body(post("/api/v1/admin/private/invite"), request), superToken, 200);
    var text = ArgumentCaptor.forClass(String.class);
    verify(mail).queue(anyString(), eq("invited@example.test"), anyString(), text.capture());
    String userId = invitation.path("user").path("id").asText(),
        id = invitation.path("admin").path("id").asText();
    String inviteToken = text.getValue().split("&token=")[1].split("\n")[0];
    var accept =
        Map.of("userId", userId, "token", inviteToken, "password", "Invited-staff-password-123");
    call(body(post("/api/v1/admin/public/accept-invite"), accept), null, 200);
    call(body(post("/api/v1/admin/public/accept-invite"), accept), null, 400);
    String jwt =
        call(
                body(
                    post("/api/v1/user/public/login"),
                    Map.of(
                        "emailAddress",
                        "invited@example.test",
                        "password",
                        "Invited-staff-password-123")),
                null,
                200)
            .path("accessToken")
            .asText();
    call(get("/api/v1/product/private"), jwt, 200);
    Admin a = admins.findById(id).orElseThrow();
    call(
        body(
            patch("/api/v1/admin/private/" + id + "/status"),
            Map.of("version", a.getVersion(), "active", false)),
        superToken,
        200);
    call(get("/api/v1/product/private"), jwt, 401);
  }

  @Test
  void pendingInvitationCanBeRevokedAndSuperAdminIsProtected() throws Exception {
    var account =
        call(
            body(
                post("/api/v1/admin/private/invite"),
                Map.of(
                    "firstName",
                    "Test",
                    "lastName",
                    "Staff",
                    "emailAddress",
                    "revoked@example.test",
                    "phoneNumber",
                    "08012345678")),
            superToken,
            200);
    var text = ArgumentCaptor.forClass(String.class);
    verify(mail).queue(anyString(), anyString(), anyString(), text.capture());
    String inviteToken = text.getValue().split("&token=")[1].split("\n")[0];
    call(
        body(
            patch("/api/v1/admin/private/" + account.path("admin").path("id").asText() + "/status"),
            Map.of("version", account.path("admin").path("version").asLong(), "active", false)),
        superToken,
        200);
    call(
        body(
            post("/api/v1/admin/public/accept-invite"),
            Map.of(
                "userId",
                account.path("user").path("id").asText(),
                "token",
                inviteToken,
                "password",
                "Revoked-staff-password-123")),
        null,
        400);
    Admin sa = admins.findByUserId(superUser.getId()).orElseThrow();
    call(
        body(
            patch("/api/v1/admin/private/" + sa.getId() + "/status"),
            Map.of("version", sa.getVersion(), "active", false)),
        superToken,
        400);
  }

  @Test
  void guestCartRequiresTokenAndClaimMergesExactlyOnce() throws Exception {
    var guest = carts.createGuest("test");
    var g = add(guest.cart(), orange.getId(), "25cl", 1);
    var account = add(carts.mine(customer.getId()), orange.getId(), "25cl", 2);
    call(get("/api/v1/cart/public/" + g.getId()).header("X-Cart-Token", "wrong"), null, 404);
    assertThat(guest.guestToken()).doesNotContain(g.getGuestTokenHash());
    var r = Map.of("cartId", g.getId(), "guestToken", guest.guestToken());
    var merged = call(body(post("/api/v1/cart/private/claim"), r), customerToken, 200);
    assertThat(merged.path("items").get(0).path("quantity").asInt()).isEqualTo(3);
    var retry = call(body(post("/api/v1/cart/private/claim"), r), customerToken, 200);
    assertThat(retry.path("items").get(0).path("quantity").asInt()).isEqualTo(3);
    call(body(post("/api/v1/cart/private/claim"), r), otherToken, 404);
    call(
        get("/api/v1/cart/public/" + g.getId()).header("X-Cart-Token", guest.guestToken()),
        null,
        404);
    call(get("/api/v1/cart/private/" + account.getId()), otherToken, 404);
  }

  @Test
  void staleCartQuantityAndInvalidSizeAreRejected() throws Exception {
    Cart c = selected();
    var r = Map.of("version", c.getVersion(), "quantity", 3);
    String path = "/api/v1/cart/private/" + c.getId() + "/item/" + c.getItems().get(0).id();
    call(body(patch(path), r), customerToken, 200);
    call(body(patch(path), r), customerToken, 409);
    call(body(patch(path), Map.of("version", c.getVersion(), "quantity", 0)), customerToken, 400);
    assertThatThrownBy(() -> add(carts.mine(customer.getId()), orange.getId(), "75cl", 1))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void checkoutUsesAccountDetailsServerPricesAndPickupOnly() throws Exception {
    Cart c = selected();
    var r =
        Map.of(
            "cartId",
            c.getId(),
            "cartVersion",
            c.getVersion(),
            "customerName",
            "Spoofed",
            "totalMinor",
            1,
            "deliveryFeeMinor",
            999,
            "address",
            "Unused");
    var result =
        call(
            body(post("/api/v1/order/private/checkout").header("Idempotency-Key", key()), r),
            customerToken,
            200);
    assertThat(result.path("totalMinor").asLong()).isEqualTo(600000);
    assertThat(result.path("customerName").asText()).isEqualTo(customer.getFullName());
    assertThat(result.path("email").asText()).isEqualTo(customer.getEmailAddress());
    assertThat(result.path("fulfillmentMethod").asText()).isEqualTo("PICKUP");
    assertThat(result.has("deliveryFeeMinor")).isFalse();
    assertThat(result.has("address")).isFalse();
    assertThat(result.path("orderId").asText())
        .startsWith("HLT-")
        .isNotEqualTo(result.path("id").asText());
    UUID.fromString(result.path("id").asText());
    orange.setVariants(
        List.of(
            new Product.Variant("25cl", "25cl", 200000),
            new Product.Variant("50cl", "50cl", 400000)));
    products.save(orange);
    assertThat(orderService.get(result.path("id").asText()).getItems().get(0).unitPriceMinor())
        .isEqualTo(300000);
  }

  @Test
  void checkoutRequiresVerifiedCustomerAndOwnedCart() throws Exception {
    Cart c = selected();
    var payload = Map.of("cartId", c.getId(), "cartVersion", c.getVersion());
    call(
        body(post("/api/v1/order/private/checkout").header("Idempotency-Key", key()), payload),
        otherToken,
        404);
    customer.setVerified(false);
    users.save(customer);
    call(
        body(post("/api/v1/order/private/checkout").header("Idempotency-Key", key()), payload),
        customerToken,
        403);
    call(
        body(post("/api/v1/order/private/checkout").header("Idempotency-Key", key()), payload),
        adminToken,
        403);
  }

  @Test
  void checkoutIsIdempotentAndAnotherKeyCannotDuplicateSameCart() throws Exception {
    Cart c = selected();
    String k = key();
    Order a = checkout(c, k);
    assertThat(checkout(c, k).getId()).isEqualTo(a.getId());
    assertThatThrownBy(() -> checkout(c, key())).isInstanceOf(ApiException.class);
    assertThatThrownBy(
            () ->
                orderService.checkout(
                    customer.getId(),
                    k,
                    new CheckoutRequest(c.getId(), c.getVersion(), "different"),
                    "test"))
        .isInstanceOf(ApiException.class);
    assertThat(orders.count()).isEqualTo(1);
  }

  @Test
  void concurrentCheckoutCreatesOneOrder() throws Exception {
    Cart c = selected();
    String k = key();
    ExecutorService pool = Executors.newFixedThreadPool(4);
    try {
      List<Callable<String>> tasks = new ArrayList<>();
      for (int i = 0; i < 4; i++) tasks.add(() -> checkout(c, k).getId());
      Set<String> ids = new HashSet<>();
      for (var f : pool.invokeAll(tasks)) ids.add(f.get());
      assertThat(ids).hasSize(1);
      assertThat(orders.count()).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void privateOrderAndPaymentAreOwnerScoped() throws Exception {
    Order o = checkout();
    Payment p = payment(o);
    call(get("/api/v1/order/private/" + o.getId()), otherToken, 404);
    call(get("/api/v1/order/private/" + o.getId()), adminToken, 200);
    call(get("/api/v1/payment/private/" + p.getReference()), otherToken, 404);
    call(post("/api/v1/payment/private/" + p.getReference() + "/verify"), otherToken, 404);
    var list = call(get("/api/v1/order/private"), otherToken, 200);
    assertThat(list.path("total").asInt()).isZero();
  }

  @Test
  void orderingStaysClosedUntilPickupLocationIsConfigured() throws Exception {
    var s = settings.business();
    s.setPickupAddress("");
    assertThatThrownBy(() -> settings.save(s)).isInstanceOf(ApiException.class);
    s.setCheckoutEnabled(false);
    settings.save(s);
    assertThatThrownBy(() -> checkout()).isInstanceOf(ApiException.class);
  }

  @Test
  void hiddenProductsAndStaleCatalogueChangesAreRejected() throws Exception {
    var original = productBody(orange, "");
    call(body(put("/api/v1/product/private/" + orange.getId()), original), adminToken, 200);
    call(body(put("/api/v1/product/private/" + orange.getId()), original), adminToken, 409);
    var p = products.findById(orange.getId()).orElseThrow();
    p.setVisible(false);
    products.save(p);
    call(get("/api/v1/product/public/" + p.getId()), null, 404);
    assertThatThrownBy(() -> selected()).isInstanceOf(ApiException.class);
  }

  @Test
  void concurrentPaymentInitializationCallsGatewayOnce() throws Exception {
    Order o = checkout();
    String k = key();
    ExecutorService pool = Executors.newFixedThreadPool(4);
    try {
      List<Callable<String>> tasks = new ArrayList<>();
      for (int i = 0; i < 4; i++)
        tasks.add(
            () ->
                payments
                    .initialize(
                        customer.getId(),
                        k,
                        new PaymentController.Initialize(o.getId(), Payment.Provider.PAYSTACK))
                    .getId());
      Set<String> ids = new HashSet<>();
      for (var f : pool.invokeAll(tasks)) ids.add(f.get());
      assertThat(ids).hasSize(1);
      verify(gateway, times(1)).initialize(any(), any());
      assertThat(paymentRepository.count()).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void providerCannotBeSwitchedForPendingPayment() {
    Order o = checkout();
    Payment p = payment(o);
    assertThatThrownBy(
            () ->
                payments.initialize(
                    customer.getId(),
                    key(),
                    new PaymentController.Initialize(o.getId(), Payment.Provider.FLUTTERWAVE)))
        .isInstanceOf(ApiException.class);
    verify(gateway, times(1)).initialize(any(), any());
    assertThat(p.getReference()).startsWith("PAY-");
  }

  @Test
  void initializationTimeoutDoesNotRepeatExternalPost() {
    Order o = checkout();
    String k = key();
    when(gateway.initialize(any(), any())).thenThrow(ApiException.unavailable());
    var r = new PaymentController.Initialize(o.getId(), Payment.Provider.PAYSTACK);
    assertThatThrownBy(() -> payments.initialize(customer.getId(), k, r))
        .isInstanceOf(ApiException.class);
    assertThat(payments.initialize(customer.getId(), k, r).getStatus())
        .isEqualTo(Payment.Status.INITIALIZING);
    verify(gateway, times(1)).initialize(any(), any());
  }

  @Test
  void verificationRequiresExactAmountCurrencyReferenceAndCustomer() {
    Payment p = payment(checkout());
    for (var v :
        List.of(
            new GatewayClient.Verification(true, p.getReference(), "NGN", 1, "tx", p.getEmail()),
            new GatewayClient.Verification(
                true, p.getReference(), "USD", p.getAmountMinor(), "tx", p.getEmail()),
            new GatewayClient.Verification(
                true, "other", "NGN", p.getAmountMinor(), "tx", p.getEmail()),
            new GatewayClient.Verification(
                true, p.getReference(), "NGN", p.getAmountMinor(), "tx", "other@example.test"))) {
      when(gateway.verify(any())).thenReturn(v);
      assertThatThrownBy(() -> payments.verify(p)).isInstanceOf(ApiException.class);
      assertThat(payments.get(p.getId()).getStatus()).isEqualTo(Payment.Status.REVIEW);
    }
    paid(p, "valid");
    doReturn(new GatewayClient.Verification(false, "", "", 0, "", "")).when(gateway).verify(any());
    assertThat(payments.verify(p).getStatus()).isEqualTo(Payment.Status.PAID);
    assertThat(orderService.get(p.getOrderId()).getPaymentStatus()).isEqualTo(Payment.Status.PAID);
  }

  @Test
  void providerTransactionCannotBeUsedForTwoOrders() {
    Payment first = payment(checkout());
    Cart c = carts.mine(customer.getId());
    c =
        carts.change(
            c,
            c.getVersion(),
            "quantity",
            new CartController.Item(c.getVersion(), null, null, 3),
            c.getItems().get(0).id());
    Payment second = payment(checkout(c, key()));
    paid(first, "shared-transaction");
    assertThatThrownBy(() -> paid(second, "shared-transaction")).isInstanceOf(ApiException.class);
    assertThat(payments.get(second.getId()).getStatus()).isEqualTo(Payment.Status.REVIEW);
  }

  @Test
  void signedWebhookIsDurableDeduplicatedAndVerifiedServerSide() throws Exception {
    Order o = checkout();
    Payment p = payment(o);
    String event =
        json.writeValueAsString(
            Map.of("event", "charge.success", "data", Map.of("reference", p.getReference())));
    String sig =
        HexFormat.of()
            .formatHex(
                Crypto.hmac(
                    "HmacSHA512", "test-paystack-secret", event.getBytes(StandardCharsets.UTF_8)));
    call(
        post("/api/v1/payment/public/webhook/paystack")
            .contentType(MediaType.APPLICATION_JSON)
            .content(event),
        null,
        401);
    for (int n = 0; n < 2; n++)
      call(
          post("/api/v1/payment/public/webhook/paystack")
              .header("x-paystack-signature", sig)
              .contentType(MediaType.APPLICATION_JSON)
              .content(event),
          null,
          200);
    assertThat(mongo.count(new Query(), WebhookJob.class)).isEqualTo(1);
    assertThat(orderService.get(o.getId()).getPaymentStatus()).isEqualTo(Payment.Status.PENDING);
    when(gateway.verify(any()))
        .thenReturn(
            new GatewayClient.Verification(
                true, p.getReference(), "NGN", p.getAmountMinor(), "webhook-tx", p.getEmail()));
    payments.processWebhooks();
    assertThat(orderService.get(o.getId()).getPaymentStatus()).isEqualTo(Payment.Status.PAID);
  }

  @Test
  void flutterwaveRejectsBadNewSignatureEvenWhenLegacyHashIsCorrect() throws Exception {
    String event =
        "{\"event\":\"charge.completed\",\"data\":{\"tx_ref\":\"PAY-00000000-0000-0000-0000-000000000000\"}}";
    call(
        post("/api/v1/payment/public/webhook/flutterwave")
            .header("verif-hash", "test-flutterwave-hash")
            .header("flutterwave-signature", "wrong")
            .contentType(MediaType.APPLICATION_JSON)
            .content(event),
        null,
        401);
    call(
        post("/api/v1/payment/public/webhook/flutterwave")
            .header("verif-hash", "test-flutterwave-hash")
            .contentType(MediaType.APPLICATION_JSON)
            .content(event),
        null,
        200);
  }

  @Test
  void paymentCleanupPreservesChangedAndNewCartLines() {
    Cart c = selected();
    Order o = checkout(c, key());
    Payment p = payment(o);
    c =
        carts.change(
            c,
            c.getVersion(),
            "quantity",
            new CartController.Item(c.getVersion(), null, null, 3),
            c.getItems().get(0).id());
    c = add(c, products.findBySku("pineapple").orElseThrow().getId(), "25cl", 1);
    paid(p, "cleanup-changed");
    payments.afterPayment();
    assertThat(carts.mine(customer.getId()).getItems()).hasSize(2);
    assertThat(carts.mine(customer.getId()).getItems().get(0).quantity()).isEqualTo(3);
  }

  @Test
  void unchangedPurchasedLinesAreRemovedOnlyAfterVerifiedPayment() {
    Order o = checkout();
    Payment p = payment(o);
    payments.afterPayment();
    assertThat(carts.mine(customer.getId()).getItems()).hasSize(1);
    paid(p, "cleanup-unchanged");
    payments.afterPayment();
    payments.afterPayment();
    assertThat(carts.mine(customer.getId()).getItems()).isEmpty();
    verify(mail, times(2)).queue(anyString(), anyString(), anyString(), anyString());
  }

  @Test
  void paidProjectionRecoversAfterInterruption() {
    Payment p = payment(checkout());
    mongo.updateFirst(
        Query.query(Criteria.where("_id").is(p.getId())),
        new Update()
            .set("status", Payment.Status.PAID)
            .set("paidAt", Instant.now())
            .set("orderUpdated", false),
        Payment.class);
    payments.reconcile();
    assertThat(orderService.get(p.getOrderId()).getPaymentStatus()).isEqualTo(Payment.Status.PAID);
  }

  @Test
  void fulfillmentRequiresPaymentAndFollowsPickupStages() throws Exception {
    Order o = checkout();
    assertThatThrownBy(
            () -> orderService.changeStatus(o.getId(), o.getVersion(), Order.Status.PREPARING))
        .isInstanceOf(ApiException.class);
    paid(payment(o), "pickup-tx");
    var paid = orderService.get(o.getId());
    var prep = orderService.changeStatus(paid.getId(), paid.getVersion(), Order.Status.PREPARING);
    assertThatThrownBy(
            () ->
                orderService.changeStatus(prep.getId(), prep.getVersion(), Order.Status.COLLECTED))
        .isInstanceOf(ApiException.class);
    var ready =
        orderService.changeStatus(prep.getId(), prep.getVersion(), Order.Status.READY_FOR_PICKUP);
    orderService.readyNotifications();
    orderService.readyNotifications();
    verify(mail, times(1))
        .queue(
            startsWith("ready:"),
            eq(customer.getEmailAddress()),
            anyString(),
            contains("dispatch rider"));
    var before = call(get("/api/v1/dashboard/private"), adminToken, 200);
    assertThat(before.path("mostPurchased")).isEmpty();
    orderService.changeStatus(ready.getId(), ready.getVersion(), Order.Status.COLLECTED);
    var dashboard = call(get("/api/v1/dashboard/private"), adminToken, 200);
    assertThat(dashboard.path("grossReceivedMinor").asLong()).isEqualTo(600000);
    assertThat(dashboard.path("mostPurchased").get(0).path("quantity").asInt()).isEqualTo(2);
  }

  @Test
  void uploadThenProductUpdateDeletesOldFileAfterSave() throws Exception {
    ManagedFile first =
        upload(ManagedFile.UsageType.PRODUCT_IMAGE, orange.getId(), regularAdmin.getId());
    call(
        body(
            put("/api/v1/product/private/" + orange.getId()),
            productBody(orange, first.getFileUrl())),
        adminToken,
        200);
    verify(cloudinary, never()).delete(anyString());
    ManagedFile second =
        upload(ManagedFile.UsageType.PRODUCT_IMAGE, orange.getId(), regularAdmin.getId());
    var current = products.findById(orange.getId()).orElseThrow();
    call(
        body(
            put("/api/v1/product/private/" + orange.getId()),
            productBody(current, second.getFileUrl())),
        adminToken,
        200);
    verify(cloudinary).delete(first.getPublicId());
    assertThat(mongo.findById(first.getId(), ManagedFile.class)).isNull();
    assertThat(products.findById(orange.getId()).orElseThrow().getImageUrl())
        .isEqualTo(second.getFileUrl());
  }

  @Test
  void fileAssociationFailureKeepsOldAndDeletesNew() {
    ManagedFile first =
        upload(ManagedFile.UsageType.PRODUCT_IMAGE, orange.getId(), regularAdmin.getId());
    productService.save(
        orange.getId(), productRequest(orange, first.getFileUrl()), regularAdmin.getId());
    var current = products.findById(orange.getId()).orElseThrow();
    ManagedFile next =
        upload(ManagedFile.UsageType.PRODUCT_IMAGE, orange.getId(), regularAdmin.getId());
    ProductRepository failing = mock(ProductRepository.class);
    when(failing.findById(current.getId())).thenReturn(Optional.of(current));
    when(failing.save(any()))
        .thenThrow(new org.springframework.dao.OptimisticLockingFailureException("simulated"));
    var service = new ProductService(failing, mongo, files);
    assertThatThrownBy(
            () ->
                service.save(
                    current.getId(),
                    productRequest(current, next.getFileUrl()),
                    regularAdmin.getId()))
        .isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);
    verify(cloudinary).delete(next.getPublicId());
    verify(cloudinary, never()).delete(first.getPublicId());
    assertThat(products.findById(current.getId()).orElseThrow().getImageUrl())
        .isEqualTo(first.getFileUrl());
  }

  ProductRequest productRequest(Product p, String url) {
    return new ProductRequest(
        p.getVersion(),
        p.getName(),
        p.getCategory(),
        p.isVisible(),
        url,
        p.getVariants().stream()
            .map(v -> new ProductRequest.VariantRequest(v.id(), v.label(), v.priceMinor()))
            .toList());
  }

  @Test
  void failedCloudinaryDeletionIsRetriedDurably() {
    ManagedFile old =
        upload(ManagedFile.UsageType.PRODUCT_IMAGE, orange.getId(), regularAdmin.getId());
    productService.save(
        orange.getId(), productRequest(orange, old.getFileUrl()), regularAdmin.getId());
    ManagedFile next =
        upload(ManagedFile.UsageType.PRODUCT_IMAGE, orange.getId(), regularAdmin.getId());
    doThrow(ApiException.unavailable()).doNothing().when(cloudinary).delete(old.getPublicId());
    var current = products.findById(orange.getId()).orElseThrow();
    productService.save(
        current.getId(), productRequest(current, next.getFileUrl()), regularAdmin.getId());
    assertThat(mongo.findById(old.getId(), ManagedFile.class).getState())
        .isEqualTo(ManagedFile.State.DELETE_PENDING);
    mongo.updateFirst(
        Query.query(Criteria.where("_id").is(old.getId())),
        new Update().set("nextAttempt", Instant.EPOCH),
        ManagedFile.class);
    files.reconcile();
    assertThat(mongo.findById(old.getId(), ManagedFile.class)).isNull();
    verify(cloudinary, times(2)).delete(old.getPublicId());
  }

  @Test
  void imageCannotBeAttachedToAnotherResourceOrAccount() throws Exception {
    ManagedFile foreign =
        upload(ManagedFile.UsageType.PROFILE_PICTURE, other.getId(), other.getId());
    var u = userService.get(customer.getId());
    call(
        body(
            put("/api/v1/user/private/profile"),
            Map.of(
                "version",
                u.getVersion(),
                "firstName",
                "Test",
                "lastName",
                "Customer",
                "phoneNumber",
                "08012345678",
                "profilePictureUrl",
                foreign.getFileUrl())),
        customerToken,
        400);
    call(
        body(
            put("/api/v1/product/private/" + orange.getId()),
            productBody(orange, "https://external.example.test/image.png")),
        adminToken,
        400);
    assertThat(mongo.findById(foreign.getId(), ManagedFile.class)).isNotNull();
    verify(cloudinary, never()).delete(anyString());
  }

  @Test
  void profilePictureUploadUsesCurrentUserAndReplacementCleanup() throws Exception {
    var image = new MockMultipartFile("file", "profile.png", "image/png", new byte[20]);
    var result =
        mvc.perform(
                multipart("/api/v1/managed-file/private")
                    .file(image)
                    .param("usageType", "PROFILE_PICTURE")
                    .param("targetId", other.getId())
                    .header("Authorization", "Bearer " + customerToken))
            .andExpect(status().isOk())
            .andReturn();
    var f = json.readTree(result.getResponse().getContentAsString()).path("data");
    assertThat(f.path("targetId").asText()).isEqualTo(customer.getId());
    var u = userService.get(customer.getId());
    call(
        body(
            put("/api/v1/user/private/profile"),
            Map.of(
                "version",
                u.getVersion(),
                "firstName",
                "Test",
                "lastName",
                "Customer",
                "phoneNumber",
                "08012345678",
                "profilePictureUrl",
                f.path("fileUrl").asText())),
        customerToken,
        200);
    call(delete("/api/v1/managed-file/private/" + f.path("id").asText()), customerToken, 409);
    u = userService.get(customer.getId());
    call(
        body(
            put("/api/v1/user/private/profile"),
            Map.of(
                "version",
                u.getVersion(),
                "firstName",
                "Test",
                "lastName",
                "Customer",
                "phoneNumber",
                "08012345678",
                "profilePictureUrl",
                "")),
        customerToken,
        200);
    verify(cloudinary).delete(f.path("publicId").asText());
    mvc.perform(
            multipart("/api/v1/managed-file/private")
                .file(image)
                .param("usageType", "PRODUCT_IMAGE")
                .param("targetId", orange.getId())
                .header("Authorization", "Bearer " + customerToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void orphanedImageAndInterruptedReplacementAreRecovered() {
    ManagedFile staged =
        upload(ManagedFile.UsageType.PRODUCT_IMAGE, orange.getId(), regularAdmin.getId());
    mongo.updateFirst(
        Query.query(Criteria.where("_id").is(staged.getId())),
        new Update().set("nextAttempt", Instant.EPOCH),
        ManagedFile.class);
    files.reconcile();
    assertThat(mongo.findById(staged.getId(), ManagedFile.class)).isNull();
    ManagedFile old =
        upload(ManagedFile.UsageType.PRODUCT_IMAGE, orange.getId(), regularAdmin.getId());
    productService.save(
        orange.getId(), productRequest(orange, old.getFileUrl()), regularAdmin.getId());
    files.prepareAssociation(
        "",
        old.getFileUrl(),
        ManagedFile.UsageType.PRODUCT_IMAGE,
        orange.getId(),
        regularAdmin.getId());
    mongo.updateFirst(
        Query.query(Criteria.where("_id").is(orange.getId())),
        new Update().set("imageUrl", ""),
        Product.class);
    mongo.updateFirst(
        Query.query(Criteria.where("_id").is(old.getId())),
        new Update().set("nextAttempt", Instant.EPOCH),
        ManagedFile.class);
    files.reconcile();
    assertThat(mongo.findById(old.getId(), ManagedFile.class)).isNull();
  }

  @Test
  void smtpQueueDeduplicatesAndRetriesFailures() {
    var sender = mock(org.springframework.mail.javamail.JavaMailSender.class);
    var service = new MailService(sender, mongo);
    ReflectionTestUtils.setField(service, "enabled", true);
    ReflectionTestUtils.setField(service, "from", "sender@example.test");
    service.queue("same-job", "customer@example.test", "Subject", "Body");
    service.queue("same-job", "customer@example.test", "Subject", "Body");
    assertThat(mongo.count(new Query(), MailJob.class)).isEqualTo(1);
    MailJob job = mongo.findOne(new Query(), MailJob.class);
    UUID.fromString(job.getId());
    doThrow(new org.springframework.mail.MailSendException("simulated"))
        .doNothing()
        .when(sender)
        .send(any(org.springframework.mail.SimpleMailMessage.class));
    service.deliver();
    assertThat(mongo.findById(job.getId(), MailJob.class).isSent()).isFalse();
    mongo.updateFirst(
        Query.query(Criteria.where("_id").is(job.getId())),
        new Update().set("nextAttempt", Instant.EPOCH),
        MailJob.class);
    service.deliver();
    assertThat(mongo.findById(job.getId(), MailJob.class).isSent()).isTrue();
  }

  @Test
  void interruptedStaffInvitationCanBeRetriedWithoutAnotherUser() throws Exception {
    User pending = new User();
    pending.setFirstName("Pending");
    pending.setLastName("Staff");
    pending.setEmailAddress("pending@example.test");
    pending.setPhoneNumber("08012345678");
    pending.setUserType(User.UserType.ADMIN);
    pending.setActive(false);
    users.insert(pending);
    long count = users.count();
    var result =
        call(
            body(
                post("/api/v1/admin/private/invite"),
                Map.of(
                    "firstName",
                    "Pending",
                    "lastName",
                    "Staff",
                    "emailAddress",
                    pending.getEmailAddress(),
                    "phoneNumber",
                    "08012345678")),
            superToken,
            200);
    assertThat(result.path("user").path("id").asText()).isEqualTo(pending.getId());
    assertThat(users.count()).isEqualTo(count);
    assertThat(admins.findByUserId(pending.getId())).isPresent();
  }

  @Test
  void checkoutRetryFindsOrderCreatedBetweenLookups() {
    Cart c = selected();
    String k = key();
    Order existing = checkout(c, k);
    OrderRepository interleaved = mock(OrderRepository.class);
    when(interleaved.findByIdempotencyHash(anyString())).thenReturn(Optional.empty());
    when(interleaved.findByCartSnapshotKey(anyString())).thenReturn(Optional.of(existing));
    var service =
        new OrderService(
            interleaved,
            carts,
            userService,
            productService,
            settings,
            mongo,
            mock(RateLimit.class),
            mail);
    assertThat(
            service
                .checkout(
                    customer.getId(),
                    k,
                    new CheckoutRequest(c.getId(), c.getVersion(), "Call before handover"),
                    "test")
                .getId())
        .isEqualTo(existing.getId());
    verify(interleaved, never()).insert(any(Order.class));
  }
}
