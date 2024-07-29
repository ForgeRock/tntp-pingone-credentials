/*
 * This code is to be used exclusively in connection with Ping Identity Corporation software or services.
 * Ping Identity Corporation only offers such software or services to legal entities who have entered into
 * a binding license agreement with Ping Identity Corporation.
 *
 * Copyright 2024 Ping Identity Corporation. All Rights Reserved
 */

package org.forgerock.am.marketplace.pingonecredentials;

import static org.forgerock.am.marketplace.pingonecredentials.Constants.OBJECT_ATTRIBUTES;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_CREDENTIAL_ID_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_CREDENTIAL_UPDATE_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_USER_ID_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.SUCCESS_OUTCOME_ID;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.ERROR_OUTCOME_ID;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;


import com.google.inject.assistedinject.Assisted;
import org.apache.commons.lang.StringUtils;
import org.forgerock.json.JsonValue;
import org.forgerock.oauth2.core.AccessToken;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.InputState;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeState;
import org.forgerock.openam.auth.node.api.OutcomeProvider;
import org.forgerock.openam.auth.node.api.OutputState;
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
import java.util.Map;
import java.util.ResourceBundle;

@Node.Metadata(
    outcomeProvider = PingOneCredentialsUpdate.UpdateOutcomeProvider.class,
    configClass = PingOneCredentialsUpdate.Config.class,
    tags = {"marketplace", "trustnetwork", "pingone"})
public class PingOneCredentialsUpdate implements Node {

    private final Config config;
    private final Realm realm;
    private final PingOneWorkerService pingOneWorkerService;

    private final Logger logger = LoggerFactory.getLogger(PingOneCredentialsUpdate.class);
    private static final String LOGGER_PREFIX = "[PingOne Credentials Update Node]" + PingOneCredentialsPlugin.LOG_APPENDER;

    public static final String BUNDLE = PingOneCredentialsUpdate.class.getName();
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

        /**
         * The shared state attribute containing the PingOne User ID
         *
         * @return The PingOne User ID shared state attribute.
         */
        @Attribute(order = 200, requiredValue = true)
        default String pingOneUserIdAttribute() {
            return PINGONE_USER_ID_KEY;
        }

        /**
         * The Credential Type ID of the Credential
         *
         * @return The Credential Type ID as a String
         */
        @Attribute(order = 300, requiredValue = true)
        String credentialTypeId();

        /**
         * The Credential ID of the Credential
         *
         * @return The Credential ID as a String
         */
        @Attribute(order = 400, requiredValue = true)
        default String credentialId() {
            return PINGONE_CREDENTIAL_ID_KEY;
        }

        /**
         * The Credential attribute mapping. The Key is the Credential attribute field name and the Value is the shared
         * state attribute.
         * @return the attribute mapping for the Credential.
         */
        @Attribute(order = 500)
        Map<String, String> attributes();

        /**
         * Store the update response in the shared state.
         * @return true if the update response should be stored, false otherwise.
         */
        @Attribute(order = 1100, requiredValue = true)
        default boolean storeResponse() {
            return false;
        }
    }

    /**
     * The PingOne Credentials Update node constructor.
     *
     *
     * @param config               the node configuration.
     * @param realm                the realm.
     * @param pingOneWorkerService the {@link PingOneWorkerService} instance.
     * @param client               the {@link PingOneCredentialsService} instance.
     */
    @Inject
    PingOneCredentialsUpdate(@Assisted Config config, @Assisted Realm realm,
                             PingOneWorkerService pingOneWorkerService, PingOneCredentialsService client) {
        this.config = config;
        this.realm = realm;
        this.pingOneWorkerService = pingOneWorkerService;
        this.client = client;
    }

    @Override
    public Action process(TreeContext context) {
        try {
            logger.debug("{} Started", LOGGER_PREFIX);

            NodeState nodeState = context.getStateFor(this);

            // Check if PingOne User ID attribute is set in sharedState directly or objectAttributes
            String pingOneUserId;
            try {
                pingOneUserId = new PingOneUserIdHelper().getPingOneUserId(nodeState, config.pingOneUserIdAttribute());
            } catch (PingOneCredentialsException e) {
                logger.warn("Expected PingOne User ID to be set in sharedState.");
                return Action.goTo(ERROR_OUTCOME_ID).build();
            }

            // Check if the Credential ID attribute is set in sharedState
            String credentialId = nodeState.isDefined(config.credentialId())
                                     ? nodeState.get(config.credentialId()).asString()
                                     : null;
            if (StringUtils.isBlank(credentialId)) {
                logger.warn("Expected credentialId to be set in sharedState.");
                return Action.goTo(ERROR_OUTCOME_ID).build();
            }

            // Get PingOne Access Token
            PingOneWorkerConfig.Worker worker = config.pingOneWorker();
            AccessToken accessToken = pingOneWorkerService.getAccessToken(realm, worker);

            if (accessToken == null) {
                logger.error("Unable to get access token for PingOne Worker.");
                return  Action.goTo(ERROR_OUTCOME_ID).build();
            }

            JsonValue response = client.credentialUpdateRequest(accessToken,
                                                                worker,
                                                                pingOneUserId,
                                                                config.credentialTypeId(),
                                                                credentialId,
                                                                getAttributes(nodeState));

            if (config.storeResponse()) {
                nodeState.putShared(PINGONE_CREDENTIAL_UPDATE_KEY, response);
            }

            return Action.goTo(SUCCESS_OUTCOME_ID).build();
        } catch (Exception ex) {
            String stackTrace = org.apache.commons.lang.exception.ExceptionUtils.getStackTrace(ex);
            logger.error(LOGGER_PREFIX + "Exception occurred: ", ex);
            NodeState nodeState = context.getStateFor(this);

            nodeState.putTransient(LOGGER_PREFIX + "Exception", ex.getMessage());
            nodeState.putTransient(LOGGER_PREFIX + "StackTrace", stackTrace);

            return Action.goTo(ERROR_OUTCOME_ID).build();
        }
    }

    private JsonValue getAttributes(NodeState sharedState) {
        JsonValue attributes = json(object(1));

        config.attributes().forEach(
            (k, v) -> {
                if (sharedState.isDefined(v)) {
                    attributes.put(k, sharedState.get(v));
                }
            });

        return attributes;
    }

    @Override
    public InputState[] getInputs() {

        List<InputState> inputs = new ArrayList<>();

        inputs.add(new InputState(config.pingOneUserIdAttribute(), false));
        inputs.add(new InputState(config.credentialId(), false));
        inputs.add(new InputState(OBJECT_ATTRIBUTES, false));

        config.attributes().forEach(
            (k, v) -> {
                inputs.add(new InputState(v, false));
            });

        return inputs.toArray(new InputState[]{});
    }

    @Override
    public OutputState[] getOutputs() {
        return new OutputState[]{
            new OutputState(PINGONE_CREDENTIAL_UPDATE_KEY)
        };
    }

    public static class UpdateOutcomeProvider implements StaticOutcomeProvider {
        @Override
        public List<Outcome> getOutcomes(PreferredLocales locales) {
            ResourceBundle bundle = locales.getBundleInPreferredLocale(PingOneCredentialsUpdate.BUNDLE,
                                                                       OutcomeProvider.class.getClassLoader());
            List<Outcome> results = new ArrayList<>();
            results.add(new Outcome(SUCCESS_OUTCOME_ID, bundle.getString("successOutcome")));
            results.add(new Outcome(ERROR_OUTCOME_ID, bundle.getString("errorOutcome")));
            return Collections.unmodifiableList(results);
        }
    }
}
