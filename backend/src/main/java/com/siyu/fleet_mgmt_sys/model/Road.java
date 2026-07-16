package com.siyu.fleet_mgmt_sys.model;

import com.siyu.fleet_mgmt_sys.model.enums.RoadStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "roads")
@Getter
@Setter
@NoArgsConstructor
public class Road {
    @Id
    private String id;

    private String roadName;
    private String roadCategory;

    private double startLat;
    private double startLon;
    private double endLat;
    private double endLon;

    private RoadStatus status = RoadStatus.UNOBSTRUCTED; // default value


    @OneToOne(mappedBy = "road", cascade = CascadeType.ALL)
    private RoadSpeedBand roadSpeedBand;
}

/* Example Json (Speed data does not need to be stored, so omitted)

    {
        "EndLat": 1.292044,
        "EndLon": 103.838331,
        "LinkID": "2",
        "MaximumSpeed": "39",
        "MinimumSpeed": "30",
        "RoadCategory": "5",
        "RoadName": "NARAYANAN CHETTY ROAD",
        "SpeedBand": 4,
        "StartLat": 1.29206,
        "StartLon": 103.838305
    },


 */
