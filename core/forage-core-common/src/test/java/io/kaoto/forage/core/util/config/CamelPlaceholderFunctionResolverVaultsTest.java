package io.kaoto.forage.core.util.config;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.apache.camel.vault.VaultConfiguration;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * On plain Camel, Forage binds the {@code camel.vault.*} properties itself, before Camel Main does (see
 * {@link CamelPlaceholderFunctionResolver}), so its list of vaults must follow Camel's. This test fails when a
 * Camel upgrade adds a vault to {@link VaultConfiguration} that {@link CamelPlaceholderFunctionResolver#VAULTS}
 * does not bind.
 */
class CamelPlaceholderFunctionResolverVaultsTest {

    @Test
    void everyCamelVaultIsBound() throws Exception {
        VaultConfiguration configuration = new VaultConfiguration();
        Set<Object> bound = Collections.newSetFromMap(new IdentityHashMap<>());
        CamelPlaceholderFunctionResolver.VAULTS.values().forEach(vault -> bound.add(vault.apply(configuration)));

        List<String> missing = new ArrayList<>();
        for (Method accessor : vaultAccessors()) {
            if (!bound.contains(accessor.invoke(configuration))) {
                missing.add("VaultConfiguration." + accessor.getName() + "()");
            }
        }

        assertThat(missing)
                .as("Vaults with no entry in CamelPlaceholderFunctionResolver.VAULTS: add each one under the "
                        + "camel.vault.<name> group that Camel Main uses (BaseMainSupport#setVaultProperties)")
                .isEmpty();
        assertThat(bound)
                .as("each camel.vault.<name> group binds a different vault")
                .hasSize(CamelPlaceholderFunctionResolver.VAULTS.size());
    }

    /** The public, zero-argument accessors of {@link VaultConfiguration}, one per vault (aws(), kubernetes(), ...). */
    private static List<Method> vaultAccessors() {
        List<Method> accessors = Arrays.stream(VaultConfiguration.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> !Modifier.isStatic(method.getModifiers()) && !method.isSynthetic())
                .filter(method -> method.getParameterCount() == 0)
                .filter(method -> !method.getReturnType().isPrimitive())
                .toList();
        // Guard against a scan that silently finds nothing
        assertThat(accessors).extracting(Method::getName).contains("aws", "kubernetes", "springConfig");
        return accessors;
    }
}
