package com.siyu.fleet_mgmt_sys.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "roadspeedbands")
@Getter
@Setter
@NoArgsConstructor
public class RoadSpeedBand {
    @Id
    private String id;        // shared PK with Road

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "id")
    private Road road;

    private Integer speedBand;

}
