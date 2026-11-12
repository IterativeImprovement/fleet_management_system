package com.siyu.fleet_mgmt_sys.service.external;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OneMapTokenProviderTest {

    private static final Instant NOW = Instant.parse("2026-11-12T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static String jwt(Instant exp) {
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        String header = enc.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = enc.encodeToString(("{\"exp\":" + exp.getEpochSecond() + "}").getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".sig";
    }

    @SuppressWarnings("unchecked")
    private static void stubTokenResponse(RestTemplate rt, Object response) {
        when(rt.postForObject(eq(OneMapTokenProvider.TOKEN_URL), any(HttpEntity.class), eq(Map.class)))
                .thenReturn((Map<String, Object>) response);
    }

    @Test
    void readsExpiryFromJwt() {
        Instant exp = Instant.ofEpochSecond(1784984819);
        assertEquals(exp, OneMapTokenProvider.readExpiry(jwt(exp)));
        assertNull(OneMapTokenProvider.readExpiry("not-a-jwt"));
    }

    @Test
    void usesStaticTokenWithoutCredentials() {
        RestTemplate rt = mock(RestTemplate.class);
        String expired = jwt(NOW.minusSeconds(60));
        OneMapTokenProvider p = new OneMapTokenProvider(rt, expired, "", "", CLOCK);

        assertEquals(expired, p.getToken());
        verifyNoInteractions(rt);
    }

    @Test
    void keepsValidTokenWithoutCallingOneMap() {
        RestTemplate rt = mock(RestTemplate.class);
        String valid = jwt(NOW.plusSeconds(86400));
        OneMapTokenProvider p = new OneMapTokenProvider(rt, valid, "a@b.c", "pw", CLOCK);

        assertEquals(valid, p.getToken());
        verifyNoInteractions(rt);
    }

    @Test
    void refreshesExpiringTokenOnceAndCachesIt() {
        RestTemplate rt = mock(RestTemplate.class);
        String fresh = jwt(NOW.plusSeconds(3 * 86400));
        stubTokenResponse(rt, Map.of("access_token", fresh));
        OneMapTokenProvider p = new OneMapTokenProvider(rt, jwt(NOW.plusSeconds(600)), "a@b.c", "pw", CLOCK);

        assertEquals(fresh, p.getToken());
        assertEquals(fresh, p.getToken());
        verify(rt, times(1)).postForObject(eq(OneMapTokenProvider.TOKEN_URL), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void keepsOldTokenAndBacksOffWhenRefreshFails() {
        RestTemplate rt = mock(RestTemplate.class);
        when(rt.postForObject(eq(OneMapTokenProvider.TOKEN_URL), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RestClientException("boom"));
        String old = jwt(NOW.minusSeconds(60));
        OneMapTokenProvider p = new OneMapTokenProvider(rt, old, "a@b.c", "pw", CLOCK);

        assertEquals(old, p.getToken());
        assertEquals(old, p.getToken());
        verify(rt, times(1)).postForObject(eq(OneMapTokenProvider.TOKEN_URL), any(HttpEntity.class), eq(Map.class));
    }
}
