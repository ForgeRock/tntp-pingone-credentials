/*
 * This code is to be used exclusively in connection with Ping Identity Corporation software or services. 
 * Ping Identity Corporation only offers such software or services to legal entities who have entered into 
 * a binding license agreement with Ping Identity Corporation.
 *
 * Copyright 2024 Ping Identity Corporation. All Rights Reserved
 */

package org.forgerock.am.marketplace.pingonecredentials;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.EXPIRED;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.ERROR_OUTCOME_ID;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.INITIAL;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.OBJECT_ATTRIBUTES;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_APPLICATION_INSTANCE_ID_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_VERIFICATION_TIMEOUT_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_VERIFICATION_DELIVERY_METHOD_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_VERIFICATION_SESSION_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_CREDENTIAL_VERIFICATION_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.REQUESTED_CREDENTIALS;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.RESPONSE_APPLICATION_INSTANCE;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.RESPONSE_APPOPENURL;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.RESPONSE_HREF;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.RESPONSE_ID;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.RESPONSE_LINKS;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.RESPONSE_STATUS;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.SUCCESS_OUTCOME_ID;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.TIMEOUT_OUTCOME_ID;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.VERIFICATION_SUCCESSFUL;
import static org.forgerock.openam.auth.node.api.Action.send;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.VerificationDeliveryMethod;

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
import org.forgerock.openam.auth.nodes.helpers.LocalizationHelper;
import org.forgerock.openam.integration.pingone.PingOneWorkerConfig;
import org.forgerock.openam.integration.pingone.PingOneWorkerService;
import org.forgerock.openam.integration.pingone.annotations.PingOneWorker;
import org.forgerock.openam.authentication.callbacks.PollingWaitCallback;
import org.forgerock.openam.core.realms.Realm;
import org.forgerock.openam.sm.annotations.adapters.TimeUnit;
import org.forgerock.openam.utils.qr.GenerationUtils;
import org.forgerock.util.i18n.PreferredLocales;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.assistedinject.Assisted;

@Node.Metadata(
		outcomeProvider = PingOneCredentialsVerification.VerificationOutcomeProvider.class,
		configClass = PingOneCredentialsVerification.Config.class,
		tags = {"marketplace", "trustnetwork", "pingone"})
public class PingOneCredentialsVerification implements Node {

	/** How often to poll AM for a response in milliseconds. */
	public static final int TRANSACTION_POLL_INTERVAL = 5000;

	/** The id of the HiddenCallback containing the URI. */
	public static final String HIDDEN_CALLBACK_ID = "pingOneCredentialVerificationUri";

	private final Logger logger = LoggerFactory.getLogger(PingOneCredentialsVerification.class);
	private static final String LOGGER_PREFIX = "[PingOne Credentials Verification Node]" + PingOneCredentialsPlugin.LOG_APPENDER;

	public static final String BUNDLE = PingOneCredentialsVerification.class.getName();

	static final String DEFAULT_DELIVERY_METHOD_MESSAGE_KEY = "default.deliveryMethodMessage";
	static final String DEFAULT_WAITING_MESSAGE_KEY = "default.waitingMessage";
	static final String DEFAULT_PUSH_MESSAGE_KEY = "default.pushMessage";
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
		 * The Credential Type of the Credential (not the ID)
		 *
		 * @return The Credential Type as a String
		 */
		@Attribute(order = 200, requiredValue = true)
		String credentialType();

		/**
		 * The list of Credential attributes to disclose during the presentation
		 *
		 * @return List of Credential attributes as a List of Strings
		 */
		@Attribute(order = 300)
		List<String> attributeKeys();

		/**
		 * The Pairing URL delivery method.
		 *
		 * @return The type of delivery method.
		 */
		@Attribute(order = 400, requiredValue = true)
		default VerificationDeliveryMethod deliveryMethod() {
			return VerificationDeliveryMethod.QRCODE;
		}

		/**
		 * The PingOne Digital Wallet Application ID
		 *
		 * @return The Digital Wallet Application ID as a String
		 */
		@Attribute(order = 500)
		Optional<String> digitalWalletApplicationId();

