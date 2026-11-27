package com.athena.tms.storage;

import java.util.Optional;

public interface PhotoStorage {

    void put(String key, byte[] jpeg);

    Optional<byte[]> open(String key);
}
