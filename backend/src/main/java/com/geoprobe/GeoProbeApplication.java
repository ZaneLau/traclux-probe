package com.geoprobe;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.geoprobe.mapper")
public class GeoProbeApplication {

    public static void main(String[] args) {
        SpringApplication.run(GeoProbeApplication.class, args);
    }
}
