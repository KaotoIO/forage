package io.kaoto.forage.policy.flip;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.spi.RouteController;
import org.apache.camel.support.RoutePolicySupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A route policy that flips between two mutually exclusive routes.
 *
 * <p>When an exchange completes on the active route, this policy stops the
 * current route and starts the paired route. This enables alternating
 * processing patterns and failover scenarios.
 *
 * <p><strong>Behavior:</strong>
 * <ul>
 *   <li>On context start: only one route (with initially-active=true) starts</li>
 *   <li>On exchange completion: current route stops, paired route starts</li>
 *   <li>Flip is asynchronous to avoid blocking the exchange</li>
 * </ul>
 *
 * <p><strong>Configuration:</strong>
 * Both routes must be configured with flip policies referencing each other:
 * <pre>
 * forage.route.policy.routeA.name=flip
 * forage.route.policy.routeA.flip.paired-route=routeB
 * forage.route.policy.routeA.flip.initially-active=true
 *
 * forage.route.policy.routeB.name=flip
 * forage.route.policy.routeB.flip.paired-route=routeA
 * forage.route.policy.routeB.flip.initially-active=false
 * </pre>
 *
 * @since 1.0
 */
public class ForageFlipRoutePolicy extends RoutePolicySupport {
    private static final Logger LOG = LoggerFactory.getLogger(ForageFlipRoutePolicy.class);

    private final String pairedRouteId;
    private final boolean initiallyActive;
    private final AtomicBoolean flipping = new AtomicBoolean(false);

    private Route route;
    private ExecutorService executor;

    /**
     * Creates a new ForageFlipRoutePolicy with the given configuration.
     *
     * @param config the flip configuration
     */
    public ForageFlipRoutePolicy(FlipRoutePolicyConfig config) {
        this.pairedRouteId = config.pairedRouteId();
        this.initiallyActive = config.initiallyActive();
    }

    /**
     * Creates a new ForageFlipRoutePolicy with explicit parameters.
     *
     * @param pairedRouteId the ID of the paired route
     * @param initiallyActive whether this route should be active initially
     */
    public ForageFlipRoutePolicy(String pairedRouteId, boolean initiallyActive) {
        this.pairedRouteId = pairedRouteId;
        this.initiallyActive = initiallyActive;
    }

    @Override
    public void onInit(Route route) {
        LOG.info("Initializing flip route policy for route {}", route.getId());
        super.onInit(route);
        this.route = route;

        LOG.info(
                "Initializing flip policy for route '{}': pairedRoute={}, initiallyActive={}",
                route.getId(),
                pairedRouteId,
                initiallyActive);
    }

    @Override
    public void onStart(Route route) {
        LOG.info("Starting flip route policy for route {}", route.getId());
        super.onStart(route);

        // Create executor for async flipping
        executor = route.getCamelContext()
                .getExecutorServiceManager()
                .newSingleThreadExecutor(this, "FlipRoutePolicy-" + route.getId());

        // If this route should not be active initially, stop it
        if (!initiallyActive) {
            LOG.info("Route '{}' configured as initially inactive, stopping", route.getId());
            try {
                RouteController controller = route.getCamelContext().getRouteController();
                // Schedule the stop to happen after startup completes
                executor.submit(() -> {
                    try {
                        Thread.sleep(1000); // Wait for startup to complete
                        controller.stopRoute(route.getId());
                        LOG.info("Route '{}' stopped (initially inactive)", route.getId());
                    } catch (Exception e) {
                        LOG.warn("Failed to stop initially inactive route '{}': {}", route.getId(), e.getMessage());
                    }
                });
            } catch (Exception e) {
                LOG.warn("Error stopping initially inactive route '{}': {}", route.getId(), e.getMessage());
            }
        } else {
            LOG.info("Route '{}' starting as initially active", route.getId());
        }
    }

    @Override
    public void onStop(Route route) {
        super.onStop(route);

        if (executor != null) {
            executor.shutdown();
            executor = null;
        }

        LOG.info("Stopped flip policy for route '{}'", route.getId());
    }

    @Override
    public void onExchangeBegin(Route route, Exchange exchange) {
        // No action needed
    }

    @Override
    public void onExchangeDone(Route route, Exchange exchange) {
        // Flip routes after exchange completes
        if (flipping.compareAndSet(false, true)) {
            LOG.info("Exchange completed on route '{}', flipping to paired route '{}'", route.getId(), pairedRouteId);
            flipRoutes();
        } else {
            LOG.debug("Flip already in progress for route '{}', skipping", route.getId());
        }
    }

    private void flipRoutes() {
        if (executor == null || executor.isShutdown()) {
            LOG.warn("Executor not available for route '{}', cannot flip", route.getId());
            flipping.set(false);
            return;
        }

        executor.submit(() -> {
            try {
                RouteController controller = route.getCamelContext().getRouteController();
                String currentRouteId = route.getId();

                // Stop current route
                LOG.debug("Stopping route '{}'", currentRouteId);
                controller.stopRoute(currentRouteId);
                LOG.info("Stopped route '{}'", currentRouteId);

                // Start paired route
                LOG.debug("Starting paired route '{}'", pairedRouteId);
                controller.startRoute(pairedRouteId);
                LOG.info("Started paired route '{}', flip complete", pairedRouteId);
            } catch (Exception e) {
                LOG.warn(
                        "Failed to flip from route '{}' to route '{}': {}",
                        route.getId(),
                        pairedRouteId,
                        e.getMessage());
                LOG.debug("Flip failure details", e);
            } finally {
                flipping.set(false);
            }
        });
    }
}
