package com.athena.tms.dto;

/**
 * tasksRequeue（主管附交代退池）的請求本體。note 是接手進度的交代，會寫進
 * tasks.requeue_note 給下一位接手的人看。
 */
public record RequeueRequest(String note) {}
