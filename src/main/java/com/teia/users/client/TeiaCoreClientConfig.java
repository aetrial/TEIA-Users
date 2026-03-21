package com.teia.users.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for TEIA Core HTTP client.
 * Provides a pre-configured RestTemplate that automatically signs all requests
 * with the internal HMAC authentication headers.
 */
@Configuration
public class TeiaCoreClientConfig {

    private static final Logger logger = LoggerFactory.getLogger(TeiaCoreClientConfig.class);

    @Value("${teia.core.internal-secret}")
    private String internalSecret;

    @Value("${teia.core.base-url:http://localhost:8000}")
    private String teiaCoreBaseUrl;

    @Value("${teia.core.connect-timeout-ms:5000}")
    private int connectTimeout;

    @Value("${teia.core.read-timeout-ms:30000}")
    private int readTimeout;

    /**
     * Creates a RestTemplate configured with HMAC signing for TEIA Core requests.
     * <p>
     * Usage:
     * <pre>
     * // Set X-Client-ID header before making the request
     * HttpHeaders headers = new HttpHeaders();
     * headers.set("X-Client-ID", clientId);
     * HttpEntity&lt;RequestBody&gt; entity = new HttpEntity&lt;&gt;(body, headers);
     * restTemplate.exchange(url, HttpMethod.POST, entity, ResponseType.class);
     * </pre>
     * The interceptor will automatically add X-Internal-Key and X-Timestamp headers.
     */
    @Bean(name = "teiaCoreRestTemplate")
    public RestTemplate teiaCoreRestTemplate() {
        logger.info("[TeiaCoreClient] Initializing TEIA Core REST client with base URL: {}", teiaCoreBaseUrl);

        // Use buffering factory to allow reading request body multiple times
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);

        RestTemplate restTemplate = new RestTemplate(new BufferingClientHttpRequestFactory(factory));

        // Add the signing interceptor
        List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>(restTemplate.getInterceptors());
        interceptors.add(new TeiaCoreSigningInterceptor(internalSecret));
        restTemplate.setInterceptors(interceptors);

        return restTemplate;
    }

    /**
     * Returns the configured base URL for TEIA Core.
     */
    public String getTeiaCoreBaseUrl() {
        return teiaCoreBaseUrl;
    }
}
