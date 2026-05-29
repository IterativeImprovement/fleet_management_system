package com.siyu.fleet_mgmt_sys.service.external;

import com.siyu.fleet_mgmt_sys.dto.OneMapRouteResponseDTO;
import com.siyu.fleet_mgmt_sys.model.WayPoint;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class OneMapService {
    private final RestTemplate restTemplate = new RestTemplate();

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
        String authToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyX2lkIjoxNDE1MSwiZm9yZXZlciI6ZmFsc2UsImlzcyI6Ik9uZU1hcCIsImlhdCI6MTc4MDA0Mzc2MywibmJmIjoxNzgwMDQzNzYzLCJleHAiOjE3ODAzMDI5NjMsImp0aSI6IjExYTQ1ODRlLTM3MWItNGU3NC04NWY3LWM5ZGVmOTU3NTMzMyJ9.jOe0l98e1B27CG1LwAb36cTYVmDjGhbsLYf97fXwqIJZzuBrrU1z9TSXXIZ8TD9xNLjXQ6rmJs7sCsrmX6VjC4fG_404JG53d1gSWUZH3WYU7v95BglzQCl7MwSXOh1hZ6O38XTKqp8l50IO-xwhKVbCyi3ozi1cjVOiKiGyxaBNLhstEkqmoBI1q3YpXyKJ-kiekDoReFVoIGwvDINymPonqaJiwwu2nyEgsN6QiOzkrHBSr2aOlFFCe-bz0iIaGrOHdyFwogPSv7vx2HpB9MO6pYH73AOdHimABQwOkIG14Nrp55IVA6UFg4nKXntXYgnonwuygFlmvlttwUh3uA";
        headers.set("Authorization", "Bearer " + authToken);
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