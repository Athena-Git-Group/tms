package com.athena.tms.push;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.ErrorCode;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.ApsAlert;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 純 notification 訊息在 Android 背景時由系統接手顯示，App 收不到內容，所以一律送 data message。
 * iOS 的 data message 預設不會在鎖屏顯示，所以另外附 APNS 的 alert 與 content-available。
 */
public class FcmPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(FcmPushSender.class);
    private static final String APP_NAME = "tms-push";

    private final FirebaseMessaging messaging;

    public FcmPushSender(Path credentialsPath) {
        this(initializeMessaging(credentialsPath));
    }

    FcmPushSender(FirebaseMessaging messaging) {
        this.messaging = messaging;
    }

    private static FirebaseMessaging initializeMessaging(Path credentialsPath) {
        try (InputStream in = Files.newInputStream(credentialsPath)) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(in))
                    .build();
            FirebaseApp app = FirebaseApp.getApps().stream()
                    .filter(existing -> APP_NAME.equals(existing.getName()))
                    .findFirst()
                    .orElseGet(() -> FirebaseApp.initializeApp(options, APP_NAME));
            log.info("FCM 推播已啟用，憑證檔：{}", credentialsPath);
            return FirebaseMessaging.getInstance(app);
        } catch (IOException e) {
            throw new UncheckedIOException("讀取 FCM 憑證檔失敗：" + credentialsPath, e);
        }
    }

    @Override
    public PushSendResult send(PushMessage message, PushDeviceAddress device) {
        try {
            messaging.send(buildMessage(message, device));
            return PushSendResult.sent();
        } catch (FirebaseMessagingException e) {
            return classify(e);
        }
    }

    private static Message buildMessage(PushMessage message, PushDeviceAddress device) {
        Message.Builder builder = Message.builder()
                .setToken(device.pushToken())
                .putData("event", message.event())
                .putData("title", message.title())
                .putData("target", message.targetJson())
                .putData("idempotencyKey", message.idempotencyKey());
        if (message.body() != null) {
            builder.putData("body", message.body());
        }
        builder.setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build());
        if (device.isIos()) {
            ApsAlert.Builder alert = ApsAlert.builder().setTitle(message.title());
            if (message.body() != null) {
                alert.setBody(message.body());
            }
            builder.setApnsConfig(ApnsConfig.builder()
                    .putHeader("apns-priority", "10")
                    .putHeader("apns-push-type", "alert")
                    .setAps(Aps.builder()
                            .setAlert(alert.build())
                            .setSound("default")
                            .setContentAvailable(true)
                            .build())
                    .build());
        }
        return builder.build();
    }

    private static PushSendResult classify(FirebaseMessagingException e) {
        MessagingErrorCode messagingCode = e.getMessagingErrorCode();
        ErrorCode code = e.getErrorCode();
        String description = (messagingCode != null ? messagingCode : code) + ": " + e.getMessage();
        if (messagingCode == MessagingErrorCode.UNREGISTERED || messagingCode == MessagingErrorCode.INVALID_ARGUMENT) {
            return PushSendResult.invalidToken(description);
        }
        if (messagingCode == MessagingErrorCode.UNAVAILABLE
                || messagingCode == MessagingErrorCode.INTERNAL
                || messagingCode == MessagingErrorCode.QUOTA_EXCEEDED
                || code == ErrorCode.UNAVAILABLE
                || code == ErrorCode.INTERNAL
                || code == ErrorCode.RESOURCE_EXHAUSTED
                || code == ErrorCode.DEADLINE_EXCEEDED) {
            throw new PushTransientException("FCM 暫時性錯誤：" + description, e);
        }
        return PushSendResult.failed(description);
    }
}
