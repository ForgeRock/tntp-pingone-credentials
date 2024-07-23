/*
 * This code is to be used exclusively in connection with Ping Identity Corporation software or services.
 * Ping Identity Corporation only offers such software or services to legal entities who have entered into
 * a binding license agreement with Ping Identity Corporation.
 *
 * Copyright 2024 Ping Identity Corporation. All Rights Reserved
 */

package org.forgerock.am.marketplace.pingonecredentials;

import static org.forgerock.am.marketplace.pingonecredentials.Constants.ACTIVE;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.FAILURE_OUTCOME_ID;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.NOT_FOUND_OUTCOME_ID;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.OBJECT_ATTRIBUTES;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_ACTIVE_WALLETS_DATA_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_APPLICATION_INSTANCE_ID_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_WALLET_ID_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_USER_ID_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.RESPONSE_APPLICATION_INSTANCE;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.RESPONSE_DIGITALWALLETS;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.RESPONSE_EMBEDDED;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.RESPONSE_ID;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.RESPONSE_STATUS;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.SUCCESS_OUTCOME_ID;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.SUCCESS_MULTI_OUTCOME_ID;
import static org.forgerock.json.JsonValue.array;
import static org.forgerock.json.JsonValue.json;

import com.google.inject.assistedinject.Assisted;
import org.apache.commons.lang.StringUtils;
import org.forgerock.json.JsonValue;
import org.forgerock.oauth2.core.AccessToken;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.InputState;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeState;
import org.forgerock.openam.auth.node.api.OutputState;
import org.forgerock.openam.auth.node.api.OutcomeProvider;
import org.forgerock.openam.auth.node.api.StaticOutcomeProvider;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.integration.pingone.PingOneWorkerConfig;
import org.forgerock.openam.integration.pingone.PingOneWorkerService;
import org.forgerock.openam.integration.pingone.annotations.PingOneWorker;
import org.forgerock.openam.core.realms.Realm;
import org.forgerock.util.i18n.PreferredLocales;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ResourceBundle;

@Node.Metadata(
    outcomeProvider = PingOneCredentialsFindWallets.IssueOutcomeProvider.class,
    configClass = PingOneCredentialsFindWallets.Config.class,
    tags = {"marketplace", "trustnetwork", "pingone"})
public class PingOneCredentialsFindWallets implements Node {

    private final Config config;
    private final Realm realm;
    private final PingOneWorkerService pingOneWorkerService;

    private final Logger logger = LoggerFactory.getLogger(PingOneCredentialsFindWallets.class);
    private static final String loggerPrefix = "[PingOne Credentials Find Wallets Node]" + PingOneCredentialsPlugin.LOG_APPENDER;

    public static final String BUNDLE = PingOneCredentialsFindWallets.class.getName();

    private final PingOneCredentialsService client;


    /**
     * Configuration for the node.
     */
    public interface Config {

        /**
         * Reference to the PingOne Worker App.
         *
         * @return The PingOne Worker App.
         */
        @Attribute(order = 100, requiredValue = true)
        @PingOneWorker
        PingOneWorkerConfig.Worker pingOneWorker();

        @Attribute(order = 200)
        default String pingOneUserIdAttribute() {
            return PINGONE_USER_ID_KEY;
        }
    }

    /**
     * Create the node using Guice injection. Just-in-time bindings can be used to
     * obtain instances of other classes from the plugin.
     *
     * @param config The service config.
     * @param realm  The realm the node is in.
     */
    @Inject
    PingOneCredentialsFindWallets(@Assisted Config config, @Assisted Realm realm,
                                  PingOneWorkerService pingOneWorkerService, PingOneCredentialsService client) {
        this.config = config;
        this.realm = realm;
        this.pingOneWorkerService = pingOneWorkerService;
        this.client = client;
    }

