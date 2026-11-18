package com.athena.tms.bdd.support;

import com.athena.tms.push.PushDeviceAddress;
import com.athena.tms.push.PushMessage;
import com.athena.tms.push.PushSendResult;
import com.athena.tms.push.PushSender;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

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
