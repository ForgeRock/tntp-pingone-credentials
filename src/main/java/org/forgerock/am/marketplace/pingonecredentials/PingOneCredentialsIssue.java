/*
 * This code is to be used exclusively in connection with Ping Identity Corporation software or services.
 * Ping Identity Corporation only offers such software or services to legal entities who have entered into
 * a binding license agreement with Ping Identity Corporation.
 *
 * Copyright 2024 Ping Identity Corporation. All Rights Reserved
 */

package org.forgerock.am.marketplace.pingonecredentials;

import static org.forgerock.am.marketplace.pingonecredentials.Constants.ERROR_OUTCOME_ID;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.OBJECT_ATTRIBUTES;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_CREDENTIAL_ID_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_CREDENTIAL_OFFER_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_CREDENTIAL_OFFER_QR_CODE_URL_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_CREDENTIAL_OFFER_STATUS_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_CREDENTIAL_OFFER_URL_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_USER_ID_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.RESPONSE_CREDENTIAL_OFFER_URL;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.RESPONSE_HREF;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.RESPONSE_ID;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.RESPONSE_LINKS;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.RESPONSE_OPENID_CREDENTIAL_OFFER_REL;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.RESPONSE_QRCODE;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.RESPONSE_STATUS;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.SUCCESS_OUTCOME_ID;

import com.google.inject.assistedinject.Assisted;

import org.apache.commons.lang3.StringUtils;
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
import org.forgerock.openam.integration.pingone.api.PingOneWorker;
import org.forgerock.openam.integration.pingone.api.PingOneWorkerService;
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

import static org.forgerock.am.marketplace.pingonecredentials.Constants.CredentialIssuanceMode;

/**
 * The PingOne Credentials Issue node lets you create a PingOne credential in a
 * journey.
 */
@Node.Metadata(
    outcomeProvider = PingOneCredentialsIssue.IssueOutcomeProvider.class,
    configClass = PingOneCredentialsIssue.Config.class,
    tags = {"marketplace", "trustnetwork", "pingone"})
public class PingOneCredentialsIssue implements Node {

    private final Config config;
    private final Realm realm;
    private final PingOneWorkerService pingOneWorkerService;

    private final Logger logger = LoggerFactory.getLogger(PingOneCredentialsIssue.class);
    private static final String LOGGER_PREFIX = "[PingOne Credentials Issue Node]" + PingOneCredentialsPlugin.LOG_APPENDER;

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
         * The Credential Type ID of the Credential
         *
         * @return The Credential Type ID as a String
         */
        @Attribute(order = 300, requiredValue = true)
        String credentialTypeId();

        /**
         * The credential issuance protocol. NATIVE uses the existing credential
         * issuance operation (the default); OID4VCI creates a documented
         * OpenID4VCI credential offer for the user.
         *
         * @return The credential issuance mode.
         */
        @Attribute(order = 350, requiredValue = true)
        default CredentialIssuanceMode credentialIssuanceMode() {
            return CredentialIssuanceMode.NATIVE;
        }

        /**
         * Optional credential type IDs to include in the OpenID4VCI offer request.
         * Omitted from the request when not configured.
         *
         * @return The OID4VCI offer credentials as a List of Strings.
         */
        @Attribute(order = 360)
        List<String> credentialOfferCredentials();

        /**
         * Optional OAuth2 grant types to include in the OpenID4VCI offer request.
         * Omitted from the request when not configured.
         *
         * @return The OID4VCI offer grant types as a List of Strings.
         */
        @Attribute(order = 370)
        List<String> credentialOfferGrantTypes();

