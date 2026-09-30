/*
 * This code is to be used exclusively in connection with Ping Identity Corporation software or services.
 * Ping Identity Corporation only offers such software or services to legal entities who have entered into
 * a binding license agreement with Ping Identity Corporation.
 *
 * Copyright 2024 Ping Identity Corporation. All Rights Reserved
 */

package org.forgerock.am.marketplace.pingonecredentials;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.ERROR_OUTCOME_ID;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_CREDENTIAL_OFFER_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_CREDENTIAL_OFFER_STATUS_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_CREDENTIAL_OFFER_TIMEOUT_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_USER_ID_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.SUCCESS_OUTCOME_ID;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.TIMEOUT_OUTCOME_ID;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openam.auth.node.api.SharedStateConstants.REALM;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.TextOutputCallback;

import com.sun.identity.authentication.callbacks.HiddenValueCallback;
import com.sun.identity.authentication.callbacks.ScriptTextOutputCallback;
import org.forgerock.json.JsonValue;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.ExternalRequestContext;
import org.forgerock.openam.auth.node.api.OutcomeProvider;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.auth.nodes.helpers.LocalizationHelper;
import org.forgerock.openam.authentication.callbacks.PollingWaitCallback;
import org.forgerock.openam.core.realms.Realm;
import org.forgerock.openam.integration.pingone.api.PingOneWorkerService;
import org.forgerock.util.i18n.PreferredLocales;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class PingOneCredentialsOfferStatusTest {

    private static final String OFFER_ID = "some-offer-id";
    private static final String OFFER_URL = "https://auth.pingone.com/some-environment-id/credentialOffer/some-offer-id";

    @Mock
    PingOneCredentialsOfferStatus.Config config;

    @Mock
    PingOneWorkerService pingOneWorkerService;

    @Mock
    PingOneWorkerService.Worker worker;

    @Mock
    Realm realm;

    @Mock
    PingOneCredentialsService client;

    @Mock
    LocalizationHelper localizationHelper;

    PingOneCredentialsOfferStatus node;

    @BeforeEach
    public void setup() throws Exception {
        given(config.pingOneWorker()).willReturn(worker);
        given(config.pingOneUserIdAttribute()).willReturn(PINGONE_USER_ID_KEY);
        given(config.timeout()).willReturn(Duration.ofSeconds(300));
        given(pingOneWorkerService.getAccessTokenId(any(), any())).willReturn("some-access-token");
        given(localizationHelper.getLocalizedMessage(any(), any(), any(), anyString())).willReturn("Some text");

        node = new PingOneCredentialsOfferStatus(config, realm, pingOneWorkerService, client, localizationHelper);
    }

    @Test
    public void testFirstPassShowsQrCodeAndStartsPolling() throws Exception {
        // Given
        JsonValue sharedState = sharedStateWithOffer(offer("CREATED", null));

        // When
        Action result = node.process(getContext(sharedState, emptyList()));

        // Then
        assertThat(result.outcome).isNull();
        assertThat(result.callbacks).hasSize(5);
        assertThat(result.callbacks.get(0)).isInstanceOf(TextOutputCallback.class);
        assertThat(result.callbacks.get(1)).isInstanceOf(ScriptTextOutputCallback.class);
        assertThat(result.callbacks.get(2)).isInstanceOf(HiddenValueCallback.class);
        assertThat(result.callbacks.get(3)).isInstanceOf(TextOutputCallback.class);
        assertThat(result.callbacks.get(4)).isInstanceOf(PollingWaitCallback.class);

        // The offer is only displayed on the first pass; PingOne is not called yet
        verify(client, never()).readCredentialOfferRequest(any(), any(), any(), any());
        assertThat(sharedState.get(PINGONE_CREDENTIAL_OFFER_TIMEOUT_KEY).asInteger())
            .isEqualTo(PingOneCredentialsOfferStatus.OFFER_POLL_INTERVAL);

        // No _links in the offer, so the standard OpenID4VCI deep link is built from the offer URL
        HiddenValueCallback hidden = (HiddenValueCallback) result.callbacks.get(2);
        assertThat(hidden.getValue()).isEqualTo("openid-credential-offer://?credential_offer_uri=" +
                                                URLEncoder.encode(OFFER_URL, StandardCharsets.UTF_8));
        assertThat(((TextOutputCallback) result.callbacks.get(3)).getMessage()).isEqualTo(OFFER_URL);
    }

    @Test
    public void testUsesTheDeepLinkReturnedByPingOne() throws Exception {
        // Given
        JsonValue offer = offer("CREATED", null);
        offer.put("_links", object(
            field("openid-credential-offer", object(
                field("href", "openid-credential-offer://?credential_offer_uri=some-pingone-link")))));

        // When
        Action result = node.process(getContext(sharedStateWithOffer(offer), emptyList()));

        // Then
        HiddenValueCallback hidden = (HiddenValueCallback) result.callbacks.get(2);
        assertThat(hidden.getValue()).isEqualTo("openid-credential-offer://?credential_offer_uri=some-pingone-link");
    }

    @Test
    public void testCompletedOfferFollowsSuccessOutcome() throws Exception {
        // Given
        JsonValue sharedState = sharedStateWithOffer(offer("CREATED", null));
        sharedState.put(PINGONE_CREDENTIAL_OFFER_TIMEOUT_KEY, 5000);
        given(client.readCredentialOfferRequest(any(), any(), any(), any())).willReturn(offer("COMPLETED", null));

        // When
        Action result = node.process(getContext(sharedState, singletonList(mock(PollingWaitCallback.class))));

        // Then
        assertThat(result.outcome).isEqualTo(SUCCESS_OUTCOME_ID);
        verify(client).readCredentialOfferRequest(eq("some-access-token"), eq(worker), eq("some-pingone-user-id"),
                                                  eq(OFFER_ID));
        assertThat(sharedState.get(PINGONE_CREDENTIAL_OFFER_STATUS_KEY).asString()).isEqualTo("COMPLETED");
        assertThat(sharedState.get(PINGONE_CREDENTIAL_OFFER_KEY).get("status").asString()).isEqualTo("COMPLETED");
        assertThat(sharedState.isDefined(PINGONE_CREDENTIAL_OFFER_TIMEOUT_KEY)).isFalse();
    }

    @Test
    public void testPartiallyProvisionedOfferKeepsPolling() throws Exception {
        // Given
        JsonValue sharedState = sharedStateWithOffer(offer("CREATED", null));
        sharedState.put(PINGONE_CREDENTIAL_OFFER_TIMEOUT_KEY, 5000);
        given(client.readCredentialOfferRequest(any(), any(), any(), any())).willReturn(offer("ISSUING", null));

        // When
        Action result = node.process(getContext(sharedState, singletonList(mock(PollingWaitCallback.class))));

        // Then
        assertThat(result.outcome).isNull();
        assertThat(result.callbacks.get(result.callbacks.size() - 1)).isInstanceOf(PollingWaitCallback.class);
        assertThat(sharedState.get(PINGONE_CREDENTIAL_OFFER_STATUS_KEY).asString()).isEqualTo("ISSUING");
        assertThat(sharedState.get(PINGONE_CREDENTIAL_OFFER_TIMEOUT_KEY).asInteger())
            .isEqualTo(5000 + PingOneCredentialsOfferStatus.OFFER_POLL_INTERVAL);
    }

    @Test
    public void testConfiguredTimeoutFollowsTimeoutOutcome() throws Exception {
        // Given
        given(config.timeout()).willReturn(Duration.ofSeconds(10));
        JsonValue sharedState = sharedStateWithOffer(offer("CREATED", null));
        sharedState.put(PINGONE_CREDENTIAL_OFFER_TIMEOUT_KEY, 10000);
        given(client.readCredentialOfferRequest(any(), any(), any(), any())).willReturn(offer("CREATED", null));

        // When
        Action result = node.process(getContext(sharedState, singletonList(mock(PollingWaitCallback.class))));

        // Then
        assertThat(result.outcome).isEqualTo(TIMEOUT_OUTCOME_ID);
        assertThat(sharedState.isDefined(PINGONE_CREDENTIAL_OFFER_TIMEOUT_KEY)).isFalse();
    }

    @Test
    public void testExpiredOfferFollowsTimeoutOutcome() throws Exception {
        // Given
        JsonValue sharedState = sharedStateWithOffer(offer("CREATED", null));
        sharedState.put(PINGONE_CREDENTIAL_OFFER_TIMEOUT_KEY, 5000);
        given(client.readCredentialOfferRequest(any(), any(), any(), any()))
            .willReturn(offer("CREATED", Instant.now().minusSeconds(60).toString()));

        // When
        Action result = node.process(getContext(sharedState, singletonList(mock(PollingWaitCallback.class))));

        // Then
        assertThat(result.outcome).isEqualTo(TIMEOUT_OUTCOME_ID);
    }

    @Test
    public void testMissingOfferFollowsErrorOutcome() throws Exception {
        // Given
        JsonValue sharedState = json(object(
            field(REALM, "/realm"),
            field(PINGONE_USER_ID_KEY, "some-pingone-user-id")));

        // When
        Action result = node.process(getContext(sharedState, emptyList()));

        // Then
        assertThat(result.outcome).isEqualTo(ERROR_OUTCOME_ID);
        verify(client, never()).readCredentialOfferRequest(any(), any(), any(), any());
    }

    @Test
    public void testMissingPingOneUserIdFollowsErrorOutcome() throws Exception {
        // Given
        JsonValue sharedState = json(object(
            field(REALM, "/realm"),
            field(PINGONE_CREDENTIAL_OFFER_KEY, offer("CREATED", null).getObject())));

        // When
        Action result = node.process(getContext(sharedState, emptyList()));

        // Then
        assertThat(result.outcome).isEqualTo(ERROR_OUTCOME_ID);
    }

    @Test
    public void testNoAccessTokenFollowsErrorOutcome() throws Exception {
        // Given
        given(pingOneWorkerService.getAccessTokenId(any(), any())).willReturn("");
        JsonValue sharedState = sharedStateWithOffer(offer("CREATED", null));
        sharedState.put(PINGONE_CREDENTIAL_OFFER_TIMEOUT_KEY, 5000);

        // When
        Action result = node.process(getContext(sharedState, singletonList(mock(PollingWaitCallback.class))));

        // Then
        assertThat(result.outcome).isEqualTo(ERROR_OUTCOME_ID);
        verify(client, never()).readCredentialOfferRequest(any(), any(), any(), any());
    }

    @Test
    public void testServiceFailureFollowsErrorOutcome() throws Exception {
        // Given
        JsonValue sharedState = sharedStateWithOffer(offer("CREATED", null));
        sharedState.put(PINGONE_CREDENTIAL_OFFER_TIMEOUT_KEY, 5000);
        given(client.readCredentialOfferRequest(any(), any(), any(), any()))
            .willThrow(new PingOneCredentialsServiceException("Failed PingOne Credentials"));

        // When
        Action result = node.process(getContext(sharedState, singletonList(mock(PollingWaitCallback.class))));

        // Then
        assertThat(result.outcome).isEqualTo(ERROR_OUTCOME_ID);
        assertThat(sharedState.isDefined(PINGONE_CREDENTIAL_OFFER_TIMEOUT_KEY)).isFalse();
    }

    @Test
    public void testOutcomeProviderExposesSuccessErrorAndTimeout() {
        List<OutcomeProvider.Outcome> outcomes =
            new PingOneCredentialsOfferStatus.OfferStatusOutcomeProvider().getOutcomes(new PreferredLocales());

        assertThat(outcomes).extracting(outcome -> outcome.id)
            .containsExactly(SUCCESS_OUTCOME_ID, ERROR_OUTCOME_ID, TIMEOUT_OUTCOME_ID);
    }

    private JsonValue offer(String status, String expiresAt) {
        JsonValue offer = json(object(
            field("id", OFFER_ID),
            field("status", status),
            field("credentialOfferUrl", OFFER_URL)));
        if (expiresAt != null) {
            offer.put("expiresAt", expiresAt);
        }
        return offer;
    }

    private JsonValue sharedStateWithOffer(JsonValue offer) {
        return json(object(
            field(REALM, "/realm"),
            field(PINGONE_USER_ID_KEY, "some-pingone-user-id"),
            field(PINGONE_CREDENTIAL_OFFER_KEY, offer.getObject())));
    }

    private TreeContext getContext(JsonValue sharedState, List<? extends Callback> callbacks) {
        return new TreeContext(sharedState, json(object()), new ExternalRequestContext.Builder().build(), callbacks,
                               Optional.empty());
    }
}
