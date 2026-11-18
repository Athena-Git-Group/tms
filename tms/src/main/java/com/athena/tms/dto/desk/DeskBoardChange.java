package com.athena.tms.dto.desk;

import java.time.LocalDate;
import java.util.Objects;

/**
 * 一張單在櫃檯看板上現在該怎麼呈現：放上這張卡片，或從看板拿掉。員工事件串流的 {@code task} 訊號帶給櫃檯角色的連線。
 *
 * <p>{@code cardSeq} 是這份內容對應到單的第幾步，{@code businessDate} 是判斷看板範圍時用的「今天」。
 * 放上時步數就是卡片自己的 {@code seq}，所以兩者不會對不上。
 */
public sealed interface DeskBoardChange {

    int cardSeq();

    LocalDate businessDate();

    record Upsert(DeskTask card, LocalDate businessDate) implements DeskBoardChange {
        public Upsert {
            Objects.requireNonNull(card, "card");
            Objects.requireNonNull(businessDate, "businessDate");
        }

        @Override
        public int cardSeq() {
            return this.card.seq();
        }
    }

    record Remove(int cardSeq, LocalDate businessDate) implements DeskBoardChange {
        public Remove {
            Objects.requireNonNull(businessDate, "businessDate");
        }
    }
}
