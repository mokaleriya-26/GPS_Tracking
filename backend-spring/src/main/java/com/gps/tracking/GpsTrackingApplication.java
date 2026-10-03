package com.gps.tracking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class GpsTrackingApplication {

    public static void main(String[] args) {
        SpringApplication.run(GpsTrackingApplication.class, args);
    }
}