		/**
		 * The shared state attribute containing the PingOne Application Instance ID
		 *
		 * @return The Application Instance ID as a String
		 */
		@Attribute(order = 600)
		default String applicationInstanceAttribute() {
			return PINGONE_APPLICATION_INSTANCE_ID_KEY;
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
		@TimeUnit(SECONDS)
		default Duration timeout() {
			return Duration.ofSeconds(DEFAULT_TIMEOUT);
		}

		/**
		 * The message to display to the user while waiting, keyed on the locale. Falls back to default.waitingMessage.
		 * @return The message to display on the waiting indicator.
		 */
		@Attribute(order = 1100)
		Map<Locale, String> waitingMessage();

		/**ss
		 * The message to display to the user during a push verification request, keyed on the locale. Falls back to default.waitingMessage.
		 * @return The message to display on the waiting indicator.
		 */
		@Attribute(order = 1200)
		Map<Locale, String> pushMessage();

		/**
		 * Store the create verification response in the shared state.
		 * @return true if the create verification response should be stored, false otherwise.
		 */
		@Attribute(order = 1300, requiredValue = true)
		default boolean storeVerificationResponse() {
			return true;
		}

		/**
		 * Toggle if a custom requested credentials payload should be used
		 * @return true if the create verification response should be stored, false otherwise.
		 */
		@Attribute(order = 1400, requiredValue = true)
		default boolean customCredentialsPayload() {
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
	PingOneCredentialsVerification(@Assisted Config config, @Assisted Realm realm,
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

			// Get PingOne Access Token
			PingOneWorkerConfig.Worker worker = config.pingOneWorker();
			AccessToken accessToken = pingOneWorkerService.getAccessToken(realm, worker);

			// Check if choice was made
			Optional<ConfirmationCallback> confirmationCallback = context.getCallback(ConfirmationCallback.class);
			if (confirmationCallback.isPresent()) {
				int choice = confirmationCallback.get().getSelectedIndex();
				nodeState.putShared(PINGONE_VERIFICATION_DELIVERY_METHOD_KEY, choice);

				return startVerificationTransaction(context, accessToken, worker, Constants.VerificationDeliveryMethod.fromIndex(choice),
				                                    config.credentialType(), getPushMessage(context),
				                                    config.attributeKeys());
			}

			// Check if transaction was started
			Optional<PollingWaitCallback> pollingWaitCallback = context.getCallback(PollingWaitCallback.class);
			if (pollingWaitCallback.isPresent()) {
				// Transaction already started
				if (!nodeState.isDefined(PINGONE_VERIFICATION_SESSION_KEY)) {
					return buildAction(ERROR_OUTCOME_ID, context);
				}
				return getActionFromVerificationStatus(context, accessToken, worker);
			} else {
				// Start new pairing transaction
				if (config.allowDeliveryMethodSelection()) {
					List<Callback> callbacks = createChoiceCallbacks(context);
					return send(callbacks).build();
				} else {
					return startVerificationTransaction(context, accessToken, worker, config.deliveryMethod(),
					                                    config.credentialType(), getPushMessage(context),
					                                    config.attributeKeys());
				}
			}
		} catch (Exception ex) {
			String stackTrace = org.apache.commons.lang.exception.ExceptionUtils.getStackTrace(ex);
			logger.error(LOGGER_PREFIX + "Exception occurred: ", ex);
			NodeState nodeState = context.getStateFor(this);

			nodeState.putTransient(LOGGER_PREFIX + "Exception", ex.getMessage());
			nodeState.putTransient(LOGGER_PREFIX + "StackTrace", stackTrace);

			return buildAction(ERROR_OUTCOME_ID, context);
		}
	}

	private Action getActionFromVerificationStatus(TreeContext context, AccessToken accessToken,
	                                               PingOneWorkerConfig.Worker worker) throws Exception {
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
		                                                    worker,
		                                                    sessionId);

		// Retrieve response values
		String status = response.get(RESPONSE_STATUS).asString();
		String qrUrl = response.get(RESPONSE_LINKS).get(RESPONSE_APPOPENURL).get(RESPONSE_HREF).asString();

		switch (status) {
			case INITIAL:
				List<Callback> callbacks = getCallbacksForDeliveryMethod(context, verificationDeliveryMethod, qrUrl);
				return waitTransactionCompletion(nodeState, callbacks).build();
			case VERIFICATION_SUCCESSFUL:
				String applicationInstanceId = response.get(RESPONSE_APPLICATION_INSTANCE).get(RESPONSE_ID).asString();

				// Store application instance ID
				nodeState.putShared(PINGONE_APPLICATION_INSTANCE_ID_KEY, applicationInstanceId);

				if (config.storeVerificationResponse()) {
					nodeState.putShared(PINGONE_CREDENTIAL_VERIFICATION_KEY, response);
				}
				return buildAction(SUCCESS_OUTCOME_ID, context);
			case EXPIRED:
				return buildAction(ERROR_OUTCOME_ID, context);
			default:
				throw new IllegalStateException("Unexpected status returned from PingOne Credential Verification: "
				                                + status);
		}
	}

