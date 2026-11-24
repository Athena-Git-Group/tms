package com.athena.tms.dto.desk;

import java.time.LocalDate;
import java.util.Objects;

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