        /**
         * The Credential attribute mapping. The Key is the Credential attribute field name and the Value is the shared
         * state attribute.
         * @return the attribute mapping for the Credential.
         */
        @Attribute(order = 400)
        Map<String, String> attributes();
    }

    /**
     * The PingOne Credentials Issue node constructor.
     *
     *
     * @param config               the node configuration.
     * @param realm                the realm.
     * @param pingOneWorkerService the {@link PingOneWorkerService} instance.
     * @param client               the {@link PingOneCredentialsService} instance.
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
            PingOneWorkerService.Worker worker = config.pingOneWorker();
            String accessToken = pingOneWorkerService.getAccessTokenId(realm, worker);

            if (StringUtils.isBlank(accessToken)) {
                logger.error("Unable to get access token for PingOne Worker.");
                return Action.goTo(ERROR_OUTCOME_ID).build();
            }

            if (CredentialIssuanceMode.OID4VCI.equals(config.credentialIssuanceMode())) {
                return issueOid4vciOffer(nodeState, accessToken, worker, pingOneUserId);
            }

            JsonValue response = client.credentialIssueRequest(accessToken,
                                                               worker,
                                                               pingOneUserId,
                                                               config.credentialTypeId(),
                                                               getAttributes(nodeState));

            nodeState.putShared(PINGONE_CREDENTIAL_ID_KEY, response.get(RESPONSE_ID).asString());

            return Action.goTo(SUCCESS_OUTCOME_ID).build();
        } catch (Exception ex) {
            String stackTrace = org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace(ex);
            logger.error(LOGGER_PREFIX + "Exception occurred: ", ex);
            NodeState nodeState = context.getStateFor(this);

            nodeState.putTransient(LOGGER_PREFIX + "Exception", ex.getMessage());
            nodeState.putTransient(LOGGER_PREFIX + "StackTrace", stackTrace);

            return Action.goTo(ERROR_OUTCOME_ID).build();
        }
    }

    /**
     * Creates the documented OpenID4VCI credential offer and exposes the offer
     * URL, QR code link, lifecycle status, and full offer response through
     * additive shared-state outputs.
     *
     * @param nodeState The node state.
     * @param accessToken The PingOne worker access token.
     * @param worker The PingOne worker.
     * @param pingOneUserId The PingOne user ID.
     * @return The action for the offer outcome.
     */
    private Action issueOid4vciOffer(NodeState nodeState, String accessToken, PingOneWorkerService.Worker worker,
                                     String pingOneUserId) throws PingOneCredentialsServiceException {
        JsonValue response = client.createCredentialOfferRequest(accessToken,
                                                                 worker,
                                                                 pingOneUserId,
                                                                 config.credentialOfferCredentials(),
                                                                 config.credentialOfferGrantTypes());

        String offerUrl = getOid4vciOfferUrl(response);
        if (StringUtils.isBlank(offerUrl)) {
            logger.error("OpenID4VCI offer response did not contain a credential offer URL.");
            return Action.goTo(ERROR_OUTCOME_ID).build();
        }

        nodeState.putShared(PINGONE_CREDENTIAL_OFFER_URL_KEY, offerUrl);
        nodeState.putShared(PINGONE_CREDENTIAL_OFFER_QR_CODE_URL_KEY,
                            getOid4vciQrCodeUrl(response));
        nodeState.putShared(PINGONE_CREDENTIAL_OFFER_STATUS_KEY,
                            response.get(RESPONSE_STATUS).asString());
        nodeState.putShared(PINGONE_CREDENTIAL_OFFER_KEY, response);

        return Action.goTo(SUCCESS_OUTCOME_ID).build();
    }

    /**
     * Extracts the documented OpenID4VCI credential offer URL. The offer URL is
     * read from the top-level {@code credentialOfferUrl} member, falling back to
     * the {@code _links.openid-credential-offer.href} link.
     *
     * @param response The OpenID4VCI offer response.
     * @return The credential offer URL, or null when the response does not define one.
     */
    private String getOid4vciOfferUrl(JsonValue response) {
        String offerUrl = response.get(RESPONSE_CREDENTIAL_OFFER_URL).asString();

        if (StringUtils.isBlank(offerUrl)) {
            offerUrl = response.get(RESPONSE_LINKS)
                               .get(RESPONSE_OPENID_CREDENTIAL_OFFER_REL)
                               .get(RESPONSE_HREF)
                               .asString();
        }

        return offerUrl;
    }

    /**
     * Extracts the documented OpenID4VCI QR code link. The QR code is exposed
     * under {@code _links.qrCode.href}; no value is invented when absent.
     *
     * @param response The OpenID4VCI offer response.
     * @return The QR code link, or null when the response does not define one.
     */
    private String getOid4vciQrCodeUrl(JsonValue response) {
        return response.get(RESPONSE_LINKS)
                       .get(RESPONSE_QRCODE)
                       .get(RESPONSE_HREF)
                       .asString();
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
            new OutputState(PINGONE_CREDENTIAL_ID_KEY),
            new OutputState(PINGONE_CREDENTIAL_OFFER_URL_KEY),
            new OutputState(PINGONE_CREDENTIAL_OFFER_QR_CODE_URL_KEY),
            new OutputState(PINGONE_CREDENTIAL_OFFER_STATUS_KEY),
            new OutputState(PINGONE_CREDENTIAL_OFFER_KEY)
        };
    }

    public static class IssueOutcomeProvider implements StaticOutcomeProvider {
        @Override
        public List<Outcome> getOutcomes(PreferredLocales locales) {
            ResourceBundle bundle = locales.getBundleInPreferredLocale(PingOneCredentialsIssue.BUNDLE,
                                                                       OutcomeProvider.class.getClassLoader());
            List<Outcome> results = new ArrayList<>();
            results.add(new Outcome(SUCCESS_OUTCOME_ID, bundle.getString("successOutcome")));
            results.add(new Outcome(ERROR_OUTCOME_ID, bundle.getString("errorOutcome")));
            return Collections.unmodifiableList(results);
        }
    }
}
