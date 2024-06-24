/*
 * This code is to be used exclusively in connection with Ping Identity Corporation software or services.
 * Ping Identity Corporation only offers such software or services to legal entities who have entered into
 * a binding license agreement with Ping Identity Corporation.
 *
 * Copyright 2024 Ping Identity Corporation. All Rights Reserved
 */

package org.forgerock.am.marketplace.pingonecredentials;

import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_USER_ID_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.SUCCESS_OUTCOME_ID;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.NOT_FOUND_OUTCOME_ID;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.FAILURE_OUTCOME_ID;

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

import java.util.List;

import java.util.ResourceBundle;




@Node.Metadata(
    outcomeProvider = PingOneCredentialsRemoveWallet.IssueOutcomeProvider.class,
    configClass = PingOneCredentialsRemoveWallet.Config.class,
    tags = {"marketplace", "trustnetwork"})
public class PingOneCredentialsRemoveWallet implements Node {

    private final Config config;
    private final Realm realm;
    private final TNTPPingOneConfig tntpPingOneConfig;

    private final Logger logger = LoggerFactory.getLogger(PingOneCredentialsRemoveWallet.class);
    private final String loggerPrefix = "[PingOne Credentials Remove Wallet Node]" + PingOneCredentialsPlugin.logAppender;

    public static final String BUNDLE = PingOneCredentialsRemoveWallet.class.getName();
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

        @Attribute(order = 300)
        default String digitalWalletIdAttribute() {
            return "";
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
    public PingOneCredentialsRemoveWallet(@Assisted Config config, @Assisted Realm realm,
                                           Helper client) {
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
            if (StringUtils.isBlank(pingOneUserId)) {
                logger.error("Expected PingOne User ID to be set in sharedState.");
                return Action.goTo(FAILURE_OUTCOME_ID).build();
            }

            // Check if Digital Wallet ID attribute is set in sharedState
            String digitalWalletId = nodeState.isDefined(config.digitalWalletIdAttribute())
                                  ? nodeState.get(config.digitalWalletIdAttribute()).asString()
                                  : null;
            if (StringUtils.isBlank(digitalWalletId)) {
                logger.error("Expected digitalWalletId to be set in sharedState.");
                return Action.goTo(FAILURE_OUTCOME_ID).build();
            }

            // Get PingOne Access Token
            TNTPPingOneUtility pingOneUtility = TNTPPingOneUtility.getInstance();
            AccessToken accessToken = pingOneUtility.getAccessToken(realm, tntpPingOneConfig);
            if (accessToken == null) {
                logger.error("Unable to get access token for PingOne Worker.");
                return  Action.goTo(FAILURE_OUTCOME_ID).build();
            }

            boolean result = client.deleteWalletRequest(accessToken,
                                                        tntpPingOneConfig.environmentRegion().getDomainSuffix(),
                                                        tntpPingOneConfig.environmentId(),
                                                        pingOneUserId,
                                                        digitalWalletId);

            if(result) {
                return Action.goTo(SUCCESS_OUTCOME_ID).build();
            } else {
                return Action.goTo(NOT_FOUND_OUTCOME_ID).build();
            }
        }
        catch (Exception ex) {
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
            new InputState(PINGONE_USER_ID_KEY, true)
        };
    }

    public static class IssueOutcomeProvider implements OutcomeProvider {
        @Override
        public List<Outcome> getOutcomes(PreferredLocales locales, JsonValue nodeAttributes) {
            ResourceBundle bundle = locales.getBundleInPreferredLocale(PingOneCredentialsRemoveWallet.BUNDLE,
                                                                       OutcomeProvider.class.getClassLoader());
            List<Outcome> results = new ArrayList<>();
            results.add(new Outcome(SUCCESS_OUTCOME_ID, bundle.getString("successOutcome")));
            results.add(new Outcome(NOT_FOUND_OUTCOME_ID, bundle.getString("notFoundOutcome")));
            results.add(new Outcome(FAILURE_OUTCOME_ID, bundle.getString("failureOutcome")));
            return Collections.unmodifiableList(results);
        }
    }
}
