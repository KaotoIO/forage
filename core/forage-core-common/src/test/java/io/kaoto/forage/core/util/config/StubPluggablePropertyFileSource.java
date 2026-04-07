package io.kaoto.forage.core.util.config;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Test-only {@link PluggablePropertyFileSource} discovered via ServiceLoader
 * to validate that {@link PluggablePropertyFileSourceLoader} can discover,
 * cache, and reload pluggable sources.
 */
public class StubPluggablePropertyFileSource implements PluggablePropertyFileSource {

    static final String STUB_FILE_NAME = "stub-pluggable.properties";
    static final int STUB_PRIORITY = 50;

    @Override
    public InputStream locate(String fileName) {
        if (STUB_FILE_NAME.equals(fileName)) {
            return new ByteArrayInputStream("stub.key=stub-value\n".getBytes(StandardCharsets.UTF_8));
        }
        return null;
    }

    @Override
    public int priority() {
        return STUB_PRIORITY;
    }
}
