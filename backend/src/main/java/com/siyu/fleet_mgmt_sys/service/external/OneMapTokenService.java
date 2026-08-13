package com.siyu.fleet_mgmt_sys.service.external;

import com.siyu.fleet_mgmt_sys.dto.external.OneMapAuthResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;

/**
 * Supplies the OneMap bearer token.
 *
 * If an email and password are present in the environment, this logs in to
 * OneMap and caches the returned token until shortly before it expires, then
 * logs in again. Otherwise it falls back to a static token supplied through
 * ONEMAP_API_KEY, which is the manual path.
 *
 * Credentials are only ever read from environment variables. Do not put real
 * values in application.properties or anywhere else that gets committed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OneMapTokenService {

    private static final String AUTH_URL = "https://www.onemap.gov.sg/api/auth/post/getToken";

    // refresh a bit early (before 3 days)
    private static final long REFRESH_MARGIN_SECONDS = 3600;

    private final RestTemplate restTemplate;

    @Value("${onemap.email:}")
    private String email;

    @Value("${onemap.password:}")
    private String password;

    @Value("${onemap.api-key:}")
    private String staticToken;

    private volatile String cachedToken;
    private volatile Instant expiresAt;

    public String getToken() {
        if (!hasCredentials()) {
            return staticToken;
        }
        if (needsRefresh()) {
            refresh();
        }
        return cachedToken != null ? cachedToken : staticToken;
    }

    private boolean hasCredentials() {
        return email != null && !email.isBlank()
                && password != null && !password.isBlank();
    }

    private boolean needsRefresh() {
        return cachedToken == null
                || expiresAt == null
                || Instant.now().isAfter(expiresAt.minusSeconds(REFRESH_MARGIN_SECONDS));
    }

    private synchronized void refresh() {
        if (!needsRefresh()) {
            return;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(
                Map.of("email", email, "password", password), headers);

        try {
            OneMapAuthResponseDTO body = restTemplate.postForObject(
                    AUTH_URL, request, OneMapAuthResponseDTO.class);

            if (body == null || body.getAccessToken() == null) {
                log.error("OneMap login returned no token, keeping the previous one");
                return;
            }

            cachedToken = body.getAccessToken();
            expiresAt = parseExpiry(body.getExpiryTimestamp());
            log.info("Refreshed OneMap token, expires at {}", expiresAt);

        } catch (RestClientException e) {
            // keep serving the old token if there is one
            log.error("OneMap login failed: {}", e.getMessage());
        }
    }

    private Instant parseExpiry(String raw) {
        try {
            return Instant.ofEpochSecond(Long.parseLong(raw.trim()));
        } catch (NumberFormatException | NullPointerException e) {
            log.warn("Could not read OneMap token expiry, assuming 3 days");
            return Instant.now().plusSeconds(3 * 24 * 3600);
        }
    }
}
