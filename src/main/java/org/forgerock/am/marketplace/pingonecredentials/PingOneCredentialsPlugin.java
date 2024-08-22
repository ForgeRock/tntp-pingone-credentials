/*
 * This code is to be used exclusively in connection with Ping Identity Corporation software or services. 
 * Ping Identity Corporation only offers such software or services to legal entities who have entered into 
 * a binding license agreement with Ping Identity Corporation.
 *
 * Copyright 2024 Ping Identity Corporation. All Rights Reserved
 */

package org.forgerock.am.marketplace.pingonecredentials;

import static java.util.Arrays.asList;

import java.util.Map;

import org.forgerock.openam.auth.node.api.AbstractNodeAmPlugin;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.plugins.PluginException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;

/**
 * Definition of an <a href="https://backstage.forgerock.com/docs/am/6/apidocs/org/forgerock/openam/auth/node/api/AbstractNodeAmPlugin.html">AbstractNodeAmPlugin</a>. 
 * Implementations can use {@code @Inject} setters to get access to APIs 
 * available via Guice dependency injection. For example, if you want to add an SMS service on install, you 
 * can add the following setter:
 * <pre><code>
 * {@code @Inject}
 * public void setPluginTools(PluginTools tools) {
 *     this.tools = tools;
 * }
 * </code></pre>
 * So that you can use the addSmsService api to load your schema XML for example.
 * PluginTools javadoc may be found 
 * <a href="https://backstage.forgerock.com/docs/am/6/apidocs/org/forgerock/openam/plugins/PluginTools.html#addSmsService-java.io.InputStream-">here</a> 
 * <p>
 *     It can be assumed that when running, implementations of this class will be singleton instances.
 * </p>
 * <p>
 *     It should <i>not</i> be expected that the runtime singleton instances will be the instances on which
 *     {@link #onAmUpgrade(String, String)} will be called. Guice-injected properties will also <i>not</i> be populated
 *     during that method call.
 * </p>
 * <p>
 *     Plugins should <i>not</i> use the {@code ShutdownManager}/{@code ShutdownListener} API for handling shutdown, as
 *     the order of calling those listeners is not deterministic. The {@link #onShutdown()} method for all plugins will
 *     be called in the reverse order from the order that {@link #onStartup()} was called, with dependent plugins being
 *     notified after their dependencies for startup, and before them for shutdown.
 * </p>
 * @since AM 5.5.0
 */
public class PingOneCredentialsPlugin extends AbstractNodeAmPlugin {
	protected static final String CURRENT_VERSION = "1.0.3";
	protected static final String LOG_APPENDER = "[Version: " + CURRENT_VERSION + "][Marketplace]";
	private static final Logger logger = LoggerFactory.getLogger(PingOneCredentialsPlugin.class);
	private final String LOGGER_PREFIX = "[PingOneCredentialsPlugin]" + PingOneCredentialsPlugin.LOG_APPENDER;

    /** 
     * Specify the Map of list of node classes that the plugin is providing. These will then be installed and
     *  registered at the appropriate times in plugin lifecycle.
     *
     * @return The list of node classes.
     */
	@Override
	protected Map<String, Iterable<? extends Class<? extends Node>>> getNodesByVersion() {
		return new ImmutableMap.Builder<String, Iterable<? extends Class<? extends Node>>>()
                .put("1.0.3", asList(PingOneCredentialsPairWallet.class,
                                    PingOneCredentialsIssue.class,
                                    PingOneCredentialsVerification.class,
                                    PingOneCredentialsFindWallets.class,
                                    PingOneCredentialsRemoveWallet.class,
                                    PingOneCredentialsUpdate.class,
                                    PingOneCredentialsRevoke.class))
                .build();
	}

	/**
	 * The plugin version. This must be in semver (semantic version) format.
	 *
	 * @return The version of the plugin.
	 * @see <a href="https://www.osgi.org/wp-content/uploads/SemanticVersioning.pdf">Semantic Versioning</a>
	 */
	@Override
	public String getPluginVersion() {
		return PingOneCredentialsPlugin.CURRENT_VERSION;
	}

    /** 
     * This method will be called when the version returned by {@link #getPluginVersion()} is higher than the
     * version already installed. This method will be called before the {@link #onStartup()} method.
     * 
     * No need to implement this untils there are multiple versions of your auth node.
     *
     * @param fromVersion The old version of the plugin that has been installed.
     */	
	@Override
	public void upgrade(String fromVersion) throws PluginException {
		logger.debug("{} fromVersion = {}", LOGGER_PREFIX, fromVersion);
		logger.debug("{} currentVersion = {}", LOGGER_PREFIX, CURRENT_VERSION);

		try {
			pluginTools.upgradeAuthNode(PingOneCredentialsPairWallet.class);
			pluginTools.upgradeAuthNode(PingOneCredentialsIssue.class);
			pluginTools.upgradeAuthNode(PingOneCredentialsVerification.class);
			pluginTools.upgradeAuthNode(PingOneCredentialsFindWallets.class);
			pluginTools.upgradeAuthNode(PingOneCredentialsRemoveWallet.class);
			pluginTools.upgradeAuthNode(PingOneCredentialsUpdate.class);
			pluginTools.upgradeAuthNode(PingOneCredentialsRevoke.class);
		} catch (Exception e) {
			throw new PluginException(e.getMessage());
		}
		super.upgrade(fromVersion);
	}


}
