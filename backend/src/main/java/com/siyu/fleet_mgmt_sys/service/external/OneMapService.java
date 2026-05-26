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
                "?date=06-06-2026" +
                "&mode=TRANSIT" +
                "&maxWalkDistance=0" + // no walking!
                "&numItineraries=3" + // number of routes returned
                "&routeType=drive" +
                "&time=11:19:47" +
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
        String authToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyX2lkIjoxNDE1MSwiZm9yZXZlciI6ZmFsc2UsImlzcyI6Ik9uZU1hcCIsImlhdCI6MTc3OTY5OTMwNywibmJmIjoxNzc5Njk5MzA3LCJleHAiOjE3Nzk5NTg1MDcsImp0aSI6ImVjNjcxY2NkLTkyZTQtNGUzOS1iNGNiLTc3ZGUxZGU1OTVmYyJ9.4Vrm_hun2gWAcksgstdm4KGjWrLP7vhKwTTSG5iaXcXqP8xf19J-AbigIcXV3OugonULx8AMFdjQUMYo80BVg55ryu567FZ0BzaDxW56MnLHsex-cfWT7GtGJxuT11b0tn3wqjU_WxuBgXAOOLYrzcMvFtXUmTAgw_3L_xPCYbK7n72DcoNLW5-ywuhXRFhILNKavsJ7jAOn8Ly2XiandErQho5j4XJWZRkF7wjzU4Np80xqmsQuEQafX_kBnvIDjgtdZ5Sfcac4evZuIKe0IW3pemKq4MoZBbxKj_kl7Ni-qiBMfOOW0HsYR3rOED2UeAp0JpZyJ-vXBi5_QPNLhw";
        headers.set("Authorization", authToken);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<OneMapRouteResponseDTO> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                OneMapRouteResponseDTO.class
        );

        System.out.println("Route retrieved successfully from OneMap API");

        return response.getBody(); // raw JSON string
    }
}