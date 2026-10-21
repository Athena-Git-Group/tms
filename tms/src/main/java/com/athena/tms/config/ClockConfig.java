package com.athena.tms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 系統時鐘的唯一出處。業務判定（訂單是否為當前、session 是否過期）一律
 * 透過注入的 Clock 取「現在」，正式環境就是系統時鐘；測試環境可改綁
 * 固定時鐘，讓固定日期的測試資料不隨真實時間流逝而失效。
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
