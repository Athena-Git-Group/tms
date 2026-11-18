package com.athena.bddsupport;

import com.athena.bddsupport.BackendChannel.CreatedStaff;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 一張「處在某種狀況的單」的內容，交給 {@link BackendChannel#createTaskInSituation} 建立。
 *
 * <p>這裡只收後端認得的值（{@code housekeeping}、{@code amenity}、{@code button}…）與已經算好的時刻，
 * 不收驗收規格裡的中文說法，也不替呼叫端補「沒寫的時候是什麼」：那些是句型自己的規則。
 * 沒有設定的欄位就是不寫進資料庫；某個狀況非有不可的欄位沒給時，建立當下會指出缺哪一個。
 *
 * <p>人有四種位置，同一位員工可以同時是其中幾種：
 * <ul>
 *   <li>{@link #createdBy} 是開單的人，櫃檯代開與同仁報修的單才有；工務單的報修人也是他。</li>
 *   <li>{@link #triagedBy} 是把單從待派單派出去的櫃檯。有給就代表這張單是從待派單派出來的。</li>
 *   <li>{@link #assignedBy} 是把單指派或轉派給負責人的主管。</li>
 *   <li>{@link #actedBy} 是做了「讓這張單變成現在這個狀況」那個動作的人：
 *       延後、取消、查房、驗收的主管，或當場回答的櫃檯。</li>
 * </ul>
 *
 * <p>時刻有六個，依序是建立、派出、到手、開工、完成，以及 {@link #at}。
 * {@link #at} 是這個狀況的計時起點；在它之前、沒有給的時刻，沿用它之後最近一個有給的時刻，
 * 所以只給 {@link #at} 時整段歷程都發生在同一刻。
 */
public final class TaskSituationSpec {

    /** 單怎麼到負責人手上；值的意思與看板回應的 {@code acceptedVia} 相同。 */
    public enum AcceptedVia {
        PLAN_ASSIGNED,
        SELF_CLAIMED,
        MANAGER_ASSIGNED,
        REASSIGNED
    }

    final String roomNo;
    final String type;
    final String source;

    String title;
    String dept;
    String displayNo;
    UUID reservationId;
    String note;
    String guestText;
    String guestLang;
    String translatedText;
    Integer estMin;
    boolean urgent;

    CreatedStaff assignee;
    CreatedStaff previousAssignee;
    CreatedStaff createdBy;
    CreatedStaff triagedBy;
    CreatedStaff assignedBy;
    CreatedStaff actedBy;
    CreatedStaff returnedBy;
    String poolSource;
    AcceptedVia acceptedVia;
    String triageVia;

    OffsetDateTime at;
    OffsetDateTime createdAt;
    OffsetDateTime triagedAt;
    OffsetDateTime acceptedAt;
    OffsetDateTime startedAt;
    OffsetDateTime doneAt;

    String reason;
    String handoverNote;
    String answer;
    Integer sopCheckedCount;
    String repairCategory;
    String resolutionType;

    private TaskSituationSpec(String roomNo, String type, String source) {
        this.roomNo = roomNo;
        this.type = type;
        this.source = source;
    }

    /**
     * 三個參數是每一種狀況都少不了的；{@code type} 與 {@code source} 給後端的值，
     * 例如 {@code amenity}、{@code guest}。
     */
    public static TaskSituationSpec of(String roomNo, String type, String source) {
        return new TaskSituationSpec(roomNo, type, source);
    }

    /** 有 {@link #guestText} 時可以不給標題，會照後端的做法取住客原文的前 120 字。 */
    public TaskSituationSpec title(String title) {
        this.title = title;
        return this;
    }

    /** 待派單與已回答固定歸 {@code frontdesk}，可以不給；其餘狀況要給派去的部門。 */
    public TaskSituationSpec dept(String dept) {
        this.dept = dept;
        return this;
    }

    /**
     * 畫面要驗某個特定單號時才給：工務單給 {@code W-} 開頭的工務單號，其餘給 {@code T-} 開頭的單號。
     * 不給時照後端的取號方式領下一號。
     */
    public TaskSituationSpec displayNo(String displayNo) {
        this.displayNo = displayNo;
        return this;
    }

    /** 住客來源的單一定要連結訂房，後端建單時也是這樣要求的；訂房用 {@link BackendChannel#createReservation} 建。 */
    public TaskSituationSpec reservationId(UUID reservationId) {
        this.reservationId = reservationId;
        return this;
    }

    /** 與 {@link #guestText} 擇一。工務單的任務本身不存備註，給了會寫成工務單的報修說明。 */
    public TaskSituationSpec note(String note) {
        this.note = note;
        return this;
    }

    /**
     * 有給就代表這張單是住客自己打字送出的，備註會存成後端替這種單記的那種格式。
     * {@code lang} 是原文的語言代碼（例如 {@code ja}），{@code translated} 是單上既有的翻譯，沒有就給 null。
     */
    public TaskSituationSpec guestText(String text, String lang, String translated) {
        this.guestText = text;
        this.guestLang = lang;
        this.translatedText = translated;
        return this;
    }

    public TaskSituationSpec estMin(Integer estMin) {
        this.estMin = estMin;
        return this;
    }

    /** 只有工務單有急件。 */
    public TaskSituationSpec urgent(boolean urgent) {
        this.urgent = urgent;
        return this;
    }

    public TaskSituationSpec assignee(CreatedStaff assignee) {
        this.assignee = assignee;
        return this;
    }

    /**
     * 轉派與退回都只會發生在已經有人拿著的單上，歷程要先有這個人認領的一筆才接得上。
     * 被轉派的單一定要給；被退回待認領的單沒給時會自動挑一位，見 {@link #returnedBy}。
     */
    public TaskSituationSpec previousAssignee(CreatedStaff previousAssignee) {
        this.previousAssignee = previousAssignee;
        return this;
    }

    public TaskSituationSpec createdBy(CreatedStaff createdBy) {
        this.createdBy = createdBy;
        return this;
    }

    /** {@code via} 給 {@code button} 或 {@code drag}，給 null 視為 {@code button}。 */
    public TaskSituationSpec triagedBy(CreatedStaff desk, String via) {
        this.triagedBy = desk;
        this.triageVia = via;
        return this;
    }

    public TaskSituationSpec assignedBy(CreatedStaff assignedBy) {
        this.assignedBy = assignedBy;
        return this;
    }

    public TaskSituationSpec actedBy(CreatedStaff actedBy) {
        this.actedBy = actedBy;
        return this;
    }

    /**
     * 待認領的單是被人退回來的：主管附交代退回，或同仁請假時由登記請假的人退回。
     * 退回的單等待時間從退回那一刻重算，所以 {@link #at} 是退回的時刻、{@link #createdAt} 另外給。
     * 不給這一項、只給了較晚的 {@link #at} 時，當成班末由系統退回，那是唯一不需要人也不需要交代的退回。
     * 退回之前拿著這張單的人用 {@link #previousAssignee} 給；沒給時挑這個部門員工編號最前面的在職同仁。
     */
    public TaskSituationSpec returnedBy(CreatedStaff returnedBy) {
        this.returnedBy = returnedBy;
        return this;
    }

    /**
     * 待認領清單顯示的「這張單從哪裡進來」。不給時照後端的做法由來源與退回方式決定。
     * 退回的單只收 {@code manager_requeue}、{@code leave_return}、{@code shift_end_return} 三種退回方式。
     */
    public TaskSituationSpec poolSource(String poolSource) {
        this.poolSource = poolSource;
        return this;
    }

    /**
     * 已經過了「已接」的狀況（進行中、已完成…）當初是怎麼到負責人手上的。
     * 不給時當成負責人自己認領；四種「已接」的狀況各自已經決定了這件事，不必給。
     */
    public TaskSituationSpec acceptedVia(AcceptedVia acceptedVia) {
        this.acceptedVia = acceptedVia;
        return this;
    }

    /**
     * 這個狀況的計時起點：待派單是住客送出、待認領是開始等待、已接是到手、進行中是開始、
     * 中斷是中斷、延後中是延後，其餘是完成或結束的時刻。不給時是後端的現在。
     */
    public TaskSituationSpec at(OffsetDateTime at) {
        this.at = at;
        return this;
    }

    public TaskSituationSpec createdAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    /** 待認領的單沒給時，派出與建立是同一刻。 */
    public TaskSituationSpec triagedAt(OffsetDateTime triagedAt) {
        this.triagedAt = triagedAt;
        return this;
    }

    public TaskSituationSpec acceptedAt(OffsetDateTime acceptedAt) {
        this.acceptedAt = acceptedAt;
        return this;
    }

    public TaskSituationSpec startedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
        return this;
    }

    public TaskSituationSpec doneAt(OffsetDateTime doneAt) {
        this.doneAt = doneAt;
        return this;
    }

    /** 本人暫停或本人延後時填的原因；請假造成的中斷則是系統記下的停住原因。 */
    public TaskSituationSpec reason(String reason) {
        this.reason = reason;
        return this;
    }

    /** 中斷的單交代做到哪；主管退回待認領時附的交代也給這裡。 */
    public TaskSituationSpec handoverNote(String handoverNote) {
        this.handoverNote = handoverNote;
        return this;
    }

    /** 只有已回答的單要給，是櫃檯回給住客的那段話。 */
    public TaskSituationSpec answer(String answer) {
        this.answer = answer;
        return this;
    }

    /**
     * 打掃單已經勾了幾項清潔 SOP，照飯店啟用中的項目由前往後勾。
     * 總項數是飯店啟用了幾項，不由這張單決定，所以這裡只收已勾的項數。
     */
    public TaskSituationSpec sopCheckedCount(Integer sopCheckedCount) {
        this.sopCheckedCount = sopCheckedCount;
        return this;
    }

    /** 工務單一定要有類別，不給時寫 {@code other}。 */
    public TaskSituationSpec repairCategory(String repairCategory) {
        this.repairCategory = repairCategory;
        return this;
    }

    /** 已完修與驗收通過的工務單一定有完修結果，不給時寫 {@code fixed}。 */
    public TaskSituationSpec resolutionType(String resolutionType) {
        this.resolutionType = resolutionType;
        return this;
    }
}
