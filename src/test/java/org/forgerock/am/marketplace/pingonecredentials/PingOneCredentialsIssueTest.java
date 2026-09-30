
/*
 * Copyright 2024 ForgeRock AS. All Rights Reserved
 *
 * Use of this code requires a commercial software license with ForgeRock AS.
 * or with one of its affiliates. All use shall be exclusively subject
 * to such license between the licensee and ForgeRock AS.
 */

package org.forgerock.am.marketplace.pingonecredentials;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.ERROR_OUTCOME_ID;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.OBJECT_ATTRIBUTES;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_CREDENTIAL_ID_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_CREDENTIAL_OFFER_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_CREDENTIAL_OFFER_QR_CODE_URL_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_CREDENTIAL_OFFER_STATUS_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_CREDENTIAL_OFFER_URL_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_USER_ID_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.SUCCESS_OUTCOME_ID;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openam.auth.node.api.SharedStateConstants.REALM;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.security.auth.callback.Callback;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.forgerock.json.JsonValue;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.ExternalRequestContext;
import org.forgerock.openam.auth.node.api.InputState;
import org.forgerock.openam.auth.node.api.OutcomeProvider;
import org.forgerock.openam.auth.node.api.OutputState;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.core.realms.Realm;
import org.forgerock.openam.integration.pingone.api.PingOneWorkerService;
import org.forgerock.openam.integration.pingone.api.PingOneWorkerException;
import org.forgerock.openam.test.extensions.LoggerExtension;
import org.forgerock.util.i18n.PreferredLocales;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class PingOneCredentialsIssueTest {

    @RegisterExtension
    public LoggerExtension loggerExtension = new LoggerExtension(PingOneCredentialsIssue.class);

    @Mock
    PingOneCredentialsIssue.Config config;

    @Mock
    PingOneWorkerService pingOneWorkerService;

    @Mock
    PingOneWorkerService.Worker worker;

    @Mock
    Realm realm;

    @Mock
    PingOneCredentialsService client;

    PingOneCredentialsIssue node;

    @BeforeEach
    public void setup() throws Exception {
        given(pingOneWorkerService.getWorker(any(), anyString())).willReturn(Optional.of(worker));
        given(pingOneWorkerService.getAccessTokenId(any(), any())).willReturn("some-access-token");

        node = new PingOneCredentialsIssue(config, realm, pingOneWorkerService, client);
    }

    @Test
    public void testPingOneUserIdNotFoundInSharedState() throws Exception {
        // Given
        JsonValue sharedState = json(field(REALM, "/realm"));
        JsonValue transientState = json(object());

        // When
        Action result = node.process(getContext(sharedState, transientState, emptyList()));

        // Then
        assertThat(result.outcome).isEqualTo(ERROR_OUTCOME_ID);
    }

    @Test
    public void testReturnOutcomeIssue() throws Exception {
        // Given
        JsonValue sharedState = json(object(
            field(REALM, "/realm"),
            field(PINGONE_USER_ID_KEY, "some-user-id"),
            field("sharedStateGivenName", "John")));

        Map<String, String> attributes = new java.util.HashMap<>();

        attributes.put("credentialsGivenName", "sharedStateGivenName");

        given(config.pingOneUserIdAttribute()).willReturn(PINGONE_USER_ID_KEY);
        given(config.credentialTypeId()).willReturn("some-credential-type-id");
        given(config.attributes()).willReturn(attributes);

        JsonValue response = json(object(
            field("id", "some-credential-id")));

        when(client.credentialIssueRequest(any(), any(), anyString(), any(), any())).thenReturn(response);

        // When
        Action result = node.process(getContext(sharedState, json(object()), emptyList()));

        // Then
        assertThat(result.outcome).isEqualTo(SUCCESS_OUTCOME_ID);
    }

    @Test
    public void testOid4vciOfferSuccessStoresOfferOutputs() throws Exception {
        // Given
        JsonValue sharedState = json(object(
            field(REALM, "/realm"),
            field(PINGONE_USER_ID_KEY, "some-user-id")));

        given(config.pingOneUserIdAttribute()).willReturn(PINGONE_USER_ID_KEY);
        given(config.credentialIssuanceMode()).willReturn(Constants.CredentialIssuanceMode.OID4VCI);
        given(config.credentialOfferCredentials())
            .willReturn(List.of("some-credential-type-id-1", "some-credential-type-id-2"));
        given(config.credentialOfferGrantTypes())
            .willReturn(List.of(Constants.GRANT_TYPE_AUTHORIZATION_CODE));

        JsonValue offerResponse = json(object(
            field("id", "some-offer-id"),
            field("status", "CREATED"),
            field("credentialOfferUrl",
                  "https://auth.pingone.com/some-environment-id/credential-offers/some-offer-id"),
            field("_links", object(
                field("openid-credential-offer", object(
                    field("href", "openid-credential-offer://?credential_offer_uri=https%3A%2F%2Fexample.com"))),
                field("qrCode", object(
                    field("href", "data:image/png;base64,some-qr-code")))))));

        when(client.createCredentialOfferRequest(any(), any(), anyString(), any(), any()))
            .thenReturn(offerResponse);

        // When
        Action result = node.process(getContext(sharedState, json(object()), emptyList()));

        // Then
        assertThat(result.outcome).isEqualTo(SUCCESS_OUTCOME_ID);

        assertThat(sharedState.get(PINGONE_CREDENTIAL_OFFER_URL_KEY).asString())
            .isEqualTo("https://auth.pingone.com/some-environment-id/credential-offers/some-offer-id");
        assertThat(sharedState.get(PINGONE_CREDENTIAL_OFFER_QR_CODE_URL_KEY).asString())
            .isEqualTo("data:image/png;base64,some-qr-code");
        assertThat(sharedState.get(PINGONE_CREDENTIAL_OFFER_STATUS_KEY).asString()).isEqualTo("CREATED");
        assertThat(sharedState.get(PINGONE_CREDENTIAL_OFFER_KEY).get("id").asString()).isEqualTo("some-offer-id");
        assertThat(sharedState.isDefined(PINGONE_CREDENTIAL_ID_KEY)).isFalse();
    }

    @Test
    public void testOid4vciOfferFallsBackToOpenidCredentialOfferLink() throws Exception {
        // Given
        JsonValue sharedState = json(object(
            field(REALM, "/realm"),
            field(PINGONE_USER_ID_KEY, "some-user-id")));

        given(config.pingOneUserIdAttribute()).willReturn(PINGONE_USER_ID_KEY);
        given(config.credentialIssuanceMode()).willReturn(Constants.CredentialIssuanceMode.OID4VCI);

        JsonValue offerResponse = json(object(
            field("id", "some-offer-id"),
            field("status", "CREATED"),
            field("_links", object(
                field("openid-credential-offer", object(
                    field("href", "openid-credential-offer://?credential_offer_uri=https%3A%2F%2Fexample.com")))))));

        when(client.createCredentialOfferRequest(any(), any(), anyString(), any(), any()))
            .thenReturn(offerResponse);

        // When
        Action result = node.process(getContext(sharedState, json(object()), emptyList()));

        // Then
        assertThat(result.outcome).isEqualTo(SUCCESS_OUTCOME_ID);
        assertThat(sharedState.get(PINGONE_CREDENTIAL_OFFER_URL_KEY).asString())
            .isEqualTo("openid-credential-offer://?credential_offer_uri=https%3A%2F%2Fexample.com");

        // No value is invented when _links.qrCode is missing: the QR key holds an
        // explicit JSON null (NodeState.putShared stores it as such, so isDefined
        // reports the key as present while its value is null). This pins the
        // known stored-shape behavior for consumers of the additive outputs.
        assertThat(sharedState.isDefined(PINGONE_CREDENTIAL_OFFER_QR_CODE_URL_KEY)).isTrue();
        assertThat(sharedState.get(PINGONE_CREDENTIAL_OFFER_QR_CODE_URL_KEY).isNull()).isTrue();
        assertThat(sharedState.get(PINGONE_CREDENTIAL_OFFER_QR_CODE_URL_KEY).asString()).isNull();
    }

    @Test
    public void testOid4vciOfferMissingOfferUrlReturnsError() throws Exception {
        // Given
        JsonValue sharedState = json(object(
            field(REALM, "/realm"),
            field(PINGONE_USER_ID_KEY, "some-user-id")));

        given(config.pingOneUserIdAttribute()).willReturn(PINGONE_USER_ID_KEY);
        given(config.credentialIssuanceMode()).willReturn(Constants.CredentialIssuanceMode.OID4VCI);

        JsonValue offerResponse = json(object(
            field("id", "some-offer-id"),
            field("status", "CREATED")));

        when(client.createCredentialOfferRequest(any(), any(), anyString(), any(), any()))
            .thenReturn(offerResponse);

        // When
        Action result = node.process(getContext(sharedState, json(object()), emptyList()));

        // Then
        assertThat(result.outcome).isEqualTo(ERROR_OUTCOME_ID);
        // No offer state at all is written on the error path.
        assertThat(sharedState.isDefined(PINGONE_CREDENTIAL_OFFER_URL_KEY)).isFalse();
        assertThat(sharedState.isDefined(PINGONE_CREDENTIAL_OFFER_QR_CODE_URL_KEY)).isFalse();
        assertThat(sharedState.isDefined(PINGONE_CREDENTIAL_OFFER_STATUS_KEY)).isFalse();
        assertThat(sharedState.isDefined(PINGONE_CREDENTIAL_OFFER_KEY)).isFalse();
    }

    @Test
    public void testOid4vciOfferServiceFailureReturnsError() throws Exception {
        // Given
        JsonValue sharedState = json(object(
            field(REALM, "/realm"),
            field(PINGONE_USER_ID_KEY, "some-user-id")));

        given(config.pingOneUserIdAttribute()).willReturn(PINGONE_USER_ID_KEY);
        given(config.credentialIssuanceMode()).willReturn(Constants.CredentialIssuanceMode.OID4VCI);

        when(client.createCredentialOfferRequest(any(), any(), anyString(), any(), any()))
            .thenThrow(new PingOneCredentialsServiceException("Failed PingOne Credentials"));

        // When
        Action result = node.process(getContext(sharedState, json(object()), emptyList()));

        // Then
        assertThat(result.outcome).isEqualTo(ERROR_OUTCOME_ID);
        assertThat(sharedState.isDefined(PINGONE_CREDENTIAL_OFFER_URL_KEY)).isFalse();
    }

    @Test
    public void testOid4vciOfferPassesConfiguredOptionalFieldsToService() throws Exception {
        // Given
        JsonValue sharedState = json(object(
            field(REALM, "/realm"),
            field(PINGONE_USER_ID_KEY, "some-user-id")));

        given(config.pingOneUserIdAttribute()).willReturn(PINGONE_USER_ID_KEY);
        given(config.credentialIssuanceMode()).willReturn(Constants.CredentialIssuanceMode.OID4VCI);
        given(config.credentialOfferCredentials()).willReturn(List.of("some-credential-type-id"));
        given(config.credentialOfferGrantTypes()).willReturn(List.of(Constants.GRANT_TYPE_PRE_AUTHORIZED_CODE));

        JsonValue offerResponse = json(object(
            field("id", "some-offer-id"),
            field("status", "CREATED"),
            field("credentialOfferUrl", "https://auth.pingone.com/credential-offers/some-offer-id")));

        when(client.createCredentialOfferRequest(any(), any(), anyString(), any(), any()))
            .thenReturn(offerResponse);

        // When
        Action result = node.process(getContext(sharedState, json(object()), emptyList()));

        // Then
        verify(client).createCredentialOfferRequest(any(), any(), anyString(),
                                                    org.mockito.ArgumentMatchers.eq(List.of("some-credential-type-id")),
                                                    org.mockito.ArgumentMatchers.eq(
                                                        List.of(Constants.GRANT_TYPE_PRE_AUTHORIZED_CODE)));
        assertThat(result.outcome).isEqualTo(SUCCESS_OUTCOME_ID);
    }

    @Test
    public void testNativeIssuanceDoesNotCallOfferRequest() throws Exception {
        // Given
        JsonValue sharedState = json(object(
            field(REALM, "/realm"),
            field(PINGONE_USER_ID_KEY, "some-user-id"),
            field("sharedStateGivenName", "John")));

        Map<String, String> attributes = new java.util.HashMap<>();
        attributes.put("credentialsGivenName", "sharedStateGivenName");

        given(config.pingOneUserIdAttribute()).willReturn(PINGONE_USER_ID_KEY);
        given(config.credentialIssuanceMode()).willReturn(Constants.CredentialIssuanceMode.NATIVE);
        given(config.credentialTypeId()).willReturn("some-credential-type-id");
        given(config.attributes()).willReturn(attributes);

        JsonValue response = json(object(
            field("id", "some-credential-id")));

        when(client.credentialIssueRequest(any(), any(), anyString(), any(), any())).thenReturn(response);

        // When
        Action result = node.process(getContext(sharedState, json(object()), emptyList()));

        // Then
        assertThat(result.outcome).isEqualTo(SUCCESS_OUTCOME_ID);
        assertThat(sharedState.get(PINGONE_CREDENTIAL_ID_KEY).asString()).isEqualTo("some-credential-id");
        verify(client, never()).createCredentialOfferRequest(any(), any(), anyString(), any(), any());
        assertThat(sharedState.isDefined(PINGONE_CREDENTIAL_OFFER_URL_KEY)).isFalse();
    }

    @Test
    public void testExceptionThrowDuringProcessing() throws Exception {
        // Given
        JsonValue sharedState = json(object(
            field(REALM, "/realm"),
            field(PINGONE_USER_ID_KEY, "some-user-id")
                                           ));
        JsonValue transientState = json(object());

        when(client.credentialIssueRequest(any(), any(), anyString(), any(), any())).thenReturn(null);

        // When
        Action result = node.process(getContext(sharedState, transientState, emptyList()));

        // Then
        assertThat(result.outcome).isEqualTo(ERROR_OUTCOME_ID);
    }

    @Test
    public void testGetInputs() {
        Map<String, String> attributes = new HashMap<String, String>();
        attributes.put("First Name", "givenName");
        attributes.put("Last Name", "sn");

        given(config.pingOneUserIdAttribute()).willReturn(PINGONE_USER_ID_KEY);
        given(config.attributes()).willReturn(attributes);

        InputState[] inputs = node.getInputs();

        assertThat(inputs[0].name).isEqualTo(PINGONE_USER_ID_KEY);
        assertThat(inputs[0].required).isEqualTo(false);

        assertThat(inputs[1].name).isEqualTo(OBJECT_ATTRIBUTES);
        assertThat(inputs[1].required).isEqualTo(false);

        assertThat(inputs[2].name).isEqualTo("givenName");
        assertThat(inputs[2].required).isEqualTo(false);

        assertThat(inputs[3].name).isEqualTo("sn");
        assertThat(inputs[3].required).isEqualTo(false);
    }

    @Test
    public void testGetOutputs() {
        OutputState[] outputs = node.getOutputs();
        assertThat(outputs[0].name).isEqualTo(PINGONE_CREDENTIAL_ID_KEY);
        assertThat(outputs[1].name).isEqualTo(PINGONE_CREDENTIAL_OFFER_URL_KEY);
        assertThat(outputs[2].name).isEqualTo(PINGONE_CREDENTIAL_OFFER_QR_CODE_URL_KEY);
        assertThat(outputs[3].name).isEqualTo(PINGONE_CREDENTIAL_OFFER_STATUS_KEY);
        assertThat(outputs[4].name).isEqualTo(PINGONE_CREDENTIAL_OFFER_KEY);
    }

    @Test
    public void testGetOutcomes() {
        PingOneCredentialsIssue.IssueOutcomeProvider outcomeProvider = new PingOneCredentialsIssue.IssueOutcomeProvider();

        PreferredLocales locales = new PreferredLocales();
        List<OutcomeProvider.Outcome> outcomes = outcomeProvider.getOutcomes(locales);

        assertThat(outcomes.get(0).id).isEqualTo("success");
        assertThat(outcomes.get(0).displayName).isEqualTo("Success");

        assertThat(outcomes.get(1).id).isEqualTo("error");
        assertThat(outcomes.get(1).displayName).isEqualTo("Error");
    }

    @Test
    public void testErrorAccessTokenNull() throws Exception {
        given(pingOneWorkerService.getAccessTokenId(any(), any())).willReturn(null);

        // Given
        JsonValue sharedState = json(object(field(REALM, "/realm")));
        JsonValue transientState = json(object());

        // When
        Action result = node.process(getContext(sharedState, transientState, emptyList()));

        // Then
        assertThat(result.outcome).isEqualTo(ERROR_OUTCOME_ID);
    }

    @Test
    public void testPingOneCommunicationFailed() throws Exception {
        // Given
        given(pingOneWorkerService.getAccessTokenId(any(), any())).willReturn(null);
        given(pingOneWorkerService.getAccessTokenId(realm, worker)).willThrow(new PingOneWorkerException(""));
        JsonValue sharedState = json(object(
            field(REALM, "/realm"),
            field(PINGONE_USER_ID_KEY, "some-user-id")
                                           ));
        JsonValue transientState = json(object());

        // When
        Action result = node.process(getContext(sharedState, transientState, emptyList()));

        // Then
        assertThat(result.outcome).isEqualTo(ERROR_OUTCOME_ID);
    }

    private TreeContext getContext(JsonValue sharedState, JsonValue transientState,
                                   List<? extends Callback> callbacks) {
        return new TreeContext(sharedState, transientState, new ExternalRequestContext.Builder().build(), callbacks,
                               Optional.empty());
    }
}