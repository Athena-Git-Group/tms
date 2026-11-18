package com.athena.tms.service;

import java.util.ArrayList;
import java.util.List;

/**
 * 決定一張單的事件裡哪些要列在櫃檯看的歷程上，只看事件種類，不碰資料庫。
 *
 * <p>略過兩種：一筆派單（{@code triaged}）的下一筆緊接著就是復原（{@code triage_undone}）時，這兩筆一起略過，
 * 讀起來就是派單前的樣子；中間只要隔了別的事件就不算一對，兩筆都照常列出。逐項勾選清潔項目
 * （{@code sop_checked}）也不列，進度看詳情的 SOP 進度。其餘每一筆都列出。
 */
final class DeskTimelineVisibility {

    private DeskTimelineVisibility() {
    }

    /** 傳入依序排好的事件種類，回傳要列出的那幾筆在原清單裡的位置，由小到大。 */
    static List<Integer> visibleIndexes(List<String> types) {
        List<Integer> visible = new ArrayList<>();
        for (int i = 0; i < types.size(); i++) {
            String type = types.get(i);
            if ("triaged".equals(type) && i + 1 < types.size() && "triage_undone".equals(types.get(i + 1))) {
                i++;
                continue;
            }
            if ("sop_checked".equals(type)) {
                continue;
            }
            visible.add(i);
        }
        return visible;
    }
}
