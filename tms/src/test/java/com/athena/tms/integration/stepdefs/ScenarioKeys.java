package com.athena.tms.integration.stepdefs;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.athena.tms.dto.Actor;
import com.athena.tms.integration.stepdefs.ScenarioContext.HttpResult;
import com.athena.tms.integration.stepdefs.ScenarioContext.Key;
import com.athena.tms.integration.stepdefs.utils.IntegrationSseSupport;
import com.athena.tms.integration.stepdefs.utils.SseSupport;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;

public final class ScenarioKeys {

    public static final Key<String> SESSION_TOKEN = key("sessionToken");
    public static final Key<String> OLD_TOKEN = key("oldToken");
    public static final Key<String> FINGERPRINT = key("fingerprint");
    public static final Key<List<HttpResult>> LOGIN_ATTEMPTS = key("loginAttempts");
    public static final Key<List<HttpResult>> CREDENTIAL_ATTEMPTS = key("credentialAttempts");
    public static final Key<HttpResult> RELOGIN_RESULT = key("reloginResult");
    public static final Key<HttpResult> OLD_TOKEN_RESULT = key("oldTokenResult");

    public static final Key<String> MY_TITLE = key("myTitle");
    public static final Key<String> FREE_TEXT = key("freeText");
    public static final Key<ListAppender<ILoggingEvent>> ALERT_APPENDER = key("alertAppender");
    public static final Key<String> ATTACKER_IP = key("attackerIp");
    public static final Key<Long> SESSIONS_BEFORE = key("sessionsBefore");
    public static final Key<Long> TASKS_BEFORE = key("tasksBefore");
    public static final Key<Long> TASKS_BEFORE_HOME = key("tasksBeforeHome");

    public static final Key<List<String>> INSPECTION_KEYS = key("inspectionKeys");
    public static final Key<UUID> CLEANER_ID = key("cleanerId");
    public static final Key<Actor> CLEANER_ACTOR = key("cleanerActor");
    public static final Key<String> PRE_INSPECTION_STATE = key("preInspectionState");
    public static final Key<String> PRINT_BODY = key("printBody");
    public static final Key<String> PRINTED_TOKEN = key("printedToken");
    public static final Key<String> SQL_STATE = key("sqlState");
    public static final Key<String> SQL_MESSAGE = key("sqlMessage");
    public static final Key<String> OLD_QR_TOKEN = key("oldQrToken");
    public static final Key<Integer> OLD_QR_VERSION = key("oldQrVersion");

    public static final Key<HttpResult> STARTED_RESPONSE = key("startedResponse");
    public static final Key<HttpResult> COMPLETED_RESPONSE = key("completedResponse");
    public static final Key<UUID> SOP_ACTOR_ID = key("sopActorId");
    public static final Key<String> SKIPPED_LABEL = key("skippedLabel");

    public static final Key<List<Long>> OUTBOX_FREE_IDS = key("outboxFreeIds");
    public static final Key<List<String>> OUTBOX_BLOCKED_KEYS = key("outboxBlockedKeys");
    public static final Key<List<ConsumerRecord<String, String>>> PUSH_RECORDS = key("pushRecords");
    public static final Key<List<ConsumerRecord<String, String>>> TASK_EVENT_RECORDS = key("records");
    public static final Key<List<ConsumerRecord<String, String>>> MY_RECORDS = key("myRecords");
    public static final Key<String> UPDATE_STATE = key("updateState");
    public static final Key<String> DELETE_STATE = key("deleteState");

    public static final Key<List<SseSupport.SseStreamClient>> SSE_CLIENTS = key("sseClients");

    public static final Key<Map<String, String>> INTEGRATION_API_KEYS = key("integration:apiKeys");
    public static final Key<List<String>> INTEGRATION_EVENT_IDS = key("integration:eventIds");
    public static final Key<Map<String, String>> INTEGRATION_EVENT_ID_BY_ROOM = key("integration:eventIdByRoom");
    public static final Key<String> INTEGRATION_TAMPERED_KEY = key("integration:tamperedKey");
    public static final Key<Map<String, String>> INTEGRATION_STAFF_TOKENS = key("integration:staffTokens");
    public static final Key<List<IntegrationSseSupport.IntegrationSseClient>> INTEGRATION_SSE_CLIENTS =
            key("integration:sseClients");
    public static final Key<IntegrationSseSupport.IntegrationSseClient> INTEGRATION_SSE = key("integration:sse");
    public static final Key<IntegrationSseSupport.IntegrationSseClient.Event> INTEGRATION_LAST_SSE_EVENT =
            key("integration:lastSseEvent");

    private ScenarioKeys() {
    }

    public static Key<Long> outboxId(String idempotencyKey) {
        return key("outbox:" + idempotencyKey);
    }

    public static Key<SseSupport.SseStreamClient> guestStream(String label) {
        return key("sse:guest:" + label);
    }

    public static Key<SseSupport.SseStreamClient> staffStream(String name) {
        return key("sse:staff:" + name);
    }

    public static Key<UUID> reservation(String label) {
        return key("reservation:" + label);
    }

    public static Key<UUID> sseTask(String label) {
        return key("sseTask:" + label);
    }

    public static Key<String> poolBody(String name) {
        return key("poolBody:" + name);
    }

    private static <T> Key<T> key(String name) {
        return new Key<>(name);
    }
}
