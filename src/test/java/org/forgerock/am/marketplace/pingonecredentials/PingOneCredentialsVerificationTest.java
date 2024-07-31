/*
 * Copyright 2024 ForgeRock AS. All Rights Reserved
 *
 * Use of this code requires a commercial software license with ForgeRock AS.
 * or with one of its affiliates. All use shall be exclusively subject
 * to such license between the licensee and ForgeRock AS.
 */

package org.forgerock.am.marketplace.pingonecredentials;

import static freemarker.template.utility.Collections12.singletonList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.ERROR_OUTCOME_ID;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.OBJECT_ATTRIBUTES;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_APPLICATION_INSTANCE_ID_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_CREDENTIAL_VERIFICATION_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_USER_ID_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_VERIFICATION_DELIVERY_METHOD_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_VERIFICATION_SESSION_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_VERIFICATION_TIMEOUT_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.TIMEOUT_OUTCOME_ID;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openam.auth.node.api.SharedStateConstants.REALM;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.ConfirmationCallback;
import javax.security.auth.callback.TextOutputCallback;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.forgerock.json.JsonValue;
import org.forgerock.oauth2.core.AccessToken;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.ExternalRequestContext;
import org.forgerock.openam.auth.node.api.InputState;
import org.forgerock.openam.auth.node.api.OutcomeProvider;
import org.forgerock.openam.auth.node.api.OutputState;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.auth.nodes.helpers.LocalizationHelper;
import org.forgerock.openam.auth.service.marketplace.TNTPPingOneConfigChoiceValues;
import org.forgerock.openam.auth.service.marketplace.TNTPPingOneUtility;
import org.forgerock.openam.authentication.callbacks.PollingWaitCallback;
import org.forgerock.openam.core.realms.Realm;
import org.forgerock.openam.test.extensions.LoggerExtension;
import org.forgerock.util.i18n.PreferredLocales;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class PingOneCredentialsVerificationTest {

    @RegisterExtension
    public LoggerExtension loggerExtension = new LoggerExtension(PingOneCredentialsVerification.class);

    MockedStatic<TNTPPingOneUtility> mockedStaticPingOneUtility;

    MockedStatic<TNTPPingOneConfigChoiceValues> mockedStaticPingOneConfigChoices;

    @Mock
    PingOneCredentialsVerification.Config config;

    @Mock
    TNTPPingOneUtility pingOneUtility;

    @Mock
    AccessToken accessToken;

    @Mock
    Realm realm;

    @Mock
    PingOneCredentialsService client;

    PingOneCredentialsVerification node;

    @Mock
    LocalizationHelper localizationHelper;

    @BeforeEach
    public void setup() throws Exception {
        mockedStaticPingOneUtility = Mockito.mockStatic(TNTPPingOneUtility.class);
        mockedStaticPingOneUtility.when(TNTPPingOneUtility::getInstance).thenReturn(pingOneUtility);

        mockedStaticPingOneConfigChoices = Mockito.mockStatic(TNTPPingOneConfigChoiceValues.class);
        mockedStaticPingOneConfigChoices.when(() -> TNTPPingOneConfigChoiceValues.
            createTNTPPingOneConfigName("Global Default")).thenReturn("Global Default");

        given(pingOneUtility.getAccessToken(any(), any())).willReturn(accessToken);

        node = new PingOneCredentialsVerification(config, realm, client, localizationHelper);
    }

    @AfterEach
    public void tearDown() {
        // Closing the mockStatic after each test
        mockedStaticPingOneUtility.close();
        mockedStaticPingOneConfigChoices.close();
    }

    @Test
    public void testShouldPresentDeliveryMethodOptions() throws Exception {
        // Given
        given(config.allowDeliveryMethodSelection()).willReturn(true);
        given(localizationHelper.getLocalizedMessage(any(), any(), any(), anyString()))
            .willReturn("Select Delivery Method:");

        JsonValue sharedState = json(object(
            field(REALM, "/realm")));

        JsonValue transientState = json(object());

        // When
        Action result = node.process(getContext(sharedState, transientState, emptyList()));

        // Then
        assertThat(result.callbacks.size()).isEqualTo(2);
        assertThat(result.callbacks.get(0)).isInstanceOf(TextOutputCallback.class);
        assertThat(result.callbacks.get(1)).isInstanceOf(ConfirmationCallback.class);
    }

    @ParameterizedTest
    @CsvSource({"0,4", "1,1"})
    public void testReturnCallbacksBasedOnSelectedDeliveryMethod(String input, String expected)
        throws Exception {
        // Given
        int choice = Integer.parseInt(input);
        int numberOfExpectedCallbacks = Integer.parseInt(expected);

        JsonValue sharedState = json(object(
            field(REALM, "/realm"),
            field(PINGONE_APPLICATION_INSTANCE_ID_KEY, "some-application-instance-id")));

        given(config.digitalWalletApplicationId()).willReturn(Optional.of("some-digital-wallet-application-id"));
        given(config.applicationInstanceAttribute()).willReturn(PINGONE_APPLICATION_INSTANCE_ID_KEY);
        given(config.credentialType()).willReturn("some-credential-type");
        given(config.allowDeliveryMethodSelection()).willReturn(true);

        given(localizationHelper.getLocalizedMessage(any(), any(), any(), anyString()))
            .willReturn("Some localized text");

        JsonValue transientState = json(object());

        Callback textOutputCallback = new TextOutputCallback(
            TextOutputCallback.INFORMATION, "Select Delivery Method:");

        ConfirmationCallback confirmationCallback = new ConfirmationCallback(
            ConfirmationCallback.INFORMATION, new String[]{"QR Code", "Push"}, 0);
        confirmationCallback.setSelectedIndex(choice);

        List<Callback> callbackList = new ArrayList<>();
        callbackList.add(textOutputCallback);
        callbackList.add(confirmationCallback);

        JsonValue response = json(object(
            field("id", "some-session-id"),
            field("status", "INITIAL"),
            field("_links", object(
                field("appOpenUrl", object(
                    field("href", "https://shocard.pingone.com/appopen?u=https%3A%2F%2Fapi.pingone.com" +
                                  "%2Fv1%2Fdistributedid%2Frequests%2Fe4974bd1-0094-4586-8e43-28c4409d4bd7")))))));

        when(client.createVerificationRequest(any(), any(), anyString(), anyString(), any(), any())).thenReturn(response);
        when(client.createVerificationRequestPush(any(), any(), anyString(), anyString(), any(),
                                                  anyString(), anyString(), any())).thenReturn(response);

        // When
        Action result = node.process(getContext(sharedState, transientState, callbackList));

        // Then
        assertThat(result.callbacks.size()).isEqualTo(numberOfExpectedCallbacks);
    }

    @Test
    public void testVerifyTransactionInitiatedButNodeTimesOut() throws Exception {
        // Given
        JsonValue sharedState = json(object(
            field(REALM, "/realm"),
            field(PINGONE_VERIFICATION_SESSION_KEY, "some-verification-session-id"),
            field(PINGONE_VERIFICATION_TIMEOUT_KEY, 30000),
            field(PINGONE_VERIFICATION_DELIVERY_METHOD_KEY, 0)));

        given(config.timeout()).willReturn(Duration.ofSeconds(30));
        given(config.allowDeliveryMethodSelection()).willReturn(true);
        given(localizationHelper.getLocalizedMessage(any(), any(), any(), anyString()))
            .willReturn("Some localized text");

        JsonValue response = json(object(
            field("status", "INITIAL"),
            field("_links", object(
                field("appOpenUrl", object(
                    field("href", "https://shocard.pingone.com/appopen?u=https%3A%2F%2Fapi.pingone.com" +
                                  "%2Fv1%2Fdistributedid%2Frequests%2Fe4974bd1-0094-4586-8e43-28c4409d4bd7")))))));

        when(client.readVerificationSession(any(), any(), anyString())).thenReturn(response);

        // When
        Action result = node.process(getContext(sharedState, json(object()), singletonList(mock(PollingWaitCallback.class))));

        // Then
        assertThat(result.outcome).isEqualTo(TIMEOUT_OUTCOME_ID);
    }

    @ParameterizedTest
    @CsvSource({
        "VERIFICATION_SUCCESSFUL,success",
        "EXPIRED,error",
    })
    public void testReturnOutcomeForVerificationStatus(String status, String expectedOutcome) throws Exception {
        // Given
        JsonValue sharedState = json(object(
            field(REALM, "/realm"),
            field(PINGONE_VERIFICATION_SESSION_KEY, "some-session-id"),
            field(PINGONE_VERIFICATION_TIMEOUT_KEY, 5000)));

        given(config.timeout()).willReturn(Duration.ofSeconds(120));
        given(config.deliveryMethod()).willReturn(Constants.VerificationDeliveryMethod.QRCODE);
        given(config.allowDeliveryMethodSelection()).willReturn(false);

        JsonValue response = json(object(
            field("id", "some-transaction-id"),
            field("status", status)));

        given(localizationHelper.getLocalizedMessage(any(), any(), any(), anyString()))
            .willReturn("Some localized text");

        when(client.readVerificationSession(any(), any(), anyString())).thenReturn(response);

        // When
        Action result = node.process(getContext(sharedState, json(object()), singletonList(mock(PollingWaitCallback.class))));

        // Then
        assertThat(result.outcome).isEqualTo(expectedOutcome);
    }

    @Test
    public void testGetInputs() {
        given(config.digitalWalletApplicationId()).willReturn(Optional.of("some-digital-wallet-app-id"));

        InputState[] inputs = node.getInputs();

        assertThat(inputs[0].name).isEqualTo(PINGONE_VERIFICATION_SESSION_KEY);
        assertThat(inputs[0].required).isEqualTo(false);

        assertThat(inputs[1].name).isEqualTo(PINGONE_VERIFICATION_DELIVERY_METHOD_KEY);
        assertThat(inputs[1].required).isEqualTo(false);

        assertThat(inputs[2].name).isEqualTo(PINGONE_VERIFICATION_TIMEOUT_KEY);
        assertThat(inputs[2].required).isEqualTo(false);

        assertThat(inputs[3].name).isEqualTo(OBJECT_ATTRIBUTES);
        assertThat(inputs[3].required).isEqualTo(false);

        assertThat(inputs[4].name).isEqualTo("some-digital-wallet-app-id");
        assertThat(inputs[4].required).isEqualTo(false);

        assertThat(inputs[5].name).isEqualTo(PINGONE_APPLICATION_INSTANCE_ID_KEY);
        assertThat(inputs[5].required).isEqualTo(false);

        assertThat(inputs[6].name).isEqualTo(PINGONE_CREDENTIAL_VERIFICATION_KEY);
        assertThat(inputs[6].required).isEqualTo(false);
    }

    @Test
    public void testGetOutputs() {
        OutputState[] outputs = node.getOutputs();

        assertThat(outputs[0].name).isEqualTo(PINGONE_VERIFICATION_SESSION_KEY);
        assertThat(outputs[1].name).isEqualTo(PINGONE_VERIFICATION_DELIVERY_METHOD_KEY);
        assertThat(outputs[2].name).isEqualTo(PINGONE_VERIFICATION_TIMEOUT_KEY);
    }

    @Test
    public void testGetOutcomes() {
        PingOneCredentialsVerification.VerificationOutcomeProvider outcomeProvider = new PingOneCredentialsVerification.VerificationOutcomeProvider();

        PreferredLocales locales = new PreferredLocales();
        List<OutcomeProvider.Outcome> outcomes = outcomeProvider.getOutcomes(locales);

        assertThat(outcomes.get(0).id).isEqualTo("success");
        assertThat(outcomes.get(0).displayName).isEqualTo("Success");

        assertThat(outcomes.get(1).id).isEqualTo("error");
        assertThat(outcomes.get(1).displayName).isEqualTo("Error");

        assertThat(outcomes.get(2).id).isEqualTo("timeout");
        assertThat(outcomes.get(2).displayName).isEqualTo("Time Out");
    }

    @Test
    public void testExceptionThrowDuringProcessing() throws Exception {
        // Given
        JsonValue sharedState = json(object(
            field(REALM, "/realm"),
            field(PINGONE_USER_ID_KEY, "some-user-id")));
        JsonValue transientState = json(object());

        given(config.allowDeliveryMethodSelection()).willReturn(false);
        given(config.deliveryMethod()).willReturn(Constants.VerificationDeliveryMethod.QRCODE);

        when(client.createVerificationRequest(any(), any(), anyString(), anyString(), any(), any())).thenReturn(null);

        // When
        Action result = node.process(getContext(sharedState, transientState, emptyList()));

        // Then
        assertThat(result.outcome).isEqualTo(ERROR_OUTCOME_ID);
    }

    @Test
    public void testErrorAccessTokenNull() throws Exception {
        given(pingOneUtility.getAccessToken(any(), any())).willReturn(null);
        given(config.allowDeliveryMethodSelection()).willReturn(false);
        given(config.deliveryMethod()).willReturn(Constants.VerificationDeliveryMethod.QRCODE);

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

        given(config.allowDeliveryMethodSelection()).willReturn(false);
        given(config.deliveryMethod()).willReturn(Constants.VerificationDeliveryMethod.QRCODE);

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