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
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_APPOPEN_URL_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_PAIRING_DELIVERY_METHOD_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_PAIRING_TIMEOUT_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_PAIRING_WALLET_ID_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_USER_ID_KEY;
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
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.ExternalRequestContext;
import org.forgerock.openam.auth.node.api.InputState;
import org.forgerock.openam.auth.node.api.OutcomeProvider;
import org.forgerock.openam.auth.node.api.OutputState;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.auth.nodes.helpers.LocalizationHelper;
import org.forgerock.openam.authentication.callbacks.PollingWaitCallback;
import org.forgerock.openam.core.realms.Realm;
import org.forgerock.openam.integration.pingone.api.PingOneWorkerService;
import org.forgerock.openam.integration.pingone.api.PingOneWorkerException;
import org.forgerock.openam.test.extensions.LoggerExtension;
import org.forgerock.util.i18n.PreferredLocales;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class PingOneCredentialsPairWalletTest {

    @RegisterExtension
    public LoggerExtension loggerExtension = new LoggerExtension(PingOneCredentialsPairWallet.class);

    @Mock
    PingOneCredentialsPairWallet.Config config;

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

    PingOneCredentialsPairWallet node;

    @BeforeEach
    public void setup() throws Exception {
        given(pingOneWorkerService.getWorker(any(), anyString())).willReturn(Optional.of(worker));
        given(pingOneWorkerService.getAccessTokenId(any(), any())).willReturn("some-access-token");

        node = new PingOneCredentialsPairWallet(config, realm, pingOneWorkerService, client, localizationHelper);
    }

    @Test
    public void testShouldPresentDeliveryMethodOptions() throws Exception {
        // Given
        given(config.allowDeliveryMethodSelection()).willReturn(true);
        given(localizationHelper.getLocalizedMessage(any(), any(), any(), anyString()))
            .willReturn("Select Delivery Method:");

        given(config.pingOneUserIdAttribute()).willReturn(PINGONE_USER_ID_KEY);

        JsonValue sharedState = json(object(
            field(REALM, "/realm"),
            field(PINGONE_USER_ID_KEY, "some-user-id")
                                           ));
        JsonValue transientState = json(object());

        // When
        Action result = node.process(getContext(sharedState, transientState, emptyList()));

        // Then
        assertThat(result.callbacks.size()).isEqualTo(2);
        assertThat(result.callbacks.get(0)).isInstanceOf(TextOutputCallback.class);
        assertThat(result.callbacks.get(1)).isInstanceOf(ConfirmationCallback.class);
    }

    @ParameterizedTest
    @CsvSource({"0,4", "1,1", "2,1"})
    public void testReturnCallbacksBasedOnSelectedDeliveryMethod(String input, String expected)
        throws Exception {
        // Given
        int choice = Integer.parseInt(input);
        int numberOfExpectedCallbacks = Integer.parseInt(expected);

        given(config.pingOneUserIdAttribute()).willReturn(PINGONE_USER_ID_KEY);
        given(config.digitalWalletApplicationId()).willReturn("some-wallet-application-id");
        given(config.allowDeliveryMethodSelection()).willReturn(true);
        given(localizationHelper.getLocalizedMessage(any(), any(), any(), anyString()))
            .willReturn("Some localized text");

        JsonValue sharedState = json(object(
            field(REALM, "/realm"),
            field(PINGONE_USER_ID_KEY, "some-user-id")));

        JsonValue transientState = json(object());

        Callback textOutputCallback = new TextOutputCallback(
            TextOutputCallback.INFORMATION, "Select Delivery Method:");

        ConfirmationCallback confirmationCallback = new ConfirmationCallback(
            ConfirmationCallback.INFORMATION, new String[]{"QR Code", "SMS", "EMAIL"}, 0);
        confirmationCallback.setSelectedIndex(choice);

        List<Callback> callbackList = new ArrayList<>();
        callbackList.add(textOutputCallback);
        callbackList.add(confirmationCallback);

        JsonValue response = json(object(
            field("id", "some-wallet-id"),
            field("status", "PAIRING_REQUIRED"),
            field("_links", object(
                field("appOpen", object(
                    field("href", "https://credentials.customer.com?u=https%3A%2F%2Fapi.pingone.com" +
                                  "%2Fv1%2Fdistributedid%2Frequests%2F4766467d-2dd8-4cba-a9b7-10ba09b97354")))))));

        when(client.createDigitalWalletRequest(any(), any(), anyString(), anyString(), any())).thenReturn(response);

        // When
        Action result = node.process(getContext(sharedState, transientState, callbackList));

        // Then
        assertThat(result.callbacks.size()).isEqualTo(numberOfExpectedCallbacks);
    }

    @Test
    public void testReturnCallbacksBasedOnQRCodeDeliveryMethod()
        throws Exception {

        given(config.pingOneUserIdAttribute()).willReturn(PINGONE_USER_ID_KEY);
        given(config.digitalWalletApplicationId()).willReturn("some-wallet-application-id");
        given(config.qrCodeDelivery()).willReturn(true);
        given(config.allowDeliveryMethodSelection()).willReturn(false);
        given(localizationHelper.getLocalizedMessage(any(), any(), any(), anyString()))
            .willReturn("Some localized text");

        JsonValue sharedState = json(object(
            field(REALM, "/realm"),
            field(PINGONE_USER_ID_KEY, "some-user-id")));

        JsonValue transientState = json(object());

        JsonValue response = json(object(
            field("id", "some-wallet-id"),
            field("status", "PAIRING_REQUIRED"),
            field("_links", object(
                field("appOpen", object(
                    field("href", "https://credentials.customer.com?u=https%3A%2F%2Fapi.pingone.com" +
                                  "%2Fv1%2Fdistributedid%2Frequests%2F4766467d-2dd8-4cba-a9b7-10ba09b97354")))))));

        when(client.createDigitalWalletRequest(any(), any(), anyString(), anyString(), any())).thenReturn(response);

        // When
        Action result = node.process(getContext(sharedState, transientState, emptyList()));

        // Then
        // These four callbacks:
        /*  scanTextOutputCallback,
            qrCodeCallback,
            hiddenCallback,
            pollingCallback */
        assertThat(result.callbacks.size()).isEqualTo(4);
    }

    @Test
    public void testVerifyTransactionInitiatedButNodeTimesOut() throws Exception {
        // Given
        JsonValue sharedState = json(object(
            field(REALM, "/realm"),
            field(PINGONE_USER_ID_KEY, "some-user-id"),
            field(PINGONE_PAIRING_WALLET_ID_KEY, "some-pairing-wallet-id"),
            field(PINGONE_APPOPEN_URL_KEY, "some-appopen-url"),
            field(PINGONE_PAIRING_TIMEOUT_KEY, 30000),
            field(PINGONE_PAIRING_DELIVERY_METHOD_KEY, 0)));

        given(config.pingOneUserIdAttribute()).willReturn(PINGONE_USER_ID_KEY);
        given(config.digitalWalletApplicationId()).willReturn("some-wallet-application-id");
        given(config.timeout()).willReturn(Duration.ofSeconds(30));
        given(config.allowDeliveryMethodSelection()).willReturn(true);
        given(localizationHelper.getLocalizedMessage(any(), any(), any(), anyString()))
            .willReturn("Some localized text");

        JsonValue response = json(object(
            field("id", "some-wallet-id"),
            field("status", "PAIRING_REQUIRED"),
            field("_links", object(
                field("appOpen", object(
                    field("href", "https://credentials.customer.com?u=https%3A%2F%2Fapi.pingone.com" +
                                  "%2Fv1%2Fdistributedid%2Frequests%2F4766467d-2dd8-4cba-a9b7-10ba09b97354")))))));

        when(client.readDigitalWallet(any(), any(), anyString(), anyString())).thenReturn(response);

        // When
        Action result = node.process(getContext(sharedState, json(object()), singletonList(mock(PollingWaitCallback.class))));

        // Then
        assertThat(result.outcome).isEqualTo(TIMEOUT_OUTCOME_ID);
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

    @ParameterizedTest
    @CsvSource({
        "ACTIVE,success",
        "EXPIRED,error",
    })
    public void testReturnOutcomeForPairingStatus(String status, String expectedOutcome) throws Exception {
        // Given
        JsonValue sharedState = json(object(
            field(REALM, "/realm"),
            field(PINGONE_USER_ID_KEY, "some-user-id"),
            field(PINGONE_PAIRING_WALLET_ID_KEY, "some-pairing-wallet-id"),
            field(PINGONE_PAIRING_TIMEOUT_KEY, 5000)));

        given(config.timeout()).willReturn(Duration.ofSeconds(120));
        given(config.pingOneUserIdAttribute()).willReturn(PINGONE_USER_ID_KEY);
        given(config.allowDeliveryMethodSelection()).willReturn(false);

        JsonValue response = json(object(
            field("id", "some-transaction-id"),
            field("applicationInstance", object(
                field("id", "some-application-instance-id"))),
            field("status", status)));

        given(localizationHelper.getLocalizedMessage(any(), any(), any(), anyString()))
            .willReturn("Some localized text");

        when(client.readDigitalWallet(any(), any(), anyString(), anyString())).thenReturn(response);

        // When
        Action result = node.process(getContext(sharedState, json(object()), singletonList(mock(PollingWaitCallback.class))));

        // Then
        assertThat(result.outcome).isEqualTo(expectedOutcome);
    }

    @Test
    public void testGetInputs() {
        given(config.pingOneUserIdAttribute()).willReturn(PINGONE_USER_ID_KEY);

        InputState[] inputs = node.getInputs();

        assertThat(inputs[0].name).isEqualTo(PINGONE_USER_ID_KEY);
        assertThat(inputs[0].required).isEqualTo(false);

        assertThat(inputs[1].name).isEqualTo(PINGONE_PAIRING_WALLET_ID_KEY);
        assertThat(inputs[1].required).isEqualTo(false);

        assertThat(inputs[2].name).isEqualTo(PINGONE_PAIRING_DELIVERY_METHOD_KEY);
        assertThat(inputs[2].required).isEqualTo(false);

        assertThat(inputs[3].name).isEqualTo(PINGONE_PAIRING_TIMEOUT_KEY);
        assertThat(inputs[3].required).isEqualTo(false);

        assertThat(inputs[4].name).isEqualTo(PINGONE_APPOPEN_URL_KEY);
        assertThat(inputs[4].required).isEqualTo(false);

        assertThat(inputs[5].name).isEqualTo(OBJECT_ATTRIBUTES);
        assertThat(inputs[5].required).isEqualTo(false);
    }

    @Test
    public void testGetOutputs() {
        OutputState[] outputs = node.getOutputs();

        assertThat(outputs[0].name).isEqualTo(PINGONE_PAIRING_WALLET_ID_KEY);
        assertThat(outputs[1].name).isEqualTo(PINGONE_PAIRING_DELIVERY_METHOD_KEY);
        assertThat(outputs[2].name).isEqualTo(PINGONE_PAIRING_TIMEOUT_KEY);
        assertThat(outputs[3].name).isEqualTo(PINGONE_APPOPEN_URL_KEY);
    }

    @Test
    public void testGetOutcomes() {
        PingOneCredentialsPairWallet.PairingOutcomeProvider outcomeProvider = new PingOneCredentialsPairWallet.PairingOutcomeProvider();

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

        when(client.createDigitalWalletRequest(any(), any(), anyString(), anyString(), any())).thenReturn(null);

        // When
        Action result = node.process(getContext(sharedState, transientState, emptyList()));

        // Then
        assertThat(result.outcome).isEqualTo(ERROR_OUTCOME_ID);
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