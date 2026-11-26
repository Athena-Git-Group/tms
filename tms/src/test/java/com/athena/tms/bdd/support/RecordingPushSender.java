package com.athena.tms.bdd.support;

import com.athena.tms.push.PushDeviceAddress;
import com.athena.tms.push.PushMessage;
import com.athena.tms.push.PushSendResult;
import com.athena.tms.push.PushSender;
import com.athena.tms.push.PushTransientException;
import java.sql.SQLTransientConnectionException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import org.springframework.transaction.CannotCreateTransactionException;

public class RecordingPushSender implements PushSender {

    public record Sent(PushMessage message, PushDeviceAddress device) {
    }

    private final List<Sent> sent = new CopyOnWriteArrayList<>();
    private final Set<String> invalidTokens = new CopyOnWriteArraySet<>();
    private final Map<String, Integer> transientFailuresLeft = new ConcurrentHashMap<>();
    private final Map<String, Integer> databaseOutagesLeft = new ConcurrentHashMap<>();

    @Override
    public PushSendResult send(PushMessage message, PushDeviceAddress device) {
        if (databaseOutagesLeft.computeIfPresent(device.pushToken(), (token, left) -> left > 0 ? left - 1 : null) != null) {
            throw new CannotCreateTransactionException("測試指定此時資料庫連不上",
                    new SQLTransientConnectionException("Connection is not available, request timed out"));
        }
        if (transientFailuresLeft.computeIfPresent(device.pushToken(), (token, left) -> left > 0 ? left - 1 : null) != null) {
            throw new PushTransientException("測試指定此地址暫時送不出去", null);
        }
        sent.add(new Sent(message, device));
        if (invalidTokens.contains(device.pushToken())) {
            return PushSendResult.invalidToken("UNREGISTERED: 測試指定此地址已失效");
        }
        return PushSendResult.sent();
    }

    public void failTransiently(String pushToken, int times) {
        transientFailuresLeft.put(pushToken, times);
    }

    public void failWithDatabaseOutage(String pushToken, int times) {
        databaseOutagesLeft.put(pushToken, times);
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
        transientFailuresLeft.clear();
        databaseOutagesLeft.clear();
    }
}
