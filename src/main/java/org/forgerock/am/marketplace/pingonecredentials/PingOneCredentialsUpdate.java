/*
 * This code is to be used exclusively in connection with Ping Identity Corporation software or services.
 * Ping Identity Corporation only offers such software or services to legal entities who have entered into
 * a binding license agreement with Ping Identity Corporation.
 *
 * Copyright 2024 Ping Identity Corporation. All Rights Reserved
 */

package org.forgerock.am.marketplace.pingonecredentials;

import static org.forgerock.am.marketplace.pingonecredentials.Constants.OBJECT_ATTRIBUTES;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_CREDENTIAL_UPDATE_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_USER_ID_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.RESPONSE_STATUS;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.SUCCESS_OUTCOME_ID;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.FAILURE_OUTCOME_ID;


import com.google.inject.assistedinject.Assisted;
import org.apache.commons.lang.StringUtils;
import org.forgerock.json.JsonValue;
import org.forgerock.oauth2.core.AccessToken;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeState;
import org.forgerock.openam.auth.node.api.OutcomeProvider;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.auth.service.marketplace.TNTPPingOneConfig;
import org.forgerock.openam.auth.service.marketplace.TNTPPingOneConfigChoiceValues;
import org.forgerock.openam.auth.service.marketplace.TNTPPingOneUtility;
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
    outcomeProvider = PingOneCredentialsUpdate.IssueOutcomeProvider.class,
    configClass = PingOneCredentialsUpdate.Config.class,
    tags = {"marketplace", "trustnetwork"})
public class PingOneCredentialsUpdate implements Node {

    private final Config config;
    private final Realm realm;
    private final TNTPPingOneConfig tntpPingOneConfig;

    private final Logger logger = LoggerFactory.getLogger(PingOneCredentialsUpdate.class);
    private final String loggerPrefix = "[PingOne Credentials Update Node]" + PingOneCredentialsPlugin.logAppender;

    public static final String BUNDLE = PingOneCredentialsUpdate.class.getName();
    private final Helper client;


    /**
     * Configuration for the node.
     */
    public interface Config {

        /**
         * The Configured service
         */
        @Attribute(order = 100, choiceValuesClass = TNTPPingOneConfigChoiceValues.class)
        default String tntpPingOneConfigName() {
            return TNTPPingOneConfigChoiceValues.createTNTPPingOneConfigName("Global Default");
        }

        @Attribute(order = 200)
        default String credentialTypeId() {
            return "";
        }

        @Attribute(order = 400)
        default String credentialId() {
            return "";
        }

        @Attribute(order = 500)
        default Map<String, String> attributes() {
            return Collections.emptyMap();
        }

        /**
         * Store the update response in the shared state.
         * @return true if the update response should be stored, false otherwise.
         */
        @Attribute(order = 1100)
        default boolean storeResponse() {
            return false;
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
    public PingOneCredentialsUpdate(@Assisted Config config, @Assisted Realm realm, Helper client) {
        this.config = config;
        this.realm = realm;
        this.tntpPingOneConfig = TNTPPingOneConfigChoiceValues.getTNTPPingOneConfig(config.tntpPingOneConfigName());
        this.client = client;
    }

    @Override
    public Action process(TreeContext context) {
        try {
            logger.debug(loggerPrefix + "Started");

            NodeState nodeState = context.getStateFor(this);

            // Check if PingOne User ID attribute is set in sharedState
            String pingOneUserId = nodeState.isDefined(PINGONE_USER_ID_KEY)
                                   ? nodeState.get(PINGONE_USER_ID_KEY).asString()
                                   : null;

            // Check if PingOne User ID attribute is in objectAttributes
            if (StringUtils.isBlank(pingOneUserId)) {
                if(nodeState.isDefined(OBJECT_ATTRIBUTES)) {
                    JsonValue objectAttributes = nodeState.get(OBJECT_ATTRIBUTES);

                    pingOneUserId = objectAttributes.isDefined(PINGONE_USER_ID_KEY)
                                    ? objectAttributes.get(PINGONE_USER_ID_KEY).asString()
                                    : null;
                }
            }

            if (StringUtils.isBlank(pingOneUserId)) {
                logger.error("Expected PingOne User ID to be set in sharedState.");
                return Action.goTo(FAILURE_OUTCOME_ID).build();
            }

            // Get PingOne Access Token
            TNTPPingOneUtility pingOneUtility = TNTPPingOneUtility.getInstance();
            AccessToken accessToken = pingOneUtility.getAccessToken(realm, tntpPingOneConfig);
            if (accessToken == null) {
                logger.error("Unable to get access token for PingOne Worker.");
                return  Action.goTo(FAILURE_OUTCOME_ID).build();
            }

            JsonValue response = client.credentialUpdateRequest(accessToken,
                                                               tntpPingOneConfig.environmentRegion().getDomainSuffix(),
                                                               tntpPingOneConfig.environmentId(),
                                                               pingOneUserId,
                                                               config.credentialTypeId(),
                                                               config.credentialId(),
                                                               getAttributesArray(nodeState));

            String result = response.get(RESPONSE_STATUS).asString();

            if (config.storeResponse()) {
                nodeState.putShared(PINGONE_CREDENTIAL_UPDATE_KEY, response);
            }

            logger.error(loggerPrefix + "Result: " + result);

            return Action.goTo(SUCCESS_OUTCOME_ID).build();
        }
        catch (Exception ex) {
            String stackTrace = org.apache.commons.lang.exception.ExceptionUtils.getStackTrace(ex);
            logger.error(loggerPrefix + "Exception occurred: ", ex);
            context.getStateFor(this).putTransient(loggerPrefix + "Exception", ex.getMessage());
            context.getStateFor(this).putTransient(loggerPrefix + "StackTrace", stackTrace);
            return Action.goTo(FAILURE_OUTCOME_ID).build();
        }
    }

    private JsonValue getAttributesArray(NodeState sharedState) {
        JsonValue attributes = new JsonValue(new LinkedHashMap<String, Object>(1));

        config.attributes().forEach(
            (k, v) -> {
                if (sharedState.isDefined(k)) {
                    attributes.put(k, sharedState.get(k));
                }

                // This approach seems unnecessary as it is possible to set static data on the credential in PingOne
                /*if (v.startsWith("{") && v.endsWith("}")) {
                    String ssKey = v.substring(1, v.length() - 1);
                    if (sharedState.isDefined(ssKey)) {
                        attributes.put(k, sharedState.get(ssKey));
                    }
                }
                else {
                    attributes.put(k, v);
                }*/
            });

        return attributes;
    }

    public static class IssueOutcomeProvider implements OutcomeProvider {
        @Override
        public List<Outcome> getOutcomes(PreferredLocales locales, JsonValue nodeAttributes) {
            ResourceBundle bundle = locales.getBundleInPreferredLocale(PingOneCredentialsUpdate.BUNDLE,
                                                                       OutcomeProvider.class.getClassLoader());
            List<Outcome> results = new ArrayList<>();
            results.add(new Outcome(SUCCESS_OUTCOME_ID, bundle.getString("successOutcome")));
            results.add(new Outcome(FAILURE_OUTCOME_ID, bundle.getString("failureOutcome")));
            return Collections.unmodifiableList(results);
        }
    }
}
