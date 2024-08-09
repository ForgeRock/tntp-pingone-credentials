/*
 * This code is to be used exclusively in connection with Ping Identity Corporation software or services.
 * Ping Identity Corporation only offers such software or services to legal entities who have entered into
 * a binding license agreement with Ping Identity Corporation.
 *
 * Copyright 2024 Ping Identity Corporation. All Rights Reserved
 */

package org.forgerock.am.marketplace.pingonecredentials;

import static org.forgerock.am.marketplace.pingonecredentials.Constants.OBJECT_ATTRIBUTES;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_WALLET_ID_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_USER_ID_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.SUCCESS_OUTCOME_ID;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.NOT_FOUND_OUTCOME_ID;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.ERROR_OUTCOME_ID;

import com.google.inject.assistedinject.Assisted;
import org.apache.commons.lang.StringUtils;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.InputState;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeState;
import org.forgerock.openam.auth.node.api.OutcomeProvider;
import org.forgerock.openam.auth.node.api.StaticOutcomeProvider;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.integration.pingone.api.PingOneWorker;
import org.forgerock.openam.integration.pingone.api.PingOneWorkerService;
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
    outcomeProvider = PingOneCredentialsRemoveWallet.RemoveWalletOutcomeProvider.class,
    configClass = PingOneCredentialsRemoveWallet.Config.class,
    tags = {"marketplace", "trustnetwork", "pingone"})
public class PingOneCredentialsRemoveWallet implements Node {

    private final Config config;
    private final Realm realm;
    private final PingOneWorkerService pingOneWorkerService;

    private final Logger logger = LoggerFactory.getLogger(PingOneCredentialsRemoveWallet.class);
    private static final String LOGGER_PREFIX = "[PingOne Credentials Remove Wallet Node]" + PingOneCredentialsPlugin.LOG_APPENDER;

    public static final String BUNDLE = PingOneCredentialsRemoveWallet.class.getName();
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
        PingOneWorkerService.Worker pingOneWorker();

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
         * The shared state attribute containing the PingOne Digital Wallet ID
         *
         * @return The Digital Wallet ID shared state attribute.
         */
        @Attribute(order = 300, requiredValue = true)
        default String digitalWalletIdAttribute() {
            return PINGONE_WALLET_ID_KEY;
        }
    }

    /**
     * The PingOne Credentials Remove Wallet node constructor.
     *
     *
     * @param config               the node configuration.
     * @param realm                the realm.
     * @param pingOneWorkerService the {@link PingOneWorkerService} instance.
     * @param client               the {@link PingOneCredentialsService} instance.
     */
    @Inject
    PingOneCredentialsRemoveWallet(@Assisted Config config, @Assisted Realm realm,
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

            // Check if Digital Wallet ID attribute is set in sharedState
            String digitalWalletId = nodeState.isDefined(config.digitalWalletIdAttribute())
                                  ? nodeState.get(config.digitalWalletIdAttribute()).asString()
                                  : null;

            if (StringUtils.isBlank(digitalWalletId)) {
                logger.warn("Expected digitalWalletId to be set in sharedState.");
                return Action.goTo(ERROR_OUTCOME_ID).build();
            }

            // Get PingOne Access Token
            PingOneWorkerService.Worker worker = config.pingOneWorker();
            String accessToken = pingOneWorkerService.getAccessTokenId(realm, worker);

            if (StringUtils.isBlank(accessToken)) {
                logger.error("Unable to get access token for PingOne Worker.");
                return  Action.goTo(ERROR_OUTCOME_ID).build();
            }

            boolean result = client.deleteWalletRequest(accessToken,
                                                        worker,
                                                        pingOneUserId,
                                                        digitalWalletId);
            if (result) {
                return Action.goTo(SUCCESS_OUTCOME_ID).build();
            } else {
                return Action.goTo(NOT_FOUND_OUTCOME_ID).build();
            }
        } catch (Exception ex) {
            String stackTrace = org.apache.commons.lang.exception.ExceptionUtils.getStackTrace(ex);
            logger.error(LOGGER_PREFIX + "Exception occurred: ", ex);
            NodeState nodeState = context.getStateFor(this);

            nodeState.putTransient(LOGGER_PREFIX + "Exception", ex.getMessage());
            nodeState.putTransient(LOGGER_PREFIX + "StackTrace", stackTrace);

            return Action.goTo(ERROR_OUTCOME_ID).build();
        }
    }

    @Override
    public InputState[] getInputs() {
        return new InputState[] {
            new InputState(config.pingOneUserIdAttribute(), false),
            new InputState(OBJECT_ATTRIBUTES, false),
            new InputState(config.digitalWalletIdAttribute(), false)

        };
    }

    public static class RemoveWalletOutcomeProvider implements StaticOutcomeProvider {
        @Override
        public List<Outcome> getOutcomes(PreferredLocales locales) {
            ResourceBundle bundle = locales.getBundleInPreferredLocale(PingOneCredentialsRemoveWallet.BUNDLE,
                                                                       OutcomeProvider.class.getClassLoader());
            List<Outcome> results = new ArrayList<>();
            results.add(new Outcome(SUCCESS_OUTCOME_ID, bundle.getString("successOutcome")));
            results.add(new Outcome(NOT_FOUND_OUTCOME_ID, bundle.getString("notFoundOutcome")));
            results.add(new Outcome(ERROR_OUTCOME_ID, bundle.getString("errorOutcome")));
            return Collections.unmodifiableList(results);
        }
    }
}
