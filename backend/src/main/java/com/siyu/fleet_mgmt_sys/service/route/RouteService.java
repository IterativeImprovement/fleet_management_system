package com.siyu.fleet_mgmt_sys.service.route;


// TODO: Integrate LTA API

// At the moment, Route Service simply converts the OneMapResponseDTO into a normal Route object
// In the future, 
import com.siyu.fleet_mgmt_sys.dto.OneMapRouteResponseDTO;
import com.siyu.fleet_mgmt_sys.model.Route;
import com.siyu.fleet_mgmt_sys.model.WayPoint;
import com.siyu.fleet_mgmt_sys.service.external.OneMapService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class RouteService {
    private final OneMapService oneMapService;

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
}
