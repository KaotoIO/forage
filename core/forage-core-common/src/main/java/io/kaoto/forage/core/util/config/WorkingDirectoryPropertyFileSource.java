package io.kaoto.forage.core.util.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link PropertyFileSource} that locates property files in the current working directory.
 *
 * @since 1.2
 */
public final class WorkingDirectoryPropertyFileSource implements PropertyFileSource {

    private static final Logger LOG = LoggerFactory.getLogger(WorkingDirectoryPropertyFileSource.class);

    @Override
    public InputStream locate(String fileName) {
        File file = Path.of("", fileName).toAbsolutePath().toFile();
        if (file.exists()) {
            try {
                LOG.debug("Loading {} from working directory: {}", fileName, file.getAbsolutePath());
                return new FileInputStream(file);
            } catch (IOException e) {
                LOG.debug("Failed to load {} from working directory", fileName, e);
            }
        }
        return null;
    }

    @Override
    public int priority() {
        return 300;
    }
}
