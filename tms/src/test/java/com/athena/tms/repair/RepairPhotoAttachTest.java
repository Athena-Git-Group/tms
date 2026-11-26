package com.athena.tms.repair;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.athena.tms.PostgresSpringTest;
import com.athena.tms.common.CurrentActor;
import com.athena.tms.common.TenantContext;
import com.athena.tms.common.UuidV7;
import com.athena.tms.common.error.StateConflictException;
import com.athena.tms.dto.Actor;
import com.athena.tms.dto.TaskCreateCommand;
import com.athena.tms.service.TaskEngine;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** 照片綁定的兩道防線：漏了上鎖時改到的筆數比對仍回 409 並回滾；同時以同一張照片開兩張單只有一張成立。 */
class RepairPhotoAttachTest extends PostgresSpringTest {

    private static final long TENANT_ID = 1L;
    private static final UUID ROOM_301 = UUID.fromString("00000000-0000-7000-8000-000000000301");
    private static final UUID ROOM_502 = UUID.fromString("00000000-0000-7000-8000-000000000502");
    private static final UUID STAFF_FD1 = UUID.fromString("00000000-0000-7000-8000-00000000a004");

    @Autowired
    RepairService repairService;

    @Autowired
    TaskEngine taskEngine;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    TransactionTemplate transactionTemplate;

    @BeforeEach
    void bindTenant() {
        TenantContext.set(TENANT_ID);
    }

    @AfterEach
    void unbindTenant() {
        TenantContext.clear();
        CurrentActor.clear();
    }

    @Test
    void bindingRollsBackWhenAPhotoIsAlreadyOnAnotherTicket() {
        UUID ticket = repairTask("這張要綁照片");
        UUID otherTicket = repairTask("照片已經在這張");
        UUID free = uploadedPhoto();
        UUID taken = uploadedPhoto();
        jdbcTemplate.update("UPDATE repair_photos SET ticket_task_id = ? WHERE id = ?", otherTicket, taken);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status ->
                repairService.bindUnclaimedPhotos(TENANT_ID, ticket, List.of(free, taken))))
                .isInstanceOfSatisfying(StateConflictException.class,
                        e -> assertThat(e.getCode()).isEqualTo("photo_already_attached"));

        assertThat(ticketOf(free)).isNull();
        assertThat(ticketOf(taken)).isEqualTo(otherTicket);
    }

    @Test
    void twoTicketsOpenedAtOnceWithTheSamePhotoOnlyOneSucceeds() throws Exception {
        UUID photo = uploadedPhoto();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (UUID room : List.of(ROOM_301, ROOM_502)) {
                results.add(pool.submit(() -> {
                    TenantContext.set(TENANT_ID);
                    CurrentActor.set(CurrentActor.staff(TENANT_ID, STAFF_FD1, "frontdesk", "frontdesk"));
                    try {
                        start.await();
                        repairService.create(null, room, null, null, "plumbing_electrical", "同一張照片", List.of(photo), false);
                        return "created";
                    } catch (StateConflictException e) {
                        return e.getCode();
                    } finally {
                        TenantContext.clear();
                        CurrentActor.clear();
                    }
                }));
            }
            start.countDown();
            List<String> outcomes = new ArrayList<>();
            for (Future<String> result : results) {
                outcomes.add(result.get());
            }
            assertThat(outcomes).containsExactlyInAnyOrder("created", "photo_already_attached");
        } finally {
            pool.shutdownNow();
        }
        assertThat(ticketOf(photo)).isNotNull();
    }

    private UUID repairTask(String description) {
        return taskEngine.create(new TaskCreateCommand(
                TENANT_ID, "repair", "desk", ROOM_502, null, "engineering", "pool", null,
                description, null, null, null, null, Actor.system(), false,
                new RepairTicketDraft("other", null, STAFF_FD1, null, null))).getId();
    }

    private UUID uploadedPhoto() {
        UUID id = UuidV7.generate();
        OffsetDateTime now = OffsetDateTime.now();
        jdbcTemplate.update(
                """
                INSERT INTO repair_photos (id, tenant_id, purpose, status, storage_ref, taken_at, uploaded_by, created_at, uploaded_at)
                VALUES (?, ?, 'report', 'uploaded', ?, ?, ?, ?, ?)
                """,
                id, TENANT_ID, "repair-photos/" + TENANT_ID + "/" + id + ".jpg", now, STAFF_FD1, now, now);
        return id;
    }

    private UUID ticketOf(UUID photoId) {
        return jdbcTemplate.queryForObject("SELECT ticket_task_id FROM repair_photos WHERE id = ?", UUID.class, photoId);
    }
}
