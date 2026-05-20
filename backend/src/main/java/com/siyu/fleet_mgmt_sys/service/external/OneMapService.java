package com.siyu.fleet_mgmt_sys.service.external;

import com.siyu.fleet_mgmt_sys.dto.OneMapRouteResponseDTO;
import com.siyu.fleet_mgmt_sys.model.WayPoint;
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

        String url = "https://www.onemap.gov.sg/api/public/routingsvc/route" +
                "?date=06-06-2026" +
                "&mode=TRANSIT" +
                "&maxWalkDistance=0" + // no walking!
                "&numItineraries=3" + // number of routes returned
                "&routeType=drive" +
                "&time=11:19:47" +
                "&start=" +
                String.valueOf(startWp.getLatitude()) +
                "," +
                String.valueOf(startWp.getLongitude()) +
                "&end=" +
                String.valueOf(endWp.getLatitude()) +
                "," +
                String.valueOf(endWp.getLongitude())
                ;

        HttpHeaders headers = new HttpHeaders();
        String authToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyX2lkIjoxNDE2MCwiZm9yZXZlciI6ZmFsc2UsImlzcyI6Ik9uZU1hcCIsImlhdCI6MTc3OTE2MjUxNywibmJmIjoxNzc5MTYyNTE3LCJleHAiOjE3Nzk0MjE3MTcsImp0aSI6ImUwNDg2NTA0LTlhM2QtNDYzNS05NjRiLTdjZjc4MTlmNmMwYiJ9.wwHNQwVn63sN0rPf_okFS4x33X5X6mjeVPOAANAodDQ_4iv9MHwWYYQf1LJvn_D0wzGe0POyIbCSzbczQ83PtcewGxmT8Fm42O4dvfsjepHXz0d_eaXRK5ReehzDmqub8tpsy92mSBKcx0245DEXBPYid9u_yZ08AJn4uC_3AqHjWI9UHRxPLdHOKM66ODodW3c13_GIV0j_yd63UZkSO9-qINcldRMlHGjYXkBz53xw_-AY-f5CCaOsW-EB9IuzcxyBMkeu-7gEGQE4-ZtR9_gawaXTJsrcPmV8d71GNfzdszgFDO_ClEl5GadPVAdiuosztOcAnEUkQmUq8JW1iw";
        headers.set("Authorization", authToken);

        HttpEntity<String> entity = new HttpEntity<>(headers);
//
        ResponseEntity<OneMapRouteResponseDTO> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                OneMapRouteResponseDTO.class
        );

       return response.getBody(); // raw JSON string

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
}