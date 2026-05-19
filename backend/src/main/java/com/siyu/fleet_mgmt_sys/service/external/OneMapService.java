//package com.siyu.fleet_mgmt_sys.service.external;
//
//import org.springframework.http.HttpEntity;
//import org.springframework.http.HttpHeaders;
//import org.springframework.http.HttpMethod;
//import org.springframework.http.ResponseEntity;
//import org.springframework.stereotype.Service;
//import org.springframework.web.client.RestTemplate;
//
//@Service
//public class OneMapService {
//    private final RestTemplate restTemplate = new RestTemplate();
//
//    public String getRoute() {
//        String url = "https://www.onemap.gov.sg/api/public/routingsvc/route" +
//                "?date=11-10-2025" +
//                "&mode=TRANSIT" +
//                "&maxWalkDistance=1000" +
//                "&numItineraries=3" +
//                "&routeType=drive" +
//                "&time=11%3A19%3A47" +
//                "&start=1.3081592%2C103.8551479" +
//                "&end=1.2739864%2C103.8012642";
//
//        HttpHeaders headers = new HttpHeaders();
//        String authToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyX2lkIjoxNDE2MCwiZm9yZXZlciI6ZmFsc2UsImlzcyI6Ik9uZU1hcCIsImlhdCI6MTc3OTE2MjUxNywibmJmIjoxNzc5MTYyNTE3LCJleHAiOjE3Nzk0MjE3MTcsImp0aSI6ImUwNDg2NTA0LTlhM2QtNDYzNS05NjRiLTdjZjc4MTlmNmMwYiJ9.wwHNQwVn63sN0rPf_okFS4x33X5X6mjeVPOAANAodDQ_4iv9MHwWYYQf1LJvn_D0wzGe0POyIbCSzbczQ83PtcewGxmT8Fm42O4dvfsjepHXz0d_eaXRK5ReehzDmqub8tpsy92mSBKcx0245DEXBPYid9u_yZ08AJn4uC_3AqHjWI9UHRxPLdHOKM66ODodW3c13_GIV0j_yd63UZkSO9-qINcldRMlHGjYXkBz53xw_-AY-f5CCaOsW-EB9IuzcxyBMkeu-7gEGQE4-ZtR9_gawaXTJsrcPmV8d71GNfzdszgFDO_ClEl5GadPVAdiuosztOcAnEUkQmUq8JW1iw";
//        headers.set("Authorization", authToken);
//
//        HttpEntity<String> entity = new HttpEntity<>(headers);
//
//        ResponseEntity<String> response = restTemplate.exchange(
//                url,
//                HttpMethod.GET,
//                entity,
//                String.class
//        );
//
//        return response.getBody(); // raw JSON string
//    }
//}