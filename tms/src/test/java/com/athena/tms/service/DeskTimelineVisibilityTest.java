package com.athena.tms.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 鎖住櫃檯歷程目前的略過規則：哪些事件種類的組合會被略過、哪些照常列出。 */
class DeskTimelineVisibilityTest {

    @Test
    @DisplayName("沒有事件時什麼都不列")
    void empty() {
        assertThat(DeskTimelineVisibility.visibleIndexes(List.of())).isEmpty();
    }

    @Test
    @DisplayName("只有逐項勾選清潔項目時全部略過")
    void onlySopChecked() {
        assertThat(DeskTimelineVisibility.visibleIndexes(List.of("sop_checked", "sop_checked"))).isEmpty();
    }

    @Test
    @DisplayName("派單的下一筆就是復原時兩筆一起略過，前後的事件照常列出")
    void assignThenUndoSkippedAsPair() {
        List<String> types = List.of("created", "triaged", "triage_undone", "claimed");

        assertThat(DeskTimelineVisibility.visibleIndexes(types)).containsExactly(0, 3);
    }

    @Test
    @DisplayName("派單、復原、再派單時只略過前面那一對，第二次派單照常列出")
    void secondAssignAfterUndoIsKept() {
        List<String> types = List.of("created", "triaged", "triage_undone", "triaged");

        assertThat(DeskTimelineVisibility.visibleIndexes(types)).containsExactly(0, 3);
    }

    @Test
    @DisplayName("復原的前一筆不是派單時，復原照常列出")
    void undoNotAfterAssignIsKept() {
        List<String> types = List.of("created", "triage_undone");

        assertThat(DeskTimelineVisibility.visibleIndexes(types)).containsExactly(0, 1);
    }

    @Test
    @DisplayName("派單與復原中間夾著逐項勾選時不算一對：派單和復原都列出，只略過勾選那筆")
    void sopCheckedBetweenAssignAndUndoBreaksThePair() {
        List<String> types = List.of("triaged", "sop_checked", "triage_undone");

        assertThat(DeskTimelineVisibility.visibleIndexes(types)).containsExactly(0, 2);
    }

    @Test
    @DisplayName("派單後連著兩筆復原時只略過第一對，第二筆復原照常列出")
    void secondUndoAfterPairIsKept() {
        List<String> types = List.of("triaged", "triage_undone", "triage_undone");

        assertThat(DeskTimelineVisibility.visibleIndexes(types)).containsExactly(2);
    }
}