    @Override
    public Action process(TreeContext context) {
        try {
            logger.debug(loggerPrefix + "Started");

            NodeState nodeState = context.getStateFor(this);

            // Check if PingOne User ID attribute is set in sharedState
            String pingOneUserId = nodeState.isDefined(config.pingOneUserIdAttribute())
                                   ? nodeState.get(config.pingOneUserIdAttribute()).asString()
                                   : null;

            // Check if PingOne User ID attribute is in objectAttributes
            if (StringUtils.isBlank(pingOneUserId)) {
                if(nodeState.isDefined(OBJECT_ATTRIBUTES)) {
                    JsonValue objectAttributes = nodeState.get(OBJECT_ATTRIBUTES);

                    pingOneUserId = objectAttributes.isDefined(config.pingOneUserIdAttribute())
                                    ? objectAttributes.get(config.pingOneUserIdAttribute()).asString()
                                    : null;
                }
            }

            if (StringUtils.isBlank(pingOneUserId)) {
                logger.warn("Expected PingOne User ID to be set in sharedState.");
                return Action.goTo(FAILURE_OUTCOME_ID).build();
            }

            // Get PingOne Access Token
            PingOneWorkerConfig.Worker worker = config.pingOneWorker();
            AccessToken accessToken = pingOneWorkerService.getAccessToken(realm, worker);

            if (accessToken == null) {
                logger.error("Unable to get access token for PingOne Worker.");
                return Action.goTo(FAILURE_OUTCOME_ID).build();
            }

            JsonValue response = client.findWalletRequest(accessToken, worker, pingOneUserId);

            JsonValue wallets = response.get(RESPONSE_EMBEDDED).get(RESPONSE_DIGITALWALLETS);
            JsonValue activeWallets = json(array());

            for (JsonValue obj : wallets) {

                String walletStatus = obj.get(RESPONSE_STATUS).asString();

                if (walletStatus.equals(ACTIVE)) {
                    activeWallets.add(obj);
                }
            }

            if(activeWallets.size() == 0) {
                return Action.goTo(NOT_FOUND_OUTCOME_ID).build();
            } else if(activeWallets.size() == 1) {
                // If the active wallet size is one, set the wallet ID attribute in the shared state
                // Otherwise if multiple wallets are returned, do not set the wallet ID attribute

                String walletId = activeWallets.get(0).get(RESPONSE_ID).asString();
                String applicationInstanceId = activeWallets.get(0).get(RESPONSE_APPLICATION_INSTANCE).get(RESPONSE_ID).asString();

                nodeState.putShared(PINGONE_WALLET_ID_KEY, walletId);
                nodeState.putShared(PINGONE_APPLICATION_INSTANCE_ID_KEY, applicationInstanceId);

                nodeState.putShared(PINGONE_ACTIVE_WALLETS_DATA_KEY, activeWallets);
                return Action.goTo(SUCCESS_OUTCOME_ID).build();
            } else {

                nodeState.putShared(PINGONE_ACTIVE_WALLETS_DATA_KEY, activeWallets);
                return Action.goTo(SUCCESS_MULTI_OUTCOME_ID).build();
            }
        } catch (Exception ex) {
            String stackTrace = org.apache.commons.lang.exception.ExceptionUtils.getStackTrace(ex);
            logger.error(loggerPrefix + "Exception occurred: ", ex);
            context.getStateFor(this).putTransient(loggerPrefix + "Exception", ex.getMessage());
            context.getStateFor(this).putTransient(loggerPrefix + "StackTrace", stackTrace);
            return Action.goTo(FAILURE_OUTCOME_ID).build();
        }
    }

    @Override
    public InputState[] getInputs() {
        return new InputState[] {
            new InputState(config.pingOneUserIdAttribute(), false),
            new InputState(OBJECT_ATTRIBUTES, false)
        };
    }

    @Override
    public OutputState[] getOutputs() {
        return new OutputState[]{
            new OutputState(PINGONE_WALLET_ID_KEY),
            new OutputState(PINGONE_APPLICATION_INSTANCE_ID_KEY)
        };
    }

    public static class IssueOutcomeProvider implements StaticOutcomeProvider {
        @Override
        public List<Outcome> getOutcomes(PreferredLocales locales) {
            ResourceBundle bundle = locales.getBundleInPreferredLocale(PingOneCredentialsFindWallets.BUNDLE,
                                                                       OutcomeProvider.class.getClassLoader());
            List<Outcome> results = new ArrayList<>();
            results.add(new Outcome(SUCCESS_OUTCOME_ID, bundle.getString("successOutcome")));
            results.add(new Outcome(SUCCESS_MULTI_OUTCOME_ID, bundle.getString("successMultiOutcome")));
            results.add(new Outcome(NOT_FOUND_OUTCOME_ID, bundle.getString("notFoundOutcome")));
            results.add(new Outcome(FAILURE_OUTCOME_ID, bundle.getString("failureOutcome")));
            return Collections.unmodifiableList(results);
        }
    }
}
