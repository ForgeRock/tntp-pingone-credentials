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
import static org.forgerock.am.marketplace.pingonecredentials.Constants.FAILURE_OUTCOME_ID;
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
import static org.forgerock.openam.auth.node.api.SharedStateConstants.USERNAME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.ConfirmationCallback;
import javax.security.auth.callback.TextOutputCallback;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.forgerock.json.JsonValue;
import org.forgerock.oauth2.core.AccessToken;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.ExternalRequestContext;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.auth.nodes.helpers.LocalizationHelper;
import org.forgerock.openam.authentication.callbacks.PollingWaitCallback;
import org.forgerock.openam.core.realms.Realm;
import org.forgerock.openam.integration.pingone.PingOneWorkerConfig;
import org.forgerock.openam.integration.pingone.PingOneWorkerException;
import org.forgerock.openam.integration.pingone.PingOneWorkerService;
import org.forgerock.openam.test.extensions.LoggerExtension;
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
    AccessToken accessToken;

    @Mock
    PingOneWorkerConfig.Worker worker;

    @Mock
    Realm realm;

    @Mock
    PingOneCredentialsService client;

    PingOneCredentialsPairWallet node;

    @Mock
    LocalizationHelper localizationHelper;

    private static final String USER = "testUser";

    @BeforeEach
    public void setup() throws Exception {
        given(pingOneWorkerService.getWorker(any(), anyString())).willReturn(Optional.of(worker));
        given(pingOneWorkerService.getAccessToken(any(), any())).willReturn(accessToken);

        node = spy(new PingOneCredentialsPairWallet(config, realm, pingOneWorkerService, client, localizationHelper));
    }

    @Test
    public void testShouldPresentDeliveryMethodOptions() throws Exception {
        // Given
        given(config.allowDeliveryMethodSelection()).willReturn(true);
        given(localizationHelper.getLocalizedMessage(any(), any(), any(), anyString()))
            .willReturn("Select Delivery Method:");

        given(config.pingOneUserIdAttribute()).willReturn(PINGONE_USER_ID_KEY);

        JsonValue sharedState = json(object(
            field(USERNAME, USER),
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
            field(USERNAME, USER),
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
    public void testVerifyTransactionInitiatedButNodeTimesOut() throws Exception {
        // Given
        JsonValue sharedState = json(object(
            field(USERNAME, USER),
            field(REALM, "/realm"),
            field(PINGONE_USER_ID_KEY, "some-user-id"),
            field(PINGONE_PAIRING_WALLET_ID_KEY, "some-pairing-wallet-id"),
            field(PINGONE_APPOPEN_URL_KEY, "some-appopen-url"),
            field(PINGONE_PAIRING_TIMEOUT_KEY, 30000),
            field(PINGONE_PAIRING_DELIVERY_METHOD_KEY, 0)));

        given(config.pingOneUserIdAttribute()).willReturn(PINGONE_USER_ID_KEY);
        given(config.digitalWalletApplicationId()).willReturn("some-wallet-application-id");
        given(config.timeout()).willReturn(30);
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
        JsonValue sharedState = json(object(field(USERNAME, USER), field(REALM, "/realm")));
        JsonValue transientState = json(object());

        // When
        Action result = node.process(getContext(sharedState, transientState, emptyList()));

        // Then
        assertThat(result.outcome).isEqualTo(FAILURE_OUTCOME_ID);
    }

    @ParameterizedTest
    @CsvSource({
        "ACTIVE,success",
        "EXPIRED,failure",
    })
    public void testReturnOutcomeForPairingStatus(String status, String expectedOutcome) throws Exception {
        // Given
        JsonValue sharedState = json(object(
            field(USERNAME, USER),
            field(REALM, "/realm"),
            field(PINGONE_USER_ID_KEY, "some-user-id"),
            field(PINGONE_PAIRING_WALLET_ID_KEY, "some-pairing-wallet-id"),
            field(PINGONE_PAIRING_TIMEOUT_KEY, 5000)));

        given(config.timeout()).willReturn(120);
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
    public void testPingOneCommunicationFailed() throws Exception {
        // Given
        given(pingOneWorkerService.getAccessToken(any(), any())).willReturn(null);
        given(pingOneWorkerService.getAccessToken(realm, worker)).willThrow(new PingOneWorkerException(""));
        JsonValue sharedState = json(object(
            field(USERNAME, USER),
            field(REALM, "/realm"),
            field(PINGONE_USER_ID_KEY, "some-user-id")
                                           ));
        JsonValue transientState = json(object());

        // When
        Action result = node.process(getContext(sharedState, transientState, emptyList()));

        // Then
        assertThat(result.outcome).isEqualTo(FAILURE_OUTCOME_ID);
    }

    private TreeContext getContext(JsonValue sharedState, JsonValue transientState,
                                   List<? extends Callback> callbacks) {
        return new TreeContext(sharedState, transientState, new ExternalRequestContext.Builder().build(), callbacks,
                               Optional.empty());
    }
}