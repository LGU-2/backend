package com.freshmarket.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/*
 * 서비스가 LocalDateTime.now()/Instant.now() 를 직접 부르지 않고 이 빈을 주입받게 한다 (MNT-2-03).
 * 직접 호출하면 "지금 시각"을 테스트에서 통제할 수 없어, 시각에 걸린 분기(만료, 유효기간)를 결정적으로 검증하기 어렵다.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}