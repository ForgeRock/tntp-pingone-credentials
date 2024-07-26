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
import static org.forgerock.am.marketplace.pingonecredentials.Constants.OBJECT_ATTRIBUTES;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_APPOPEN_URL_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_WALLET_ID_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.RESPONSE_APPOPEN;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.RESPONSE_HREF;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.RESPONSE_LINKS;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_PAIRING_TIMEOUT_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_PAIRING_WALLET_ID_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_WALLET_DATA_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.RESPONSE_STATUS;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.TIMEOUT_OUTCOME_ID;
import static org.forgerock.openam.auth.node.api.Action.send;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PAIRING_REQUIRED;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.RESPONSE_ID;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_USER_ID_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_PAIRING_DELIVERY_METHOD_KEY;

import static org.forgerock.am.marketplace.pingonecredentials.Constants.SUCCESS_OUTCOME_ID;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.ERROR_OUTCOME_ID;

import static org.forgerock.am.marketplace.pingonecredentials.Constants.PairingDeliveryMethod;


import java.time.Duration;
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
import org.forgerock.openam.auth.nodes.helpers.LocalizationHelper;
import org.forgerock.openam.integration.pingone.PingOneWorkerConfig;
import org.forgerock.openam.integration.pingone.PingOneWorkerService;
import org.forgerock.openam.integration.pingone.annotations.PingOneWorker;
import org.forgerock.openam.authentication.callbacks.PollingWaitCallback;
import org.forgerock.openam.core.realms.Realm;
import org.forgerock.util.i18n.PreferredLocales;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.forgerock.openam.utils.qr.GenerationUtils;

import com.google.inject.assistedinject.Assisted;

/**
 * The PingOne Credentials Pair Wallet node lets you pair PingOne digital wallet credentials with a Ping user ID.
 */
@Node.Metadata(
    outcomeProvider = PingOneCredentialsPairWallet.PairingOutcomeProvider.class,
    configClass = PingOneCredentialsPairWallet.Config.class,
    tags = {"marketplace", "trustnetwork", "pingone"})
public class PingOneCredentialsPairWallet implements Node {

    /** How often to poll AM for a response in milliseconds. */
    public static final int TRANSACTION_POLL_INTERVAL = 5000;

    /** The id of the HiddenCallback containing the URI. */
    public static final String HIDDEN_CALLBACK_ID = "pingOneCredentialPairingUri";

    private final Logger logger = LoggerFactory.getLogger(PingOneCredentialsPairWallet.class);
    private static final String LOGGER_PREFIX = "[PingOne Credentials Pair Wallet Node]" + PingOneCredentialsPlugin.LOG_APPENDER;

    public static final String BUNDLE = PingOneCredentialsPairWallet.class.getName();

    static final String DEFAULT_DELIVERY_METHOD_MESSAGE_KEY = "default.deliveryMethodMessage";
    static final String DEFAULT_WAITING_MESSAGE_KEY = "default.waitingMessage";
    static final String SCAN_QR_CODE_MSG_KEY = "default.scanQRCodeMessage";

    static final String QR_CALLBACK_STRING = "callback_0";
    static final int DEFAULT_TIMEOUT = 120;

    private final Config config;
    private final Realm realm;
    private final PingOneWorkerService pingOneWorkerService;
    private final LocalizationHelper localizationHelper;
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
         * The PingOne Digital Wallet Application ID
         *
         * @return The Digital Wallet Application ID as a String
         */
        @Attribute(order = 300, requiredValue = true)
        default String digitalWalletApplicationId() {
            return "";
        }

        /**
         * The QR Code Pairing URL delivery method
         *
         * @return Return true if the QR Code should be used to deliver the pairing URL, otherwise false.
         */
        @Attribute(order = 400, requiredValue = true)
        default boolean qrCodeDelivery() { return true; }

