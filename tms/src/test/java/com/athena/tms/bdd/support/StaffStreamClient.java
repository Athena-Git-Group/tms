package com.athena.tms.bdd.support;

import com.athena.tms.bdd.ScenarioWorld;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import org.springframework.core.env.Environment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class StaffStreamClient implements AutoCloseable {

    public static final Duration DEFAULT_WAIT = Duration.ofSeconds(10);

    private static final String STREAM_PATH = "/staff/stream";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    private static final Duration READER_JOIN_TIMEOUT = Duration.ofSeconds(2);

    private static final Duration SERVER_RELEASE_PAUSE = Duration.ofMillis(200);

    private final Environment environment;
    private final ObjectMapper objectMapper;
    private final ScenarioWorld world;
    private final JobTrigger jobTrigger;

    private final List<Connection> opened = new ArrayList<>();
    private final Map<String, Connection> latestByStaff = new LinkedHashMap<>();
    private Connection lastAttempt;
    private HttpClient http;

    public StaffStreamClient(
            Environment environment, ObjectMapper objectMapper, ScenarioWorld world, JobTrigger jobTrigger) {
        this.environment = environment;
        this.objectMapper = objectMapper;
        this.world = world;
        this.jobTrigger = jobTrigger;
    }

    public synchronized Connection connect(String staffName) {
        Connection connection = open(world.jwt(staffName));
        opened.add(connection);
        latestByStaff.put(staffName, connection);
        lastAttempt = connection;
        return connection;
    }

    public synchronized Connection connection(String staffName) {
        Connection connection = latestByStaff.get(staffName);
        if (connection == null) {
            throw new IllegalStateException("「" + staffName + "」在本場景尚未連過員工事件串流");
        }
        return connection;
    }

    public synchronized Connection lastAttempt() {
        if (lastAttempt == null) {
            throw new IllegalStateException("本場景尚未連過員工事件串流");
        }
        return lastAttempt;
    }

    public synchronized Map<String, Connection> latestConnections() {
        return new LinkedHashMap<>(latestByStaff);
    }

    public synchronized List<Connection> connections() {
        return List.copyOf(opened);
    }

    /**
     * 後端不會主動發現客戶端已經關線，要到它第二次往這條連線寫資料才會放掉。
     * 所以這裡關線後立刻觸發兩次心跳，讓後端當場寫失敗、當場放掉。
     */
    @Override
    public synchronized void close() {
        boolean serverHoldsConnections = false;
        for (Connection connection : opened) {
            serverHoldsConnections |= connection.status() == 200;
            connection.close();
        }
        if (http != null) {
            http.shutdownNow();
            http = null;
        }
        for (Connection connection : opened) {
            connection.awaitReaderStopped(READER_JOIN_TIMEOUT);
        }
        opened.clear();
        if (serverHoldsConnections) {
            releaseOnServer();
        }
    }

    private void releaseOnServer() {
        jobTrigger.run("stream-heartbeat");
        try {
            Thread.sleep(SERVER_RELEASE_PAUSE.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        jobTrigger.run("stream-heartbeat");
    }

    private Connection open(String jwt) {
        if (http == null) {
            // 固定用 HTTP/1.1：一條連線只載這一個回應，關掉回應就等於關掉這條 TCP 連線。
            http = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(CONNECT_TIMEOUT)
                    .build();
        }
        String port = environment.getRequiredProperty("local.server.port");
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + STREAM_PATH))
                .header("Authorization", "Bearer " + jwt)
                .header("Accept", "text/event-stream")
                .GET()
                .build();
        HttpResponse<InputStream> response = await(
                http.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()), "連上員工事件串流");
        if (response.statusCode() != 200) {
            InputStream body = response.body();
            String errorBody = await(CompletableFuture.supplyAsync(() -> readAll(body)), "讀取串流被拒絕的回應");
            return new Connection(response.statusCode(), errorBody, null, objectMapper);
        }
        return new Connection(200, null, response.body(), objectMapper);
    }

    private static <T> T await(CompletableFuture<T> future, String what) {
        try {
            return future.get(CONNECT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(what + "時被中斷", e);
        } catch (ExecutionException | TimeoutException e) {
            future.cancel(true);
            throw new IllegalStateException(what + "失敗，上限 " + CONNECT_TIMEOUT.toSeconds() + " 秒", e);
        }
    }

    private static String readAll(InputStream body) {
        try (body) {
            return new String(body.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("讀取串流被拒絕的回應失敗", e);
        }
    }

    public record Event(String name, JsonNode data) {
    }

    public static final class Connection {

        private final int status;
        private final String errorBody;
        private final InputStream body;
        private final ObjectMapper objectMapper;
        private final Thread reader;

        private final Object lock = new Object();
        private final List<Event> events = new ArrayList<>();
        private boolean ended;
        private Throwable readFailure;
        private volatile boolean closedByTest;

        private Connection(int status, String errorBody, InputStream body, ObjectMapper objectMapper) {
            this.status = status;
            this.errorBody = errorBody;
            this.body = body;
            this.objectMapper = objectMapper;
            if (body == null) {
                this.ended = true;
                this.reader = null;
            } else {
                this.reader = new Thread(this::readLoop, "bdd-staff-stream-reader");
                this.reader.setDaemon(true);
                this.reader.start();
            }
        }

        public int status() {
            return status;
        }

        public String errorBody() {
            return errorBody;
        }

        public List<Event> events() {
            synchronized (lock) {
                return List.copyOf(events);
            }
        }

        public Optional<Event> awaitEvent(String name, Duration timeout) {
            return awaitEvent(event -> event.name().equals(name), timeout);
        }

        public Optional<Event> awaitEvent(Predicate<Event> match, Duration timeout) {
            long deadline = System.nanoTime() + timeout.toNanos();
            int checked = 0;
            synchronized (lock) {
                while (true) {
                    for (; checked < events.size(); checked++) {
                        Event event = events.get(checked);
                        if (match.test(event)) {
                            return Optional.of(event);
                        }
                    }
                    long remainingNanos = deadline - System.nanoTime();
                    if (ended || remainingNanos <= 0) {
                        return Optional.empty();
                    }
                    try {
                        TimeUnit.NANOSECONDS.timedWait(lock, remainingNanos);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return Optional.empty();
                    }
                }
            }
        }

        public boolean ended() {
            synchronized (lock) {
                return ended;
            }
        }

        public String describe() {
            synchronized (lock) {
                StringBuilder text = new StringBuilder("status=").append(status);
                if (errorBody != null) {
                    text.append("，body=").append(errorBody);
                }
                text.append("，已收到 ").append(events.size()).append(" 則：");
                for (Event event : events) {
                    text.append(event.name()).append(event.data()).append(' ');
                }
                if (readFailure != null) {
                    text.append("，讀取中斷：").append(readFailure);
                }
                return text.toString();
            }
        }

        public void close() {
            closedByTest = true;
            if (body == null) {
                return;
            }
            try {
                body.close();
            } catch (IOException ignored) {
            }
            reader.interrupt();
        }

        private void awaitReaderStopped(Duration timeout) {
            if (reader == null) {
                return;
            }
            try {
                reader.join(timeout.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        private void readLoop() {
            Throwable failure = null;
            try (BufferedReader lines = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
                String name = null;
                StringBuilder data = new StringBuilder();
                String line;
                while ((line = lines.readLine()) != null) {
                    if (line.isEmpty()) {
                        if (name != null || !data.isEmpty()) {
                            record(new Event(
                                    name == null ? "message" : name,
                                    data.isEmpty() ? null : objectMapper.readTree(data.toString())));
                        }
                        name = null;
                        data.setLength(0);
                    } else if (line.startsWith("event:")) {
                        name = line.substring("event:".length()).trim();
                    } else if (line.startsWith("data:")) {
                        if (!data.isEmpty()) {
                            data.append('\n');
                        }
                        data.append(line.substring("data:".length()).trim());
                    }
                }
            } catch (Exception e) {
                if (!closedByTest) {
                    failure = e;
                }
            }
            synchronized (lock) {
                ended = true;
                readFailure = failure;
                lock.notifyAll();
            }
        }

        private void record(Event event) {
            synchronized (lock) {
                events.add(event);
                lock.notifyAll();
            }
        }
    }
}
