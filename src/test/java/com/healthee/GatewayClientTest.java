package com.healthee;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import com.healthee.order.Order;
import com.healthee.payment.GatewayClient;
import com.healthee.payment.Payment;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;

class GatewayClientTest {
  HttpServer server;
  GatewayClient gateway;
  ObjectMapper json = new ObjectMapper();
  AtomicReference<String> received = new AtomicReference<>();

  @BeforeEach
  void setup() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    gateway = new GatewayClient(HttpClient.newHttpClient(), json);
    for (String p : new String[] {"paystackSecret", "flutterwaveSecret"})
      ReflectionTestUtils.setField(gateway, p, "test-only");
    for (String p : new String[] {"paystackUrl", "flutterwaveUrl"})
      ReflectionTestUtils.setField(gateway, p, "http://127.0.0.1:" + server.getAddress().getPort());
    ReflectionTestUtils.setField(gateway, "publicUrl", "https://example.test");
    server.start();
  }

  @AfterEach
  void close() {
    server.stop(0);
  }

  void response(String path, String body) {
    server.createContext(
        path,
        e -> {
          received.set(new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          e.getResponseHeaders().set("Content-Type", "application/json");
          e.sendResponseHeaders(200, bytes.length);
          e.getResponseBody().write(bytes);
          e.close();
        });
  }

  Order order(Payment.Provider p) {
    Order o = new Order();
    o.setEmail("a@example.test");
    o.setCustomerName("Test");
    o.setPhone("08012345678");
    o.setTotalMinor(150000);
    return o;
  }

  Payment payment(Payment.Provider provider) {
    Payment p = new Payment();
    p.setProvider(provider);
    p.setAmountMinor(150000);
    p.setEmail("a@example.test");
    p.setReference("PAY-test");
    return p;
  }

  @Test
  void paystackReceivesKobo() throws Exception {
    response(
        "/transaction/initialize",
        "{\"status\":true,\"data\":{\"authorization_url\":\"https://checkout.paystack.com/test\"}}");
    gateway.initialize(payment(Payment.Provider.PAYSTACK), order(Payment.Provider.PAYSTACK));
    assertThat(json.readTree(received.get()).path("amount").asLong()).isEqualTo(150000);
  }

  @Test
  void flutterwaveReceivesNaira() throws Exception {
    response(
        "/payments",
        "{\"status\":\"success\",\"data\":{\"link\":\"https://checkout.flutterwave.com/test\"}}");
    gateway.initialize(payment(Payment.Provider.FLUTTERWAVE), order(Payment.Provider.FLUTTERWAVE));
    assertThat(json.readTree(received.get()).path("amount").asText()).isEqualTo("1500.00");
  }

  @Test
  void flutterwaveVerificationConvertsNairaExactly() {
    response(
        "/transactions/verify_by_reference",
        "{\"status\":\"success\",\"data\":{\"status\":\"successful\",\"tx_ref\":\"PAY-test\",\"amount\":1500.25,\"currency\":\"NGN\",\"id\":4,\"customer\":{\"email\":\"a@example.test\"}}}");
    assertThat(gateway.verify(payment(Payment.Provider.FLUTTERWAVE)).amountMinor())
        .isEqualTo(150025);
  }
}