        /**
         * The Email Pairing URL delivery method.  The PingOne User object must have the email attribute populated.
         *
         * @return Return true if email should be used to deliver the pairing URL, otherwise false.
         */
        @Attribute(order = 500, requiredValue = true)
        default boolean emailDelivery() {
            return false;
        }

        /**
         * The SMS Pairing URL delivery method.  The PingOne User object must have the primaryPhone attribute populated.
         *
         * @return Return true if SMS should be used to deliver the pairing URL, otherwise false.
         */
        @Attribute(order = 600, requiredValue = true)
        default boolean smsDelivery() {
            return false;
        }

        /**
         * Allow user to choose the URL delivery method.
         * @return true if user will be prompted for delivery method, false otherwise.
         */
        @Attribute(order = 700, requiredValue = true)
        default boolean allowDeliveryMethodSelection() {
            return false;
        }

        /**
         * The message to display to the user allowing them to choose the delivery method. Keyed on the locale.
         * Falls back to default.deliverMethodMessage.
         * @return The message to display while choosing the delivery method.
         */
        @Attribute(order = 800)
        Map<Locale, String> deliveryMethodMessage();

        /**
         * The message to displayed to user to scan the QR code. Keyed on the locale. Falls back to
         * default.scanQRCodeMessage.
         * @return The mapping of locales to scan QR code messages.
         */
        @Attribute(order = 900)
        Map<Locale, String> scanQRCodeMessage();

        /**
         * The timeout in seconds for the verification process.
         * @return The timeout in seconds.
         */
        @Attribute(order = 1000)
        default Duration timeout() {
            return Duration.ofSeconds(DEFAULT_TIMEOUT);
        }

        /**
         * The message to display to the user while waiting, keyed on the locale. Falls back to default.waitingMessage.
         * @return The message to display on the waiting indicator.
         */
        @Attribute(order = 1100)
        Map<Locale, String> waitingMessage();

