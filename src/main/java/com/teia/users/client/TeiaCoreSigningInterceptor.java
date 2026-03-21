package com.teia.users.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * HTTP client interceptor that signs outbound requests to TEIA Core.
 * Adds HMAC-SHA256 signature headers for internal service authentication.
 *
 * Required headers added:
 * - X-Internal-Key: HMAC-SHA256(requestBody + timestamp, INTERNAL_SECRET)
 * - X-Timestamp: Unix timestamp in milliseconds
 * - X-Client-ID: Client identifier
 */
public class TeiaCoreSigningInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(TeiaCoreSigningInterceptor.class);
    private static final String HMAC_SHA256 = "HmacSHA256";

    public static final String HEADER_INTERNAL_KEY = "X-Internal-Key";
    public static final String HEADER_TIMESTAMP = "X-Timestamp";
    public static final String HEADER_CLIENT_ID = "X-Client-ID";

    private final String internalSecret;

    /**
     * Creates a new signing interceptor.
     *
     * @param internalSecret The shared secret for HMAC signing (must match TEIA Core's INTERNAL_SECRET)
     */
    public TeiaCoreSigningInterceptor(String internalSecret) {
        if (internalSecret == null || internalSecret.isBlank()) {
            throw new IllegalArgumentException("INTERNAL_SECRET must not be null or blank");
        }
        this.internalSecret = internalSecret;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                         ClientHttpRequestExecution execution) throws IOException {

        String clientId = request.getHeaders().getFirst(HEADER_CLIENT_ID);
        if (clientId == null || clientId.isBlank()) {
            logger.warn("[TeiaCoreClient] X-Client-ID header not set on request to TEIA Core. " +
                       "Caller must set this header before sending the request.");
        }

        String timestamp = String.valueOf(System.currentTimeMillis());
        String bodyString = body != null && body.length > 0 ? new String(body, StandardCharsets.UTF_8) : "";
        String signatureInput = bodyString + timestamp;

        try {
            String signature = computeHmacSha256(signatureInput, internalSecret);

            request.getHeaders().set(HEADER_INTERNAL_KEY, signature);
            request.getHeaders().set(HEADER_TIMESTAMP, timestamp);

            logger.debug("[TeiaCoreClient] Request signed: timestamp={}, clientId={}",
                        timestamp, clientId);

        } catch (Exception e) {
            logger.error("[TeiaCoreClient] Failed to sign request: {}", e.getMessage());
            throw new IOException("Failed to sign request to TEIA Core", e);
        }

        return execution.execute(request, body);
    }

    /**
     * Computes HMAC-SHA256 signature as a hex string.
     *
     * @param data   The data to sign
     * @param secret The secret key
     * @return Hex-encoded HMAC-SHA256 signature
     */
    static String computeHmacSha256(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance(HMAC_SHA256);
        SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
        mac.init(secretKeySpec);
        byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hmacBytes);
    }

    /**
     * Converts byte array to lowercase hex string.
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
