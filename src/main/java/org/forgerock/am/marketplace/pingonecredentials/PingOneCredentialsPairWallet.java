/*
 * This code is to be used exclusively in connection with Ping Identity Corporation software or services.
 * Ping Identity Corporation only offers such software or services to legal entities who have entered into
 * a binding license agreement with Ping Identity Corporation.
 *
 * Copyright 2024 Ping Identity Corporation. All Rights Reserved
 */

package org.forgerock.am.marketplace.pingonecredentials;

import static org.forgerock.am.marketplace.pingonecredentials.Constants.ACTIVE;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.EXPIRED;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.RESPONSE_PAIRING_SESSION;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_PAIRING_TIMEOUT_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.RESPONSE_QR_URL;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_WALLET_ID_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_WALLET_DATA_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.RESPONSE_STATUS;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.TIMEOUT_OUTCOME_ID;
import static org.forgerock.openam.auth.node.api.Action.send;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PAIRING_REQUIRED;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.RESPONSE_ID;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_USER_ID_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_PAIRING_DELIVERY_METHOD_KEY;

import static org.forgerock.am.marketplace.pingonecredentials.Constants.SUCCESS_OUTCOME_ID;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.FAILURE_OUTCOME_ID;

import static org.forgerock.am.marketplace.pingonecredentials.Constants.PairingDeliveryMethod;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;

import javax.inject.Inject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.ConfirmationCallback;
import javax.security.auth.callback.TextOutputCallback;

import com.google.common.collect.ImmutableList;
import com.sun.identity.authentication.callbacks.HiddenValueCallback;
import com.sun.identity.authentication.callbacks.ScriptTextOutputCallback;
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
import org.forgerock.openam.auth.nodes.helpers.LocalizationHelper;
import org.forgerock.openam.auth.service.marketplace.TNTPPingOneConfig;
import org.forgerock.openam.auth.service.marketplace.TNTPPingOneConfigChoiceValues;
import org.forgerock.openam.auth.service.marketplace.TNTPPingOneUtility;
import org.forgerock.openam.authentication.callbacks.PollingWaitCallback;
import org.forgerock.openam.core.realms.Realm;
import org.forgerock.util.i18n.PreferredLocales;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.forgerock.openam.utils.qr.GenerationUtils;

import com.google.inject.assistedinject.Assisted;


@Node.Metadata(
    outcomeProvider = PingOneCredentialsPairWallet.ProofingOutcomeProvider.class,
    configClass = PingOneCredentialsPairWallet.Config.class,
    tags = {"marketplace", "trustnetwork"})
public class PingOneCredentialsPairWallet implements Node {

    /** How often to poll AM for a response in milliseconds. */
    public static final int TRANSACTION_POLL_INTERVAL = 5000;

    /** The id of the HiddenCallback containing the URI. */
    public static final String HIDDEN_CALLBACK_ID = "pingOneCredentialPairingUri";

    private final Logger logger = LoggerFactory.getLogger(PingOneCredentialsPairWallet.class);
    private final String loggerPrefix = "[PingOne Credentials Pair Wallet Node]" + PingOneCredentialsPlugin.logAppender;

    public static final String BUNDLE = PingOneCredentialsPairWallet.class.getName();

    static final String DEFAULT_DELIVERY_METHOD_MESSAGE_KEY = "default.deliveryMethodMessage";
    static final String DEFAULT_WAITING_MESSAGE_KEY = "default.waitingMessage";
    static final String SCAN_QR_CODE_MSG_KEY = "default.scanQRCodeMessage";

    static final String QR_CALLBACK_STRING = "callback_0";
    static final int DEFAULT_TIMEOUT = 120;

    private final Config config;
    private final Realm realm;
    private final TNTPPingOneConfig tntpPingOneConfig;
    private final LocalizationHelper localizationHelper;
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
        default String digitalWalletApplicationId() {
            return "";
        }

        /**
         * The Pairing URL delivery method.
         *
         * @return The type of delivery method.
         */
        @Attribute(order = 400)
        default PairingDeliveryMethod deliveryMethod() {
            return PairingDeliveryMethod.QRCODE;
        }

        /**
         * Allow user to choose the URL delivery method.
         * @return true if user will be prompted for delivery method, false otherwise.
         */
        @Attribute(order = 500)
        default boolean allowDeliveryMethodSelection() {
            return false;
        }

        /**
         * The message to display to the user allowing them to choose the delivery method. Keyed on the locale.
         * Falls back to default.deliverMethodMessage.
         * @return The message to display while choosing the delivery method.
         */
        @Attribute(order = 600)
        default Map<Locale, String> deliveryMethodMessage() {
            return Collections.emptyMap();
        }

