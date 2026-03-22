package com.teia.users.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teia.users.dto.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Security filter for inbound requests from TEIA Core to /internal/** endpoints.
 * Validates HMAC-SHA256 signed requests using the shared INTERNAL_SECRET.
 *
 * Required headers:
 * - X-Internal-Key: HMAC-SHA256(requestBody + timestamp, INTERNAL_SECRET)
 * - X-Timestamp: Unix timestamp in milliseconds
 * - X-Client-ID: Client identifier (TEIA Core instance)
 */
@Component
public class InternalRequestFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(InternalRequestFilter.class);
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final long TIMESTAMP_TOLERANCE_MS = 30_000L;

    private static final String HEADER_INTERNAL_KEY = "X-Internal-Key";
    private static final String HEADER_TIMESTAMP = "X-Timestamp";
    private static final String HEADER_CLIENT_ID = "X-Client-ID";

    @Value("${internal.secret}")
    private String internalSecret;

    private final ObjectMapper objectMapper;

    public InternalRequestFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath(); // /teiaUsers
        String path = uri.startsWith(contextPath) ? uri.substring(contextPath.length()) : uri;
        return !path.startsWith("/internal/") && !path.equals("/internal");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String signature = request.getHeader(HEADER_INTERNAL_KEY);
        String timestamp = request.getHeader(HEADER_TIMESTAMP);
        String clientId = request.getHeader(HEADER_CLIENT_ID);

        if (signature == null || signature.isBlank() ||
            timestamp == null || timestamp.isBlank() ||
            clientId == null || clientId.isBlank()) {
            log.warn("[InternalRequestFilter] Missing required authentication headers from {}", request.getRemoteAddr());
            sendUnauthorized(response, request.getRequestURI(), "Missing required authentication headers");
            return;
        }

        // Validate timestamp freshness
        long timestampMs;
        try {
            timestampMs = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            log.warn("[InternalRequestFilter] Invalid timestamp format: {}", timestamp);
            sendUnauthorized(response, request.getRequestURI(), "Invalid timestamp format");
            return;
        }

        long delta = Math.abs(System.currentTimeMillis() - timestampMs);
        if (delta > TIMESTAMP_TOLERANCE_MS) {
            log.warn("[InternalRequestFilter] Timestamp expired: delta={}ms, threshold={}ms, clientId={}",
                    delta, TIMESTAMP_TOLERANCE_MS, clientId);
            sendUnauthorized(response, request.getRequestURI(), "Request timestamp expired");
            return;
        }

        // Read body bytes and wrap request to allow repeated reads
        byte[] bodyBytes = request.getInputStream().readAllBytes();
        String cachedBody = bodyBytes.length > 0
                ? new String(bodyBytes, StandardCharsets.UTF_8)
                : "";

        String signatureInput = cachedBody + timestamp;

        try {
            String expectedSignature = computeHmacSha256(signatureInput, internalSecret);

            byte[] expectedBytes = hexToBytes(expectedSignature);
            byte[] actualBytes = hexToBytes(signature);

            if (expectedBytes == null || actualBytes == null ||
                !MessageDigest.isEqual(expectedBytes, actualBytes)) {
                log.warn("[InternalRequestFilter] Invalid signature from clientId={}", clientId);
                sendUnauthorized(response, request.getRequestURI(), "Invalid signature");
                return;
            }
        } catch (Exception e) {
            log.error("[InternalRequestFilter] HMAC computation failed: {}", e.getMessage());
            sendUnauthorized(response, request.getRequestURI(), "Signature verification failed");
            return;
        }

        log.info("[InternalRequestFilter] Request authenticated for clientId={}", clientId);

        // Wrap request with a repeatable body so @RequestBody can read it again
        HttpServletRequest repeatableRequest = new RepeatableBodyRequestWrapper(request, bodyBytes);
        filterChain.doFilter(repeatableRequest, response);
    }

    /**
     * Request wrapper that allows the request body to be read multiple times.
     */
    private static class RepeatableBodyRequestWrapper extends HttpServletRequestWrapper {
        private final byte[] body;

        public RepeatableBodyRequestWrapper(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return byteArrayInputStream.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public int read() {
                    return byteArrayInputStream.read();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }

    private void sendUnauthorized(HttpServletResponse response, String path, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse error = new ErrorResponse(401, "Unauthorized", message, path);
        objectMapper.writeValue(response.getOutputStream(), error);
    }

    private static String computeHmacSha256(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance(HMAC_SHA256);
        SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
        mac.init(keySpec);
        byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hmacBytes);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) sb.append('0');
            sb.append(hex);
        }
        return sb.toString();
    }

    /**
     * Converts a hex string to bytes, returning null if the input is malformed.
     */
    private static byte[] hexToBytes(String hex) {
        if (hex == null || hex.length() % 2 != 0) return null;
        int len = hex.length();
        byte[] bytes = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int hi = Character.digit(hex.charAt(i), 16);
            int lo = Character.digit(hex.charAt(i + 1), 16);
            if (hi < 0 || lo < 0) return null;
            bytes[i / 2] = (byte) ((hi << 4) | lo);
        }
        return bytes;
    }
}
