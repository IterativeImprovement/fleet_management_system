package com.siyu.fleet_mgmt_sys.service.route;

// TODO: Integrate LTA API

// At the moment, Route Service simply converts the OneMapResponseDTO into a normal Route object
// In the future, 
import com.siyu.fleet_mgmt_sys.dto.external.ColoredSegmentDTO;
import com.siyu.fleet_mgmt_sys.dto.external.LtaTrafficSpeedBandResponseDTO;
import com.siyu.fleet_mgmt_sys.dto.external.OneMapRouteResponseDTO;
import com.siyu.fleet_mgmt_sys.model.Route;
import com.siyu.fleet_mgmt_sys.model.WayPoint;
import com.siyu.fleet_mgmt_sys.service.external.OneMapService;
import com.siyu.fleet_mgmt_sys.service.external.LTAService;
import com.siyu.fleet_mgmt_sys.util.PolylineDecoder;
import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class RouteService {
    private final OneMapService oneMapService;
    private final LTAService LTAService;

    // this method is only used by the controller
    public OneMapRouteResponseDTO getRouteForController(String start, String end) {
        return oneMapService.getRoute(start, end);
    }

    // this method is used internally for getting Route object
    public Route getRoute(WayPoint start, WayPoint end) {
        OneMapRouteResponseDTO oneMapRouteResponseDTO = oneMapService.getRoute(start, end);
        Route resultRoute = new Route();
        resultRoute.setRouteGeo(oneMapRouteResponseDTO.getRouteGeometry());
        resultRoute.setTotalDistance(oneMapRouteResponseDTO.getRouteSummary().getTotalDistance());
        return resultRoute;
    }

    public List<ColoredSegmentDTO> getColoredRoute(String start, String end) {
        // get the route from OneMap and decode it into GPS coordinates
        OneMapRouteResponseDTO routeResponse = oneMapService.getRoute(start, end);
        List<double[]> coords = PolylineDecoder.decode(routeResponse.getRouteGeometry());

        // fetch all LTA traffic speed bands
        List<LtaTrafficSpeedBandResponseDTO> bands = LTAService.getAllSpeedBands();

        // for each route coordinate, find the nearest LTA segment and get its color
        List<String> colors = new ArrayList<>();
        List<Integer> speedBands = new ArrayList<>();
        for (double[] coord : coords) {
            LtaTrafficSpeedBandResponseDTO nearest = findNearestBand(coord[0], coord[1], bands);
            if (nearest != null) {
                colors.add(bandToColor(nearest.getSpeedBand()));
                speedBands.add(nearest.getSpeedBand());
            } else {
                colors.add("#64748b"); // grey if no data
                speedBands.add(0);
            }
        }

        // group consecutive same-color points into one segment
        return groupIntoSegments(coords, colors, speedBands);
    }

    private List<ColoredSegmentDTO> groupIntoSegments(
            List<double[]> coords, List<String> colors, List<Integer> speedBands) {

        List<ColoredSegmentDTO> segments = new ArrayList<>();
        int i = 0;

        while (i < coords.size() - 1) {
            String currentColor = colors.get(i);
            int currentBand = speedBands.get(i);
            List<List<Double>> segCoords = new ArrayList<>();
            segCoords.add(List.of(coords.get(i)[0], coords.get(i)[1]));

            // keep adding points while the color stays the same
            while (i < coords.size() - 1 && colors.get(i + 1).equals(currentColor)) {
                i++;
                segCoords.add(List.of(coords.get(i)[0], coords.get(i)[1]));
            }

            // Add the next point to close the segment (avoid gaps between segments)
            if (i + 1 < coords.size()) {
                segCoords.add(List.of(coords.get(i + 1)[0], coords.get(i + 1)[1]));
            }

            segments.add(new ColoredSegmentDTO(segCoords, currentColor, currentBand));
            i++;
        }

        return segments;
    }

    private LtaTrafficSpeedBandResponseDTO findNearestBand(
            double lat, double lon, List<LtaTrafficSpeedBandResponseDTO> bands) {

        LtaTrafficSpeedBandResponseDTO nearest = null;
        double minDist = Double.MAX_VALUE;

        for (LtaTrafficSpeedBandResponseDTO band : bands) {
            // Use the midpoint of the LTA segment for comparison
            double midLat = (band.getStartLat() + band.getEndLat()) / 2;
            double midLon = (band.getStartLon() + band.getEndLon()) / 2;
            double dist = haversineDistance(lat, lon, midLat, midLon);
            if (dist < minDist) {
                minDist = dist;
                nearest = band;
            }
        }

        return nearest;
    }

    private double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6371000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private String bandToColor(int speedBand) {
        // Band 1 is >90 km/h, Band 8 is <10 km/h
        if (speedBand <= 3)
            return "#22c55e"; // green
        if (speedBand <= 5)
            return "#f97316"; // orange
        return "#ef4444"; // red
    }

}
