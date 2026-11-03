package io.kaoto.forage.core.jta.recovery;

import javax.transaction.xa.XAResource;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jboss.tm.XAResourceRecovery;
import org.jboss.tm.XAResourceRecoveryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.arjuna.ats.arjuna.recovery.RecoveryManager;
import com.arjuna.ats.arjuna.recovery.RecoveryModule;
import com.arjuna.ats.internal.jta.recovery.arjunacore.XARecoveryModule;
import com.arjuna.ats.jta.recovery.XAResourceRecoveryHelper;

/**
 * JVM-global coordinator for Narayana periodic crash recovery, shared by the Forage JDBC and JMS
 * modules (the {@link RecoveryManager} and {@link XARecoveryModule} are JVM-wide singletons, so
 * start-once/stop-last must be coordinated across modules — see issue #432).
 *
 * <p>The recovery manager is started lazily on the first helper registration and is only ever
 * started here — modules that configure recovery but never register a helper do not cause a
 * recovery thread to run. Helpers are tracked under a caller-supplied key (e.g. {@code "jms:myBroker"})
 * so that dev-mode reloads can swap the helpers of one module instance without touching others,
 * and so the manager can be terminated once the last registration is gone.
 *
 * <p>On Quarkus, quarkus-narayana-jta owns the recovery manager lifecycle. When
 * {@code io.quarkus.narayana.jta.runtime.QuarkusRecoveryService} is on the classpath this service
 * never starts its own manager and instead delegates helper registration to the Quarkus recovery
 * service (which buffers registrations made before it is created, so ordering is safe). Stop
 * methods are no-ops in that case.
 *
 * <p>Note: helpers are keyed by configuration id only, so two CamelContexts in the same JVM using
 * the same prefix would share (and replace) each other's registrations. Recovery state is JVM-global
 * in Narayana anyway, so this is acceptable.
 */
public final class ForageRecoveryService {
    private static final Logger LOG = LoggerFactory.getLogger(ForageRecoveryService.class);

    private static final String QUARKUS_RECOVERY_SERVICE = "io.quarkus.narayana.jta.runtime.QuarkusRecoveryService";

    private static final ForageRecoveryService INSTANCE = new ForageRecoveryService();

    private final boolean quarkusManaged;

    private RecoveryManager recoveryManager;
    private XARecoveryModule xaRecoveryModule;

    /**
     * Registered helpers by key. For helpers that entered through {@link #scopedRegistry(String)}
     * (or that were delegated to Quarkus) the originating {@link XAResourceRecovery} is kept so the
     * exact same instance can be removed again.
     */
    private final Map<String, List<Registration>> registrations = new LinkedHashMap<>();

    private record Registration(XAResourceRecoveryHelper helper, XAResourceRecovery source) {}

    private ForageRecoveryService() {
        this.quarkusManaged = isClassPresent(QUARKUS_RECOVERY_SERVICE);
        if (quarkusManaged) {
            LOG.debug("quarkus-narayana-jta detected; delegating XA recovery registrations to Quarkus");
        }
    }

    public static ForageRecoveryService getInstance() {
        return INSTANCE;
    }

    /**
     * Whether quarkus-narayana-jta owns the Narayana lifecycle (recovery manager and object store
     * configuration) in this JVM.
     */
    public boolean isQuarkusManaged() {
        return quarkusManaged;
    }

    /**
     * Whether this service has started the Narayana recovery manager. Once it runs, its recovery
     * modules have opened the transaction object store, so the store implementation can no longer
     * be changed in this JVM.
     */
    public synchronized boolean isRecoveryManagerStarted() {
        return recoveryManager != null;
    }

    /**
     * Registers a recovery helper under the given key, starting the recovery manager on first use.
     * Re-registering an existing key replaces the previously registered helpers (reload safety).
     */
    public synchronized void registerHelper(String key, XAResourceRecoveryHelper helper) {
        deregisterHelpers(key);

        if (quarkusManaged) {
            XAResourceRecovery source = toXaResourceRecovery(helper);
            delegateToQuarkus("addXAResourceRecovery", source);
            registrations.computeIfAbsent(key, k -> new ArrayList<>()).add(new Registration(helper, source));
            LOG.debug("Registered XA recovery helper '{}' with the Quarkus recovery service", key);
            return;
        }

        ensureStarted();
        xaRecoveryModule.addXAResourceRecoveryHelper(helper);
        registrations.computeIfAbsent(key, k -> new ArrayList<>()).add(new Registration(helper, null));
        LOG.info("Registered XA recovery helper '{}'", key);
    }

    /**
     * Removes all helpers registered under the given key. The recovery manager keeps running (a
     * dev-mode reload re-registers fresh helpers right after); use {@link #stopIfNoRegistrations()}
     * on shutdown paths.
     */
    public synchronized void deregisterHelpers(String key) {
        List<Registration> removed = registrations.remove(key);
        if (removed == null) {
            return;
        }
        for (Registration registration : removed) {
            if (quarkusManaged) {
                delegateToQuarkus("removeXAResourceRecovery", registration.source());
            } else if (xaRecoveryModule != null) {
                xaRecoveryModule.removeXAResourceRecoveryHelper(registration.helper());
            }
        }
        LOG.debug("Deregistered {} XA recovery helper(s) for '{}'", removed.size(), key);
    }

    /**
     * Returns an {@link XAResourceRecoveryRegistry} view whose entries are tracked under the given
     * key, for integrations that speak the JBoss SPI (Agroal registers each XA-enabled datasource
     * through this and removes it again when the datasource is closed).
     */
    public XAResourceRecoveryRegistry scopedRegistry(String key) {
        return new ScopedRegistry(key);
    }

