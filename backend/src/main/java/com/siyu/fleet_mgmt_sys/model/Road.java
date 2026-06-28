package com.siyu.fleet_mgmt_sys.model;

import com.siyu.fleet_mgmt_sys.model.enums.RoadStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
    private Long id;

    private String roadName;
    private String roadCategory;

    private double startLat;
    private double startLon;
    private double endLat;
    private double endLon;

    private RoadStatus status = RoadStatus.UNOBSTRUCTED; // default value
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
