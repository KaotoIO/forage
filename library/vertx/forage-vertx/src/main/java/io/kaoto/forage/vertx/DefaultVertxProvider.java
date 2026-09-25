package io.kaoto.forage.vertx;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.kaoto.forage.core.annotations.ForageBean;
import io.kaoto.forage.core.vertx.VertxProvider;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

/**
 * Provider for creating Vert.x instances with configurable parameters.
 *
 * <p>This provider creates instances of {@link Vertx} using configuration
 * values managed by {@link VertxConfig}. The configuration supports environment
 * variables, system properties, and configuration files for flexible deployment.
 *
 * <p><strong>Configuration:</strong>
 * <ul>
 *   <li>Worker Pool Size: Configured via VERTX_WORKER_POOL_SIZE environment variable (no default)</li>
 *   <li>Event Loop Pool Size: Configured via VERTX_EVENT_LOOP_POOL_SIZE environment variable (no default)</li>
 *   <li>Clustered: Configured via VERTX_CLUSTERED environment variable (default: false)</li>
 * </ul>
 *
 * <p><strong>Usage:</strong>
 * <pre>{@code
 * // Configuration is automatic through environment variables or defaults
 * DefaultVertxProvider provider = new DefaultVertxProvider();
 * Vertx vertx = provider.create();
 * }</pre>
 *
 * @see VertxConfig
 * @see VertxProvider
 * @since 1.0
 */
@ForageBean(
        value = "default-vertx",
        components = {"camel-vertx"},
        description = "Default Vert.x instance provider")
public class DefaultVertxProvider implements VertxProvider {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultVertxProvider.class);

    /**
     * Creates a new Vert.x instance with the configured parameters.
     *
     * <p>This method creates a {@link Vertx} instance using the configuration
     * settings. If clustered mode is enabled, it will attempt to create a clustered
     * Vert.x instance. Otherwise, it creates a standard non-clustered instance.
     *
     * @param id optional identifier for prefixed configuration
     * @return a new configured Vert.x instance
     */
    @Override
    public Vertx create(String id) {
        final VertxConfig config = new VertxConfig(id);

        Boolean clustered = config.clustered();

        LOG.trace("Creating Vert.x instance with configuration: clustered={}", clustered);

        VertxOptions options = new VertxOptions()
                .setEventLoopPoolSize(config.eventLoopPoolSize())
                .setWorkerPoolSize(config.workerPoolSize())
                .setBlockedThreadCheckInterval(config.blockedThreadCheckInterval())
                .setMaxEventLoopExecuteTime(config.maxEventLoopExecuteTime())
                .setMaxWorkerExecuteTime(config.maxEventLoopExecuteTime())
                .setInternalBlockingPoolSize(config.internalBlockingPoolSize())
                .setHAEnabled(config.haEnabled())
                .setQuorumSize(config.quorumSize())
                .setHAGroup(config.haGroup())
                .setWarningExceptionTime(config.warningExceptionTime())
                .setPreferNativeTransport(config.preferNativeTransport())
                .setMaxEventLoopExecuteTimeUnit(config.maxEventLoopExecuteTimeUnit())
                .setMaxWorkerExecuteTimeUnit(config.maxWorkerExecuteTimeUnit())
                .setWarningExceptionTimeUnit(config.warningExceptionTimeUnit())
                .setBlockedThreadCheckIntervalUnit(config.blockedThreadCheckIntervalUnit())
                .setDisableTCCL(config.disableTccl())
                .setUseDaemonThread(config.useDaemonThread());

        if (clustered != null && clustered) {
            try {
                return Vertx.clusteredVertx(options)
                        .toCompletionStage()
                        .toCompletableFuture()
                        .get(30, TimeUnit.SECONDS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(ie);
            } catch (TimeoutException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
        return Vertx.vertx(options);
    }
}
