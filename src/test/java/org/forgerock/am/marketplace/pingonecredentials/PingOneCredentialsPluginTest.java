/*
 * Copyright 2024 ForgeRock AS. All Rights Reserved
 *
 * Use of this code requires a commercial software license with ForgeRock AS.
 * or with one of its affiliates. All use shall be exclusively subject
 * to such license between the licensee and ForgeRock AS.
 */

package org.forgerock.am.marketplace.pingonecredentials;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.forgerock.am.marketplace.pingonecredentials.Constants.CredentialIssuanceMode;
import org.forgerock.am.marketplace.pingonecredentials.Constants.VerificationProtocol;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.plugins.AmPlugin;
import org.forgerock.openam.plugins.PluginTools;
import org.forgerock.openam.plugins.VersionComparison;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for the plugin wiring: the {@link AmPlugin} service
 * registration, node discovery/registration, node metadata and configuration,
 * and the upgrade path that makes the OID4VCI offer and OID4VP verification
 * configuration reachable on existing installations. Also proves the NATIVE
 * protocol remains the default on both protocol-capable nodes and that no
 * additional node is registered.
 */
public class PingOneCredentialsPluginTest {

    /**
     * The last released plugin version. The node configuration added since then
     * only becomes reachable on existing installations when the plugin version
     * exceeds this value and AM therefore runs the plugin upgrade path.
     */
    private static final String LAST_RELEASED_VERSION = "1.0.6";

    /** The nodes that shipped before the offer status node was added. */
    private static final List<Class<? extends Node>> EXISTING_NODES = asList(
        PingOneCredentialsPairWallet.class,
        PingOneCredentialsIssue.class,
        PingOneCredentialsVerification.class,
        PingOneCredentialsFindWallets.class,
        PingOneCredentialsRemoveWallet.class,
        PingOneCredentialsUpdate.class,
        PingOneCredentialsRevoke.class);

    private static final List<Class<? extends Node>> REGISTERED_NODES = new ArrayList<>(EXISTING_NODES);

    static {
        REGISTERED_NODES.add(PingOneCredentialsOfferStatus.class);
    }

