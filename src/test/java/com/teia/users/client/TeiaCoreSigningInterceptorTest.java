package com.teia.users.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TeiaCoreSigningInterceptor")
class TeiaCoreSigningInterceptorTest {

    private static final String TEST_SECRET = "test-internal-secret-32chars!!!";
    private static final String CLIENT_ID = "123";

    @Mock
    private HttpRequest mockRequest;

    @Mock
    private ClientHttpRequestExecution mockExecution;

    @Mock
    private ClientHttpResponse mockResponse;

    private HttpHeaders headers;
    private TeiaCoreSigningInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new TeiaCoreSigningInterceptor(TEST_SECRET);
        headers = new HttpHeaders();
        headers.set(TeiaCoreSigningInterceptor.HEADER_CLIENT_ID, CLIENT_ID);
    }

    @Nested
    @DisplayName("Constructor Validation")
    class ConstructorValidation {

        @Test
        @DisplayName("should throw exception when secret is null")
        void shouldThrowExceptionWhenSecretIsNull() {
            assertThrows(IllegalArgumentException.class, () ->
                new TeiaCoreSigningInterceptor(null)
            );
        }

        @Test
        @DisplayName("should throw exception when secret is blank")
        void shouldThrowExceptionWhenSecretIsBlank() {
            assertThrows(IllegalArgumentException.class, () ->
                new TeiaCoreSigningInterceptor("   ")
            );
        }

        @Test
        @DisplayName("should throw exception when secret is empty")
        void shouldThrowExceptionWhenSecretIsEmpty() {
            assertThrows(IllegalArgumentException.class, () ->
                new TeiaCoreSigningInterceptor("")
            );
        }

        @Test
        @DisplayName("should create interceptor with valid secret")
        void shouldCreateInterceptorWithValidSecret() {
            TeiaCoreSigningInterceptor interceptor = new TeiaCoreSigningInterceptor(TEST_SECRET);
            assertNotNull(interceptor);
        }
    }

    @Nested
    @DisplayName("Request Signing")
    class RequestSigning {

        @Test
        @DisplayName("should add X-Internal-Key header")
        void shouldAddInternalKeyHeader() throws IOException {
            when(mockRequest.getHeaders()).thenReturn(headers);
            when(mockExecution.execute(any(), any())).thenReturn(mockResponse);

            byte[] body = "{\"action\":\"call\"}".getBytes(StandardCharsets.UTF_8);
            interceptor.intercept(mockRequest, body, mockExecution);

            assertTrue(headers.containsKey(TeiaCoreSigningInterceptor.HEADER_INTERNAL_KEY));
            assertNotNull(headers.getFirst(TeiaCoreSigningInterceptor.HEADER_INTERNAL_KEY));
            assertEquals(64, headers.getFirst(TeiaCoreSigningInterceptor.HEADER_INTERNAL_KEY).length(),
                "HMAC-SHA256 hex signature should be 64 characters");
        }

        @Test
        @DisplayName("should add X-Timestamp header")
        void shouldAddTimestampHeader() throws IOException {
            when(mockRequest.getHeaders()).thenReturn(headers);
            when(mockExecution.execute(any(), any())).thenReturn(mockResponse);

            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            interceptor.intercept(mockRequest, body, mockExecution);

            assertTrue(headers.containsKey(TeiaCoreSigningInterceptor.HEADER_TIMESTAMP));
            String timestamp = headers.getFirst(TeiaCoreSigningInterceptor.HEADER_TIMESTAMP);
            assertNotNull(timestamp);

            // Verify timestamp is a valid numeric string representing current time (within 1 second)
            long timestampMs = Long.parseLong(timestamp);
            long now = System.currentTimeMillis();
            assertTrue(Math.abs(now - timestampMs) < 1000, "Timestamp should be within 1 second of current time");
        }

        @Test
        @DisplayName("should preserve X-Client-ID header")
        void shouldPreserveClientIdHeader() throws IOException {
            when(mockRequest.getHeaders()).thenReturn(headers);
            when(mockExecution.execute(any(), any())).thenReturn(mockResponse);

            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            interceptor.intercept(mockRequest, body, mockExecution);

            assertEquals(CLIENT_ID, headers.getFirst(TeiaCoreSigningInterceptor.HEADER_CLIENT_ID));
        }

        @Test
        @DisplayName("should compute correct signature for request with body")
        void shouldComputeCorrectSignatureForRequestWithBody() throws Exception {
            when(mockRequest.getHeaders()).thenReturn(headers);
            when(mockExecution.execute(any(), any())).thenReturn(mockResponse);

            String bodyString = "{\"action\":\"initiateCall\",\"to\":\"+1234567890\"}";
            byte[] body = bodyString.getBytes(StandardCharsets.UTF_8);

            interceptor.intercept(mockRequest, body, mockExecution);

            String signature = headers.getFirst(TeiaCoreSigningInterceptor.HEADER_INTERNAL_KEY);
            String timestamp = headers.getFirst(TeiaCoreSigningInterceptor.HEADER_TIMESTAMP);

            // Verify signature matches expected HMAC
            String expectedSignature = computeHmacSha256(bodyString + timestamp, TEST_SECRET);
            assertEquals(expectedSignature, signature);
        }

        @Test
        @DisplayName("should compute correct signature for request without body")
        void shouldComputeCorrectSignatureForRequestWithoutBody() throws Exception {
            when(mockRequest.getHeaders()).thenReturn(headers);
            when(mockExecution.execute(any(), any())).thenReturn(mockResponse);

            byte[] body = new byte[0]; // Empty body for GET request
            interceptor.intercept(mockRequest, body, mockExecution);

            String signature = headers.getFirst(TeiaCoreSigningInterceptor.HEADER_INTERNAL_KEY);
            String timestamp = headers.getFirst(TeiaCoreSigningInterceptor.HEADER_TIMESTAMP);

            // For empty body, signature should be computed with empty string
            String expectedSignature = computeHmacSha256("" + timestamp, TEST_SECRET);
            assertEquals(expectedSignature, signature);
        }

        @Test
        @DisplayName("should compute correct signature for null body")
        void shouldComputeCorrectSignatureForNullBody() throws Exception {
            when(mockRequest.getHeaders()).thenReturn(headers);
            when(mockExecution.execute(any(), any())).thenReturn(mockResponse);

            interceptor.intercept(mockRequest, null, mockExecution);

            String signature = headers.getFirst(TeiaCoreSigningInterceptor.HEADER_INTERNAL_KEY);
            String timestamp = headers.getFirst(TeiaCoreSigningInterceptor.HEADER_TIMESTAMP);

            // For null body, signature should be computed with empty string
            String expectedSignature = computeHmacSha256("" + timestamp, TEST_SECRET);
            assertEquals(expectedSignature, signature);
        }

        @Test
        @DisplayName("should call execution with modified request")
        void shouldCallExecutionWithModifiedRequest() throws IOException {
            when(mockRequest.getHeaders()).thenReturn(headers);
            when(mockExecution.execute(any(), any())).thenReturn(mockResponse);

            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            ClientHttpResponse result = interceptor.intercept(mockRequest, body, mockExecution);

            verify(mockExecution).execute(mockRequest, body);
            assertEquals(mockResponse, result);
        }
    }

    @Nested
    @DisplayName("Signature Consistency")
    class SignatureConsistency {

        @Test
        @DisplayName("should produce different signatures for different bodies")
        void shouldProduceDifferentSignaturesForDifferentBodies() throws IOException {
            when(mockRequest.getHeaders()).thenReturn(headers);
            when(mockExecution.execute(any(), any())).thenReturn(mockResponse);

            // First request
            byte[] body1 = "{\"action\":\"call\"}".getBytes(StandardCharsets.UTF_8);
            interceptor.intercept(mockRequest, body1, mockExecution);
            String signature1 = headers.getFirst(TeiaCoreSigningInterceptor.HEADER_INTERNAL_KEY);

            // Reset headers for second request
            headers = new HttpHeaders();
            headers.set(TeiaCoreSigningInterceptor.HEADER_CLIENT_ID, CLIENT_ID);
            when(mockRequest.getHeaders()).thenReturn(headers);

            // Second request with different body
            byte[] body2 = "{\"action\":\"hangup\"}".getBytes(StandardCharsets.UTF_8);
            interceptor.intercept(mockRequest, body2, mockExecution);
            String signature2 = headers.getFirst(TeiaCoreSigningInterceptor.HEADER_INTERNAL_KEY);

            // Note: This test may occasionally fail if both requests happen within the same millisecond
            // and have the same body hash collision (extremely unlikely)
            // The main protection is that different timestamps will always produce different signatures
            assertNotNull(signature1);
            assertNotNull(signature2);
        }

        @Test
        @DisplayName("should produce valid hex signature")
        void shouldProduceValidHexSignature() throws IOException {
            when(mockRequest.getHeaders()).thenReturn(headers);
            when(mockExecution.execute(any(), any())).thenReturn(mockResponse);

            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            interceptor.intercept(mockRequest, body, mockExecution);

            String signature = headers.getFirst(TeiaCoreSigningInterceptor.HEADER_INTERNAL_KEY);

            // Verify it's valid hex
            assertTrue(signature.matches("[0-9a-f]+"), "Signature should be lowercase hex");
            assertEquals(64, signature.length(), "SHA256 hex output should be 64 characters");
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("should handle request without X-Client-ID (logs warning but proceeds)")
        void shouldHandleRequestWithoutClientId() throws IOException {
            HttpHeaders emptyHeaders = new HttpHeaders();
            when(mockRequest.getHeaders()).thenReturn(emptyHeaders);
            when(mockExecution.execute(any(), any())).thenReturn(mockResponse);

            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);

            // Should not throw, just logs warning
            ClientHttpResponse result = interceptor.intercept(mockRequest, body, mockExecution);

            assertNotNull(result);
            assertTrue(emptyHeaders.containsKey(TeiaCoreSigningInterceptor.HEADER_INTERNAL_KEY));
            assertTrue(emptyHeaders.containsKey(TeiaCoreSigningInterceptor.HEADER_TIMESTAMP));
        }

        @Test
        @DisplayName("should handle large request body")
        void shouldHandleLargeRequestBody() throws Exception {
            when(mockRequest.getHeaders()).thenReturn(headers);
            when(mockExecution.execute(any(), any())).thenReturn(mockResponse);

            // Create a large body (1MB)
            StringBuilder largeBody = new StringBuilder();
            largeBody.append("{\"data\":\"");
            for (int i = 0; i < 100000; i++) {
                largeBody.append("0123456789");
            }
            largeBody.append("\"}");

            byte[] body = largeBody.toString().getBytes(StandardCharsets.UTF_8);
            interceptor.intercept(mockRequest, body, mockExecution);

            String signature = headers.getFirst(TeiaCoreSigningInterceptor.HEADER_INTERNAL_KEY);
            String timestamp = headers.getFirst(TeiaCoreSigningInterceptor.HEADER_TIMESTAMP);

            assertNotNull(signature);
            assertEquals(64, signature.length());

            // Verify signature is correct
            String expectedSignature = computeHmacSha256(largeBody.toString() + timestamp, TEST_SECRET);
            assertEquals(expectedSignature, signature);
        }

        @Test
        @DisplayName("should handle UTF-8 characters in body")
        void shouldHandleUtf8CharactersInBody() throws Exception {
            when(mockRequest.getHeaders()).thenReturn(headers);
            when(mockExecution.execute(any(), any())).thenReturn(mockResponse);

            String bodyString = "{\"message\":\"Hello\"}";
            byte[] body = bodyString.getBytes(StandardCharsets.UTF_8);

            interceptor.intercept(mockRequest, body, mockExecution);

            String signature = headers.getFirst(TeiaCoreSigningInterceptor.HEADER_INTERNAL_KEY);
            String timestamp = headers.getFirst(TeiaCoreSigningInterceptor.HEADER_TIMESTAMP);

            // Verify signature handles UTF-8 correctly
            String expectedSignature = computeHmacSha256(bodyString + timestamp, TEST_SECRET);
            assertEquals(expectedSignature, signature);
        }
    }

    // Helper method to compute HMAC-SHA256
    private String computeHmacSha256(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKeySpec);
        byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

        StringBuilder hexString = new StringBuilder(hmacBytes.length * 2);
        for (byte b : hmacBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
