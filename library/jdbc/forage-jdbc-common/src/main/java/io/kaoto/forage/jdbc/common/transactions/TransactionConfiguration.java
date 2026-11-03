package io.kaoto.forage.jdbc.common.transactions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.agroal.narayana.LocalXAResource;
import io.kaoto.forage.core.jta.store.NarayanaObjectStore;
import io.kaoto.forage.core.jta.store.ObjectStoreDataSourceProvider;
import io.kaoto.forage.core.jta.store.ObjectStoreSettings;
import io.kaoto.forage.jdbc.common.DataSourceFactoryConfig;
import com.arjuna.ats.arjuna.common.CoordinatorEnvironmentBean;
import com.arjuna.ats.arjuna.common.CoreEnvironmentBean;
import com.arjuna.ats.arjuna.common.CoreEnvironmentBeanException;
import com.arjuna.ats.arjuna.common.RecoveryEnvironmentBean;
import com.arjuna.ats.jta.common.JTAEnvironmentBean;
import com.arjuna.common.internal.util.propertyservice.BeanPopulator;

public class TransactionConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TransactionConfiguration.class);

    /** Narayana's out-of-the-box node identifier, treated as "not yet configured by Forage". */
    private static final String NARAYANA_DEFAULT_NODE_IDENTIFIER = "1";

    private final DataSourceFactoryConfig config;
    private final String transactionNodeId;
    private final ObjectStoreDataSourceProvider objectStoreDataSources;

    public TransactionConfiguration(DataSourceFactoryConfig config, String id) {
        this(config, id, new JdbcObjectStoreDataSourceProvider());
    }

    /**
     * @param objectStoreDataSources creates the datasource of the {@code jdbc} object store
     */
    public TransactionConfiguration(
            DataSourceFactoryConfig config, String id, ObjectStoreDataSourceProvider objectStoreDataSources) {
        this.config = config;
        this.objectStoreDataSources = objectStoreDataSources;
        if (config.transactionNodeId() == null) {
            transactionNodeId = id;
        } else {
            transactionNodeId = config.transactionNodeId();
        }
        log.info("TransactionConfiguration initialized with nodeId: {}", transactionNodeId);
    }

    public void initializeNarayana() {
        log.info("Initializing Narayana transaction manager with nodeId: {}", transactionNodeId);
        try {
            configureCoreEnvironment();
        } catch (CoreEnvironmentBeanException e) {
            log.error("Failed to configure core environment for transaction manager", e);
            throw new RuntimeException(e);
        }
        configureObjectStore();
        configureCoordinator();
        configureRecovery();
        configureJTA();
        log.info("Narayana transaction manager initialization completed");
    }

    private void configureCoreEnvironment() throws CoreEnvironmentBeanException {
        log.debug("Configuring core environment with nodeId: {}", transactionNodeId);
        CoreEnvironmentBean coreBean = BeanPopulator.getDefaultInstance(CoreEnvironmentBean.class);
        // The node identifier is JVM-global: set it only once, first writer wins.
        // The CoreEnvironmentBean singleton is shared with the JMS TransactionConfiguration,
        // so synchronizing on it guards the check-and-set across both modules.
        synchronized (coreBean) {
            String currentNodeId = coreBean.getNodeIdentifier();
            if (currentNodeId == null || NARAYANA_DEFAULT_NODE_IDENTIFIER.equals(currentNodeId)) {
                coreBean.setNodeIdentifier(transactionNodeId);
                log.debug("Core environment configured with node identifier: {}", transactionNodeId);
            } else if (!currentNodeId.equals(transactionNodeId)) {
                log.warn(
                        "Narayana transaction node identifier is already set to '{}' (JVM-global); "
                                + "ignoring new value '{}'. The active node identifier remains '{}'.",
                        currentNodeId,
                        transactionNodeId,
                        currentNodeId);
            }
        }
    }

    private void configureObjectStore() {
        ObjectStoreSettings settings = config.transactionObjectStoreSettings();
        log.debug(
                "Configuring object store with type: {}, directory: {}, datasource: {}",
                settings.type(),
                settings.directory(),
                settings.dataSource());
        // The object store environment beans are JVM-global and shared with the JMS module:
        // NarayanaObjectStore applies the cross-module rules under the default bean's monitor.
        NarayanaObjectStore.configure(settings, objectStoreDataSources);
        log.debug("Object store configured successfully");
    }

    private void configureCoordinator() {
        log.debug("Configuring coordinator with timeout: {} seconds", config.transactionTimeoutSeconds());
        BeanPopulator.getDefaultInstance(CoordinatorEnvironmentBean.class)
                .setDefaultTimeout(config.transactionTimeoutSeconds());
        log.debug("Coordinator configured successfully");
    }

    private void configureRecovery() {
        if (config.transactionEnableRecovery()) {
            log.debug(
                    "Configuring recovery with modules: {} and expiry scanners: {}",
                    config.transactionRecoveryModules(),
                    config.transactionExpiryScanners());
            BeanPopulator.getDefaultInstance(RecoveryEnvironmentBean.class)
                    .setRecoveryModuleClassNames(
                            Arrays.stream(config.transactionRecoveryModules().split(","))
                                    .map(String::trim)
                                    .toList());
            BeanPopulator.getDefaultInstance(RecoveryEnvironmentBean.class)
                    .setExpiryScannerClassNames(
                            Arrays.stream(config.transactionExpiryScanners().split(","))
                                    .map(String::trim)
                                    .toList());
            RecoveryEnvironmentBean recoveryEnvironmentBean =
                    BeanPopulator.getDefaultInstance(RecoveryEnvironmentBean.class);
            recoveryEnvironmentBean.setPeriodicRecoveryPeriod(config.transactionRecoveryPeriodSeconds());
            recoveryEnvironmentBean.setRecoveryBackoffPeriod(config.transactionRecoveryBackoffSeconds());
            // The recovery manager is driven in-process by ForageRecoveryService; never open the
            // legacy recovery listener socket.
            recoveryEnvironmentBean.setRecoveryListener(false);
            log.debug("Recovery configured successfully");
        } else {
            log.debug("Recovery disabled in configuration");
        }
    }

    private void configureJTA() {
        log.debug(
                "Configuring JTA with recovery nodes: {} and orphan filters: {}",
                transactionNodeId,
                config.transactionXaResourceOrphanFilters());
        JTAEnvironmentBean jtaBean = BeanPopulator.getDefaultInstance(JTAEnvironmentBean.class);

        // Accumulate recovery nodes instead of replacing so multiple datasources all register.
        // The JTAEnvironmentBean singleton is shared with the JMS TransactionConfiguration,
        // so synchronizing on it guards the read-modify-write across both modules.
        synchronized (jtaBean) {
            List<String> currentNodes = jtaBean.getXaRecoveryNodes();
            if (currentNodes == null || !currentNodes.contains(transactionNodeId)) {
                List<String> updatedNodes = new ArrayList<>(currentNodes == null ? List.of() : currentNodes);
                updatedNodes.add(transactionNodeId);
                jtaBean.setXaRecoveryNodes(updatedNodes);
            }
        }

        jtaBean.setLastResourceOptimisationInterface(LocalXAResource.class);
        jtaBean.setXaResourceOrphanFilterClassNames(
                Arrays.stream(config.transactionXaResourceOrphanFilters().split(","))
                        .map(String::trim)
                        .toList());
        log.debug("JTA configured successfully");
    }
}
