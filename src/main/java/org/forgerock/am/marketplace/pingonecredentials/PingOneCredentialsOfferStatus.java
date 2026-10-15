/*
 * This code is to be used exclusively in connection with Ping Identity Corporation software or services.
 * Ping Identity Corporation only offers such software or services to legal entities who have entered into
 * a binding license agreement with Ping Identity Corporation.
 *
 * Copyright 2024 Ping Identity Corporation. All Rights Reserved
 */

package org.forgerock.am.marketplace.pingonecredentials;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.ERROR_OUTCOME_ID;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.OFFER_STATUS_COMPLETED;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_CREDENTIAL_OFFER_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_CREDENTIAL_OFFER_STATUS_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_CREDENTIAL_OFFER_TIMEOUT_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_USER_ID_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.RESPONSE_CREDENTIAL_OFFER_URL;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.RESPONSE_EXPIRES_AT;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.RESPONSE_HREF;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.RESPONSE_ID;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.RESPONSE_LINKS;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.RESPONSE_OPENID_CREDENTIAL_OFFER_REL;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.RESPONSE_STATUS;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.SUCCESS_OUTCOME_ID;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.TIMEOUT_OUTCOME_ID;
import static org.forgerock.openam.auth.node.api.Action.send;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import javax.inject.Inject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.TextOutputCallback;

import com.google.common.collect.ImmutableList;
import com.google.inject.assistedinject.Assisted;
import com.sun.identity.authentication.callbacks.HiddenValueCallback;
import com.sun.identity.authentication.callbacks.ScriptTextOutputCallback;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.forgerock.json.JsonValue;
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
import org.forgerock.openam.authentication.callbacks.PollingWaitCallback;
import org.forgerock.openam.core.realms.Realm;
import org.forgerock.openam.integration.pingone.api.PingOneWorker;
import org.forgerock.openam.integration.pingone.api.PingOneWorkerService;
import org.forgerock.openam.sm.annotations.adapters.TimeUnit;
import org.forgerock.openam.utils.qr.GenerationUtils;
import org.forgerock.util.i18n.PreferredLocales;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The PingOne Credentials Offer Status node follows an OpenID4VCI credential offer created by the
 * PingOne Credentials Issue node. It shows the offer to the user as a QR code and waits, by reading the offer
 * from PingOne, until the wallet has been provisioned with every credential in the offer.
 */
@Node.Metadata(
	outcomeProvider = PingOneCredentialsOfferStatus.OfferStatusOutcomeProvider.class,
	configClass = PingOneCredentialsOfferStatus.Config.class,
	tags = {"marketplace", "trustnetwork", "pingone"})
public class PingOneCredentialsOfferStatus implements Node {

	/** How often to poll PingOne for the offer status in milliseconds. */
	public static final int OFFER_POLL_INTERVAL = 5000;

	/** The id of the HiddenCallback containing the wallet offer link. */
	public static final String HIDDEN_CALLBACK_ID = "pingOneCredentialOfferUri";

	static final String SCAN_QR_CODE_MSG_KEY = "default.scanQRCodeMessage";
	static final String DEFAULT_WAITING_MESSAGE_KEY = "default.waitingMessage";
	static final String QR_CALLBACK_STRING = "callback_0";
	static final int DEFAULT_TIMEOUT = 300;

	/** Wallet deep link scheme for an OpenID4VCI credential offer. */
	static final String OFFER_DEEP_LINK_PREFIX = "openid-credential-offer://?credential_offer_uri=";

	private final Logger logger = LoggerFactory.getLogger(PingOneCredentialsOfferStatus.class);
	private static final String LOGGER_PREFIX =
		"[PingOne Credentials Offer Status Node]" + PingOneCredentialsPlugin.LOG_APPENDER;

	public static final String BUNDLE = PingOneCredentialsOfferStatus.class.getName();

	private final Config config;
	private final Realm realm;
	private final PingOneWorkerService pingOneWorkerService;
	private final PingOneCredentialsService client;
	private final LocalizationHelper localizationHelper;

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
		 * The shared state attribute containing the PingOne User ID.
		 *
		 * @return The PingOne User ID shared state attribute.
		 */
		@Attribute(order = 200, requiredValue = true)
		default String pingOneUserIdAttribute() {
			return PINGONE_USER_ID_KEY;
		}

		/**
		 * How long to wait, in seconds, for the wallet to be provisioned. PingOne offers are valid for 30 minutes.
		 *
		 * @return The timeout.
		 */
		@Attribute(order = 300)
		@TimeUnit(SECONDS)
		default Duration timeout() {
			return Duration.ofSeconds(DEFAULT_TIMEOUT);
		}

