
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
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_APPOPEN_URL_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_CREDENTIAL_ID_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_CREDENTIAL_UPDATE_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_PAIRING_DELIVERY_METHOD_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_PAIRING_TIMEOUT_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_PAIRING_WALLET_ID_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_USER_ID_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.SUCCESS_OUTCOME_ID;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openam.auth.node.api.SharedStateConstants.REALM;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import javax.security.auth.callback.Callback;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.forgerock.json.JsonValue;
import org.forgerock.oauth2.core.AccessToken;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.ExternalRequestContext;
import org.forgerock.openam.auth.node.api.InputState;
import org.forgerock.openam.auth.node.api.OutcomeProvider;
import org.forgerock.openam.auth.node.api.OutputState;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.core.realms.Realm;
import org.forgerock.openam.integration.pingone.PingOneWorkerConfig;
import org.forgerock.openam.integration.pingone.PingOneWorkerException;
import org.forgerock.openam.integration.pingone.PingOneWorkerService;
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
public class PingOneCredentialsUpdateTest {

    @RegisterExtension
    public LoggerExtension loggerExtension = new LoggerExtension(PingOneCredentialsUpdate.class);

    @Mock
    PingOneCredentialsUpdate.Config config;

    @Mock
    PingOneWorkerService pingOneWorkerService;

    @Mock
    AccessToken accessToken;

    @Mock
    PingOneWorkerConfig.Worker worker;

    @Mock
    Realm realm;

    @Mock
    PingOneCredentialsService client;

    PingOneCredentialsUpdate node;

    @BeforeEach
    public void setup() throws Exception {
        given(pingOneWorkerService.getWorker(any(), anyString())).willReturn(Optional.of(worker));
        given(pingOneWorkerService.getAccessToken(any(), any())).willReturn(accessToken);

        node = new PingOneCredentialsUpdate(config, realm, pingOneWorkerService, client);
    }

    @Test
    public void testPingOneUserIdNotFoundInSharedState() throws Exception {
        // Given
        JsonValue sharedState = json(object(field(REALM, "/realm")));
        JsonValue transientState = json(object());

        // When
        Action result = node.process(getContext(sharedState, transientState, emptyList()));

        // Then
        assertThat(result.outcome).isEqualTo(ERROR_OUTCOME_ID);
    }

    @Test
    public void testReturnOutcomeUpdate() throws Exception {
        // Given
        JsonValue sharedState = json(object(
            field(REALM, "/realm"),
            field(PINGONE_USER_ID_KEY, "some-user-id"),
            field(PINGONE_CREDENTIAL_ID_KEY, "some-credential-id"),
            field("sharedStateGivenName", "John")));

        Map<String, String> attributes = new java.util.HashMap<>();

        attributes.put("credentialsGivenName", "sharedStateGivenName");

        given(config.pingOneUserIdAttribute()).willReturn(PINGONE_USER_ID_KEY);
        given(config.credentialTypeId()).willReturn("some-credential-type-id");
        given(config.credentialId()).willReturn(PINGONE_CREDENTIAL_ID_KEY);
        given(config.attributes()).willReturn(attributes);

        JsonValue response = json(object(
            field("id", "some-credential-id")));

        when(client.credentialUpdateRequest(any(), any(), anyString(), anyString(), anyString(), any())).thenReturn(response);

        // When
        Action result = node.process(getContext(sharedState, json(object()), emptyList()));

        // Then
        assertThat(result.outcome).isEqualTo(SUCCESS_OUTCOME_ID);
    }

    @Test
    public void testGetInputs() {
        Map<String, String> attributes = new HashMap<String, String>();
        attributes.put("First Name", "givenName");
        attributes.put("Last Name", "sn");

        given(config.pingOneUserIdAttribute()).willReturn(PINGONE_USER_ID_KEY);
        given(config.credentialId()).willReturn(PINGONE_CREDENTIAL_ID_KEY);
        given(config.attributes()).willReturn(attributes);

        InputState[] inputs = node.getInputs();

        assertThat(inputs[0].name).isEqualTo(PINGONE_USER_ID_KEY);
        assertThat(inputs[0].required).isEqualTo(false);

        assertThat(inputs[1].name).isEqualTo(PINGONE_CREDENTIAL_ID_KEY);
        assertThat(inputs[1].required).isEqualTo(false);

        assertThat(inputs[2].name).isEqualTo(OBJECT_ATTRIBUTES);
        assertThat(inputs[2].required).isEqualTo(false);

        assertThat(inputs[3].name).isEqualTo("givenName");
        assertThat(inputs[3].required).isEqualTo(false);

        assertThat(inputs[4].name).isEqualTo("sn");
        assertThat(inputs[4].required).isEqualTo(false);
    }

    @Test
    public void testGetOutputs() {
        OutputState[] outputs = node.getOutputs();
        assertThat(outputs[0].name).isEqualTo(PINGONE_CREDENTIAL_UPDATE_KEY);
    }

    @Test
    public void testGetOutcomes() {
        PingOneCredentialsUpdate.UpdateOutcomeProvider outcomeProvider = new PingOneCredentialsUpdate.UpdateOutcomeProvider();

        PreferredLocales locales = new PreferredLocales();
        List<OutcomeProvider.Outcome> outcomes = outcomeProvider.getOutcomes(locales);

        assertThat(outcomes.get(0).id).isEqualTo("success");
        assertThat(outcomes.get(0).displayName).isEqualTo("Success");

        assertThat(outcomes.get(1).id).isEqualTo("error");
        assertThat(outcomes.get(1).displayName).isEqualTo("Error");
    }

    @Test
    public void testExceptionThrowDuringProcessing() throws Exception {
        // Given
        JsonValue sharedState = json(object(
            field(REALM, "/realm"),
            field(PINGONE_USER_ID_KEY, "some-user-id")
                                           ));
        JsonValue transientState = json(object());

        when(client.credentialUpdateRequest(any(), any(), anyString(), anyString(), anyString(), any())).thenReturn(null);

        // When
        Action result = node.process(getContext(sharedState, transientState, emptyList()));

        // Then
        assertThat(result.outcome).isEqualTo(ERROR_OUTCOME_ID);
    }

    @Test
    public void testErrorAccessTokenNull() throws Exception {
        given(pingOneWorkerService.getAccessToken(any(), any())).willReturn(null);

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
        given(pingOneWorkerService.getAccessToken(any(), any())).willReturn(null);
        given(pingOneWorkerService.getAccessToken(realm, worker)).willThrow(new PingOneWorkerException(""));
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