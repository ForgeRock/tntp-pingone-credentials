
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
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_USER_ID_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.SUCCESS_OUTCOME_ID;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openam.auth.node.api.SharedStateConstants.REALM;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;

import javax.security.auth.callback.Callback;
import java.util.List;
import java.util.Optional;

import org.forgerock.json.JsonValue;
import org.forgerock.oauth2.core.AccessToken;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.ExternalRequestContext;
import org.forgerock.openam.auth.node.api.InputState;
import org.forgerock.openam.auth.node.api.OutcomeProvider;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.auth.service.marketplace.TNTPPingOneConfigChoiceValues;
import org.forgerock.openam.auth.service.marketplace.TNTPPingOneUtility;
import org.forgerock.openam.core.realms.Realm;
import org.forgerock.openam.test.extensions.LoggerExtension;
import org.forgerock.util.i18n.PreferredLocales;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class PingOneCredentialsRevokeTest {

    @RegisterExtension
    public LoggerExtension loggerExtension = new LoggerExtension(PingOneCredentialsRevoke.class);

    MockedStatic<TNTPPingOneUtility> mockedStaticPingOneUtility;

    MockedStatic<TNTPPingOneConfigChoiceValues> mockedStaticPingOneConfigChoices;

    @Mock
    PingOneCredentialsRevoke.Config config;

    @Mock
    TNTPPingOneUtility pingOneUtility;

    @Mock
    AccessToken accessToken;

    @Mock
    Realm realm;

    @Mock
    PingOneCredentialsService client;

    PingOneCredentialsRevoke node;

    @BeforeEach
    public void setup() throws Exception {
        mockedStaticPingOneUtility = Mockito.mockStatic(TNTPPingOneUtility.class);
        mockedStaticPingOneUtility.when(TNTPPingOneUtility::getInstance).thenReturn(pingOneUtility);

        mockedStaticPingOneConfigChoices = Mockito.mockStatic(TNTPPingOneConfigChoiceValues.class);
        mockedStaticPingOneConfigChoices.when(() -> TNTPPingOneConfigChoiceValues.
            createTNTPPingOneConfigName("Global Default")).thenReturn("Global Default");

        given(pingOneUtility.getAccessToken(any(), any())).willReturn(accessToken);

        node = new PingOneCredentialsRevoke(config, realm, client);
    }

    @AfterEach
    public void tearDown() {
        // Closing the mockStatic after each test
        mockedStaticPingOneUtility.close();
        mockedStaticPingOneConfigChoices.close();
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
    public void testReturnOutcomeRevokedTest() throws Exception {
        // Given
        JsonValue sharedState = json(object(
            field(REALM, "/realm"),
            field(PINGONE_USER_ID_KEY, "some-user-id"),
            field(PINGONE_CREDENTIAL_ID_KEY, "some-credential-id")));

        given(config.pingOneUserIdAttribute()).willReturn(PINGONE_USER_ID_KEY);
        given(config.credentialId()).willReturn(PINGONE_CREDENTIAL_ID_KEY);

        when(client.revokeCredentialRequest(any(), any(), anyString(), anyString())).thenReturn(Constants.RevokeResult.REVOKED);

        // When
        Action result = node.process(getContext(sharedState, json(object()), emptyList()));

        // Then
        assertThat(result.outcome).isEqualTo(SUCCESS_OUTCOME_ID);
    }

    @Test
    public void testGetInputs() {
        given(config.pingOneUserIdAttribute()).willReturn(PINGONE_USER_ID_KEY);
        given(config.credentialId()).willReturn(PINGONE_CREDENTIAL_ID_KEY);

        InputState[] inputs = node.getInputs();

        assertThat(inputs[0].name).isEqualTo(PINGONE_USER_ID_KEY);
        assertThat(inputs[0].required).isEqualTo(false);

        assertThat(inputs[1].name).isEqualTo(PINGONE_CREDENTIAL_ID_KEY);
        assertThat(inputs[1].required).isEqualTo(false);

        assertThat(inputs[2].name).isEqualTo(OBJECT_ATTRIBUTES);
        assertThat(inputs[2].required).isEqualTo(false);
    }

    @Test
    public void testGetOutcomes() {
        PingOneCredentialsRevoke.RevokeOutcomeProvider outcomeProvider = new PingOneCredentialsRevoke.RevokeOutcomeProvider();

        PreferredLocales locales = new PreferredLocales();
        List<OutcomeProvider.Outcome> outcomes = outcomeProvider.getOutcomes(locales);

        assertThat(outcomes.get(0).id).isEqualTo("success");
        assertThat(outcomes.get(0).displayName).isEqualTo("Success");

        assertThat(outcomes.get(1).id).isEqualTo("notFound");
        assertThat(outcomes.get(1).displayName).isEqualTo("Not Found");

        assertThat(outcomes.get(2).id).isEqualTo("error");
        assertThat(outcomes.get(2).displayName).isEqualTo("Error");
    }

    @Test
    public void testExceptionThrowDuringProcessing() throws Exception {
        // Given
        JsonValue sharedState = json(object(
            field(REALM, "/realm"),
            field(PINGONE_USER_ID_KEY, "some-user-id")));

        JsonValue transientState = json(object());

        when(config.credentialId()).thenReturn(null);

        // When
        Action result = node.process(getContext(sharedState, transientState, emptyList()));

        // Then
        assertThat(result.outcome).isEqualTo(ERROR_OUTCOME_ID);
    }

    @Test
    public void testErrorAccessTokenNull() throws Exception {
        given(pingOneUtility.getAccessToken(any(), any())).willReturn(null);

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
        given(pingOneUtility.getAccessToken(any(), any())).willReturn(null);

        JsonValue sharedState = json(object(
            field(REALM, "/realm"),
            field(PINGONE_USER_ID_KEY, "some-user-id")));

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