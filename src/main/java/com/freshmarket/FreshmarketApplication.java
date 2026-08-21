package com.freshmarket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// (2026-08-20) KakaoUnlinkRetryScheduler(DI-6-02 아웃박스 재시도)를 위해 @EnableScheduling 추가.
@SpringBootApplication
@EnableScheduling
public class FreshmarketApplication {

    public static void main(String[] args) {
        SpringApplication.run(FreshmarketApplication.class, args);
    }
}
