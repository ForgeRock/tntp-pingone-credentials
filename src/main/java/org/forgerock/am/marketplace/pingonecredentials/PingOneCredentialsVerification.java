/*
 * This code is to be used exclusively in connection with Ping Identity Corporation software or services. 
 * Ping Identity Corporation only offers such software or services to legal entities who have entered into 
 * a binding license agreement with Ping Identity Corporation.
 *
 * Copyright 2024 Ping Identity Corporation. All Rights Reserved
 */

package org.forgerock.am.marketplace.pingonecredentials;

import static org.forgerock.am.marketplace.pingonecredentials.Constants.EXPIRED;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.FAILURE_OUTCOME_ID;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.INITIAL;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_VERIFICATION_TIMEOUT_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_VERIFICATION_DELIVERY_METHOD_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_VERIFICATION_SESSION_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_CREDENTIAL_VERIFICATION_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.RESPONSE_HREF;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.RESPONSE_ID;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.RESPONSE_LINKS;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.RESPONSE_QR;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.RESPONSE_STATUS;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.SUCCESS_OUTCOME_ID;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.TIMEOUT_OUTCOME_ID;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.VERIFICATION_SUCCESSFUL;
import static org.forgerock.openam.auth.node.api.Action.send;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.VerificationDeliveryMethod;

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
import org.forgerock.openam.utils.qr.GenerationUtils;
import org.forgerock.util.i18n.PreferredLocales;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.assistedinject.Assisted;




@Node.Metadata(
		outcomeProvider = PingOneCredentialsVerification.AuthenticationOutcomeProvider.class,
		configClass = PingOneCredentialsVerification.Config.class,
		tags = {"marketplace", "trustnetwork" })
public class PingOneCredentialsVerification implements Node {

	/** How often to poll AM for a response in milliseconds. */
	public static final int TRANSACTION_POLL_INTERVAL = 5000;

	/** The id of the HiddenCallback containing the URI. */
	public static final String HIDDEN_CALLBACK_ID = "pingOneCredentialVerificationUri";

	private final Logger logger = LoggerFactory.getLogger(PingOneCredentialsVerification.class);
	private final String loggerPrefix = "[PingOne Credentials Verification Node]" + PingOneCredentialsPlugin.logAppender;

	public static final String BUNDLE = PingOneCredentialsVerification.class.getName();

	static final String DEFAULT_DELIVERY_METHOD_MESSAGE_KEY = "default.deliveryMethodMessage";
	static final String DEFAULT_WAITING_MESSAGE_KEY = "default.waitingMessage";
	static final String DEFAULT_PUSH_MESSAGE_KEY = "default.pushMessage";
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
		default String credentialType() {
			return "";
		}

		@Attribute(order = 300)
		default List<String> attributeKeys() {
			return Collections.emptyList();
		}

		@Attribute(order = 400)
		default String applicationInstanceId() {
			return "";
		}

		@Attribute(order = 500)
		default String digitalWalletApplicationId() {return ""; }

		/**
		 * The Pairing URL delivery method.
		 *
		 * @return The type of delivery method.
		 */
		@Attribute(order = 600)
		default VerificationDeliveryMethod deliveryMethod() {
			return VerificationDeliveryMethod.QRCODE;
		}

		/**
		 * Allow user to choose the URL delivery method.
		 * @return true if user will be prompted for delivery method, false otherwise.
		 */
		@Attribute(order = 700)
		default boolean allowDeliveryMethodSelection() {
			return false;
		}


		/**
		 * The message to display to the user allowing them to choose the delivery method. Keyed on the locale.
		 * Falls back to default.deliverMethodMessage.
		 * @return The message to display while choosing the delivery method.
		 */
		@Attribute(order = 800)
		default Map<Locale, String> deliveryMethodMessage() {
			return Collections.emptyMap();
		}