    /**
     * Terminates the recovery manager if (and only if) no helpers remain registered. Called by each
     * module's stop path so the last module out stops the shared manager, without ref-counting.
     */
    public synchronized void stopIfNoRegistrations() {
        if (!registrations.isEmpty()) {
            LOG.debug("XA recovery manager kept running; {} registration key(s) remain", registrations.size());
            return;
        }
        terminate();
    }

    /**
     * Deregisters all helpers and terminates the recovery manager unconditionally (Spring context
     * close). Idempotent; a later registration can start a fresh manager in the same JVM.
     */
    public synchronized void shutdown() {
        for (String key : List.copyOf(registrations.keySet())) {
            deregisterHelpers(key);
        }
        terminate();
    }

    private void ensureStarted() {
        if (recoveryManager != null) {
            return;
        }
        // delayRecoveryManagerThread() must precede manager(): otherwise the INDIRECT_MANAGEMENT
        // singleton starts scanning before any helper is registered. Never use
        // XARecoveryModule.getRegisteredXARecoveryModule() — it force-creates the manager and
        // caches the module in a static that survives terminate().
        RecoveryManager.delayRecoveryManagerThread();
        recoveryManager = RecoveryManager.manager(RecoveryManager.INDIRECT_MANAGEMENT);
        xaRecoveryModule = findXaRecoveryModule(recoveryManager);
        if (xaRecoveryModule == null) {
            LOG.warn(
                    "Configured recovery modules do not include {}; adding it so XA resources can be recovered",
                    XARecoveryModule.class.getName());
            xaRecoveryModule = new XARecoveryModule();
            recoveryManager.addModule(xaRecoveryModule);
        }
        recoveryManager.startRecoveryManagerThread();
        LOG.info("Narayana recovery manager started");
    }

    private void terminate() {
        // Under Quarkus the manager is never started here (recoveryManager stays null),
        // so this is naturally a no-op there.
        if (recoveryManager == null) {
            return;
        }
        LOG.info("Terminating Narayana recovery manager");
        recoveryManager.terminate(false);
        recoveryManager = null;
        xaRecoveryModule = null;
    }

    private static XARecoveryModule findXaRecoveryModule(RecoveryManager manager) {
        for (RecoveryModule module : manager.getModules()) {
            if (module instanceof XARecoveryModule xaModule) {
                return xaModule;
            }
        }
        return null;
    }

    private static XAResourceRecovery toXaResourceRecovery(XAResourceRecoveryHelper helper) {
        return () -> {
            try {
                XAResource[] resources = helper.getXAResources();
                return resources != null ? resources : new XAResource[0];
            } catch (Exception e) {
                throw new IllegalStateException("Failed to obtain XAResources for recovery", e);
            }
        };
    }

    private static void delegateToQuarkus(String methodName, XAResourceRecovery recovery) {
        try {
            Class<?> serviceClass = Class.forName(QUARKUS_RECOVERY_SERVICE);
            Object service = serviceClass.getMethod("getInstance").invoke(null);
            Method method = service.getClass().getMethod(methodName, XAResourceRecovery.class);
            method.invoke(service, recovery);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Failed to delegate '" + methodName + "' to the Quarkus recovery service", e);
        }
    }

    private static boolean isClassPresent(String className) {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        try {
            Class.forName(className, false, tccl != null ? tccl : ForageRecoveryService.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    private final class ScopedRegistry implements XAResourceRecoveryRegistry {
        private final String key;

        private ScopedRegistry(String key) {
            this.key = key;
        }

        @Override
        public void addXAResourceRecovery(XAResourceRecovery recovery) {
            synchronized (ForageRecoveryService.this) {
                if (quarkusManaged) {
                    delegateToQuarkus("addXAResourceRecovery", recovery);
                    registrations.computeIfAbsent(key, k -> new ArrayList<>()).add(new Registration(null, recovery));
                    return;
                }
                ensureStarted();
                XAResourceRecoveryHelper helper = new XAResourceRecoveryAdapter(recovery);
                xaRecoveryModule.addXAResourceRecoveryHelper(helper);
                registrations.computeIfAbsent(key, k -> new ArrayList<>()).add(new Registration(helper, recovery));
                LOG.info("Registered XA recovery integration '{}'", key);
            }
        }

        @Override
        public void removeXAResourceRecovery(XAResourceRecovery recovery) {
            synchronized (ForageRecoveryService.this) {
                List<Registration> forKey = registrations.get(key);
                if (forKey == null) {
                    return;
                }
                forKey.removeIf(registration -> {
                    if (registration.source() != recovery) {
                        return false;
                    }
                    if (quarkusManaged) {
                        delegateToQuarkus("removeXAResourceRecovery", recovery);
                    } else if (xaRecoveryModule != null) {
                        xaRecoveryModule.removeXAResourceRecoveryHelper(registration.helper());
                    }
                    return true;
                });
                if (forKey.isEmpty()) {
                    registrations.remove(key);
                }
            }
        }
    }

    /** Bridges the JBoss {@link XAResourceRecovery} SPI onto Narayana's helper interface. */
    private static final class XAResourceRecoveryAdapter implements XAResourceRecoveryHelper {
        private final XAResourceRecovery delegate;

        private XAResourceRecoveryAdapter(XAResourceRecovery delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean initialise(String param) {
            return true;
        }

        @Override
        public XAResource[] getXAResources() {
            return delegate.getXAResources();
        }
    }
}
