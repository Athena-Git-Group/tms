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

/**
 * 員工事件串流的句型共用的連線入口：以某位員工的憑證連 {@code GET /staff/stream}，把收到的訊號依序記下來。
 *
 * <p>串流是一條不會自己結束的長連線，一般的 {@link BddApiClient} 會等回應讀完才回來，所以另外寫這一支。
 * 每個場景一份：這個場景開過的連線都記在這裡，場景結束時由 {@link #close()} 全部關掉，
 * 句型不必自己收尾。沒關的連線會一直佔著後端的連線名額，也會讓讀取的執行緒留到整個測試套件結束。
 *
 * <p>這裡只負責連線、記錄與等待，不判斷誰該收到什麼：連線被拒絕也照樣回傳，
 * 等不到訊號回傳空值，成功或失敗由句型決定。
 *
 * <p>後端另外每 15 秒對所有連線送一則心跳，測試不會也不該把它停掉，
 * 所以任何一條連線的紀錄裡都可能夾著句型沒有觸發的 {@code heartbeat}，比對順序時要略過它。
 */
public class StaffStreamClient implements AutoCloseable {

    /** 模組 {@code dsl.md} 規定 Then 最長等 10 秒；句型沒有特別理由就用這個上限。 */
    public static final Duration DEFAULT_WAIT = Duration.ofSeconds(10);

    private static final String STREAM_PATH = "/staff/stream";

    /** 後端在本機，回應標頭與被拒絕時的錯誤內容都該立刻回來；超過這個時間就當成測試環境壞了。 */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    /** 關線後讀取的執行緒應該馬上結束；只等一下，免得一條卡住的連線拖住每個場景的收尾。 */
    private static final Duration READER_JOIN_TIMEOUT = Duration.ofSeconds(2);

    /**
     * {@link #close()} 觸發的兩次心跳之間等多久。
     * 第一次寫入的結果要從作業系統傳回後端，第二次寫入才會失敗；本機來回遠小於這個時間。
     */
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

    /**
     * 以這位員工的憑證連上串流，連上或被拒絕都回傳。
     * 不在這裡等 {@code init}，因為被拒絕的連線永遠等不到，要不要等由句型決定。
     * 同一位員工在同一個場景再連一次時，舊的那條不會被關掉，因為驗重連的場景要兩條都在。
     * 之後 {@link #connection(String)} 拿到的是新的這一條。
     */
    public synchronized Connection connect(String staffName) {
        Connection connection = open(world.jwt(staffName));
        opened.add(connection);
        latestByStaff.put(staffName, connection);
        lastAttempt = connection;
        return connection;
    }

    /** 這位員工最近一次開的連線。 */
    public synchronized Connection connection(String staffName) {
        Connection connection = latestByStaff.get(staffName);
        if (connection == null) {
            throw new IllegalStateException("「" + staffName + "」在本場景尚未連過員工事件串流");
        }
        return connection;
    }

    /** 本場景最近一次連線嘗試，給「這次連線被拒絕」這類不帶員工姓名的句型用。 */
    public synchronized Connection lastAttempt() {
        if (lastAttempt == null) {
            throw new IllegalStateException("本場景尚未連過員工事件串流");
        }
        return lastAttempt;
    }

    /** 本場景開過的每一條連線，依開的先後。 */
    public synchronized List<Connection> connections() {
        return List.copyOf(opened);
    }

    /**
     * 保證場景結束後，這個場景開過的連線在測試這一頭與後端那一頭都不再存在。場景結束時由 Spring 呼叫。
     *
     * <p>測試這一頭：關掉每一條連線，連 {@link HttpClient} 一起關。
     * {@link HttpClient} 自己有一條背景執行緒，每個場景各留一條會越積越多。
     *
     * <p>後端那一頭：後端不會主動發現客戶端已經關線，要到它第二次往這條連線寫資料才會放掉。
     * 所以這裡關線後立刻觸發兩次心跳，讓後端當場寫失敗、當場放掉。
     * 這時本場景的連線都已經關了，多出來的心跳不會出現在任何句型讀得到的紀錄裡。
     *
     * <p>不這樣做的話，後端要等到之後的第二次定時心跳才放，最久約 30 秒。
     * 測試套件若在這段時間內結束，後端關機會為了這條還沒放掉的連線多等 30 秒。
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

    /** 串流上的一則訊號：事件名稱與 {@code data} 那一行解出來的 JSON。事件名稱例如 {@code init}、{@code task}。 */
    public record Event(String name, JsonNode data) {
    }

    /**
     * 一次連線嘗試。連上的話背景有一條執行緒一直讀，收到的訊號依序放進紀錄；
     * 被拒絕的話只有狀態碼與錯誤內容，紀錄永遠是空的。
     */
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
                // 就算有場景沒收尾，也不能讓這條執行緒擋住整個測試套件結束。
                this.reader.setDaemon(true);
                this.reader.start();
            }
        }

        /** 連線的 HTTP 狀態碼；200 才有訊號可收。 */
        public int status() {
            return status;
        }

        /** 被拒絕時的回應內容，形狀是契約的 {@code Error}；連上時是 null。 */
        public String errorBody() {
            return errorBody;
        }

        /** 到目前為止收到的訊號，依收到的先後；回傳的是當下的副本。 */
        public List<Event> events() {
            synchronized (lock) {
                return List.copyOf(events);
            }
        }

        /** 等到出現這個名稱的訊號，上限 {@code timeout}；等不到回傳空值。已經收到的也算。 */
        public Optional<Event> awaitEvent(String name, Duration timeout) {
            return awaitEvent(event -> event.name().equals(name), timeout);
        }

        /**
         * 等到有一則訊號符合條件，上限 {@code timeout}；等不到回傳空值，不丟例外。
         * 從第一則開始比，所以先收到、後來才問也找得到。
         * 連線已經結束而且沒有符合的，不會等滿時間，立刻回傳空值。哪些情況算結束見 {@link #ended()}。
         */
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

        /** 連線是不是已經不會再有訊號進來。被拒絕、被後端收掉、讀取出錯、測試自己關線，四種都算。 */
        public boolean ended() {
            synchronized (lock) {
                return ended;
            }
        }

        /** 給句型組失敗訊息用：狀態碼、收到的每一則訊號，以及讀取中途出錯的原因。 */
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

        /**
         * 關掉這條連線；重複呼叫沒有作用。場景結束時 {@link StaffStreamClient#close()} 會代為呼叫。
         * 場景中途自己關的話，這裡只關測試這一頭，後端那一頭何時放掉見 {@link StaffStreamClient#close()}。
         * 這裡不代為觸發心跳，因為那會讓同一個場景裡其他還開著的連線多收到心跳。
         */
        public void close() {
            closedByTest = true;
            if (body == null) {
                return;
            }
            try {
                body.close();
            } catch (IOException ignored) {
                // 這條連線之後沒有人會再讀，關的時候出錯不會讓任何句型看到不同的結果，所以不往外丟。
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
                // 測試自己關線時讀取一定會丟例外，那不是錯；只有不是自己關的才記下來。
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
