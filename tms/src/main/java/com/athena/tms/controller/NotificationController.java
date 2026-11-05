package com.athena.tms.controller;

import com.athena.tms.dto.NotificationResponse;
import com.athena.tms.service.NotificationService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通知中心 API，對應 contracts 的 listNotifications（GET /notifications）、
 * markNotificationRead（POST /notifications/{id}/read）、
 * markAllNotificationsRead（POST /notifications/read-all）。業務規則（誰能標、
 * 已讀冪等）都在 {@link NotificationService}，這裡只做路徑與請求轉接。
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<NotificationResponse> list() {
        return notificationService.list();
    }

    @PostMapping("/{id}/read")
    public NotificationResponse markRead(@PathVariable UUID id) {
        return notificationService.markRead(id);
    }

    @PostMapping("/read-all")
    public List<NotificationResponse> markAllRead() {
        return notificationService.markAllRead();
    }
}
