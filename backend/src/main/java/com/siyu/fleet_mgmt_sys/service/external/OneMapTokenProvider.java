package com.siyu.fleet_mgmt_sys.service.external;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Supplies a valid OneMap access token.
 * <p>
 * OneMap tokens expire after 3 days. When {@code onemap.email} and {@code onemap.password}
 * are set, a new token is requested from OneMap whenever the current one is missing or
 * close to expiry. Otherwise the static {@code onemap.api-key} is used as-is.
 */
@Slf4j
@Component
public class OneMapTokenProvider {

    static final String TOKEN_URL = "https://www.onemap.gov.sg/api/auth/post/getToken";
    /** Refresh this long before the token's exp claim. */
    static final Duration REFRESH_MARGIN = Duration.ofHours(1);
    /** After a failed refresh, wait this long before trying again. */
    static final Duration RETRY_BACKOFF = Duration.ofMinutes(1);

    private static final Pattern EXP_CLAIM = Pattern.compile("\"exp\"\\s*:\\s*(\\d+)");

    private final RestTemplate restTemplate;
    private final String email;
    private final String password;
    private final Clock clock;

    private String token;
    private Instant expiresAt;
    private Instant nextAttemptAt = Instant.MIN;

    @Autowired
    public OneMapTokenProvider(RestTemplate restTemplate,
                               @Value("${onemap.api-key:}") String staticToken,
                               @Value("${onemap.email:}") String email,
                               @Value("${onemap.password:}") String password) {
        this(restTemplate, staticToken, email, password, Clock.systemUTC());
    }

    OneMapTokenProvider(RestTemplate restTemplate, String staticToken,
                        String email, String password, Clock clock) {
        this.restTemplate = restTemplate;
        this.email = email == null ? "" : email.trim();
        this.password = password == null ? "" : password;
        this.clock = clock;
        this.token = staticToken == null || staticToken.isBlank() ? null : staticToken.trim();
        this.expiresAt = this.token == null ? null : readExpiry(this.token);

        if (!canRefresh()) {
            log.info("OneMap credentials not set; using static onemap.api-key without refresh.");
            if (this.expiresAt != null && !this.expiresAt.isAfter(clock.instant())) {
                log.warn("Static OneMap token expired at {}. Set ONEMAP_EMAIL/ONEMAP_PASSWORD "
                        + "or a fresh ONEMAP_API_KEY.", this.expiresAt);
            }
        }
    }

    /** Returns a token, refreshing it first if it is missing or about to expire. */
    public synchronized String getToken() {
        if (canRefresh() && needsRefresh() && !clock.instant().isBefore(nextAttemptAt)) {
            refresh();
        }
        return token == null ? "" : token;
    }

    private boolean canRefresh() {
        return !email.isEmpty() && !password.isEmpty();
    }

    private boolean needsRefresh() {
        return token == null
                || expiresAt == null
                || !clock.instant().plus(REFRESH_MARGIN).isBefore(expiresAt);
    }

    private void refresh() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> body = Map.of("email", email, "password", password);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    TOKEN_URL, new HttpEntity<>(body, headers), Map.class);
            Object accessToken = response == null ? null : response.get("access_token");
            if (!(accessToken instanceof String newToken) || newToken.isBlank()) {
                throw new IllegalStateException("response had no access_token");
            }
            token = newToken;
            expiresAt = readExpiry(newToken);
            nextAttemptAt = Instant.MIN;
            log.info("Refreshed OneMap token; expires at {}", expiresAt);
        } catch (RestClientException | IllegalStateException e) {
            nextAttemptAt = clock.instant().plus(RETRY_BACKOFF);
            log.error("Failed to refresh OneMap token ({}); keeping previous token.", e.getMessage());
        }
    }

    /** Reads the exp claim from a JWT, or null if it can't be parsed. */
    static Instant readExpiry(String jwt) {
        String[] parts = jwt.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        try {
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            Matcher m = EXP_CLAIM.matcher(payload);
            return m.find() ? Instant.ofEpochSecond(Long.parseLong(m.group(1))) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
