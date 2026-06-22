package com.siyu.fleet_mgmt_sys.service.external;

import com.siyu.fleet_mgmt_sys.dto.OneMapRouteResponseDTO;
import com.siyu.fleet_mgmt_sys.model.WayPoint;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;


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

//        Debug Code to print raw JSON
//        ResponseEntity<String> response = restTemplate.exchange(
//                url,
//                HttpMethod.GET,
//                entity,
//                String.class
//        );
//
//        System.out.println("Raw JSON: " + response.getBody());
//
//        System.out.println("Raw status: " + response.getStatusCode());
//        System.out.println("Body: " + response.getBody());
    }

    public OneMapRouteResponseDTO getRoute(WayPoint startWp, WayPoint endWp, String... blockages) {
        return getOneMapRouteResponseDTO(startWp, endWp);
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
                endWp.getLongitude()
                ;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + this.apiKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<OneMapRouteResponseDTO> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                OneMapRouteResponseDTO.class
        );

        System.out.println("Route retrieved successfully from OneMap API");
        System.out.println("Start WP: " + startWp.getLatitude() + "," + startWp.getLongitude());
        System.out.println("End WP: " + endWp.getLatitude() + "," + endWp.getLongitude());
        return response.getBody(); // raw JSON string
    }

}