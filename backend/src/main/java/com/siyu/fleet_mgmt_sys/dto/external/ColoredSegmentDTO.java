package com.siyu.fleet_mgmt_sys.dto.external;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

@Data
@AllArgsConstructor
public class ColoredSegmentDTO {

    private List<List<Double>> coordinates; // example [[1,2], [2,3]]
    private String color; // example "#22c55e"
    private int speedBand;
}