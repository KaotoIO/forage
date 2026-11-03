package io.kaoto.forage.jms.common.transactions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.kaoto.forage.core.jta.store.NarayanaObjectStore;
import io.kaoto.forage.core.jta.store.ObjectStoreDataSourceProvider;
import io.kaoto.forage.core.jta.store.ObjectStoreSettings;
import io.kaoto.forage.jms.common.ConnectionFactoryConfig;
import com.arjuna.ats.arjuna.common.CoordinatorEnvironmentBean;
import com.arjuna.ats.arjuna.common.CoreEnvironmentBean;
import com.arjuna.ats.arjuna.common.CoreEnvironmentBeanException;
import com.arjuna.ats.arjuna.common.RecoveryEnvironmentBean;
import com.arjuna.ats.jta.common.JTAEnvironmentBean;
import com.arjuna.common.internal.util.propertyservice.BeanPopulator;

/**
 * Manages Narayana transaction manager configuration for JMS operations.
 * Handles initialization of XA transaction support and recovery mechanisms.
 */
public class TransactionConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(TransactionConfiguration.class);

    /** Narayana's out-of-the-box node identifier, treated as "not yet configured by Forage". */
    private static final String NARAYANA_DEFAULT_NODE_IDENTIFIER = "1";

    private final ConnectionFactoryConfig config;
    private final String instanceId;
    private final ObjectStoreDataSourceProvider objectStoreDataSources;

    public TransactionConfiguration(ConnectionFactoryConfig config, String instanceId) {
        this(config, instanceId, null);
    }

    /**
     * @param objectStoreDataSources creates the datasource of the {@code jdbc} object store;
     *     {@code null} discovers the Forage JDBC implementation on the classpath when needed
     */
    public TransactionConfiguration(
            ConnectionFactoryConfig config, String instanceId, ObjectStoreDataSourceProvider objectStoreDataSources) {
        this.config = config;
        this.instanceId = instanceId;
        this.objectStoreDataSources = objectStoreDataSources;
    }

    public void initializeNarayana() {
        LOG.info("Initializing Narayana transaction manager for instance: {}", instanceId);

        try {
            configureNodeIdentifier();
            configureObjectStore();
            configureCoordinator();
            configureRecovery();
            configureJTA();
            LOG.info("Narayana transaction manager initialized successfully");
        } catch (RuntimeException e) {
            // Configuration errors (e.g. an unusable object store) already carry an actionable
            // message: propagate them as they are.
            LOG.error("Failed to initialize Narayana transaction manager", e);
            throw e;
        } catch (Exception e) {
            LOG.error("Failed to initialize Narayana transaction manager", e);
            throw new RuntimeException("Transaction manager initialization failed", e);
        }
    }

    private void configureNodeIdentifier() throws CoreEnvironmentBeanException {
        String nodeId = config.transactionNodeId();
        if (nodeId == null) {
            LOG.debug("Using default node identifier for instance: {}", instanceId);
            return;
        }

        CoreEnvironmentBean coreEnvironmentBean = BeanPopulator.getDefaultInstance(CoreEnvironmentBean.class);
        // The node identifier is JVM-global: set it only once, first writer wins.
        // The CoreEnvironmentBean singleton is shared with the JDBC TransactionConfiguration,
        // so synchronizing on it guards the check-and-set across both modules.
        synchronized (coreEnvironmentBean) {
            String currentNodeId = coreEnvironmentBean.getNodeIdentifier();
            if (currentNodeId == null || NARAYANA_DEFAULT_NODE_IDENTIFIER.equals(currentNodeId)) {
                LOG.debug("Setting transaction node identifier: {}", nodeId);
                coreEnvironmentBean.setNodeIdentifier(nodeId);
            } else if (!currentNodeId.equals(nodeId)) {
                LOG.warn(
                        "Narayana transaction node identifier is already set to '{}' (JVM-global); "
                                + "ignoring new value '{}'. The active node identifier remains '{}'.",
                        currentNodeId,
                        nodeId,
                        currentNodeId);
            }
        }
    }

    private void configureObjectStore() {
        ObjectStoreSettings settings = config.transactionObjectStoreSettings();
        LOG.debug(
                "Configuring object store - Type: {}, Directory: {}, DataSource: {}",
                settings.type(),
                settings.directory(),
                settings.dataSource());
        // The object store environment beans are JVM-global and shared with the JDBC module:
        // NarayanaObjectStore applies the cross-module rules under the default bean's monitor.
        // The jdbc store's datasource comes from the Forage JDBC module (discovered when null).
        NarayanaObjectStore.configure(settings, objectStoreDataSources);
    }

    private void configureCoordinator() {
        LOG.debug("Configuring coordinator with timeout: {} seconds", config.transactionTimeoutSeconds());
        BeanPopulator.getDefaultInstance(CoordinatorEnvironmentBean.class)
                .setDefaultTimeout(config.transactionTimeoutSeconds());
    }

    private void configureRecovery() {
        if (!config.transactionEnableRecovery()) {
            LOG.debug("Transaction recovery is disabled");
            return;
        }

        LOG.debug("Configuring transaction recovery mechanisms");

        RecoveryEnvironmentBean recoveryEnvironmentBean =
                com.arjuna.ats.arjuna.common.recoveryPropertyManager.getRecoveryEnvironmentBean();

        recoveryEnvironmentBean.setRecoveryModuleClassNames(
                Arrays.stream(config.transactionRecoveryModules().split(","))
                        .map(String::trim)
                        .toList());

        recoveryEnvironmentBean.setExpiryScannerClassNames(
                Arrays.stream(config.transactionExpiryScanners().split(","))
                        .map(String::trim)
                        .toList());

        recoveryEnvironmentBean.setPeriodicRecoveryPeriod(config.transactionRecoveryPeriodSeconds());
        recoveryEnvironmentBean.setRecoveryBackoffPeriod(config.transactionRecoveryBackoffSeconds());
        // The recovery manager is driven in-process by ForageRecoveryService; never open the
        // legacy recovery listener socket.
        recoveryEnvironmentBean.setRecoveryListener(false);

        LOG.info(
                "Transaction recovery configured with modules: {} (scan period {}s)",
                config.transactionRecoveryModules(),
                config.transactionRecoveryPeriodSeconds());
    }

    private void configureJTA() {
        JTAEnvironmentBean jtaBean = BeanPopulator.getDefaultInstance(JTAEnvironmentBean.class);

        // Accumulate recovery nodes instead of replacing.
        // The JTAEnvironmentBean singleton is shared with the JDBC TransactionConfiguration,
        // so synchronizing on it guards the read-modify-write across both modules.
        String nodeId = config.transactionNodeId() != null ? config.transactionNodeId() : instanceId;
        synchronized (jtaBean) {
            List<String> currentNodes = jtaBean.getXaRecoveryNodes();
            if (currentNodes == null || !currentNodes.contains(nodeId)) {
                List<String> updatedNodes = new ArrayList<>(currentNodes == null ? List.of() : currentNodes);
                updatedNodes.add(nodeId);
                jtaBean.setXaRecoveryNodes(updatedNodes);
            }
        }

        // Note: the last-resource-optimisation interface is deliberately NOT set here. It is a
        // JVM-global setting also configured by the JDBC module (to Agroal's LocalXAResource);
        // setting it here would clobber that value order-dependently in mixed JDBC+JMS apps.

        jtaBean.setXaResourceOrphanFilterClassNames(
                Arrays.stream(config.transactionXaResourceOrphanFilters().split(","))
                        .map(String::trim)
                        .toList());

        LOG.debug("JTA configured with recovery node: {}", nodeId);
    }
}
