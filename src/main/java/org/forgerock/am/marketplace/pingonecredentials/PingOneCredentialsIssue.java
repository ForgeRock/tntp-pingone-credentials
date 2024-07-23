/*
 * This code is to be used exclusively in connection with Ping Identity Corporation software or services.
 * Ping Identity Corporation only offers such software or services to legal entities who have entered into
 * a binding license agreement with Ping Identity Corporation.
 *
 * Copyright 2024 Ping Identity Corporation. All Rights Reserved
 */

package org.forgerock.am.marketplace.pingonecredentials;

import static org.forgerock.am.marketplace.pingonecredentials.Constants.FAILURE_OUTCOME_ID;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.OBJECT_ATTRIBUTES;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_CREDENTIAL_ID_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_USER_ID_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.RESPONSE_ID;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.SUCCESS_OUTCOME_ID;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

@Node.Metadata(
    outcomeProvider = PingOneCredentialsIssue.IssueOutcomeProvider.class,
    configClass = PingOneCredentialsIssue.Config.class,
    tags = {"marketplace", "trustnetwork", "pingone"})
public class PingOneCredentialsIssue implements Node {

    private final Config config;
    private final Realm realm;
    private final PingOneWorkerService pingOneWorkerService;

    private final Logger logger = LoggerFactory.getLogger(PingOneCredentialsIssue.class);
    private static final String loggerPrefix = "[PingOne Credentials Issue Node]" + PingOneCredentialsPlugin.LOG_APPENDER;

    public static final String BUNDLE = PingOneCredentialsIssue.class.getName();
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

        @Attribute(order = 300)
        default String credentialTypeId() {
            return "";
        }

        @Attribute(order = 400)
        Map<String, String> attributes();
    }

    /**
     * Create the node using Guice injection. Just-in-time bindings can be used to
     * obtain instances of other classes from the plugin.
     *
     * @param config The service config.
     * @param realm  The realm the node is in.
     */
    @Inject
    PingOneCredentialsIssue(@Assisted Config config, @Assisted Realm realm,
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

            JsonValue response = client.credentialIssueRequest(accessToken,
                                                               worker,
                                                               pingOneUserId,
                                                               config.credentialTypeId(),
                                                               getAttributes(nodeState));

            nodeState.putShared(PINGONE_CREDENTIAL_ID_KEY, response.get(RESPONSE_ID).asString());

            return Action.goTo(SUCCESS_OUTCOME_ID).build();
        } catch (Exception ex) {
            String stackTrace = org.apache.commons.lang.exception.ExceptionUtils.getStackTrace(ex);
            logger.error(loggerPrefix + "Exception occurred: ", ex);
            context.getStateFor(this).putTransient(loggerPrefix + "Exception", ex.getMessage());
            context.getStateFor(this).putTransient(loggerPrefix + "StackTrace", stackTrace);
            return Action.goTo(FAILURE_OUTCOME_ID).build();
        }
    }

    private JsonValue getAttributes(NodeState sharedState) {
        JsonValue attributes = new JsonValue(new LinkedHashMap<String, Object>(1));

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
            new OutputState(PINGONE_CREDENTIAL_ID_KEY)
        };
    }

    public static class IssueOutcomeProvider implements StaticOutcomeProvider {
        @Override
        public List<Outcome> getOutcomes(PreferredLocales locales) {
            ResourceBundle bundle = locales.getBundleInPreferredLocale(PingOneCredentialsIssue.BUNDLE,
                                                                       OutcomeProvider.class.getClassLoader());
            List<Outcome> results = new ArrayList<>();
            results.add(new Outcome(SUCCESS_OUTCOME_ID, bundle.getString("successOutcome")));
            results.add(new Outcome(FAILURE_OUTCOME_ID, bundle.getString("failureOutcome")));
            return Collections.unmodifiableList(results);
        }
    }
}
