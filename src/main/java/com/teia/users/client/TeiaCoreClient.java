package com.teia.users.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP client for communicating with TEIA Core service.
 * All requests are automatically signed with HMAC authentication.
 */
@Component
public class TeiaCoreClient {

    private static final Logger logger = LoggerFactory.getLogger(TeiaCoreClient.class);

    private final RestTemplate restTemplate;
    private final TeiaCoreClientConfig config;

    public TeiaCoreClient(@Qualifier("teiaCoreRestTemplate") RestTemplate restTemplate,
                          TeiaCoreClientConfig config) {
        this.restTemplate = restTemplate;
        this.config = config;
    }

    /**
     * Sends a POST request to TEIA Core.
     *
     * @param path         The API path (will be appended to base URL)
     * @param clientId     The client ID to include in X-Client-ID header
     * @param requestBody  The request body (will be serialized to JSON)
     * @param responseType The expected response type
     * @return ResponseEntity with the response
     * @throws RestClientException if the request fails
     */
    public <T, R> ResponseEntity<R> post(String path, String clientId, T requestBody, Class<R> responseType) {
        String url = config.getTeiaCoreBaseUrl() + path;
        HttpHeaders headers = createHeaders(clientId);
        HttpEntity<T> entity = new HttpEntity<>(requestBody, headers);

        logger.debug("[TeiaCoreClient] POST {} for client {}", path, clientId);
        return restTemplate.exchange(url, HttpMethod.POST, entity, responseType);
    }

    /**
     * Sends a GET request to TEIA Core.
     *
     * @param path         The API path (will be appended to base URL)
     * @param clientId     The client ID to include in X-Client-ID header
     * @param responseType The expected response type
     * @return ResponseEntity with the response
     * @throws RestClientException if the request fails
     */
    public <R> ResponseEntity<R> get(String path, String clientId, Class<R> responseType) {
        String url = config.getTeiaCoreBaseUrl() + path;
        HttpHeaders headers = createHeaders(clientId);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        logger.debug("[TeiaCoreClient] GET {} for client {}", path, clientId);
        return restTemplate.exchange(url, HttpMethod.GET, entity, responseType);
    }

    /**
     * Sends a PUT request to TEIA Core.
     *
     * @param path         The API path (will be appended to base URL)
     * @param clientId     The client ID to include in X-Client-ID header
     * @param requestBody  The request body (will be serialized to JSON)
     * @param responseType The expected response type
     * @return ResponseEntity with the response
     * @throws RestClientException if the request fails
     */
    public <T, R> ResponseEntity<R> put(String path, String clientId, T requestBody, Class<R> responseType) {
        String url = config.getTeiaCoreBaseUrl() + path;
        HttpHeaders headers = createHeaders(clientId);
        HttpEntity<T> entity = new HttpEntity<>(requestBody, headers);

        logger.debug("[TeiaCoreClient] PUT {} for client {}", path, clientId);
        return restTemplate.exchange(url, HttpMethod.PUT, entity, responseType);
    }

    /**
     * Sends a DELETE request to TEIA Core.
     *
     * @param path         The API path (will be appended to base URL)
     * @param clientId     The client ID to include in X-Client-ID header
     * @param responseType The expected response type
     * @return ResponseEntity with the response
     * @throws RestClientException if the request fails
     */
    public <R> ResponseEntity<R> delete(String path, String clientId, Class<R> responseType) {
        String url = config.getTeiaCoreBaseUrl() + path;
        HttpHeaders headers = createHeaders(clientId);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        logger.debug("[TeiaCoreClient] DELETE {} for client {}", path, clientId);
        return restTemplate.exchange(url, HttpMethod.DELETE, entity, responseType);
    }

    /**
     * Creates HTTP headers with JSON content type and client ID.
     * The signing interceptor will add X-Internal-Key and X-Timestamp.
     */
    private HttpHeaders createHeaders(String clientId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(TeiaCoreSigningInterceptor.HEADER_CLIENT_ID, clientId);
        return headers;
    }
}
