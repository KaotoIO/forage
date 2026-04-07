package io.kaoto.forage.core.util.config;

import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link PropertyFileSource} that locates property files on the classpath
 * using the thread's context classloader.
 *
 * @since 1.2
 */
public final class ClassPathPropertyFileSource implements PropertyFileSource {

    private static final Logger LOG = LoggerFactory.getLogger(ClassPathPropertyFileSource.class);

    @Override
    public InputStream locate(String fileName) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl != null) {
            InputStream is = cl.getResourceAsStream(fileName);
            if (is != null) {
                LOG.debug("Loading {} from classpath via context classloader", fileName);
                return is;
            }
        }
        cl = ClassPathPropertyFileSource.class.getClassLoader();
        if (cl != null) {
            InputStream is = cl.getResourceAsStream(fileName);
            if (is != null) {
                LOG.debug("Loading {} from classpath via ClassPathPropertyFileSource classloader", fileName);
                return is;
            }
        }
        return null;
    }

    @Override
    public int priority() {
        return 100;
    }
}
