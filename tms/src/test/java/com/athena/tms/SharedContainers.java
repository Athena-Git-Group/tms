package com.athena.tms;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Map;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 整個 JVM 只起一個 Postgres、一個 Kafka 與一個 fake-gcs-server（照片儲存），所有測試套件共用。
 *
 * <p>共用容器但不共用資料：每份 Spring context 設定拿自己的資料庫與 topic。
 * 先跑完的套件，它的 context 會留在快取裡，背景的 outbox relay 與 Kafka 消費者還在跑；
 * 若和下一個套件同庫同 topic，就會搶走對方的 outbox 列、讀到對方的事件，或被對方每個場景的清表清掉種子資料。
 */
public final class SharedContainers {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:4.0.0"));

    private static final int FAKE_GCS_PORT = 4443;

    private static final GenericContainer<?> FAKE_GCS =
            new GenericContainer<>(DockerImageName.parse("fsouza/fake-gcs-server:1.56.1"))
                    .withCommand("-scheme", "http", "-port", String.valueOf(FAKE_GCS_PORT))
                    .withExposedPorts(FAKE_GCS_PORT);

    private SharedContainers() {
    }

    public static synchronized PostgreSQLContainer<?> postgres() {
        if (!POSTGRES.isRunning()) {
            POSTGRES.start();
        }
        return POSTGRES;
    }

    public static synchronized KafkaContainer kafka() {
        if (!KAFKA.isRunning()) {
            KAFKA.start();
        }
        return KAFKA;
    }

    public static synchronized GenericContainer<?> fakeGcs() {
        if (!FAKE_GCS.isRunning()) {
            FAKE_GCS.start();
        }
        return FAKE_GCS;
    }

    public static String fakeGcsUrl() {
        GenericContainer<?> container = fakeGcs();
        return "http://" + container.getHost() + ":" + container.getMappedPort(FAKE_GCS_PORT);
    }

    public static void wireStorage(DynamicPropertyRegistry registry, String bucket) {
        registry.add("tms.storage.emulator-host", SharedContainers::fakeGcsUrl);
        registry.add("tms.storage.bucket", () -> bucket);
    }

    public static synchronized String database(String name) {
        PostgreSQLContainer<?> postgres = postgres();
        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement statement = connection.createStatement()) {
            try (ResultSet exists = statement.executeQuery(
                    "SELECT 1 FROM pg_database WHERE datname = '" + name + "'")) {
                if (!exists.next()) {
                    statement.execute("CREATE DATABASE " + name);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("建立測試資料庫失敗：" + name, e);
        }
        return postgres.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + name + "$1");
    }

    public static synchronized void createTopics(String... topics) {
        try (AdminClient admin = AdminClient.create(Map.of("bootstrap.servers", kafka().getBootstrapServers()))) {
            var existing = admin.listTopics().names().get();
            var missing = Arrays.stream(topics)
                    .filter(topic -> !existing.contains(topic))
                    .map(topic -> new NewTopic(topic, 1, (short) 1))
                    .toList();
            admin.createTopics(missing).all().get();
        } catch (Exception e) {
            throw new IllegalStateException("建立 Kafka topic 失敗", e);
        }
    }
}
