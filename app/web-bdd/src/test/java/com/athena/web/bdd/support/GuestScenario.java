package com.athena.web.bdd.support;

import com.microsoft.playwright.Page;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 一個住客場景記下的東西：句子裡的房號、標題要換成哪一枚代碼、哪一張單，以及之後的句子要比對的數字。
 *
 * <p>規格的句子只寫房號與需求標題，步驟要開網址、讀後端時從這裡換成代碼與 task id，不自己去資料庫查：
 * 同一間房換過 QR 之後查得到兩枚代碼，同標題的需求也可能不只一張，查不出「案例說的是哪一個」。
 *
 * <p>一個場景一份，場景結束就丟掉。
 */
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

    /** 由 {@link GuestHooks} 在住客場景開始時標記；櫃檯場景永遠是 {@code false}。 */
    void markActive() {
        active = true;
    }

    /**
     * 這個場景是不是住客掃碼頁的場景。
     *
     * <p>住客與櫃檯的規格有幾句字面完全相同（「畫面是 …」「畫面上看得到 …」等），同一個 glue 只能有一個步驟定義，
     * 由櫃檯那邊的定義看這個旗標決定走哪一套。
     */
    public boolean active() {
        return active;
    }

    /** 記下「住客」這支手機現在開的是哪一間房；之後沒寫房號的句子（後端這次住宿…）都指這間房。 */
    public void rememberPhoneRoom(String roomNo) {
        phoneRoom = roomNo;
    }

    /** 規格先寫「{房號} 房的住客這次住宿…」、沒有先開首頁就直接開某一頁時，「住客」就是那間房的住客。 */
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

    /** 記下「住客打開 QR Code 的網址」這一步從開網址到畫面畫完，測試替住客點按與輸入了幾次。 */
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

    /** 記下這間房現在的 QR 代碼；換過 QR 時再記一次就蓋掉舊的。 */
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

    /** 記下這個分頁目前手上的住客工作階段權杖。 */
    public void rememberSession(Page page, String sessionToken) {
        sessionByPage.put(page, sessionToken);
    }

    public Optional<String> session(Page page) {
        return Optional.ofNullable(sessionByPage.get(page));
    }

    /** 記下某個標題對到哪一張單；同一個場景同標題只能有一張，否則之後分不出是哪一張。 */
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

    /** 記下住客在 When 按下送出的那一刻，給比對送出時間的結果句用。 */
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

    /** 記下「另一支手機」的分頁：同一間房、同一筆訂房，但儲存空間和這支手機完全分開。 */
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
