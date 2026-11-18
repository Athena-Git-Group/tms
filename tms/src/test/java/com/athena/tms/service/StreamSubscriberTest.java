package com.athena.tms.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.athena.tms.dto.TaskChange;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StreamSubscriberTest {

    private static final long TENANT = 1L;
    private static final long OTHER_TENANT = 2L;
    private static final UUID ME = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID SOMEONE_ELSE = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID MY_RESERVATION = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID OTHER_RESERVATION = UUID.fromString("00000000-0000-0000-0000-0000000000d2");

    private static TaskChange change(long tenantId, String type, String dept, UUID assigneeId, UUID reservationId, String recalledFromDept) {
        return new TaskChange(tenantId, UUID.randomUUID(), 1, type, null, dept, assigneeId, reservationId, recalledFromDept);
    }

    private static TaskChange deptChange(String dept, UUID assigneeId) {
        return change(TENANT, "created", dept, assigneeId, null, null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"frontdesk", "manager", "duty_manager", "housekeeping", "engineering"})
    @DisplayName("別家飯店的單，員工連線一律不收，即使部門相同、指派給自己")
    void staffIgnoresOtherTenant(String role) {
        StreamSubscriber subscriber = StreamSubscriber.staff(TENANT, ME, role, "housekeeping");

        assertThat(subscriber.wants(change(OTHER_TENANT, "created", "housekeeping", ME, null, null))).isFalse();
    }

    @Test
    @DisplayName("別家飯店的單，住客連線即使訂房相同也不收")
    void guestIgnoresOtherTenant() {
        StreamSubscriber subscriber = StreamSubscriber.guest(TENANT, MY_RESERVATION);

        assertThat(subscriber.wants(change(OTHER_TENANT, "created", "housekeeping", null, MY_RESERVATION, null))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"frontdesk", "manager", "duty_manager"})
    @DisplayName("櫃檯、主管、值班主管收別部門而且沒指派給自己的單")
    void frontdeskRolesReceiveEveryDept(String role) {
        StreamSubscriber subscriber = StreamSubscriber.staff(TENANT, ME, role, "frontdesk");

        assertThat(subscriber.wants(deptChange("engineering", SOMEONE_ELSE))).isTrue();
        assertThat(subscriber.wants(deptChange("housekeeping", null))).isTrue();
    }

    @Test
    @DisplayName("房務收本部門的單")
    void housekeepingReceivesOwnDept() {
        StreamSubscriber subscriber = StreamSubscriber.staff(TENANT, ME, "housekeeping", "housekeeping");

        assertThat(subscriber.wants(deptChange("housekeeping", null))).isTrue();
        assertThat(subscriber.wants(deptChange("housekeeping", SOMEONE_ELSE))).isTrue();
    }

    @Test
    @DisplayName("房務收別部門但指派給自己的單")
    void housekeepingReceivesOtherDeptAssignedToSelf() {
        StreamSubscriber subscriber = StreamSubscriber.staff(TENANT, ME, "housekeeping", "housekeeping");

        assertThat(subscriber.wants(deptChange("engineering", ME))).isTrue();
    }

    @Test
    @DisplayName("房務不收別部門、沒指派或指派給別人的單")
    void housekeepingIgnoresOtherDept() {
        StreamSubscriber subscriber = StreamSubscriber.staff(TENANT, ME, "housekeeping", "housekeeping");

        assertThat(subscriber.wants(deptChange("engineering", null))).isFalse();
        assertThat(subscriber.wants(deptChange("engineering", SOMEONE_ELSE))).isFalse();
    }

    @Test
    @DisplayName("復原派單時，被收回部門是工務，工務收、房務不收")
    void triageUndoneGoesToRecalledDept() {
        TaskChange undone = change(TENANT, "triage_undone", "frontdesk", null, null, "engineering");

        assertThat(StreamSubscriber.staff(TENANT, ME, "engineering", "engineering").wants(undone)).isTrue();
        assertThat(StreamSubscriber.staff(TENANT, ME, "housekeeping", "housekeeping").wants(undone)).isFalse();
    }

    @Test
    @DisplayName("被收回部門是 null 的復原事件，部門員工照一般規則判斷")
    void triageUndoneWithoutRecalledDept() {
        TaskChange undone = change(TENANT, "triage_undone", "frontdesk", null, null, null);

        assertThat(StreamSubscriber.staff(TENANT, ME, "engineering", "engineering").wants(undone)).isFalse();
        assertThat(StreamSubscriber.staff(TENANT, ME, "housekeeping", "housekeeping").wants(undone)).isFalse();
        assertThat(StreamSubscriber.staff(TENANT, ME, "frontdesk", "frontdesk").wants(undone)).isTrue();
    }

    @Test
    @DisplayName("被收回部門只在復原事件上算數，別種事件帶了也不會讓那個部門收到")
    void recalledDeptOnlyCountsForTriageUndone() {
        TaskChange created = change(TENANT, "created", "frontdesk", null, null, "engineering");

        assertThat(StreamSubscriber.staff(TENANT, ME, "engineering", "engineering").wants(created)).isFalse();
    }

    @Test
    @DisplayName("沒有部門的員工連線只收指派給自己的單")
    void staffWithoutDept() {
        StreamSubscriber subscriber = StreamSubscriber.staff(TENANT, ME, "housekeeping", null);

        assertThat(subscriber.wants(deptChange(null, null))).isFalse();
        assertThat(subscriber.wants(change(TENANT, "triage_undone", "frontdesk", null, null, null))).isFalse();
        assertThat(subscriber.wants(deptChange("engineering", ME))).isTrue();
    }

    @Test
    @DisplayName("住客連線只收自己這筆訂房的單，部門與指派都不看")
    void guestReceivesOnlyOwnReservation() {
        StreamSubscriber subscriber = StreamSubscriber.guest(TENANT, MY_RESERVATION);

        assertThat(subscriber.wants(change(TENANT, "created", "housekeeping", null, MY_RESERVATION, null))).isTrue();
        assertThat(subscriber.wants(change(TENANT, "created", "housekeeping", null, OTHER_RESERVATION, null))).isFalse();
        assertThat(subscriber.wants(change(TENANT, "created", "housekeeping", null, null, null))).isFalse();
    }

    @Test
    @DisplayName("沒綁訂房的住客連線什麼都不收")
    void guestWithoutReservation() {
        StreamSubscriber subscriber = StreamSubscriber.guest(TENANT, null);

        assertThat(subscriber.wants(change(TENANT, "created", "housekeeping", null, null, null))).isFalse();
        assertThat(subscriber.wants(change(TENANT, "created", "housekeeping", null, MY_RESERVATION, null))).isFalse();
    }

    @Test
    @DisplayName("對接系統的連線不收單的變化")
    void integrationReceivesNoTaskChange() {
        StreamSubscriber subscriber = StreamSubscriber.integration(TENANT);

        assertThat(subscriber.wants(change(TENANT, "created", "housekeeping", null, MY_RESERVATION, null))).isFalse();
    }

    @Test
    @DisplayName("連上之後建議重抓：住客重抓首頁，櫃檯類角色重抓看板，其他員工重抓部門待認領")
    void refetchPath() {
        assertThat(StreamSubscriber.guest(TENANT, MY_RESERVATION).refetchPath()).isEqualTo("/guest/home");
        assertThat(StreamSubscriber.staff(TENANT, ME, "frontdesk", "frontdesk").refetchPath()).isEqualTo("/api/v1/desk/board");
        assertThat(StreamSubscriber.staff(TENANT, ME, "manager", "housekeeping").refetchPath()).isEqualTo("/api/v1/desk/board");
        assertThat(StreamSubscriber.staff(TENANT, ME, "duty_manager", null).refetchPath()).isEqualTo("/api/v1/desk/board");
        assertThat(StreamSubscriber.staff(TENANT, ME, "housekeeping", "housekeeping").refetchPath()).isEqualTo("/api/v1/pool");
        assertThat(StreamSubscriber.staff(TENANT, ME, "engineering", "engineering").refetchPath()).isEqualTo("/api/v1/pool");
    }
}
