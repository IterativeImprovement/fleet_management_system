package com.siyu.fleet_mgmt_sys;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FleetMgmtSysApplication {
    public static void main(String[] args) {
        SpringApplication.run(FleetMgmtSysApplication.class, args);
    }
}