	private Action startVerificationTransaction(TreeContext context, AccessToken accessToken,
	                                            PingOneWorkerConfig.Worker worker,
	                                            VerificationDeliveryMethod deliveryMethod,
	                                            String credentialType, String message,
	                                            List<String> attributeKeys) throws Exception {

		String qrUrl = ""; // Value will not be used if delivery is not QRCODE

		if(Constants.VerificationDeliveryMethod.QRCODE.equals(deliveryMethod)) {
			NodeState nodeState = context.getStateFor(this);

			JsonValue customCredentialsPayload = null;

			if(config.customCredentialsPayload()) {
				customCredentialsPayload = nodeState.get(REQUESTED_CREDENTIALS);
			}

			JsonValue response = client.createVerificationRequest(accessToken,
																  worker,
			                                                      message,
			                                                      credentialType,
			                                                      attributeKeys,
			                                                      customCredentialsPayload);

			// Retrieve response values
			String sessionId = response.get(RESPONSE_ID).asString();

			qrUrl = response.get(RESPONSE_LINKS).get(RESPONSE_APPOPENURL).get(RESPONSE_HREF).asString();

			// Store session ID in shared state
			nodeState.putShared(PINGONE_VERIFICATION_SESSION_KEY, sessionId);
			nodeState.putShared(PINGONE_VERIFICATION_TIMEOUT_KEY, TRANSACTION_POLL_INTERVAL);
		} else if(Constants.VerificationDeliveryMethod.PUSH.equals(deliveryMethod)) {

			NodeState nodeState = context.getStateFor(this);

			String digitalWalletApplicationId;

			// Check if PingOne User ID attribute is set in sharedState
			String applicationInstanceId = nodeState.isDefined(PINGONE_APPLICATION_INSTANCE_ID_KEY)
			                               ? nodeState.get(PINGONE_APPLICATION_INSTANCE_ID_KEY).asString()
			                               : null;

			if (StringUtils.isBlank(applicationInstanceId)) {
				logger.error("Expected applicationInstanceId to be set in sharedState.");
				return Action.goTo(ERROR_OUTCOME_ID).build();
			}

			if (config.digitalWalletApplicationId().isPresent()) {
				digitalWalletApplicationId = config.digitalWalletApplicationId().get();
			} else {
				logger.error("Exepcted digitalWalletApplicationId to be configured for Push notification");
				return Action.goTo(ERROR_OUTCOME_ID).build();
			}


			JsonValue customCredentialsPayload = null;

			if(config.customCredentialsPayload()) {
				customCredentialsPayload = nodeState.get(REQUESTED_CREDENTIALS);
			}

			JsonValue response = client.createVerificationRequestPush(accessToken,
			                                                          worker,
			                                                          message,
			                                                          credentialType,
			                                                          attributeKeys,
			                                                          applicationInstanceId,
			                                                          digitalWalletApplicationId,
			                                                          customCredentialsPayload);

			// Retrieve response values
			String sessionId = response.get(RESPONSE_ID).asString();

			// Store session ID in shared state
			nodeState.putShared(PINGONE_VERIFICATION_SESSION_KEY, sessionId);
			nodeState.putShared(PINGONE_VERIFICATION_TIMEOUT_KEY, TRANSACTION_POLL_INTERVAL);
		}

		// Create callbacks and send
		List<Callback> callbacks = getCallbacksForDeliveryMethod(context, deliveryMethod, qrUrl);

		return send(callbacks).build();
	}

	private List<Callback> getCallbacksForDeliveryMethod(TreeContext context, VerificationDeliveryMethod deliveryMethod,
	                                                     String url) {
		String waitingMessage = getWaitingMessage(context);

		Callback pollingCallback = PollingWaitCallback.makeCallback()
		                                              .withWaitTime(String.valueOf(TRANSACTION_POLL_INTERVAL))
		                                              .withMessage(waitingMessage)
		                                              .build();

		if (VerificationDeliveryMethod.QRCODE.equals(deliveryMethod)) {
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

	private Action.ActionBuilder waitTransactionCompletion(NodeState nodeState, List<Callback> callbacks) {
		long timeOutInMs = config.timeout().getSeconds() * 1000;
		int timeElapsed = nodeState.get(PINGONE_VERIFICATION_TIMEOUT_KEY).asInteger();

		if (timeElapsed >= timeOutInMs) {
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

	@Override
	public InputState[] getInputs() {
		return new InputState[] {
			new InputState(PINGONE_VERIFICATION_SESSION_KEY, false),
			new InputState(PINGONE_VERIFICATION_DELIVERY_METHOD_KEY, false),
			new InputState(PINGONE_VERIFICATION_TIMEOUT_KEY, false),
			new InputState(OBJECT_ATTRIBUTES, false),
			new InputState(config.digitalWalletApplicationId().orElse(""), false),
			new InputState(PINGONE_APPLICATION_INSTANCE_ID_KEY, false),
			new InputState(PINGONE_CREDENTIAL_VERIFICATION_KEY, false)
		};
	}

	@Override
	public OutputState[] getOutputs() {
		return new OutputState[] {
				new OutputState(PINGONE_VERIFICATION_SESSION_KEY),
				new OutputState(PINGONE_VERIFICATION_DELIVERY_METHOD_KEY),
				new OutputState(PINGONE_VERIFICATION_TIMEOUT_KEY)
			};
	}

	public static class VerificationOutcomeProvider implements StaticOutcomeProvider {
		@Override
		public List<Outcome> getOutcomes(PreferredLocales locales) {
			ResourceBundle bundle = locales.getBundleInPreferredLocale(PingOneCredentialsVerification.BUNDLE,
			                                                           OutcomeProvider.class.getClassLoader());
			List<Outcome> results = new ArrayList<>();
			results.add(new Outcome(SUCCESS_OUTCOME_ID, bundle.getString("successOutcome")));
			results.add(new Outcome(ERROR_OUTCOME_ID, bundle.getString("errorOutcome")));
			results.add(new Outcome(TIMEOUT_OUTCOME_ID, bundle.getString("timeoutOutcome")));
			return Collections.unmodifiableList(results);
		}
	}
}
