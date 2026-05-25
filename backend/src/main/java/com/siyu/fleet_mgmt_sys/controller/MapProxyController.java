package com.siyu.fleet_mgmt_sys.controller;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/map")
public class MapProxyController {

    private final RestTemplate restTemplate = new RestTemplate();

    @GetMapping("/tiles/{z}/{x}/{y}.png")
    public ResponseEntity<byte[]> proxyTile(
            @PathVariable int z,
            @PathVariable int x,
            @PathVariable int y) {

        String url = String.format(
                "https://www.onemap.gov.sg/maps/tiles/Default/%d/%d/%d.png",
                z, x, y);

        ResponseEntity<byte[]> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                byte[].class
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        return new ResponseEntity<>(
                response.getBody(),
                headers,
                response.getStatusCode()
        );
    }
}