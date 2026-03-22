package com.teia.users.security;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal test-only controller that exposes /internal/test so that
 * InternalRequestFilterTest can exercise the full security filter chain
 * end-to-end via MockMvc without adding a production endpoint.
 *
 * Spring Boot picks this up via component scan because it lives in
 * src/test/java, so it is only present on the test classpath.
 */
@RestController
public class InternalTestController {

    @PostMapping("/internal/test")
    public ResponseEntity<String> test(@RequestBody(required = false) String body) {
        return ResponseEntity.ok("ok");
    }
}