		/**
		 * The message to displayed to user to scan the QR code. Keyed on the locale. Falls back to
		 * default.scanQRCodeMessage.
		 * @return The mapping of locales to scan QR code messages.
		 */
		@Attribute(order = 900)
		default Map<Locale, String> scanQRCodeMessage() {
			return Collections.emptyMap();
		}

		/**
		 * The timeout in seconds for the verification process.
		 * @return The timeout in seconds.
		 */
		@Attribute(order = 1000)
		default int timeout() {
			return DEFAULT_TIMEOUT;
		}

		/**
		 * The message to display to the user while waiting, keyed on the locale. Falls back to default.waitingMessage.
		 * @return The message to display on the waiting indicator.
		 */
		@Attribute(order = 1100)
		default Map<Locale, String> waitingMessage() {
			return Collections.emptyMap();
		}

		/**
		 * The message to display to the user during a push verification request, keyed on the locale. Falls back to default.waitingMessage.
		 * @return The message to display on the waiting indicator.
		 */
		@Attribute(order = 1100)
		default Map<Locale, String> pushMessage() {
			return Collections.emptyMap();
		}

		/**
		 * Store the create verification response in the shared state.
		 * @return true if the create verification response should be stored, false otherwise.
		 */
		@Attribute(order = 1200)
		default boolean storeVerificationResponse() {
			return false;
		}

	}

	@Inject
	public PingOneCredentialsVerification(@Assisted Config config, @Assisted Realm realm, Helper client,
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
				nodeState.putShared(PINGONE_VERIFICATION_DELIVERY_METHOD_KEY, choice);
				return startVerificationTransaction(context, accessToken, Constants.VerificationDeliveryMethod.fromIndex(choice),
				                                    config.credentialType(), getWaitingMessage(context),
				                                    config.attributeKeys());
			}

			// Check if transaction was started
			Optional<PollingWaitCallback> pollingWaitCallback = context.getCallback(PollingWaitCallback.class);
			if (pollingWaitCallback.isPresent()) {
				// Transaction already started
				logger.error("Identity Verification process already started. Waiting for completion...");
				if (!nodeState.isDefined(PINGONE_VERIFICATION_SESSION_KEY)) {
					logger.error("Unable to find the PingOne Verify Transaction ID in sharedState.");
					return buildAction(FAILURE_OUTCOME_ID, context);
				}
				return getActionFromVerificationStatus(context, accessToken);
			} else {
				// Check if it should resume a previous transaction set by the Completion Decision node
                /*if (nodeState.isDefined(PINGONE_WALLET_ID_KEY)) {
                    logger.debug("Resuming an Identity Verification process previously started.");
                    nodeState.putShared(PINGONE_VERIFY_DELIVERY_METHOD_KEY, 0);
                    nodeState.putShared(PINGONE_VERIFY_TIMEOUT_KEY, 0);
                    return getActionFromPairingTransactionStatus(context, accessToken, pingOneUserId);
                }*/

				// Start new pairing transaction
				if (config.allowDeliveryMethodSelection()) {
					logger.error("Present options to select the delivery method.");
					List<Callback> callbacks = createChoiceCallbacks(context);
					return send(callbacks).build();
				} else {
					logger.error("Start new Identity Verification process.");
					return startVerificationTransaction(context, accessToken, config.deliveryMethod(),
					                                    config.credentialType(), "Message for Pairing...",
					                                    config.attributeKeys());
				}
			}
		} catch (Exception ex) {
			String stackTrace = org.apache.commons.lang.exception.ExceptionUtils.getStackTrace(ex);
			logger.error(loggerPrefix + "Exception occurred: ", ex);
			context.getStateFor(this).putTransient(loggerPrefix + "Exception", ex.getMessage());
			context.getStateFor(this).putTransient(loggerPrefix + "StackTrace", stackTrace);
			return Action.goTo(FAILURE_OUTCOME_ID).build();
		}
	}

	private Action getActionFromVerificationStatus(TreeContext context, AccessToken accessToken)
		throws Exception {
		NodeState nodeState = context.getStateFor(this);

		// Retrieve verification session ID from shared state
		String sessionId = Objects.requireNonNull(nodeState.get(PINGONE_VERIFICATION_SESSION_KEY)).asString();

		// Determine delivery method
		Constants.VerificationDeliveryMethod verificationDeliveryMethod;
		if (config.allowDeliveryMethodSelection()) {
			int index = Objects.requireNonNull(nodeState.get(PINGONE_VERIFICATION_DELIVERY_METHOD_KEY)).asInteger();
			verificationDeliveryMethod = Constants.VerificationDeliveryMethod.fromIndex(index);
		} else {
			verificationDeliveryMethod = config.deliveryMethod();
		}

		// Check transaction status and take appropriate action
		JsonValue response = client.readVerificationSession(accessToken,
		                                                    tntpPingOneConfig.environmentRegion().getDomainSuffix(),
		                                                    tntpPingOneConfig.environmentId(),
		                                                    sessionId);

		logger.error("readVerificationSession: " + response.toString());
		// Retrieve response values
		String status = response.get(RESPONSE_STATUS).asString();
		String qrUrl = response.get(RESPONSE_LINKS).get(RESPONSE_QR).get(RESPONSE_HREF).asString();


		switch (status) {
			case INITIAL:
				logger.error("Status is initial, waiting...");
				List<Callback> callbacks = getCallbacksForDeliveryMethod(context, verificationDeliveryMethod, qrUrl);
				return waitTransactionCompletion(nodeState, callbacks, config.timeout()).build();
			case VERIFICATION_SUCCESSFUL:
				logger.error("Status is VERIFICATION_SUCCESSFUL, returning success");
				if (config.storeVerificationResponse()) {
					nodeState.putShared(PINGONE_CREDENTIAL_VERIFICATION_KEY, response);
				}
				return buildAction(SUCCESS_OUTCOME_ID, context);
			case EXPIRED:
				logger.error("Status is expired, returning failure");
				return buildAction(FAILURE_OUTCOME_ID, context);
			default:
				throw new IllegalStateException("Unexpected status returned from PingOne Credential Verification: "
				                                + status);
		}
	}

	private Action startVerificationTransaction(TreeContext context, AccessToken accessToken,
	                                            Constants.VerificationDeliveryMethod deliveryMethod,
	                                            String credentialType, String message,
	                                            List<String> attributeKeys) throws Exception {

		String qrUrl = ""; // Value will not be used if delivery is not QRCODE

		if(deliveryMethod.equals(Constants.VerificationDeliveryMethod.QRCODE)) {


			JsonValue response = client.createVerificationRequest(accessToken,
			                                                      tntpPingOneConfig.environmentRegion().getDomainSuffix(),
			                                                      tntpPingOneConfig.environmentId(),
			                                                      message,
			                                                      credentialType,
			                                                      attributeKeys);

			// Retrieve response values
			String sessionId = response.get(RESPONSE_ID).asString();
			qrUrl = response.get(RESPONSE_LINKS).get(RESPONSE_QR).get(RESPONSE_HREF).asString();

			// Store transaction ID in shared state
			NodeState nodeState = context.getStateFor(this);
			nodeState.putShared(PINGONE_VERIFICATION_SESSION_KEY, sessionId);
			nodeState.putShared(PINGONE_VERIFICATION_TIMEOUT_KEY, TRANSACTION_POLL_INTERVAL);

		} else if(deliveryMethod.equals(Constants.VerificationDeliveryMethod.PUSH)) {

			JsonValue response = client.createVerificationRequestPush(accessToken,
			                                                          tntpPingOneConfig.environmentRegion().getDomainSuffix(),
			                                                          tntpPingOneConfig.environmentId(),
			                                                          message,
			                                                          credentialType,
			                                                          attributeKeys,
			                                                          config.applicationInstanceId(),
			                                                          config.digitalWalletApplicationId());

			// Retrieve response values
			String sessionId = response.get(RESPONSE_ID).asString();

			// Store transaction ID in shared state
			NodeState nodeState = context.getStateFor(this);
			nodeState.putShared(PINGONE_VERIFICATION_SESSION_KEY, sessionId);
			nodeState.putShared(PINGONE_VERIFICATION_TIMEOUT_KEY, TRANSACTION_POLL_INTERVAL);
		}

		// Create callbacks and send
		List<Callback> callbacks = getCallbacksForDeliveryMethod(context, deliveryMethod, qrUrl);

		return send(callbacks).build();
	}

	private List<Callback> getCallbacksForDeliveryMethod(TreeContext context, Constants.VerificationDeliveryMethod deliveryMethod,
	                                                     String url) {
		String waitingMessage = getWaitingMessage(context);

		Callback pollingCallback = PollingWaitCallback.makeCallback()
		                                              .withWaitTime(String.valueOf(TRANSACTION_POLL_INTERVAL))
		                                              .withMessage(waitingMessage)
		                                              .build();

		if (deliveryMethod.equals(VerificationDeliveryMethod.QRCODE)) {
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
		                                              PingOneCredentialsVerification.class,
		                                              config.waitingMessage(),
		                                              DEFAULT_WAITING_MESSAGE_KEY);
	}

	private String getPushMessage(TreeContext context) {
		return localizationHelper.getLocalizedMessage(context,
		                                              PingOneCredentialsVerification.class,
		                                              config.pushMessage(),
		                                              DEFAULT_PUSH_MESSAGE_KEY);
	}

	private Action.ActionBuilder waitTransactionCompletion(NodeState nodeState, List<Callback> callbacks, int timeout) {
		logger.error("Waiting pairing transaction to be completed.");
		int timeOutInMs = timeout * 1000;
		int timeElapsed = nodeState.get(PINGONE_VERIFICATION_TIMEOUT_KEY).asInteger();

		logger.error("timeElapsed: " + timeElapsed);
		logger.error("timeOutInMs: " + timeOutInMs);
		if (timeElapsed >= timeOutInMs) {
			logger.error("Returning timeout.");
			return Action.goTo(TIMEOUT_OUTCOME_ID);
		} else {
			nodeState.putShared(PINGONE_VERIFICATION_TIMEOUT_KEY, timeElapsed + TRANSACTION_POLL_INTERVAL);
		}
		return send(callbacks);
	}

	private List<Callback> createChoiceCallbacks(TreeContext context) {
		List<Callback> callbacks = new ArrayList<>();
		String message = localizationHelper.getLocalizedMessage(context, this.getClass(),
		                                                        config.deliveryMethodMessage(), DEFAULT_DELIVERY_METHOD_MESSAGE_KEY);
		String[] options = {
			localizationHelper.getLocalizedMessage(context, this.getClass(), null, "deliveryMethod.QRCODE"),
			localizationHelper.getLocalizedMessage(context, this.getClass(), null, "deliveryMethod.PUSH"),
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
		nodeState.remove(PINGONE_VERIFICATION_SESSION_KEY);
		nodeState.remove(PINGONE_VERIFICATION_DELIVERY_METHOD_KEY);
		nodeState.remove(PINGONE_VERIFICATION_TIMEOUT_KEY);
		return builder;
	}

	public static class AuthenticationOutcomeProvider implements OutcomeProvider {
		@Override
		public List<Outcome> getOutcomes(PreferredLocales locales, JsonValue nodeAttributes) {
			ResourceBundle bundle = locales.getBundleInPreferredLocale(PingOneCredentialsVerification.BUNDLE,
			                                                           OutcomeProvider.class.getClassLoader());
			List<Outcome> results = new ArrayList<>();
			results.add(new Outcome(SUCCESS_OUTCOME_ID, bundle.getString("successOutcome")));
			results.add(new Outcome(FAILURE_OUTCOME_ID, bundle.getString("failureOutcome")));
			results.add(new Outcome(TIMEOUT_OUTCOME_ID, bundle.getString("timeoutOutcome")));
			return Collections.unmodifiableList(results);
		}
	}
}