        /**
         * The message to displayed to user to scan the QR code. Keyed on the locale. Falls back to
         * default.scanQRCodeMessage.
         * @return The mapping of locales to scan QR code messages.
         */
        @Attribute(order = 700)
        default Map<Locale, String> scanQRCodeMessage() {
            return Collections.emptyMap();
        }

        /**
         * The timeout in seconds for the verification process.
         * @return The timeout in seconds.
         */
        @Attribute(order = 800)
        default int timeout() {
            return DEFAULT_TIMEOUT;
        }

        /**
         * The message to display to the user while waiting, keyed on the locale. Falls back to default.waitingMessage.
         * @return The message to display on the waiting indicator.
         */
        @Attribute(order = 900)
        default Map<Locale, String> waitingMessage() {
            return Collections.emptyMap();
        }

        /**
         * Store the create wallet response in the shared state.
         * @return true if the create wallet response should be stored, false otherwise.
         */
        @Attribute(order = 1100)
        default boolean storeWalletResponse() {
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
    public PingOneCredentialsPairWallet(@Assisted Config config, @Assisted Realm realm, Helper client,
                                        LocalizationHelper localizationHelper) {
        this.config = config;
        this.realm = realm;
        this.tntpPingOneConfig = TNTPPingOneConfigChoiceValues.getTNTPPingOneConfig(config.tntpPingOneConfigName());
        this.client = client;
        this.localizationHelper = localizationHelper;
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
                return buildAction(FAILURE_OUTCOME_ID, context);
            }

            // Get PingOne Access Token
            TNTPPingOneUtility pingOneUtility = TNTPPingOneUtility.getInstance();
            AccessToken accessToken = pingOneUtility.getAccessToken(realm, tntpPingOneConfig);
            if (accessToken == null) {
                logger.error("Unable to get access token for PingOne Worker.");
                return buildAction(FAILURE_OUTCOME_ID, context);
            }

            // Check if choice was made
            Optional<ConfirmationCallback> confirmationCallback = context.getCallback(ConfirmationCallback.class);
            if (confirmationCallback.isPresent()) {
                logger.error("Retrieve selected delivery method and start a new Wallet Pairing process.");
                int choice = confirmationCallback.get().getSelectedIndex();
                nodeState.putShared(PINGONE_PAIRING_DELIVERY_METHOD_KEY, choice);
                return startPairingTransaction(context, accessToken, PairingDeliveryMethod.fromIndex(choice),
                                               pingOneUserId, config.digitalWalletApplicationId());
            }

            // Check if transaction was started
            Optional<PollingWaitCallback> pollingWaitCallback = context.getCallback(PollingWaitCallback.class);
            if (pollingWaitCallback.isPresent()) {
                // Transaction already started
                logger.error("Pairing process already started. Waiting for completion...");
                if (!nodeState.isDefined(PINGONE_WALLET_ID_KEY)) {
                    logger.error("Unable to find the PingOne Credentials Wallet ID in sharedState.");
                    return buildAction(FAILURE_OUTCOME_ID, context);
                }
                return getActionFromPairingTransactionStatus(context, accessToken, pingOneUserId);
            } else {

                // Start new pairing transaction
                if (config.allowDeliveryMethodSelection()) {
                    logger.error("Present options to select the delivery method.");
                    List<Callback> callbacks = createChoiceCallbacks(context);
                    return send(callbacks).build();
                } else {
                    logger.error("Start new Identity Verification process.");
                    return startPairingTransaction(context, accessToken, config.deliveryMethod(),
                                                   pingOneUserId, config.digitalWalletApplicationId());
                }
            }
        }
        catch (Exception ex) {
            String stackTrace = org.apache.commons.lang.exception.ExceptionUtils.getStackTrace(ex);
            logger.error(loggerPrefix + "Exception occurred: ", ex);
            context.getStateFor(this).putTransient(loggerPrefix + "Exception", ex.getMessage());
            context.getStateFor(this).putTransient(loggerPrefix + "StackTrace", stackTrace);
            return buildAction(FAILURE_OUTCOME_ID, context);
        }
    }

    private Action getActionFromPairingTransactionStatus(TreeContext context, AccessToken accessToken,
                                                         String pingOneUserId)
        throws Exception {
        NodeState nodeState = context.getStateFor(this);

        // Retrieve transaction ID from shared state
        String walletId = Objects.requireNonNull(nodeState.get(PINGONE_WALLET_ID_KEY)).asString();

        // Determine delivery method
        Constants.PairingDeliveryMethod pairingDeliveryMethod;
        if (config.allowDeliveryMethodSelection()) {
            int index = Objects.requireNonNull(nodeState.get(PINGONE_PAIRING_DELIVERY_METHOD_KEY)).asInteger();
            pairingDeliveryMethod = PairingDeliveryMethod.fromIndex(index);
        } else {
            pairingDeliveryMethod = config.deliveryMethod();
        }

        // Check transaction status and take appropriate action
        JsonValue response = client.readDigitalWallet(accessToken,
                                                               tntpPingOneConfig.environmentRegion().getDomainSuffix(),
                                                               tntpPingOneConfig.environmentId(),
                                                               pingOneUserId,
                                                               walletId);
        logger.error("readWallet: " + response.toString());
        // Retrieve response values
        String status = response.get(RESPONSE_STATUS).asString();
        String qrUrl = response.get(RESPONSE_PAIRING_SESSION).get(RESPONSE_QR_URL).asString();

        switch (status) {
            case PAIRING_REQUIRED:
                logger.error("Status is pairing required, waiting...");
                List<Callback> callbacks = getCallbacksForDeliveryMethod(context, pairingDeliveryMethod, qrUrl);
                return waitTransactionCompletion(nodeState, callbacks, config.timeout()).build();
            case ACTIVE:
                logger.error("Status is active, returning success");
                if (config.storeWalletResponse()) {
                    nodeState.putShared(PINGONE_WALLET_DATA_KEY, response);
                }
                return buildAction(SUCCESS_OUTCOME_ID, context);
            case EXPIRED:
                logger.error("Status is expired, returning failure");
                return buildAction(FAILURE_OUTCOME_ID, context);
            default:
                throw new IllegalStateException("Unexpected status returned from PingOne Pairing Transaction: "
                                                + status);
        }
    }

    private Action startPairingTransaction(TreeContext context, AccessToken accessToken, Constants.PairingDeliveryMethod pairingDeliveryMethod,
                                           String pingOneUserId, String digitalWalletApplicationId)
        throws Exception {

        List<String> notificationList = new ArrayList<String>();

        if(pairingDeliveryMethod.equals(PairingDeliveryMethod.EMAIL) ||
           pairingDeliveryMethod.equals(PairingDeliveryMethod.SMS) ||
           pairingDeliveryMethod.equals(PairingDeliveryMethod.EMAIL_SMS)) {
            notificationList.add(pairingDeliveryMethod.name());
        }

        JsonValue response = client.createDigitalWalletRequest(accessToken,
                                                               tntpPingOneConfig.environmentRegion().getDomainSuffix(),
                                                               tntpPingOneConfig.environmentId(),
                                                               pingOneUserId,
                                                               digitalWalletApplicationId,
                                                               notificationList);

        logger.error("Response: " + response);
        // Retrieve response values
        String digitalWalletId = response.get(RESPONSE_ID).asString();
        String qrUrl = response.get(RESPONSE_PAIRING_SESSION).get(RESPONSE_QR_URL).asString();

        logger.error("digitalWalletId: " + digitalWalletId);
        // Store transaction ID in shared state
        NodeState nodeState = context.getStateFor(this);
        nodeState.putShared(PINGONE_WALLET_ID_KEY, digitalWalletId);
        nodeState.putShared(PINGONE_PAIRING_TIMEOUT_KEY, TRANSACTION_POLL_INTERVAL);

        logger.error(nodeState.get(PINGONE_WALLET_ID_KEY).asString());

        // Create callbacks and send
        List<Callback> callbacks = getCallbacksForDeliveryMethod(context, pairingDeliveryMethod, qrUrl);
        return send(callbacks).build();
    }

    private List<Callback> getCallbacksForDeliveryMethod(TreeContext context, Constants.PairingDeliveryMethod pairingDeliveryMethod,
                                                         String url) {
        String waitingMessage = getWaitingMessage(context);

        Callback pollingCallback = PollingWaitCallback.makeCallback()
                                                      .withWaitTime(String.valueOf(TRANSACTION_POLL_INTERVAL))
                                                      .withMessage(waitingMessage)
                                                      .build();

        if (pairingDeliveryMethod.equals(Constants.PairingDeliveryMethod.QRCODE)) {
            Callback scanTextOutputCallback = createLocalizedTextCallback(context, this.getClass(),
                                                                          config.scanQRCodeMessage(), SCAN_QR_CODE_MSG_KEY);

            Callback qrCodeCallback = new ScriptTextOutputCallback(GenerationUtils
                                                                       .getQRCodeGenerationJavascriptForAuthenticatorAppRegistration(QR_CALLBACK_STRING, url));

            Callback hiddenCallback = new HiddenValueCallback(HIDDEN_CALLBACK_ID, url);

            return ImmutableList.of(
                scanTextOutputCallback,
                qrCodeCallback,
                hiddenCallback,
                pollingCallback);
        } else {
            return ImmutableList.of(
                pollingCallback);
        }
    }

    private Callback createLocalizedTextCallback(TreeContext context, Class<?> bundleClass,
                                                 Map<Locale, String> scanQRCodeMessage, String key) {
        String message = localizationHelper.getLocalizedMessage(context, bundleClass, scanQRCodeMessage, key);
        return new TextOutputCallback(TextOutputCallback.INFORMATION, message);
    }

    private String getWaitingMessage(TreeContext context) {
        return localizationHelper.getLocalizedMessage(context,
                                                              PingOneCredentialsPairWallet.class,
                                                              config.waitingMessage(),
                                                              DEFAULT_WAITING_MESSAGE_KEY);
    }

    private Action.ActionBuilder waitTransactionCompletion(NodeState nodeState, List<Callback> callbacks, int timeout) {
        logger.error("Waiting pairing transaction to be completed.");
        int timeOutInMs = timeout * 1000;
        int timeElapsed = nodeState.get(PINGONE_PAIRING_TIMEOUT_KEY).asInteger();

        if (timeElapsed >= timeOutInMs) {
            return Action.goTo(TIMEOUT_OUTCOME_ID);
        } else {
            nodeState.putShared(PINGONE_PAIRING_TIMEOUT_KEY, timeElapsed + TRANSACTION_POLL_INTERVAL);
        }
        return send(callbacks);
    }

    private List<Callback> createChoiceCallbacks(TreeContext context) {
        List<Callback> callbacks = new ArrayList<>();
        String message = localizationHelper.getLocalizedMessage(context, this.getClass(),
                                                                config.deliveryMethodMessage(), DEFAULT_DELIVERY_METHOD_MESSAGE_KEY);
        String[] options = {
            localizationHelper.getLocalizedMessage(context, this.getClass(), null, "deliveryMethod.QRCODE"),
            localizationHelper.getLocalizedMessage(context, this.getClass(), null, "deliveryMethod.EMAIL"),
            localizationHelper.getLocalizedMessage(context, this.getClass(), null, "deliveryMethod.SMS"),
            };

        Callback textOutputCallback = new TextOutputCallback(TextOutputCallback.INFORMATION, message);
        callbacks.add(textOutputCallback);

        ConfirmationCallback deliveryChoiceCallback = new ConfirmationCallback(message,
                                                                               ConfirmationCallback.INFORMATION, options, 0);
        callbacks.add(deliveryChoiceCallback);

        return callbacks;
    }

    private Action buildAction(String outcome, TreeContext context) {
        Action.ActionBuilder builder = Action.goTo(outcome);
        return cleanupSharedState(context, builder).build();
    }

    private Action.ActionBuilder cleanupSharedState(TreeContext context, Action.ActionBuilder builder) {
        logger.error("Cleaning up shared state...");
        NodeState nodeState = context.getStateFor(this);
        nodeState.remove(PINGONE_WALLET_ID_KEY);
        nodeState.remove(PINGONE_PAIRING_DELIVERY_METHOD_KEY);
        nodeState.remove(PINGONE_PAIRING_TIMEOUT_KEY);
        return builder;
    }

    @Override
    public InputState[] getInputs() {
        return new InputState[] {
            new InputState(PINGONE_USER_ID_KEY, true),
            new InputState(PINGONE_WALLET_ID_KEY),
            new InputState(PINGONE_PAIRING_DELIVERY_METHOD_KEY),
            new InputState(PINGONE_PAIRING_TIMEOUT_KEY),
            };
    }

    public static class ProofingOutcomeProvider implements OutcomeProvider {
        @Override
        public List<Outcome> getOutcomes(PreferredLocales locales, JsonValue nodeAttributes) {
            ResourceBundle bundle = locales.getBundleInPreferredLocale(PingOneCredentialsPairWallet.BUNDLE,
                                                                       OutcomeProvider.class.getClassLoader());
            List<Outcome> results = new ArrayList<>();
            results.add(new Outcome(SUCCESS_OUTCOME_ID, bundle.getString("successOutcome")));
            results.add(new Outcome(FAILURE_OUTCOME_ID, bundle.getString("failureOutcome")));
            results.add(new Outcome(TIMEOUT_OUTCOME_ID, bundle.getString("timeoutOutcome")));
            return Collections.unmodifiableList(results);
        }
    }
}
