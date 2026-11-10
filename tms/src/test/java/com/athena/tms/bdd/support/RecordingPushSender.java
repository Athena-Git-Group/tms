package com.athena.tms.bdd.support;

import com.athena.tms.push.PushDeviceAddress;
import com.athena.tms.push.PushMessage;
import com.athena.tms.push.PushSendResult;
import com.athena.tms.push.PushSender;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 測試用的推播服務替身：記下每一次送往裝置的呼叫，讓情境能斷言「手機真的收到了幾次」。
 * 可以預先指定哪些推播地址要回報「已失效」，模擬裝置解除安裝或換機後舊地址被推播服務拒絕。
 * 推播消費者在另一條執行緒呼叫，所以用執行緒安全的集合。每個情境開始前由掛勾清空。
 */
public class RecordingPushSender implements PushSender {

    public record Sent(PushMessage message, PushDeviceAddress device) {
    }

    private final List<Sent> sent = new CopyOnWriteArrayList<>();
    private final Set<String> invalidTokens = new CopyOnWriteArraySet<>();

    @Override
    public PushSendResult send(PushMessage message, PushDeviceAddress device) {
        sent.add(new Sent(message, device));
        if (invalidTokens.contains(device.pushToken())) {
            return PushSendResult.invalidToken("UNREGISTERED: 測試指定此地址已失效");
        }
        return PushSendResult.sent();
    }

    public void markTokenInvalid(String pushToken) {
        invalidTokens.add(pushToken);
    }

    public List<Sent> sentTo(String pushToken, String title) {
        return sent.stream()
                .filter(s -> s.device().pushToken().equals(pushToken) && s.message().title().equals(title))
                .toList();
    }

    public List<Sent> sentWithTitle(String title) {
        return sent.stream().filter(s -> s.message().title().equals(title)).toList();
    }

    public void reset() {
        sent.clear();
        invalidTokens.clear();
    }
}
