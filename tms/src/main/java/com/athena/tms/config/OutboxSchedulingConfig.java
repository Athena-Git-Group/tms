package com.athena.tms.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @EnableScheduling 掛在 outbox 自己的 config 而不是 TmsApplication：
 * 排程是本模組（relay＋保留期清理＋冪等簿清理）引入的需求，
 * 不讓模組私有的需求散進全應用共用的啟動類。
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class OutboxSchedulingConfig {
}
