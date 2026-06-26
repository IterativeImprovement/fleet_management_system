package com.siyu.fleet_mgmt_sys.service.external;

import com.siyu.fleet_mgmt_sys.dto.external.OneMapRouteResponseDTO;
import com.siyu.fleet_mgmt_sys.dto.OneMapSearchResponseDTO;
import com.siyu.fleet_mgmt_sys.model.WayPoint;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.client.RestTemplate;

import java.util.List;


/**
 * Connects to the OneMap API to retrieve basic Routing information (Soon to be replaced by LTA Traffic-related calculations)
 *
 */

@Service
public class OneMapService {
    private final RestTemplate restTemplate = new RestTemplate();


    @Value("${onemap.api-key}")
    private String apiKey;


    public OneMapRouteResponseDTO getRoute(String start, String end, String... blockages) {

        WayPoint startWp = new WayPoint(start);
        WayPoint endWp = new WayPoint(end);

        return getOneMapRouteResponseDTO(startWp, endWp);

        // Debug Code to print raw JSON
        // ResponseEntity<String> response = restTemplate.exchange(
        // url,
        // HttpMethod.GET,
        // entity,
        // String.class
        // );
        //
        // System.out.println("Raw JSON: " + response.getBody());
        //
        // System.out.println("Raw status: " + response.getStatusCode());
        // System.out.println("Body: " + response.getBody());
    }

    public OneMapRouteResponseDTO getRoute(WayPoint startWp, WayPoint endWp, String... blockages) {

        return getOneMapRouteResponseDTO(startWp, endWp);
    }

    public List<OneMapSearchResponseDTO.SearchResult> searchLocations(String searchValue) {
        String url = UriComponentsBuilder
                .fromUriString("https://www.onemap.gov.sg/api/common/elastic/search")
                .queryParam("searchVal", searchValue)
                .queryParam("returnGeom", "Y")
                .queryParam("getAddrDetails", "Y")
                .queryParam("pageNum", 1)
                .build()
                .encode()
                .toUriString();

        ResponseEntity<OneMapSearchResponseDTO> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                getStringHttpEntity(),
                OneMapSearchResponseDTO.class);

        OneMapSearchResponseDTO body = response.getBody();
        if (body == null || body.getResults() == null) {
            return List.of();
        }

        return body.getResults();
    }

    @Nullable
    private OneMapRouteResponseDTO getOneMapRouteResponseDTO(WayPoint startWp, WayPoint endWp) {
        String url = "https://www.onemap.gov.sg/api/public/routingsvc/route" +
                "?routeType=drive" +
                "&start=" +
                startWp.getLatitude() +
                "," +
                startWp.getLongitude() +
                "&end=" +
                endWp.getLatitude() +
                "," +
                endWp.getLongitude();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + this.apiKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<OneMapRouteResponseDTO> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                OneMapRouteResponseDTO.class);

        System.out.println("Route retrieved successfully from OneMap API");
        System.out.println("Start WP: " + startWp.getLatitude() + "," + startWp.getLongitude());
        System.out.println("End WP: " + endWp.getLatitude() + "," + endWp.getLongitude());
        return response.getBody(); // raw JSON string
    }

    private static @NonNull HttpEntity<String> getStringHttpEntity() {
        HttpHeaders headers = new HttpHeaders();
        String authToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyX2lkIjoxNDY0MCwiZm9yZXZlciI6ZmFsc2UsImlzcyI6Ik9uZU1hcCIsImlhdCI6MTc4MjEyOTk3MSwibmJmIjoxNzgyMTI5OTcxLCJleHAiOjE3ODIzODkxNzEsImp0aSI6Ijk0NTU1NzhkLWQ4Y2MtNGRjNi1iYzA1LTFkNTlmMDg1MGFiYSJ9.p79248mtPEXxb6Sv3CFm6CcW0Qv_8u1pVoWRLE8UNxIE55b5736ZjXhUKrfn_Pzv5lXOfjIeThMB64gQptaaXzrg8mIKT9TJmHuZtxY0dZoGlvAO2bL6HU3R1Tf2URRQbaSAAYfzRculj6p-T1-GY-QLKkEZUXyslWSzG0rttZWSzsUykiHO3U3dOQBb9Lh73P_-g_Mulu0QkZ1HT30L6FdgUk2s3osufaphMS8s9oR3vrcN9gDxFxAQQ2RBrVOh9plvlDZ2ZdatAibfPM3japncsJGptFckREsYXhwsYj_cLboq8ELllLPAUJNUdcqcam8CqaULrZTf0MY9p_dEcA";
        headers.set("Authorization", "Bearer " + authToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        return entity;
    }
}
