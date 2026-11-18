package com.athena.web.bdd.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 這個場景準備了哪些單：句子裡寫的房號（同房兩張時加標題）對到後端哪一張單。
 *
 * <p>規格的句子用房號指一張單，例如「打開 "305" 的詳情面板」「後端 "305" 的單在房務待認領」；
 * 規則是房號代表案例裡那間房唯一的一張單，同一間房有兩張單時用標題分辨。
 * 步驟要對後端讀寫或在畫面上找這張單時，從這裡換成 task id，不自己去資料庫用房號查：
 * 房號查得到的單可能不只一張，而且查不出「案例說的是哪一張」。
 *
 * <p>一個場景一份，跟著 {@link WebWorld} 建立與丟棄。登記的先後就是案例裡寫的先後，
 * 「案例裡第一張待派單」這種說法照這個順序找。
 *
 * <p>另外記每間房在這個場景用的那筆訂房：同一間房再建一張有住客的單時沿用同一筆，
 * 兩筆住期重疊的訂房資料庫不收，而且住客收回需求時認的也是這一筆。
 */
public final class ScenarioTasks {

    /**
     * 一張準備好的單。標題是畫面上看到的那一個，表格沒寫時是步驟替它取的。
     * 狀況是準備時用的說法，例如「待派單」「已接（自己認領）」；不是經過表格建的單可以沒有。
     */
    public record Entry(String roomNo, String title, UUID taskId, String situation) {
    }

    private final List<Entry> entries = new ArrayList<>();

    private final Map<String, UUID> reservationByRoom = new LinkedHashMap<>();

    /** 登記一張單。同一間房可以登記好幾張，但同房同標題只能有一張，否則之後分不出是哪一張。 */
    public void add(Entry entry) {
        Objects.requireNonNull(entry.roomNo(), "房號必填");
        Objects.requireNonNull(entry.taskId(), "task id 必填");
        for (Entry existing : entries) {
            if (existing.roomNo().equals(entry.roomNo()) && Objects.equals(existing.title(), entry.title())) {
                throw new IllegalArgumentException("「" + entry.roomNo() + "」已經有一張標題是「" + entry.title()
                        + "」的單，同房同標題的兩張單之後分不出來");
            }
        }
        entries.add(entry);
    }

    /** 這間房唯一的那張單；沒有或不只一張時直接失敗，失敗訊息會列出這間房有哪些標題。 */
    public UUID idOf(String roomNo) {
        return entryOf(roomNo).taskId();
    }

    /** 同一間房有兩張以上時，用標題指定是哪一張。 */
    public UUID idOf(String roomNo, String title) {
        return entryOf(roomNo, title).taskId();
    }

    public Entry entryOf(String roomNo) {
        List<Entry> inRoom = entriesIn(roomNo);
        if (inRoom.isEmpty()) {
            throw new IllegalArgumentException("這個場景沒有準備「" + roomNo + "」的單");
        }
        if (inRoom.size() > 1) {
            throw new IllegalArgumentException("「" + roomNo + "」有 " + inRoom.size() + " 張單，要用標題分辨："
                    + inRoom.stream().map(Entry::title).toList());
        }
        return inRoom.get(0);
    }

    public Entry entryOf(String roomNo, String title) {
        return entriesIn(roomNo).stream()
                .filter(entry -> Objects.equals(entry.title(), title))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("「" + roomNo + "」沒有標題是「" + title
                        + "」的單，這間房有：" + entriesIn(roomNo).stream().map(Entry::title).toList()));
    }

    /** 這間房在這個場景準備的所有單，依登記的先後。 */
    public List<Entry> entriesIn(String roomNo) {
        return entries.stream().filter(entry -> entry.roomNo().equals(roomNo)).toList();
    }

    /** 從 task id 反查是哪一張，用在把後端讀到的單換回房號的地方。 */
    public Optional<Entry> entryOf(UUID taskId) {
        return entries.stream().filter(entry -> entry.taskId().equals(taskId)).findFirst();
    }

    /** 這個場景準備的所有單，依登記的先後。 */
    public List<Entry> all() {
        return List.copyOf(entries);
    }

    /** 案例裡第一張處在這個狀況的單，例如「待派單」。 */
    public Optional<Entry> firstIn(String situation) {
        return entries.stream().filter(entry -> situation.equals(entry.situation())).findFirst();
    }

    /** 這間房在這個場景用的訂房；還沒建過時是空的。 */
    public Optional<UUID> reservationOf(String roomNo) {
        return Optional.ofNullable(reservationByRoom.get(roomNo));
    }

    public void rememberReservation(String roomNo, UUID reservationId) {
        reservationByRoom.put(roomNo, reservationId);
    }
}
