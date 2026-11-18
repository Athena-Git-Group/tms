package com.athena.tms.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.athena.tms.service.StreamDegradation.Reason;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StreamDegradationTest {

    private final StreamDegradation degradation = new StreamDegradation();
    private final List<Reason> announced = new ArrayList<>();

    @Test
    @DisplayName("正常時進入降級會轉換，並以這次的原因宣告一次")
    void enterFromNormal() {
        boolean changed = degradation.enter(Reason.CONSUMER_LAG, announced::add);

        assertThat(changed).isTrue();
        assertThat(degradation.isDegraded()).isTrue();
        assertThat(degradation.current()).isEqualTo(Reason.CONSUMER_LAG);
        assertThat(announced).containsExactly(Reason.CONSUMER_LAG);
    }

    @Test
    @DisplayName("已經在降級時再進入不轉換、不宣告，原因維持第一次的")
    void enterWhileDegraded() {
        degradation.enter(Reason.CONSUMER_LAG, announced::add);

        boolean changed = degradation.enter(Reason.KAFKA_UNAVAILABLE, announced::add);

        assertThat(changed).isFalse();
        assertThat(degradation.current()).isEqualTo(Reason.CONSUMER_LAG);
        assertThat(announced).containsExactly(Reason.CONSUMER_LAG);
    }

    @Test
    @DisplayName("降級時離開會轉回正常，並以原本的原因宣告一次")
    void leaveWhileDegraded() {
        degradation.enter(Reason.KAFKA_UNAVAILABLE, r -> { });

        boolean changed = degradation.leave(announced::add);

        assertThat(changed).isTrue();
        assertThat(degradation.isDegraded()).isFalse();
        assertThat(degradation.current()).isNull();
        assertThat(announced).containsExactly(Reason.KAFKA_UNAVAILABLE);
    }

    @Test
    @DisplayName("正常時離開不轉換、不宣告")
    void leaveFromNormal() {
        boolean changed = degradation.leave(announced::add);

        assertThat(changed).isFalse();
        assertThat(degradation.isDegraded()).isFalse();
        assertThat(announced).isEmpty();
    }

    @Test
    @DisplayName("只在原因相符時離開：原因相符就轉回正常")
    void leaveIfMatchingReason() {
        degradation.enter(Reason.KAFKA_UNAVAILABLE, r -> { });

        boolean changed = degradation.leaveIf(Reason.KAFKA_UNAVAILABLE, announced::add);

        assertThat(changed).isTrue();
        assertThat(degradation.isDegraded()).isFalse();
        assertThat(announced).containsExactly(Reason.KAFKA_UNAVAILABLE);
    }

    @Test
    @DisplayName("只在原因相符時離開：原因不符就維持降級、不宣告")
    void leaveIfOtherReason() {
        degradation.enter(Reason.CONSUMER_LAG, r -> { });

        boolean changed = degradation.leaveIf(Reason.KAFKA_UNAVAILABLE, announced::add);

        assertThat(changed).isFalse();
        assertThat(degradation.current()).isEqualTo(Reason.CONSUMER_LAG);
        assertThat(announced).isEmpty();
    }

    @Test
    @DisplayName("只在原因相符時離開：正常時不轉換")
    void leaveIfFromNormal() {
        boolean changed = degradation.leaveIf(Reason.KAFKA_UNAVAILABLE, announced::add);

        assertThat(changed).isFalse();
        assertThat(announced).isEmpty();
    }

    @Test
    @DisplayName("離開後可以再次進入，原因換成新的這次")
    void enterAgainAfterLeave() {
        degradation.enter(Reason.CONSUMER_LAG, r -> { });
        degradation.leave(r -> { });

        boolean changed = degradation.enter(Reason.KAFKA_UNAVAILABLE, announced::add);

        assertThat(changed).isTrue();
        assertThat(degradation.current()).isEqualTo(Reason.KAFKA_UNAVAILABLE);
        assertThat(announced).containsExactly(Reason.KAFKA_UNAVAILABLE);
    }

    @Test
    @DisplayName("送給客戶端的原因代碼")
    void reasonCodes() {
        assertThat(Reason.CONSUMER_LAG.code()).isEqualTo("kafka_consumer_lag");
        assertThat(Reason.KAFKA_UNAVAILABLE.code()).isEqualTo("kafka_unavailable");
    }
}
