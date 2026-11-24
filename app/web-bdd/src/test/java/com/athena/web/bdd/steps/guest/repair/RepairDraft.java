package com.athena.web.bdd.steps.guest.repair;

/**
 * 保管測試替住客打滿補充說明的那 200 個字，讓結果句可以比對輸入框的內容有沒有多出字。
 * 一個場景一份，前置沒有打滿時讀它會直接失敗。
 */
public class RepairDraft {

    private String fullNote;

    void rememberFullNote(String note) {
        fullNote = note;
    }

    String fullNote() {
        if (fullNote == null) {
            throw new IllegalStateException("這個場景沒有「補充說明已有 200 個字」這個前置");
        }
        return fullNote;
    }

    /** 測試自編、不含空白的中文字，剛好 {@code count} 個。 */
    static String chineseChars(int count) {
        String source = "浴室排水很慢冷氣滴水燈具一直閃";
        StringBuilder text = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            text.append(source.charAt(i % source.length()));
        }
        return text.toString();
    }
}
