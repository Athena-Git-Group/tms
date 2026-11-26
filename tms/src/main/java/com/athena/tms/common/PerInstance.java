package com.athena.tms.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 標在每個 instance 都必須各自執行的排程方法上，例如替自己手上的連線送心跳。
 * 沒標的排程方法要逐個租戶隔離執行並加跨 instance 鎖，由測試檢查。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PerInstance {
}
