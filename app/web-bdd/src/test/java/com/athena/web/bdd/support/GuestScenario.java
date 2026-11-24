package com.athena.web.bdd.support;

import com.microsoft.playwright.Page;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class GuestScenario {

    private final Map<String, String> qrTokenByRoom = new LinkedHashMap<>();

    private final Map<Page, String> sessionByPage = new HashMap<>();

    private final Map<String, UUID> requestByTitle = new LinkedHashMap<>();

    private OffsetDateTime submittedAt;

    private int taps;

    private int inputs;

    private Page secondPhone;

    private boolean active;

    private String phoneRoom;

    private int openingTaps = -1;

    private int openingInputs = -1;

    void markActive() {
        active = true;
    }

    public boolean active() {
        return active;
    }

    public void rememberPhoneRoom(String roomNo) {
        phoneRoom = roomNo;
    }

    public void rememberPhoneRoomIfAbsent(String roomNo) {
        if (phoneRoom == null) {
            phoneRoom = roomNo;
        }
    }

    public String phoneRoom() {
        if (phoneRoom == null) {
            throw new IllegalStateException("這個場景的住客還沒打開任何一間房的網址，不知道「這次住宿」是哪一間房");
        }
        return phoneRoom;
    }

    public void rememberOpening(int taps, int inputs) {
        openingTaps = taps;
        openingInputs = inputs;
    }

    public int openingTaps() {
        requireOpening();
        return openingTaps;
    }

    public int openingInputs() {
        requireOpening();
        return openingInputs;
    }

    private void requireOpening() {
        if (openingTaps < 0) {
            throw new IllegalStateException("這個場景沒有「住客打開 … 房 QR Code 的網址」這一步");
        }
    }

    public void rememberQrToken(String roomNo, String qrToken) {
        qrTokenByRoom.put(roomNo, qrToken);
    }

    public String qrToken(String roomNo) {
        String token = qrTokenByRoom.get(roomNo);
        if (token == null) {
            throw new IllegalStateException("這個場景還沒取過 " + roomNo + " 房的 QR 代碼");
        }
        return token;
    }

    public void rememberSession(Page page, String sessionToken) {
        sessionByPage.put(page, sessionToken);
    }

    public Optional<String> session(Page page) {
        return Optional.ofNullable(sessionByPage.get(page));
    }

    public void rememberRequest(String title, UUID taskId) {
        UUID previous = requestByTitle.putIfAbsent(title, taskId);
        if (previous != null && !previous.equals(taskId)) {
            throw new IllegalStateException("標題「" + title + "」已經對到另一張單，同標題的需求要用送出時間分辨");
        }
    }

    public boolean knowsRequest(String title) {
        return requestByTitle.containsKey(title);
    }

    public UUID request(String title) {
        UUID taskId = requestByTitle.get(title);
        if (taskId == null) {
            throw new IllegalStateException("這個場景沒有標題是「" + title + "」的需求，已知的有：" + requestByTitle.keySet());
        }
        return taskId;
    }

    public void rememberSubmittedAt(OffsetDateTime moment) {
        submittedAt = moment;
    }

    public OffsetDateTime submittedAt() {
        if (submittedAt == null) {
            throw new IllegalStateException("這個場景的住客還沒按過送出");
        }
        return submittedAt;
    }

    public void countTap() {
        taps++;
    }

    public int taps() {
        return taps;
    }

    public void countInput() {
        inputs++;
    }

    public int inputs() {
        return inputs;
    }

    public void rememberSecondPhone(Page page) {
        secondPhone = page;
    }

    public Page secondPhone() {
        if (secondPhone == null) {
            throw new IllegalStateException("這個場景還沒有開另一支手機");
        }
        return secondPhone;
    }
}
