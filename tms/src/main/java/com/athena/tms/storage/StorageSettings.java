package com.athena.tms.storage;

import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("tms.storage")
record StorageSettings(StorageMode mode, @DefaultValue("") String emulatorHost) {

    StorageSettings {
        Objects.requireNonNull(mode, "要設 tms.storage.mode（emulator 或 gcs）");
    }
}
