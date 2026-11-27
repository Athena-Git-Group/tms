package com.athena.tms.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.ServerSocket;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;

class StorageConfigTest {

    @Test
    void contextFailsWhenTheEmulatorIsUnreachable() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        String emulator = "http://localhost:" + closedPort;

        new ApplicationContextRunner()
                .withUserConfiguration(StorageConfig.class)
                .withPropertyValues("tms.storage.bucket=unreachable-photos", "tms.storage.mode=emulator",
                        "tms.storage.emulator-host=" + emulator)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause().isNotNull();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining(emulator)
                            .hasStackTraceContaining("unreachable-photos");
                });
    }

    @Test
    void contextFailsWhenEmulatorModeHasNoEmulatorHost() {
        new ApplicationContextRunner()
                .withUserConfiguration(StorageConfig.class)
                .withPropertyValues("tms.storage.bucket=local-photos", "tms.storage.mode=emulator")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("tms.storage.emulator-host");
                });
    }

    @Test
    void defaultModeIsTheEmulator() {
        StandardEnvironment environment = environmentWithoutOsVariables();
        ConfigDataEnvironmentPostProcessor.applyTo(environment, new DefaultResourceLoader(), null, List.of());

        assertThat(environment.getProperty("tms.storage.mode")).isEqualTo("emulator");
    }

    @Test
    void prodProfileUsesCloudStorage() {
        StandardEnvironment environment = new StandardEnvironment();
        ConfigDataEnvironmentPostProcessor.applyTo(environment, new DefaultResourceLoader(), null, List.of("prod"));

        assertThat(environment.getProperty("tms.storage.mode")).isEqualTo("gcs");
    }

    @Test
    void prodProfileRefusesToStartWithoutABucketName() {
        StandardEnvironment environment = environmentWithoutOsVariables();
        ConfigDataEnvironmentPostProcessor.applyTo(environment, new DefaultResourceLoader(), null, List.of("prod"));

        assertThatThrownBy(() -> environment.getProperty("tms.storage.bucket"))
                .hasMessageContaining("TMS_STORAGE_BUCKET");
    }

    @Test
    void prodProfileUsesTheBucketNameFromTheEnvironment() {
        StandardEnvironment environment = environmentWithoutOsVariables();
        environment.getPropertySources().addFirst(
                new MapPropertySource("deploy", Map.of("TMS_STORAGE_BUCKET", "hotel-photos-prod")));
        ConfigDataEnvironmentPostProcessor.applyTo(environment, new DefaultResourceLoader(), null, List.of("prod"));

        assertThat(environment.getProperty("tms.storage.bucket")).isEqualTo("hotel-photos-prod");
    }

    private static StandardEnvironment environmentWithoutOsVariables() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        return environment;
    }
}
