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
import static org.forgerock.am.marketplace.pingonecredentials.Constants.*;
import static org.forgerock.json.JsonValue.array;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openam.auth.node.api.SharedStateConstants.REALM;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.ConfirmationCallback;
import javax.security.auth.callback.TextOutputCallback;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.sun.identity.authentication.callbacks.HiddenValueCallback;
import com.sun.identity.authentication.callbacks.ScriptTextOutputCallback;
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
public class PingOneCredentialsVerificationTest {

    @RegisterExtension
    public LoggerExtension loggerExtension = new LoggerExtension(PingOneCredentialsVerification.class);

    @Mock
    PingOneCredentialsVerification.Config config;

    @Mock
    PingOneWorkerService pingOneWorkerService;

    @Mock
    PingOneWorkerService.Worker worker;

    @Mock
    Realm realm;

    @Mock
    PingOneCredentialsService client;

    PingOneCredentialsVerification node;

    @Mock
    LocalizationHelper localizationHelper;

    @BeforeEach
    public void setup() throws Exception {
        given(pingOneWorkerService.getWorker(any(), anyString())).willReturn(Optional.of(worker));
        given(pingOneWorkerService.getAccessTokenId(any(), any())).willReturn("some-access-token");

        node = new PingOneCredentialsVerification(config, realm, pingOneWorkerService, client, localizationHelper);
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

        when(client.createVerificationRequest(any(), any(), anyString(), anyString(), any(), any(), any())).thenReturn(response);
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
    public void testOid4vpSessionCreatedAndCallbacksSent() throws Exception {
        // Given
        JsonValue sharedState = json(object(
            field(REALM, "/realm"),
            field(PINGONE_APPLICATION_INSTANCE_ID_KEY, "some-application-instance-id")));

        given(config.credentialType()).willReturn("some-credential-type");
        given(config.protocol()).willReturn(Constants.VerificationProtocol.OPENID4VP);
        given(config.protocolVersion()).willReturn(Optional.of("JWT-VC-Presentation-Profile-v0.0.1"));
        given(config.didMethod()).willReturn(Optional.of("JWK"));
        given(config.issuerFilter()).willReturn(List.of("did:web:some-issuer.example"));
        given(config.issuerFilterEnvironmentIds()).willReturn(List.of("some-environment-id"));
        given(config.oid4vpTimeoutSeconds()).willReturn(Optional.of(30));
        given(config.storeVerificationResponse()).willReturn(true);
        given(localizationHelper.getLocalizedMessage(any(), any(), any(), anyString()))
            .willReturn("Some localized text");

        JsonValue response = json(object(
            field("id", "some-session-id"),
            field("status", "INITIAL"),
            field("_links", object(
                field("qr", object(
                    field("href", "data:image/png;base64,some-qr-code"))),
                field("appOpenUrl", object(
                    field("href", "openid-vc://?request_uri=https%3A%2F%2Fapi.pingone.com%2Fv1%2F" +
                                  "presentationSessions%2Fsome-presentation-id")))))));

        when(client.createVerificationRequestOid4vp(any(), any(), anyString(), anyString(), any(), any(),
                                                    any(), any(), any())).thenReturn(response);

        // When
        Action result = node.process(getContext(sharedState, json(object()), emptyList()));

        // Then
        assertThat(result.callbacks.size()).isEqualTo(4);
        assertThat(result.callbacks.get(0)).isInstanceOf(TextOutputCallback.class);
        assertThat(result.callbacks.get(1)).isInstanceOf(ScriptTextOutputCallback.class);
        assertThat(result.callbacks.get(2)).isInstanceOf(HiddenValueCallback.class);
        assertThat(result.callbacks.get(3)).isInstanceOf(PollingWaitCallback.class);

        // The QR callback encodes the documented app-open deep link: the QR
        // generator emits the wallet deep link as the QR payload (URL-escaped),
        // and the hidden callback carries the same deep link verbatim. This pins
        // the link-mapping decision: the documented _links.appOpenUrl.href is the
        // content delivered to the wallet, not the pre-rendered _links.qr image.
        String deepLink = "openid-vc://?request_uri=https%3A%2F%2Fapi.pingone.com%2Fv1%2F"
                          + "presentationSessions%2Fsome-presentation-id";
        ScriptTextOutputCallback qrCallback = (ScriptTextOutputCallback) result.callbacks.get(1);
        assertThat(qrCallback.getMessage()).contains("callback_0");
        assertThat(qrCallback.getMessage()).contains("openid\\x2Dvc\\x3A\\x2F\\x2F\\x3F");
        assertThat(qrCallback.getMessage()).contains("presentationSessions\\x252Fsome\\x2Dpresentation\\x2Did");
        assertThat(qrCallback.getMessage()).doesNotContain("data:image/png;base64");

        HiddenValueCallback hiddenCallback = (HiddenValueCallback) result.callbacks.get(2);
        assertThat(hiddenCallback.getId()).isEqualTo(PingOneCredentialsVerification.HIDDEN_CALLBACK_ID);
        assertThat(hiddenCallback.getValue()).isEqualTo(deepLink);

        // The QR callback encodes the documented app-open deep link
        assertThat(sharedState.get(PINGONE_VERIFICATION_SESSION_KEY).asString()).isEqualTo("some-session-id");
        assertThat(sharedState.get(PINGONE_VERIFICATION_TIMEOUT_KEY).asInteger())
            .isEqualTo(PingOneCredentialsVerification.TRANSACTION_POLL_INTERVAL);
        assertThat(sharedState.get(PINGONE_VERIFICATION_PROTOCOL_KEY).asString()).isEqualTo("OPENID4VP");
        assertThat(sharedState.get(PINGONE_VERIFICATION_PROTOCOL_VERSION_KEY).asString())
            .isEqualTo("JWT-VC-Presentation-Profile-v0.0.1");
        assertThat(sharedState.get(PINGONE_VERIFICATION_DID_METHOD_KEY).asString()).isEqualTo("JWK");
        assertThat(sharedState.get(PINGONE_VERIFICATION_ISSUER_FILTER_KEY).asList())
            .containsExactly("did:web:some-issuer.example");
        assertThat(sharedState.isDefined(PINGONE_VERIFIED_DATA_KEY)).isFalse();

        verify(client).createVerificationRequestOid4vp(any(), any(), anyString(),
                                                       org.mockito.ArgumentMatchers.eq("some-credential-type"),
                                                       org.mockito.ArgumentMatchers.eq("JWT-VC-Presentation-Profile-v0.0.1"),
                                                       org.mockito.ArgumentMatchers.eq("JWK"),
                                                       org.mockito.ArgumentMatchers.eq(List.of("did:web:some-issuer.example")),
                                                       org.mockito.ArgumentMatchers.eq(List.of("some-environment-id")),
                                                       org.mockito.ArgumentMatchers.eq(30));
    }

    @Test
    public void testOid4vpSessionStoresNoUnconfiguredOptionalProtocolState() throws Exception {
        // Given
        JsonValue sharedState = json(object(
            field(REALM, "/realm")));

        given(config.credentialType()).willReturn("some-credential-type");
        given(config.protocol()).willReturn(Constants.VerificationProtocol.OPENID4VP);
        given(localizationHelper.getLocalizedMessage(any(), any(), any(), anyString()))
            .willReturn("Some localized text");

        JsonValue response = json(object(
            field("id", "some-session-id"),
            field("status", "INITIAL"),
            field("_links", object(
                field("appOpenUrl", object(
                    field("href", "openid-vc://?request_uri=https%3A%2F%2Fapi.pingone.com%2Fv1%2F" +
                                  "presentationSessions%2Fsome-presentation-id")))))));

        when(client.createVerificationRequestOid4vp(any(), any(), anyString(), anyString(), any(), any(),
                                                    any(), any(), any())).thenReturn(response);

        // When
        node.process(getContext(sharedState, json(object()), emptyList()));

        // Then
        assertThat(sharedState.isDefined(PINGONE_VERIFICATION_SESSION_KEY)).isTrue();
        assertThat(sharedState.get(PINGONE_VERIFICATION_PROTOCOL_KEY).asString()).isEqualTo("OPENID4VP");

        // Unconfigured optional members are absent rather than stored as JSON nulls
        assertThat(sharedState.isDefined(PINGONE_VERIFICATION_PROTOCOL_VERSION_KEY)).isFalse();
        assertThat(sharedState.isDefined(PINGONE_VERIFICATION_DID_METHOD_KEY)).isFalse();
        assertThat(sharedState.isDefined(PINGONE_VERIFICATION_ISSUER_FILTER_KEY)).isFalse();

        // The OID4VP request must carry no Native keys semantics
        verify(client).createVerificationRequestOid4vp(any(), any(), anyString(), anyString(),
                                                       org.mockito.ArgumentMatchers.isNull(),
                                                       org.mockito.ArgumentMatchers.isNull(),
                                                       org.mockito.ArgumentMatchers.eq(Collections.emptyList()),
                                                       org.mockito.ArgumentMatchers.eq(Collections.emptyList()),
                                                       org.mockito.ArgumentMatchers.isNull());
    }

    @ParameterizedTest
    @CsvSource({
        "VERIFICATION_SUCCESSFUL,success",
        "VERIFICATION_FAILED,error",
        "EXPIRED,error",
    })
    public void testReturnOutcomeForOid4vpVerificationStatus(String status, String expectedOutcome) throws Exception {
        // Given
        JsonValue sharedState = json(object(
            field(REALM, "/realm"),
            field(PINGONE_VERIFICATION_SESSION_KEY, "some-session-id"),
            field(PINGONE_VERIFICATION_TIMEOUT_KEY, 5000)));

        given(config.protocol()).willReturn(Constants.VerificationProtocol.OPENID4VP);
        given(config.timeout()).willReturn(Duration.ofSeconds(120));
        given(config.storeVerificationResponse()).willReturn(true);

        JsonValue response = json(object(
            field("id", "some-session-id"),
            field("status", status),
            field("applicationInstance", object(
                field("id", "some-application-instance-id"))),
            field("verifiedData", array(object(
                field("type", array("NonVerifiedEmployee")),
                field("data", object(
                    field("mail", "example@email.com"))))))));

        when(client.readVerificationSession(any(), any(), anyString())).thenReturn(response);

        // When
        Action result = node.process(getContext(sharedState, json(object()), singletonList(mock(PollingWaitCallback.class))));

        // Then
        assertThat(result.outcome).isEqualTo(expectedOutcome);
    }

    @ParameterizedTest
    @CsvSource({"VERIFICATION_FAILED", "EXPIRED"})
    public void testOid4vpTerminalFailureKeepsTheSessionWithItsErrors(String status) throws Exception {
        // Given
        JsonValue sharedState = json(object(
            field(REALM, "/realm"),
            field(PINGONE_VERIFICATION_SESSION_KEY, "some-session-id"),
            field(PINGONE_VERIFICATION_TIMEOUT_KEY, 5000)));

        given(config.protocol()).willReturn(Constants.VerificationProtocol.OPENID4VP);
        given(config.timeout()).willReturn(Duration.ofSeconds(120));
        given(config.storeVerificationResponse()).willReturn(true);

        JsonValue response = json(object(
            field("id", "some-session-id"),
            field("status", status),
            field("errors", array(object(
                field("code", "INVALID_CREDENTIAL"))))));

        when(client.readVerificationSession(any(), any(), anyString())).thenReturn(response);

        // When
        Action result = node.process(getContext(sharedState, json(object()), singletonList(mock(PollingWaitCallback.class))));

        // Then
        assertThat(result.outcome).isEqualTo("error");
        assertThat(sharedState.get(PINGONE_CREDENTIAL_VERIFICATION_KEY).get("status").asString()).isEqualTo(status);
        assertThat(sharedState.get(PINGONE_CREDENTIAL_VERIFICATION_KEY).get("errors").get(0).get("code").asString())
            .isEqualTo("INVALID_CREDENTIAL");
        assertThat(sharedState.isDefined(PINGONE_VERIFIED_DATA_KEY)).isFalse();
    }

    @Test
    public void testOid4vpVerificationSuccessfulStoresVerifiedData() throws Exception {
        // Given
        JsonValue sharedState = json(object(
            field(REALM, "/realm"),
            field(PINGONE_VERIFICATION_SESSION_KEY, "some-session-id"),
            field(PINGONE_VERIFICATION_TIMEOUT_KEY, 5000)));

        given(config.protocol()).willReturn(Constants.VerificationProtocol.OPENID4VP);
        given(config.timeout()).willReturn(Duration.ofSeconds(120));
        given(config.storeVerificationResponse()).willReturn(true);

        JsonValue response = json(object(
            field("id", "some-session-id"),
            field("status", "VERIFICATION_SUCCESSFUL"),
            field("applicationInstance", object(
                field("id", "some-application-instance-id"))),
            field("verifiedData", array(object(
                field("type", array("NonVerifiedEmployee")),
                field("data", object(
                    field("mail", "example@email.com"))))))));

        when(client.readVerificationSession(any(), any(), anyString())).thenReturn(response);

        // When
        Action result = node.process(getContext(sharedState, json(object()), singletonList(mock(PollingWaitCallback.class))));

        // Then
        assertThat(result.outcome).isEqualTo(SUCCESS_OUTCOME_ID);
        assertThat(sharedState.get(PINGONE_VERIFIED_DATA_KEY).get(0).get("data").get("mail").asString())
            .isEqualTo("example@email.com");
        assertThat(sharedState.get(PINGONE_APPLICATION_INSTANCE_ID_KEY).asString())
            .isEqualTo("some-application-instance-id");
        assertThat(sharedState.get(PINGONE_CREDENTIAL_VERIFICATION_KEY).get("id").asString())
            .isEqualTo("some-session-id");
        // No Native delivery-method keys are written by the OpenID4VP flow
        assertThat(sharedState.isDefined(PINGONE_VERIFICATION_DELIVERY_METHOD_KEY)).isFalse();
    }

    @Test
    public void testOid4vpVerificationSuccessfulWithoutVerifiedDataMemberLeavesKeyAbsent() throws Exception {
        // Given
        JsonValue sharedState = json(object(
            field(REALM, "/realm"),
            field(PINGONE_VERIFICATION_SESSION_KEY, "some-session-id"),
            field(PINGONE_VERIFICATION_TIMEOUT_KEY, 5000)));

        given(config.protocol()).willReturn(Constants.VerificationProtocol.OPENID4VP);
        given(config.timeout()).willReturn(Duration.ofSeconds(120));
        given(config.storeVerificationResponse()).willReturn(false);

        JsonValue response = json(object(
            field("id", "some-session-id"),
            field("status", "VERIFICATION_SUCCESSFUL")));

        when(client.readVerificationSession(any(), any(), anyString())).thenReturn(response);

        // When
        Action result = node.process(getContext(sharedState, json(object()), singletonList(mock(PollingWaitCallback.class))));

        // Then
        assertThat(result.outcome).isEqualTo(SUCCESS_OUTCOME_ID);
        assertThat(sharedState.isDefined(PINGONE_VERIFIED_DATA_KEY)).isFalse();
        assertThat(sharedState.isDefined(PINGONE_CREDENTIAL_VERIFICATION_KEY)).isFalse();
    }

    @Test
    public void testOid4vpVerificationSuccessfulStoresCredentialData() throws Exception {
        // Given
        JsonValue sharedState = json(object(
            field(REALM, "/realm"),
            field(PINGONE_VERIFICATION_SESSION_KEY, "some-session-id"),
            field(PINGONE_VERIFICATION_TIMEOUT_KEY, 5000)));

        given(config.protocol()).willReturn(Constants.VerificationProtocol.OPENID4VP);
        given(config.timeout()).willReturn(Duration.ofSeconds(120));
        given(config.storeVerificationResponse()).willReturn(true);
        given(config.storeCredentialData()).willReturn(true);

        JsonValue response = json(object(
            field("id", "some-session-id"),
            field("status", "VERIFICATION_SUCCESSFUL")));

        JsonValue credentialDataResponse = json(object(
            field("id", "some-session-id"),
            field("status", "VERIFICATION_SUCCESSFUL"),
            field("credentialData", array(object(
                field("type", "VerifiedEmployee"),
                field("data", array(
                    object(
                        field("key", "mail"),
                        field("value", "johndoe@example.com")))),
                field("issuerId", "9536b3bf-556c-4ed5-a357-5da900ce17d1"),
                field("issuerName", "Issuer Name"))))));

        when(client.readVerificationSession(any(), any(), anyString())).thenReturn(response);
        when(client.readVerificationCredentialData(any(), any(), anyString())).thenReturn(credentialDataResponse);

        // When
        Action result = node.process(getContext(sharedState, json(object()), singletonList(mock(PollingWaitCallback.class))));

        // Then
        assertThat(result.outcome).isEqualTo(SUCCESS_OUTCOME_ID);
        verify(client).readVerificationCredentialData(any(), any(), anyString());
        assertThat(sharedState.get(PINGONE_CREDENTIAL_DATA_KEY).get(0).get("type").asString())
            .isEqualTo("VerifiedEmployee");
        assertThat(sharedState.get(PINGONE_CREDENTIAL_DATA_KEY).get(0).get("data").get(0).get("value").asString())
            .isEqualTo("johndoe@example.com");
        assertThat(sharedState.get(PINGONE_CREDENTIAL_DATA_KEY).get(0).get("issuerName").asString())
            .isEqualTo("Issuer Name");
    }

    @Test
    public void testOid4vpVerificationSuccessfulWithoutStoreCredentialDataSkipsCredentialDataCall() throws Exception {
        // Given
        JsonValue sharedState = json(object(
            field(REALM, "/realm"),
            field(PINGONE_VERIFICATION_SESSION_KEY, "some-session-id"),
            field(PINGONE_VERIFICATION_TIMEOUT_KEY, 5000)));

        given(config.protocol()).willReturn(Constants.VerificationProtocol.OPENID4VP);
        given(config.timeout()).willReturn(Duration.ofSeconds(120));
        given(config.storeVerificationResponse()).willReturn(false);
        given(config.storeCredentialData()).willReturn(false);

        JsonValue response = json(object(
            field("id", "some-session-id"),
            field("status", "VERIFICATION_SUCCESSFUL")));

        when(client.readVerificationSession(any(), any(), anyString())).thenReturn(response);

        // When
        Action result = node.process(getContext(sharedState, json(object()), singletonList(mock(PollingWaitCallback.class))));

        // Then
        assertThat(result.outcome).isEqualTo(SUCCESS_OUTCOME_ID);
        assertThat(sharedState.isDefined(PINGONE_CREDENTIAL_DATA_KEY)).isFalse();
        verify(client, never()).readVerificationCredentialData(any(), any(), anyString());
    }

    @Test
    public void testOid4vpVerificationSuccessfulWithoutCredentialDataMemberLeavesKeyAbsent() throws Exception {
        // Given
        JsonValue sharedState = json(object(
            field(REALM, "/realm"),
            field(PINGONE_VERIFICATION_SESSION_KEY, "some-session-id"),
            field(PINGONE_VERIFICATION_TIMEOUT_KEY, 5000)));

        given(config.protocol()).willReturn(Constants.VerificationProtocol.OPENID4VP);
        given(config.timeout()).willReturn(Duration.ofSeconds(120));
        given(config.storeVerificationResponse()).willReturn(false);
        given(config.storeCredentialData()).willReturn(true);

        JsonValue response = json(object(
            field("id", "some-session-id"),
            field("status", "VERIFICATION_SUCCESSFUL")));

        JsonValue credentialDataResponse = json(object(
            field("id", "some-session-id"),
            field("status", "VERIFICATION_SUCCESSFUL")));

        when(client.readVerificationSession(any(), any(), anyString())).thenReturn(response);
        when(client.readVerificationCredentialData(any(), any(), anyString())).thenReturn(credentialDataResponse);

        // When
        Action result = node.process(getContext(sharedState, json(object()), singletonList(mock(PollingWaitCallback.class))));

        // Then
        assertThat(result.outcome).isEqualTo(SUCCESS_OUTCOME_ID);
        verify(client).readVerificationCredentialData(any(), any(), anyString());
        assertThat(sharedState.isDefined(PINGONE_CREDENTIAL_DATA_KEY)).isFalse();
    }

    @Test
    public void testOid4vpPollingContinuesWhileWaiting() throws Exception {
        // Given
        JsonValue sharedState = json(object(
            field(REALM, "/realm"),
            field(PINGONE_VERIFICATION_SESSION_KEY, "some-session-id"),
            field(PINGONE_VERIFICATION_TIMEOUT_KEY, 5000)));

        given(config.protocol()).willReturn(Constants.VerificationProtocol.OPENID4VP);
        given(config.timeout()).willReturn(Duration.ofSeconds(120));
        given(localizationHelper.getLocalizedMessage(any(), any(), any(), anyString()))
            .willReturn("Some localized text");

        JsonValue response = json(object(
            field("id", "some-session-id"),
            field("status", "WAITING"),
            field("_links", object(
                field("appOpenUrl", object(
                    field("href", "openid-vc://?request_uri=https%3A%2F%2Fapi.pingone.com%2Fv1%2F" +
                                  "presentationSessions%2Fsome-presentation-id")))))));

        when(client.readVerificationSession(any(), any(), anyString())).thenReturn(response);

        // When
        Action result = node.process(getContext(sharedState, json(object()), singletonList(mock(PollingWaitCallback.class))));

        // Then
        assertThat(result.callbacks.size()).isEqualTo(4);
        assertThat(result.outcome).isNull();
    }

    @Test
    public void testOid4vpVerificationTimesOut() throws Exception {
        // Given
        JsonValue sharedState = json(object(
            field(REALM, "/realm"),
            field(PINGONE_VERIFICATION_SESSION_KEY, "some-session-id"),
            field(PINGONE_VERIFICATION_TIMEOUT_KEY, 30000),
            field(PINGONE_VERIFICATION_PROTOCOL_KEY, "OPENID4VP")));

        given(config.protocol()).willReturn(Constants.VerificationProtocol.OPENID4VP);
        given(config.timeout()).willReturn(Duration.ofSeconds(30));
        given(localizationHelper.getLocalizedMessage(any(), any(), any(), anyString()))
            .willReturn("Some localized text");

        JsonValue response = json(object(
            field("id", "some-session-id"),
            field("status", "WAITING"),
            field("_links", object(
                field("appOpenUrl", object(
                    field("href", "openid-vc://?request_uri=https%3A%2F%2Fapi.pingone.com%2Fv1%2F" +
                                  "presentationSessions%2Fsome-presentation-id")))))));

        when(client.readVerificationSession(any(), any(), anyString())).thenReturn(response);

        // When
        Action result = node.process(getContext(sharedState, json(object()), singletonList(mock(PollingWaitCallback.class))));

        // Then
        assertThat(result.outcome).isEqualTo(TIMEOUT_OUTCOME_ID);
    }

    @Test
    public void testOid4vpMissingSessionStateDuringPollingReturnsError() throws Exception {
        // Given
        JsonValue sharedState = json(object(
            field(REALM, "/realm")));

        given(config.protocol()).willReturn(Constants.VerificationProtocol.OPENID4VP);

        // When
        Action result = node.process(getContext(sharedState, json(object()),
                                                singletonList(mock(PollingWaitCallback.class))));

        // Then
        assertThat(result.outcome).isEqualTo(ERROR_OUTCOME_ID);
        verify(client, never()).readVerificationSession(any(), any(), anyString());
        verify(client, never()).createVerificationRequestOid4vp(any(), any(), anyString(), anyString(), any(), any(),
                                                                any(), any(), any());
    }

    @Test
    public void testOid4vpServiceFailureReturnsError() throws Exception {
        // Given
        JsonValue sharedState = json(object(
            field(REALM, "/realm")));

        given(config.credentialType()).willReturn("some-credential-type");
        given(config.protocol()).willReturn(Constants.VerificationProtocol.OPENID4VP);
        given(localizationHelper.getLocalizedMessage(any(), any(), any(), anyString()))
            .willReturn("Some localized text");

        when(client.createVerificationRequestOid4vp(any(), any(), anyString(), anyString(), any(), any(),
                                                    any(), any(), any()))
            .thenThrow(new PingOneCredentialsServiceException("Failed PingOne Credentials"));

        // When
        Action result = node.process(getContext(sharedState, json(object()), emptyList()));

        // Then
        assertThat(result.outcome).isEqualTo(ERROR_OUTCOME_ID);
        assertThat(sharedState.isDefined(PINGONE_VERIFICATION_SESSION_KEY)).isFalse();
    }

    @Test
    public void testOid4vpMissingSessionIdInResponseReturnsError() throws Exception {
        // Given
        JsonValue sharedState = json(object(
            field(REALM, "/realm")));

        given(config.credentialType()).willReturn("some-credential-type");
        given(config.protocol()).willReturn(Constants.VerificationProtocol.OPENID4VP);
        given(localizationHelper.getLocalizedMessage(any(), any(), any(), anyString()))
            .willReturn("Some localized text");

        JsonValue response = json(object(
            field("status", "INITIAL")));

        when(client.createVerificationRequestOid4vp(any(), any(), anyString(), anyString(), any(), any(),
                                                    any(), any(), any())).thenReturn(response);

        // When
        Action result = node.process(getContext(sharedState, json(object()), emptyList()));

        // Then
        assertThat(result.outcome).isEqualTo(ERROR_OUTCOME_ID);
        assertThat(sharedState.isDefined(PINGONE_VERIFICATION_SESSION_KEY)).isFalse();
    }

    @Test
    public void testOid4vpResponseWithoutAppOpenUrlLinkStillStoresSessionAndSendsNullLinkCallbacks() throws Exception {
        // Given: a documented-behavior case mirroring the Native flow. A create
        // response without _links.appOpenUrl.href carries no value to invent, so
        // the deep link is null: the QR script renders a 'null' payload and the
        // hidden callback carries no value, while the session ID is already
        // stored and polling continues against the created session. This test
        // pins that known behavior rather than inventing a fallback.
        JsonValue sharedState = json(object(
            field(REALM, "/realm")));

        given(config.credentialType()).willReturn("some-credential-type");
        given(config.protocol()).willReturn(Constants.VerificationProtocol.OPENID4VP);
        given(localizationHelper.getLocalizedMessage(any(), any(), any(), anyString()))
            .willReturn("Some localized text");

        // No _links member at all
        JsonValue response = json(object(
            field("id", "some-session-id"),
            field("status", "INITIAL")));

        when(client.createVerificationRequestOid4vp(any(), any(), anyString(), anyString(), any(), any(),
                                                    any(), any(), any())).thenReturn(response);

        // When
        Action result = node.process(getContext(sharedState, json(object()), emptyList()));

        // Then
        assertThat(result.outcome).isNull();
        assertThat(result.callbacks.size()).isEqualTo(4);

        // The session was stored before the callbacks were built, so polling can
        // proceed on the next pass.
        assertThat(sharedState.get(PINGONE_VERIFICATION_SESSION_KEY).asString()).isEqualTo("some-session-id");
        assertThat(sharedState.get(PINGONE_VERIFICATION_PROTOCOL_KEY).asString()).isEqualTo("OPENID4VP");

        // The QR generator receives the null link and emits a literal 'null'
        // payload; the hidden callback carries no value.
        ScriptTextOutputCallback qrCallback = (ScriptTextOutputCallback) result.callbacks.get(1);
        assertThat(qrCallback.getMessage()).contains("text: 'null'");

        HiddenValueCallback hiddenCallback = (HiddenValueCallback) result.callbacks.get(2);
        assertThat(hiddenCallback.getId()).isEqualTo(PingOneCredentialsVerification.HIDDEN_CALLBACK_ID);
        assertThat(hiddenCallback.getValue()).isNull();
    }

    @Test
    public void testOid4vpMissingCredentialTypeReturnsErrorWithoutCallingService() throws Exception {
        // Given: OPENID4VP requires exactly one requested credential type; an
        // unconfigured Credential Type is a configuration error.
        JsonValue sharedState = json(object(
            field(REALM, "/realm")));

        given(config.credentialType()).willReturn("");
        given(config.protocol()).willReturn(Constants.VerificationProtocol.OPENID4VP);

        // When
        Action result = node.process(getContext(sharedState, json(object()), emptyList()));

        // Then
        assertThat(result.outcome).isEqualTo(ERROR_OUTCOME_ID);
        verify(client, never()).createVerificationRequestOid4vp(any(), any(), anyString(), anyString(), any(), any(),
                                                                any(), any(), any());
        assertThat(sharedState.isDefined(PINGONE_VERIFICATION_SESSION_KEY)).isFalse();
    }

    @Test
    public void testNativeProtocolNeverCallsOid4vpRequest() throws Exception {
        // Given
        JsonValue sharedState = json(object(
            field(REALM, "/realm"),
            field(PINGONE_APPLICATION_INSTANCE_ID_KEY, "some-application-instance-id")));

        given(config.credentialType()).willReturn("some-credential-type");
        given(config.protocol()).willReturn(Constants.VerificationProtocol.NATIVE);
        given(config.deliveryMethod()).willReturn(Constants.VerificationDeliveryMethod.QRCODE);
        given(config.allowDeliveryMethodSelection()).willReturn(false);
        given(localizationHelper.getLocalizedMessage(any(), any(), any(), anyString()))
            .willReturn("Some localized text");

        JsonValue response = json(object(
            field("id", "some-session-id"),
            field("status", "INITIAL"),
            field("_links", object(
                field("appOpenUrl", object(
                    field("href", "https://shocard.pingone.com/appopen?u=https%3A%2F%2Fapi.pingone.com")))))));

        when(client.createVerificationRequest(any(), any(), anyString(), anyString(), any(), any(), any()))
            .thenReturn(response);

        // When
        Action result = node.process(getContext(sharedState, json(object()), emptyList()));

        // Then
        assertThat(result.callbacks.size()).isEqualTo(4);
        assertThat(sharedState.get(PINGONE_VERIFICATION_SESSION_KEY).asString()).isEqualTo("some-session-id");
        // NATIVE stores no additive OID4VP protocol state
        assertThat(sharedState.isDefined(PINGONE_VERIFICATION_PROTOCOL_KEY)).isFalse();
        assertThat(sharedState.isDefined(PINGONE_VERIFIED_DATA_KEY)).isFalse();

        verify(client, never()).createVerificationRequestOid4vp(any(), any(), anyString(), anyString(), any(), any(),
                                                                any(), any(), any());
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

        assertThat(inputs[7].name).isEqualTo(PINGONE_CREDENTIAL_DATA_KEY);
        assertThat(inputs[7].required).isEqualTo(false);

        assertThat(inputs[8].name).isEqualTo(REQUESTED_CREDENTIALS);
        assertThat(inputs[8].required).isEqualTo(false);
    }

    @Test
    public void testGetOutputs() {
        OutputState[] outputs = node.getOutputs();

        assertThat(outputs[0].name).isEqualTo(PINGONE_VERIFICATION_SESSION_KEY);
        assertThat(outputs[1].name).isEqualTo(PINGONE_VERIFICATION_DELIVERY_METHOD_KEY);
        assertThat(outputs[2].name).isEqualTo(PINGONE_VERIFICATION_TIMEOUT_KEY);
        assertThat(outputs[3].name).isEqualTo(PINGONE_VERIFICATION_PROTOCOL_KEY);
        assertThat(outputs[4].name).isEqualTo(PINGONE_VERIFICATION_PROTOCOL_VERSION_KEY);
        assertThat(outputs[5].name).isEqualTo(PINGONE_VERIFICATION_DID_METHOD_KEY);
        assertThat(outputs[6].name).isEqualTo(PINGONE_VERIFICATION_ISSUER_FILTER_KEY);
        assertThat(outputs[7].name).isEqualTo(PINGONE_VERIFIED_DATA_KEY);
        assertThat(outputs[8].name).isEqualTo(PINGONE_CREDENTIAL_DATA_KEY);
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

        when(client.createVerificationRequest(any(), any(), anyString(), anyString(), any(), any(), any())).thenReturn(null);

        // When
        Action result = node.process(getContext(sharedState, transientState, emptyList()));

        // Then
        assertThat(result.outcome).isEqualTo(ERROR_OUTCOME_ID);
    }

    @Test
    public void testErrorAccessTokenNull() throws Exception {
        given(pingOneWorkerService.getAccessTokenId(any(), any())).willReturn(null);
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
        given(pingOneWorkerService.getAccessTokenId(any(), any())).willReturn(null);
        given(pingOneWorkerService.getAccessTokenId(realm, worker)).willThrow(new PingOneWorkerException(""));

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