    @Test
    public void amPluginServiceRegistrationIsDiscoverableOnTheClasspath() throws Exception {
        // Several AM jars on the classpath also ship AmPlugin service files, so
        // every resource must be scanned rather than only the first classpath
        // match; a full ServiceLoader iteration is not used because foreign
        // provider classes may fail to load in isolation.
        Enumeration<URL> resources = getClass().getClassLoader()
                .getResources("META-INF/services/org.forgerock.openam.plugins.AmPlugin");
        List<String> registeredProviders = new ArrayList<>();
        while (resources.hasMoreElements()) {
            try (InputStream inputStream = resources.nextElement().openStream()) {
                new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)).lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .forEach(registeredProviders::add);
            }
        }

        assertThat(registeredProviders).contains(PingOneCredentialsPlugin.class.getName());
    }

    @Test
    public void registeredNodesCoverTheExistingNodesPlusTheOfferStatusNode() {
        PingOneCredentialsPlugin plugin = new PingOneCredentialsPlugin();
        Map<String, Iterable<? extends Class<? extends Node>>> nodesByVersion = plugin.getNodesByVersion();

        assertThat(nodesByVersion).isNotEmpty();

        Set<Class<? extends Node>> registeredNodes = new HashSet<>();
        for (Iterable<? extends Class<? extends Node>> nodes : nodesByVersion.values()) {
            for (Class<? extends Node> nodeClass : nodes) {
                registeredNodes.add(nodeClass);
            }
        }

        // The OID4VCI offer and OID4VP verification capabilities are reached
        // through the already-registered issue and verification nodes. The only
        // addition is the offer status node, which follows an offer to completion;
        // no unrelated node is dropped.
        assertThat(registeredNodes).containsExactlyInAnyOrderElementsOf(REGISTERED_NODES);
        assertThat(registeredNodes)
            .contains(PingOneCredentialsIssue.class, PingOneCredentialsVerification.class);
    }

    @Test
    public void everyRegisteredNodeCarriesResolvableMetadata() {
        for (Class<? extends Node> nodeClass : REGISTERED_NODES) {
            Node.Metadata metadata = nodeClass.getAnnotation(Node.Metadata.class);
            assertThat(metadata).as(nodeClass.getSimpleName()).isNotNull();
            assertThat(metadata.configClass()).as(nodeClass.getSimpleName()).isNotNull();
        }
    }

    @Test
    public void protocolNodeMetadataResolvesTheProtocolConfiguration() {
        Node.Metadata issueMetadata = PingOneCredentialsIssue.class.getAnnotation(Node.Metadata.class);
        assertThat(issueMetadata.configClass()).isEqualTo(PingOneCredentialsIssue.Config.class);
        assertThat(issueMetadata.outcomeProvider()).isEqualTo(PingOneCredentialsIssue.IssueOutcomeProvider.class);

        Node.Metadata verificationMetadata = PingOneCredentialsVerification.class.getAnnotation(Node.Metadata.class);
        assertThat(verificationMetadata.configClass()).isEqualTo(PingOneCredentialsVerification.Config.class);
        assertThat(verificationMetadata.outcomeProvider())
            .isEqualTo(PingOneCredentialsVerification.VerificationOutcomeProvider.class);
    }

    @Test
    public void protocolConfigurationAttributesAreDeclared() throws Exception {
        assertDeclaredAttribute(PingOneCredentialsIssue.Config.class, "credentialIssuanceMode");
        assertDeclaredAttribute(PingOneCredentialsIssue.Config.class, "credentialOfferCredentials");
        assertDeclaredAttribute(PingOneCredentialsIssue.Config.class, "credentialOfferGrantTypes");

        assertDeclaredAttribute(PingOneCredentialsVerification.Config.class, "protocol");
        assertDeclaredAttribute(PingOneCredentialsVerification.Config.class, "protocolVersion");
        assertDeclaredAttribute(PingOneCredentialsVerification.Config.class, "didMethod");
        assertDeclaredAttribute(PingOneCredentialsVerification.Config.class, "issuerFilter");
        assertDeclaredAttribute(PingOneCredentialsVerification.Config.class, "oid4vpTimeoutSeconds");
    }

    @Test
    public void protocolConfigurationDefaultsKeepExistingJourneysOnNativeRouting() throws Throwable {
        // Unset configuration falls back to the NATIVE protocol on both nodes,
        // so existing journeys keep their routing without consumer
        // configuration changes.
        assertThat(defaultConfigValue(PingOneCredentialsIssue.Config.class, "credentialIssuanceMode"))
            .isEqualTo(CredentialIssuanceMode.NATIVE);
        assertThat(defaultConfigValue(PingOneCredentialsVerification.Config.class, "protocol"))
            .isEqualTo(VerificationProtocol.NATIVE);
    }

    @Test
    public void pluginVersionExceedsTheLastReleasedVersion() {
        // AM runs the plugin upgrade path (which refreshes the node schemas so
        // the new configuration is reachable on existing installations) only
        // when the plugin version is higher than the installed version. The
        // helper returns a positive value when its first argument is older,
        // matching AbstractNodeAmPlugin.isNewerVersion(installed, candidate).
        assertThat(VersionComparison.compareVersionStrings(
                       LAST_RELEASED_VERSION, new PingOneCredentialsPlugin().getPluginVersion()))
            .isPositive();
    }

    @Test
    public void upgradeRefreshesEveryRegisteredNode() throws Exception {
        PingOneCredentialsPlugin plugin = new PingOneCredentialsPlugin();
        PluginTools pluginTools = mock(PluginTools.class);
        plugin.setPluginTools(pluginTools);

        plugin.upgrade(LAST_RELEASED_VERSION);

        for (Class<? extends Node> nodeClass : EXISTING_NODES) {
            verify(pluginTools).upgradeAuthNode(nodeClass);
        }
        // The offer status node is new in this version, so it is installed, not upgraded.
        verify(pluginTools).installAuthNode(PingOneCredentialsOfferStatus.class);
        verify(pluginTools, never()).upgradeAuthNode(PingOneCredentialsOfferStatus.class);
    }

    @Test
    public void onInstallRegistersEveryNode() throws Exception {
        PingOneCredentialsPlugin plugin = new PingOneCredentialsPlugin();
        PluginTools pluginTools = mock(PluginTools.class);
        plugin.setPluginTools(pluginTools);

        plugin.onInstall();

        for (Class<? extends Node> nodeClass : REGISTERED_NODES) {
            verify(pluginTools).installAuthNode(nodeClass);
        }
    }

    private static void assertDeclaredAttribute(Class<?> configClass, String attributeName) throws Exception {
        Method attribute = configClass.getMethod(attributeName);
        assertThat(attribute.isAnnotationPresent(Attribute.class))
            .as(configClass.getSimpleName() + "." + attributeName)
            .isTrue();
    }

    /**
     * Resolves the interface default of a node configuration attribute, which
     * is the value AM applies when the attribute is unset.
     */
    private static Object defaultConfigValue(Class<?> configInterface, String attributeName) throws Throwable {
        Object proxy = Proxy.newProxyInstance(configInterface.getClassLoader(), new Class<?>[]{configInterface},
            (p, method, args) -> {
                if (method.isDefault()) {
                    return InvocationHandler.invokeDefault(p, method, args);
                }
                throw new UnsupportedOperationException(method.getName());
            });
        return configInterface.getMethod(attributeName).invoke(proxy);
    }
}