		/**
		 * The message with instructions to scan the QR code, keyed on the locale. Falls back to
		 * default.scanQRCodeMessage.
		 *
		 * @return The mapping of locales to scan QR code messages.
		 */
		@Attribute(order = 400)
		Map<Locale, String> scanQRCodeMessage();

		/**
		 * The message to display to the user while waiting, keyed on the locale. Falls back to
		 * default.waitingMessage.
		 *
		 * @return The message to display on the waiting indicator.
		 */
		@Attribute(order = 500)
		Map<Locale, String> waitingMessage();
	}

	/**
	 * The PingOne Credentials Offer Status node constructor.
	 *
	 * @param config               the node configuration.
	 * @param realm                the realm.
	 * @param pingOneWorkerService the {@link PingOneWorkerService} instance.
	 * @param client               the {@link PingOneCredentialsService} instance.
	 * @param localizationHelper   the {@link LocalizationHelper} instance.
	 */
	@Inject
	PingOneCredentialsOfferStatus(@Assisted Config config, @Assisted Realm realm,
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

			String pingOneUserId;
			try {
				pingOneUserId = new PingOneUserIdHelper().getPingOneUserId(nodeState, config.pingOneUserIdAttribute());
			} catch (PingOneCredentialsException e) {
				logger.warn("{} Expected PingOne User ID to be set in sharedState.", LOGGER_PREFIX);
				return buildAction(ERROR_OUTCOME_ID, context);
			}

			JsonValue offer = nodeState.isDefined(PINGONE_CREDENTIAL_OFFER_KEY)
			                  ? nodeState.get(PINGONE_CREDENTIAL_OFFER_KEY) : null;
			String offerId = offer == null ? null : offer.get(RESPONSE_ID).asString();
			if (StringUtils.isBlank(offerId)) {
				logger.error("{} Expected an OpenID4VCI credential offer in sharedState.", LOGGER_PREFIX);
				return buildAction(ERROR_OUTCOME_ID, context);
			}

			// First pass: show the offer and start polling
			if (context.getCallback(PollingWaitCallback.class).isEmpty()) {
				nodeState.putShared(PINGONE_CREDENTIAL_OFFER_TIMEOUT_KEY, OFFER_POLL_INTERVAL);
				return send(getCallbacks(context, offer)).build();
			}

			PingOneWorkerService.Worker worker = config.pingOneWorker();
			String accessToken = pingOneWorkerService.getAccessTokenId(realm, worker);
			if (StringUtils.isBlank(accessToken)) {
				logger.error("{} Unable to get access token for PingOne Worker.", LOGGER_PREFIX);
				return buildAction(ERROR_OUTCOME_ID, context);
			}

			JsonValue current = client.readCredentialOfferRequest(accessToken, worker, pingOneUserId, offerId);
			String status = current.get(RESPONSE_STATUS).asString();

			nodeState.putShared(PINGONE_CREDENTIAL_OFFER_KEY, current);
			nodeState.putShared(PINGONE_CREDENTIAL_OFFER_STATUS_KEY, status);

			if (OFFER_STATUS_COMPLETED.equals(status)) {
				return buildAction(SUCCESS_OUTCOME_ID, context);
			}

			if (isExpired(current)) {
				return buildAction(TIMEOUT_OUTCOME_ID, context);
			}

			return waitForCompletion(context, nodeState, current);
		} catch (Exception ex) {
			String stackTrace = ExceptionUtils.getStackTrace(ex);
			logger.error(LOGGER_PREFIX + "Exception occurred: ", ex);
			NodeState nodeState = context.getStateFor(this);

			nodeState.putTransient(LOGGER_PREFIX + "Exception", ex.getMessage());
			nodeState.putTransient(LOGGER_PREFIX + "StackTrace", stackTrace);

			return buildAction(ERROR_OUTCOME_ID, context);
		}
	}

	private Action waitForCompletion(TreeContext context, NodeState nodeState, JsonValue offer) {
		long timeOutInMs = config.timeout().getSeconds() * 1000;
		int timeElapsed = nodeState.get(PINGONE_CREDENTIAL_OFFER_TIMEOUT_KEY).asInteger();

		if (timeElapsed >= timeOutInMs) {
			return buildAction(TIMEOUT_OUTCOME_ID, context);
		}

		nodeState.putShared(PINGONE_CREDENTIAL_OFFER_TIMEOUT_KEY, timeElapsed + OFFER_POLL_INTERVAL);
		return send(getCallbacks(context, offer)).build();
	}

	/**
	 * An offer whose {@code expiresAt} has passed can no longer be claimed, so there is nothing left to wait for.
	 */
	private boolean isExpired(JsonValue offer) {
		String expiresAt = offer.get(RESPONSE_EXPIRES_AT).asString();
		if (StringUtils.isBlank(expiresAt)) {
			return false;
		}
		try {
			return Instant.parse(expiresAt).isBefore(Instant.now());
		} catch (RuntimeException e) {
			return false;
		}
	}

	private List<Callback> getCallbacks(TreeContext context, JsonValue offer) {
		String offerUrl = offer.get(RESPONSE_CREDENTIAL_OFFER_URL).asString();
		String deepLink = getDeepLink(offer, offerUrl);

		Callback scanTextOutputCallback = new TextOutputCallback(
			TextOutputCallback.INFORMATION,
			localizationHelper.getLocalizedMessage(context, PingOneCredentialsOfferStatus.class,
			                                       config.scanQRCodeMessage(), SCAN_QR_CODE_MSG_KEY));

		Callback qrCodeCallback = new ScriptTextOutputCallback(
			GenerationUtils.getQRCodeGenerationJavascriptForAuthenticatorAppRegistration(QR_CALLBACK_STRING,
			                                                                            deepLink));

		Callback hiddenCallback = new HiddenValueCallback(HIDDEN_CALLBACK_ID, deepLink);

		Callback pollingCallback = PollingWaitCallback.makeCallback()
		                                              .withWaitTime(String.valueOf(OFFER_POLL_INTERVAL))
		                                              .withMessage(localizationHelper.getLocalizedMessage(
			                                              context, PingOneCredentialsOfferStatus.class,
			                                              config.waitingMessage(), DEFAULT_WAITING_MESSAGE_KEY))
		                                              .build();

		List<Callback> callbacks = new ArrayList<>();
		callbacks.add(scanTextOutputCallback);
		callbacks.add(qrCodeCallback);
		callbacks.add(hiddenCallback);
		if (StringUtils.isNotBlank(offerUrl)) {
			callbacks.add(new TextOutputCallback(TextOutputCallback.INFORMATION, offerUrl));
		}
		callbacks.add(pollingCallback);
		return ImmutableList.copyOf(callbacks);
	}

	/**
	 * The wallet launch link: the documented {@code openid-credential-offer} link when PingOne returns it,
	 * otherwise the standard OpenID4VCI deep link built from the credential offer URL.
	 */
	private String getDeepLink(JsonValue offer, String offerUrl) {
		String deepLink = offer.get(RESPONSE_LINKS)
		                       .get(RESPONSE_OPENID_CREDENTIAL_OFFER_REL)
		                       .get(RESPONSE_HREF)
		                       .asString();

		if (StringUtils.isBlank(deepLink) && StringUtils.isNotBlank(offerUrl)) {
			deepLink = OFFER_DEEP_LINK_PREFIX + URLEncoder.encode(offerUrl, StandardCharsets.UTF_8);
		}

		return deepLink;
	}

	private Action buildAction(String outcome, TreeContext context) {
		context.getStateFor(this).remove(PINGONE_CREDENTIAL_OFFER_TIMEOUT_KEY);
		return Action.goTo(outcome).build();
	}

	@Override
	public InputState[] getInputs() {
		return new InputState[] {
			new InputState(config.pingOneUserIdAttribute(), false),
			new InputState(PINGONE_CREDENTIAL_OFFER_KEY, false),
			new InputState(PINGONE_CREDENTIAL_OFFER_TIMEOUT_KEY, false)
		};
	}

	@Override
	public OutputState[] getOutputs() {
		return new OutputState[] {
			new OutputState(PINGONE_CREDENTIAL_OFFER_KEY),
			new OutputState(PINGONE_CREDENTIAL_OFFER_STATUS_KEY),
			new OutputState(PINGONE_CREDENTIAL_OFFER_TIMEOUT_KEY)
		};
	}

	public static class OfferStatusOutcomeProvider implements StaticOutcomeProvider {
		@Override
		public List<Outcome> getOutcomes(PreferredLocales locales) {
			ResourceBundle bundle = locales.getBundleInPreferredLocale(PingOneCredentialsOfferStatus.BUNDLE,
			                                                           OutcomeProvider.class.getClassLoader());
			List<Outcome> results = new ArrayList<>();
			results.add(new Outcome(SUCCESS_OUTCOME_ID, bundle.getString("successOutcome")));
			results.add(new Outcome(ERROR_OUTCOME_ID, bundle.getString("errorOutcome")));
			results.add(new Outcome(TIMEOUT_OUTCOME_ID, bundle.getString("timeoutOutcome")));
			return Collections.unmodifiableList(results);
		}
	}
}
