package com.athena.tms.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ServerSocket;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
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
                .withPropertyValues("tms.storage.bucket=unreachable-photos", "tms.storage.emulator-host=" + emulator)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause().isNotNull();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining(emulator)
                            .hasStackTraceContaining("unreachable-photos");
                });
    }

    @Test
    void prodProfileTurnsOffTheEmulator() {
        StandardEnvironment environment = new StandardEnvironment();
        ConfigDataEnvironmentPostProcessor.applyTo(environment, new DefaultResourceLoader(), null, List.of("prod"));

        assertThat(environment.getProperty("tms.storage.emulator-host")).isEmpty();
    }
}
