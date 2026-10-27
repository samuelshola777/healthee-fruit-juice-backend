package com.healthee;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthee.common.ApiException;
import com.healthee.upload.CloudinaryService;
import java.io.ByteArrayOutputStream;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

class CloudinaryClientTest {
  HttpClient http;
  CloudinaryService cloudinary;
  String publicId = "healthee/assets/00000000-0000-0000-0000-000000000001";

  @BeforeEach
  void setup() {
    http = mock(HttpClient.class);
    cloudinary = new CloudinaryService(http, new ObjectMapper());
    ReflectionTestUtils.setField(cloudinary, "cloud", "demo");
    ReflectionTestUtils.setField(cloudinary, "key", "test-api-key");
    ReflectionTestUtils.setField(cloudinary, "secret", "test-cloud-secret");
  }

  @SuppressWarnings("unchecked")
  void response(String body) throws Exception {
    HttpResponse<String> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(200);
    when(response.body()).thenReturn(body);
    when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(response);
  }

  byte[] png() {
    return new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 0, 0, 0, 0, 0};
  }

  @Test
  void uploadSignsReservedPublicIdAndValidatesResponse() throws Exception {
    response(
        "{\"secure_url\":\"https://res.cloudinary.com/demo/image/upload/test.png\",\"public_id\":\""
            + publicId
            + "\",\"format\":\"png\"}");
    var result =
        cloudinary.upload(new MockMultipartFile("file", "image.png", "image/png", png()), publicId);
    assertThat(result.publicId()).isEqualTo(publicId);
    var request = ArgumentCaptor.forClass(HttpRequest.class);
    verify(http).send(request.capture(), any(HttpResponse.BodyHandler.class));
    HttpRequest sent = request.getValue();
    assertThat(sent.uri().toString())
        .isEqualTo("https://api.cloudinary.com/v1_1/demo/image/upload");
    String body = read(sent), timestamp = field(body, "timestamp");
    String input =
        "allowed_formats=jpg,png,webp&overwrite=false&public_id="
            + publicId
            + "&timestamp="
            + timestamp
            + "test-cloud-secret";
    String signature =
        HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-1").digest(input.getBytes(StandardCharsets.UTF_8)));
    assertThat(field(body, "signature")).isEqualTo(signature);
    assertThat(body).doesNotContain("test-cloud-secret");
  }

  @Test
  void invalidImageAndOversizedPayloadNeverReachCloudinary() {
    for (byte[] bytes : new byte[][] {new byte[20], new byte[5 * 1024 * 1024 + 1]})
      assertThatThrownBy(
              () ->
                  cloudinary.upload(
                      new MockMultipartFile("file", "fake.png", "image/png", bytes), publicId))
          .isInstanceOf(ApiException.class);
    verifyNoInteractions(http);
  }

  @Test
  void unexpectedPublicIdIsRejected() throws Exception {
    response(
        "{\"secure_url\":\"https://res.cloudinary.com/demo/image/upload/test.png\",\"public_id\":\"other/asset\",\"format\":\"png\"}");
    assertThatThrownBy(
            () ->
                cloudinary.upload(
                    new MockMultipartFile("file", "image.png", "image/png", png()), publicId))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void deleteIsIdempotentButRejectsUnexpectedProviderResult() throws Exception {
    response("{\"result\":\"not found\"}");
    cloudinary.delete(publicId);
    response("{\"result\":\"error\"}");
    assertThatThrownBy(() -> cloudinary.delete(publicId)).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> cloudinary.delete("unmanaged/asset")).isInstanceOf(ApiException.class);
  }

  String field(String body, String name) {
    return body.split("name=\"" + name + "\"\\r\\n\\r\\n")[1].split("\r\n")[0];
  }

  String read(HttpRequest request) {
    CompletableFuture<String> result = new CompletableFuture<>();
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    request
        .bodyPublisher()
        .orElseThrow()
        .subscribe(
            new Flow.Subscriber<ByteBuffer>() {
              public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
              }

              public void onNext(ByteBuffer buffer) {
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                output.writeBytes(bytes);
              }

              public void onError(Throwable t) {
                result.completeExceptionally(t);
              }

              public void onComplete() {
                result.complete(output.toString(StandardCharsets.UTF_8));
              }
            });
    return result.join();
  }
}
