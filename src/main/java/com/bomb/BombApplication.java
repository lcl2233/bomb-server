package com.bomb;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.bomb.module.**.mapper")
@EnableScheduling
public class BombApplication {

    public static void main(String[] args) {
        SpringApplication.run(BombApplication.class, args);
    }
}
