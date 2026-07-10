package io.kaoto.forage.core.util.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class HomeDirectoryPropertyFileSource implements PropertyFileSource {

    private static final Logger LOG = LoggerFactory.getLogger(HomeDirectoryPropertyFileSource.class);

    static final String PROPERTY = "forage.home.dir";
    static final String ENV_VAR = "FORAGE_HOME_DIR";

    private final Supplier<String> homeDirSupplier;

    HomeDirectoryPropertyFileSource() {
        this(HomeDirectoryPropertyFileSource::resolveHomeDir);
    }

    HomeDirectoryPropertyFileSource(Supplier<String> homeDirSupplier) {
        this.homeDirSupplier = homeDirSupplier;
    }

    @Override
    public InputStream locate(String fileName) {
        String homeDir = homeDirSupplier.get();
        if (homeDir != null) {
            Path file = Path.of(homeDir, fileName).toAbsolutePath();
            if (file.toFile().exists()) {
                try {
                    LOG.info("Loading {} from home directory ({})", fileName, homeDir);
                    return new FileInputStream(file.toFile());
                } catch (IOException e) {
                    LOG.debug("Failed to load {} from home directory ({})", fileName, homeDir, e);
                }
            }
        }
        return null;
    }

    @Override
    public int priority() {
        return 150;
    }

    static String resolveHomeDir() {
        String homeDir = System.getProperty(PROPERTY);
        if (homeDir == null) {
            homeDir = System.getenv(ENV_VAR);
        }
        if (homeDir == null) {
            String userHome = System.getProperty("user.home");
            if (userHome != null) {
                homeDir = Path.of(userHome, ".forage").toString();
            }
        }
        return homeDir;
    }
}
