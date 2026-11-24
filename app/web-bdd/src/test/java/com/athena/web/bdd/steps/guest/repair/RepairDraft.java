package com.athena.web.bdd.steps.guest.repair;

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

    static String chineseChars(int count) {
        String source = "浴室排水很慢冷氣滴水燈具一直閃";
        StringBuilder text = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            text.append(source.charAt(i % source.length()));
        }
        return text.toString();
    }
}
