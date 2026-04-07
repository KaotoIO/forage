package io.kaoto.forage.core.util.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link PropertyFileSource} that locates property files in the Forage configuration directory.
 *
 * <p>The configuration directory is resolved from:
 * <ol>
 *   <li>The {@code forage.config.dir} system property</li>
 *   <li>The {@code FORAGE_CONFIG_DIR} environment variable</li>
 * </ol>
 *
 * @since 1.2
 */
public final class ConfigDirPropertyFileSource implements PropertyFileSource {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigDirPropertyFileSource.class);

    private final Supplier<String> configDirSupplier;

    public ConfigDirPropertyFileSource() {
        this(PropertyFileLocator::resolveConfigDir);
    }

    ConfigDirPropertyFileSource(Supplier<String> configDirSupplier) {
        this.configDirSupplier = configDirSupplier;
    }

    @Override
    public InputStream locate(String fileName) {
        String configDir = configDirSupplier.get();
        if (configDir != null) {
            File file = Path.of(configDir, fileName).toAbsolutePath().toFile();
            if (file.exists()) {
                try {
                    LOG.debug("Loading {} from config dir: {}", fileName, file.getAbsolutePath());
                    return new FileInputStream(file);
                } catch (IOException e) {
                    LOG.debug("Failed to load {} from config dir", fileName, e);
                }
            }
        }
        return null;
    }

    @Override
    public int priority() {
        return 200;
    }
}