        /**
         * Store the create wallet response in the shared state.
         * @return true if the create wallet response should be stored, false otherwise.
         */
        @Attribute(order = 1200, requiredValue = true)
        default boolean storeWalletResponse() {
            return false;
        }
    }

    /**
     * The PingOne Credentials Pair Wallet node constructor.
     *
     * @param config               the node configuration.
     * @param realm                the realm.
     * @param pingOneWorkerService the {@link PingOneWorkerService} instance.
     * @param client               the {@link PingOneCredentialsService} instance.
     * @param localizationHelper   the {@link LocalizationHelper} instance.
     */
    @Inject
    PingOneCredentialsPairWallet(@Assisted Config config, @Assisted Realm realm,
                                 PingOneWorkerService pingOneWorkerService, PingOneCredentialsService client,
                                 LocalizationHelper localizationHelper) {
        this.config = config;
        this.realm = realm;
        this.pingOneWorkerService = pingOneWorkerService;
        this.client = client;
        this.localizationHelper = localizationHelper;
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

            // Get PingOne Access Token
            PingOneWorkerConfig.Worker worker = config.pingOneWorker();
            AccessToken accessToken = pingOneWorkerService.getAccessToken(realm, worker);

            if (accessToken == null) {
                logger.error("Unable to get access token for PingOne Worker.");
                return buildAction(ERROR_OUTCOME_ID, context);
            }

            // Check if choice was made
            Optional<ConfirmationCallback> confirmationCallback = context.getCallback(ConfirmationCallback.class);

            if (confirmationCallback.isPresent()) {
                int choice = confirmationCallback.get().getSelectedIndex();
                nodeState.putShared(PINGONE_PAIRING_DELIVERY_METHOD_KEY, choice);

                PairingDeliveryMethod deliveryMethod = PairingDeliveryMethod.fromIndex(choice);

                boolean qrCodeDelivery = false;
                boolean emailDelivery = false;
                boolean smsDelivery = false;

                switch(deliveryMethod) {
                    case QRCODE -> qrCodeDelivery = true;
                    case EMAIL -> emailDelivery = true;
                    case SMS -> smsDelivery = true;
                }

                return startPairingTransaction(context, accessToken, worker, qrCodeDelivery, emailDelivery, smsDelivery,
                                               pingOneUserId, config.digitalWalletApplicationId());
            }

            // Check if transaction was started
            Optional<PollingWaitCallback> pollingWaitCallback = context.getCallback(PollingWaitCallback.class);
            if (pollingWaitCallback.isPresent()) {
                // Transaction already started;
                if (!nodeState.isDefined(PINGONE_PAIRING_WALLET_ID_KEY)) {
                    return buildAction(ERROR_OUTCOME_ID, context);
                }
                return getActionFromPairingTransactionStatus(context, accessToken, worker, pingOneUserId);
            } else {

                // Start new pairing transaction
                if (config.allowDeliveryMethodSelection()) {
                    List<Callback> callbacks = createChoiceCallbacks(context);
                    return send(callbacks).build();
                } else {
                    return startPairingTransaction(context, accessToken, worker, config.qrCodeDelivery(),
                                                   config.emailDelivery(), config.smsDelivery(), pingOneUserId,
                                                   config.digitalWalletApplicationId());
                }
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

    private Action getActionFromPairingTransactionStatus(TreeContext context, AccessToken accessToken,
                                                         PingOneWorkerConfig.Worker worker,
                                                         String pingOneUserId)
        throws Exception {
        NodeState nodeState = context.getStateFor(this);

        // Retrieve transaction ID from shared state
        String walletId = Objects.requireNonNull(nodeState.get(PINGONE_PAIRING_WALLET_ID_KEY)).asString();

        // Default to qr code delivery is disabled
        boolean qrCodeDelivery = false;

        if (config.allowDeliveryMethodSelection()) {
            int index = Objects.requireNonNull(nodeState.get(PINGONE_PAIRING_DELIVERY_METHOD_KEY)).asInteger();
            if(PairingDeliveryMethod.QRCODE.equals(PairingDeliveryMethod.fromIndex(index))) {
                qrCodeDelivery = true;
            }
        } else {

            if(config.qrCodeDelivery()) {
                qrCodeDelivery = true;
            }
        }

        // Check transaction status and take appropriate action
        JsonValue response = client.readDigitalWallet(accessToken,
                                                      worker,
                                                      pingOneUserId,
                                                      walletId);
        // Retrieve response values
        String status = response.get(RESPONSE_STATUS).asString();

        switch (status) {
            case PAIRING_REQUIRED:
                if(nodeState.isDefined(PINGONE_APPOPEN_URL_KEY)) {
                    String qrUrl = nodeState.get(PINGONE_APPOPEN_URL_KEY).asString();

                    List<Callback> callbacks = getCallbacksForDeliveryMethod(context, qrCodeDelivery, qrUrl);
                    return waitTransactionCompletion(nodeState, callbacks).build();
                } else {
                    throw new IllegalStateException("Missing AppOpen URL in nodeState.");
                }
            case ACTIVE:
                nodeState.putShared(PINGONE_WALLET_ID_KEY, response.get(RESPONSE_ID));

                if (config.storeWalletResponse()) {
                    nodeState.putShared(PINGONE_WALLET_DATA_KEY, response);
                }
                return buildAction(SUCCESS_OUTCOME_ID, context);
            case EXPIRED:
                return buildAction(ERROR_OUTCOME_ID, context);
            default:
                throw new IllegalStateException("Unexpected status returned from PingOne Pairing Transaction: "
                                                + status);
        }
    }

    private Action startPairingTransaction(TreeContext context, AccessToken accessToken,
                                           PingOneWorkerConfig.Worker worker, boolean qrCodeDelivery,
                                           boolean emailDelivery, boolean smsDelivery, String pingOneUserId,
                                           String digitalWalletApplicationId)
        throws Exception {

        List<String> notificationList = new ArrayList<String>();

        if(emailDelivery) {
            notificationList.add(PairingDeliveryMethod.EMAIL.name());
        }

        if(smsDelivery) {
            notificationList.add(PairingDeliveryMethod.SMS.name());
        }

        JsonValue response = client.createDigitalWalletRequest(accessToken,
                                                               worker,
                                                               pingOneUserId,
                                                               digitalWalletApplicationId,
                                                               notificationList);

        // Retrieve response values
        String digitalWalletId = response.get(RESPONSE_ID).asString();

        // Use the App Open URL for the QR Code URL
        String appOpenUrl = response.get(RESPONSE_LINKS).get(RESPONSE_APPOPEN).get(RESPONSE_HREF).asString();

        // Store transaction ID in shared state
        NodeState nodeState = context.getStateFor(this);
        nodeState.putShared(PINGONE_PAIRING_WALLET_ID_KEY, digitalWalletId);
        nodeState.putShared(PINGONE_PAIRING_TIMEOUT_KEY, TRANSACTION_POLL_INTERVAL);

        // Store the app open URL to be used during the polling
        nodeState.putTransient(PINGONE_APPOPEN_URL_KEY, appOpenUrl);

        // Create callbacks and send
        List<Callback> callbacks = getCallbacksForDeliveryMethod(context, qrCodeDelivery, appOpenUrl);
        return send(callbacks).build();
    }

    private List<Callback> getCallbacksForDeliveryMethod(TreeContext context, boolean qrCodeDelivery,
                                                         String url) {
        String waitingMessage = getWaitingMessage(context);

        Callback pollingCallback = PollingWaitCallback.makeCallback()
                                                      .withWaitTime(String.valueOf(TRANSACTION_POLL_INTERVAL))
                                                      .withMessage(waitingMessage)
                                                      .build();

        if (qrCodeDelivery) {
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

    private Action.ActionBuilder waitTransactionCompletion(NodeState nodeState, List<Callback> callbacks) {
        long timeOutInMs = config.timeout().getSeconds() * 1000;
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
        NodeState nodeState = context.getStateFor(this);
        nodeState.remove(PINGONE_PAIRING_WALLET_ID_KEY);
        nodeState.remove(PINGONE_PAIRING_DELIVERY_METHOD_KEY);
        nodeState.remove(PINGONE_PAIRING_TIMEOUT_KEY);
        return builder;
    }

    @Override
    public InputState[] getInputs() {
        return new InputState[] {
            new InputState(config.pingOneUserIdAttribute(), false),
            new InputState(PINGONE_PAIRING_WALLET_ID_KEY, false),
            new InputState(PINGONE_PAIRING_DELIVERY_METHOD_KEY, false),
            new InputState(PINGONE_PAIRING_TIMEOUT_KEY, false),
            new InputState(PINGONE_APPOPEN_URL_KEY, false),
            new InputState(OBJECT_ATTRIBUTES, false)
            };
    }

    @Override
    public OutputState[] getOutputs() {
        return new OutputState[]{
            new OutputState(PINGONE_PAIRING_WALLET_ID_KEY),
            new OutputState(PINGONE_PAIRING_DELIVERY_METHOD_KEY),
            new OutputState(PINGONE_PAIRING_TIMEOUT_KEY),
            new OutputState(PINGONE_APPOPEN_URL_KEY),
        };
    }

    public static class PairingOutcomeProvider implements StaticOutcomeProvider {
        @Override
        public List<Outcome> getOutcomes(PreferredLocales locales) {
            ResourceBundle bundle = locales.getBundleInPreferredLocale(PingOneCredentialsPairWallet.BUNDLE,
                                                                       OutcomeProvider.class.getClassLoader());
            List<Outcome> results = new ArrayList<>();
            results.add(new Outcome(SUCCESS_OUTCOME_ID, bundle.getString("successOutcome")));
            results.add(new Outcome(ERROR_OUTCOME_ID, bundle.getString("errorOutcome")));
            results.add(new Outcome(TIMEOUT_OUTCOME_ID, bundle.getString("timeoutOutcome")));
            return Collections.unmodifiableList(results);
        }
    }
}
