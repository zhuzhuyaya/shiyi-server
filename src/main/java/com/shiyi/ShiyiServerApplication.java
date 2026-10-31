package com.shiyi;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.shiyi.module.*.mapper")
public class ShiyiServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShiyiServerApplication.class, args);
    }

}
