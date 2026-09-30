/*
 * Copyright 2024 ForgeRock AS. All Rights Reserved
 *
 * Use of this code requires a commercial software license with ForgeRock AS.
 * or with one of its affiliates. All use shall be exclusively subject
 * to such license between the licensee and ForgeRock AS.
 */

package org.forgerock.am.marketplace.pingonecredentials;

import org.forgerock.json.JsonValue;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.openam.integration.pingone.api.PingOneWorkerService;
import org.forgerock.openam.test.extensions.LoggerExtension;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.forgerock.http.util.Json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.json.JsonValue.array;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class PingOneCredentialsServiceTest {

    @RegisterExtension
    public LoggerExtension loggerExtension = new LoggerExtension(PingOneCredentialsFindWallets.class);

    @Mock
    private Handler handler;

    @Mock
    PingOneWorkerService.Worker worker;

    @Mock
    private Promise<Response, NeverThrowsException> promise;

    PingOneCredentialsService service;

    String accessToken;

    @BeforeEach
    public void setup() throws Exception {
        this.accessToken = "some-access-token";

        given(worker.environmentId()).willReturn("some-environment-id");
        given(worker.apiUrl()).willReturn("https://api.pingone.com/v1");

        service = new PingOneCredentialsService(handler);
    }

    @Test
    public void testFindWalletRequest() throws Exception {
        // Given
        String pingOneUserId = "some-pingone-userid";

        JsonValue expected = json(object(
            field("_links", object(
                field("self", object(
                            field("href", "https://api.pingone.com/v1/environments/some-environment-id" +
                                          "/users/some-pingone-userid/digitalWallets"))))),
            field("_embedded", object(
                field("digitalWallets", array(
                    object(
                        field("_links", object(
                            field("self", object(
                                field("href", "https://api.pingone.com/v1/environments/some-environment-id" +
                                              "/users/some-pingone-userid/digitalWallets" +
                                              "/some-wallet-id1"))))),
                        field("id", "some-wallet-id1"),
                        field("createdAt", "2023-02-10T16:55:46.541Z"),
                        field("updatedAt", "2023-02-10T16:56:51.605Z"),
                        field("status", "PAIRING_REQUIRED"),
                        field("environment", object(
                            field("id", "abfba8f6-49eb-49f5-a5d9-80ad5c98f9f6"))),
                        field("user", object(
                            field("id", "49825b76-e1df-4cdc-b973-0c580f1cb049"))),
                        field("digitalWalletApplication", object(
                            field("id", "6815c8a6-bc0b-4105-8f37-50f6c35583d7")))),
                    object(
                        field("_links", object(
                            field("self", object(
                                field("href", "https://api.pingone.com/v1/environments/abfba8f6-49eb-49f5-a5d9-80ad5c98f9f6" +
                                              "/users/some-pingone-userid/digitalWallets" +
                                              "/some-wallet-id2"))))),
                    field("id", "some-wallet-id2"),
                    field("createdAt", "2023-02-10T14:46:24.669Z"),
                    field("updatedAt", "2023-02-13T15:24:32.401Z"),
                    field("status", "ACTIVE"),
                    field("environment", object(
                        field("id", "some-environment-id"))),
                    field("user", object(
                        field("id", "some-pingone-userid"))),
                    field("digitalWalletApplication", object(
                        field("id", "6815c8a6-bc0b-4105-8f37-50f6c35583d7")))
                    )))))));


        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        Response response = new Response(Status.OK);
        response.setEntity(expected);

        given(promise.getOrThrow()).willReturn(response);
        given(handler.handle(any(), captor.capture())).willReturn(promise);

        // When
        JsonValue result = service.findWalletRequest(accessToken, worker, pingOneUserId);

        // Then
        Request request = captor.getAllValues().get(0);
        assertThat(request.getUri().toString()).isEqualTo("https://api.pingone.com/v1/environments/" +
                                                          "some-environment-id/users/some-pingone-userid/digitalWallets");
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getHeaders().getFirst("Authorization")).isEqualTo("Bearer some-access-token");
        assertThat(result.get("_embedded").get("digitalWallets").get(1).get("id").asString()).isEqualTo("some-wallet-id2");
        assertThat(result.get("_embedded").get("digitalWallets").get(1).get("status").asString()).isEqualTo("ACTIVE");
        assertThat(result.get("_embedded").get("digitalWallets").get(1).get("digitalWalletApplication").get("id").asString())
            .isEqualTo("6815c8a6-bc0b-4105-8f37-50f6c35583d7");
    }

    @Test
    public void testCredentialIssueRequest() throws Exception {
        // Given
        String pingOneUserId = "some-pingone-userid";
        String credentialType = "some-credential-type";

        JsonValue attributes = json(object(
            field("firstName", "some-first-name"),
            field("lastName", "some-last-name")));

        JsonValue expected = json(object(
            field("_links", object(
                field("self", object(
                    field("href", "https://api.pingone.com/v1/environments/some-environment-id/users/" +
                                  "some-pingone-userid/credentials"))))),
            field("id", "some-credential"),
            field("createdAt", "2023-02-10T16:55:46.541Z"),
            field("updatedAt", "2023-02-10T16:56:51.605Z"),
            field("status", "PENDING"),
            field("title", "Card Developers"),
            field("environment", object(
                field("id", "some-environment-id"))),
            field("user", object(
                field("id", "some-pingone-userid"))),
            field("credentialType", object(
                field("id", "edc25883-a7f8-44e3-83eb-3c15a7b58de4")))));

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        Response response = new Response(Status.OK);
        response.setEntity(expected);

        given(promise.getOrThrow()).willReturn(response);
        given(handler.handle(any(), captor.capture())).willReturn(promise);

        // When
        JsonValue result = service.credentialIssueRequest(accessToken, worker, pingOneUserId, credentialType, attributes);

        // Then
        Request request = captor.getAllValues().get(0);
        assertThat(request.getUri().toString()).isEqualTo("https://api.pingone.com/v1/environments/" +
                                                          "some-environment-id/users/some-pingone-userid/credentials");
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getHeaders().getFirst("Authorization")).isEqualTo("Bearer some-access-token");
        assertThat(result.get("id").asString()).isEqualTo("some-credential");
        assertThat(result.get("status").asString()).isEqualTo("PENDING");
        assertThat(result.get("credentialType").get("id").asString())
            .isEqualTo("edc25883-a7f8-44e3-83eb-3c15a7b58de4");
    }

    @Test
    public void testCredentialIssueRequestSerializedBody() throws Exception {
        // Given
        String pingOneUserId = "some-pingone-userid";
        String credentialType = "some-credential-type";

        JsonValue attributes = json(object(
            field("firstName", "some-first-name"),
            field("lastName", "some-last-name")));

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        Response response = new Response(Status.CREATED);
        response.setEntity(json(object(field("id", "some-credential"), field("status", "PENDING"))));

        given(promise.getOrThrow()).willReturn(response);
        given(handler.handle(any(), captor.capture())).willReturn(promise);

        // When
        service.credentialIssueRequest(accessToken, worker, pingOneUserId, credentialType, attributes);

        // Then
        Request request = captor.getAllValues().get(0);
        assertThat(request.getUri().toString()).isEqualTo("https://api.pingone.com/v1/environments/" +
                                                          "some-environment-id/users/some-pingone-userid/credentials");
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getHeaders().getFirst("Authorization")).isEqualTo("Bearer some-access-token");

        JsonValue body = json(Json.readJson(request.getEntity().getString()));
        assertThat(body.get("credentialType").get("id").asString()).isEqualTo("some-credential-type");
        assertThat(body.get("data").get("firstName").asString()).isEqualTo("some-first-name");
        assertThat(body.get("data").get("lastName").asString()).isEqualTo("some-last-name");
    }

    @Test
    public void testCreateCredentialOfferRequestWithOptionalFields() throws Exception {
        // Given
        String pingOneUserId = "some-pingone-userid";

        List<String> credentials = List.of("some-credential-type-id-1", "some-credential-type-id-2");
        List<String> grantTypes = List.of(Constants.GRANT_TYPE_AUTHORIZATION_CODE,
                                          Constants.GRANT_TYPE_PRE_AUTHORIZED_CODE);

        JsonValue expected = json(object(
            field("_links", object(
                field("self", object(
                    field("href", "https://api.pingone.com/v1/environments/some-environment-id/users/" +
                                  "some-pingone-userid/openid4vciOffers/some-offer-id"))))),
            field("id", "some-offer-id"),
            field("status", "CREATED"),
            field("credentialOfferUrl", "https://auth.pingone.com/some-environment-id/credential-offers/some-offer-id"),
            field("createdAt", "2026-09-29T10:00:00.000Z"),
            field("expiresAt", "2026-09-29T10:30:00.000Z")));

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        Response response = new Response(Status.CREATED);
        response.setEntity(expected);

        given(promise.getOrThrow()).willReturn(response);
        given(handler.handle(any(), captor.capture())).willReturn(promise);

        // When
        JsonValue result = service.createCredentialOfferRequest(accessToken, worker, pingOneUserId,
                                                                credentials, grantTypes);

        // Then
        Request request = captor.getAllValues().get(0);
        assertThat(request.getUri().toString()).isEqualTo("https://api.pingone.com/v1/environments/" +
                                                          "some-environment-id/users/some-pingone-userid" +
                                                          "/openid4vciOffers");
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getHeaders().getFirst("Authorization")).isEqualTo("Bearer some-access-token");
        assertThat(request.getHeaders().getFirst("Content-Type")).isEqualTo("application/json; charset=UTF-8");

        JsonValue body = json(Json.readJson(request.getEntity().getString()));

        // Only the documented optional members are serialized; no unsupported
        // fields are invented.
        assertThat(body.keys()).containsExactlyInAnyOrder(Constants.OFFER_CREDENTIALS_KEY,
                                                          Constants.OFFER_GRANT_TYPES_KEY);
        assertThat(body.get(Constants.OFFER_CREDENTIALS_KEY).asList())
            .containsExactly("some-credential-type-id-1", "some-credential-type-id-2");
        assertThat(body.get(Constants.OFFER_GRANT_TYPES_KEY).asList())
            .containsExactly(Constants.GRANT_TYPE_AUTHORIZATION_CODE, Constants.GRANT_TYPE_PRE_AUTHORIZED_CODE);

        assertThat(result.get("id").asString()).isEqualTo("some-offer-id");
        assertThat(result.get("status").asString()).isEqualTo("CREATED");
        assertThat(result.get("credentialOfferUrl").asString())
            .isEqualTo("https://auth.pingone.com/some-environment-id/credential-offers/some-offer-id");
    }

    @Test
    public void testCreateCredentialOfferRequestOmitsUnconfiguredOptionalFields() throws Exception {
        // Given
        String pingOneUserId = "some-pingone-userid";

        JsonValue expected = json(object(
            field("id", "some-offer-id"),
            field("status", "CREATED"),
            field("credentialOfferUrl", "https://auth.pingone.com/some-environment-id/credential-offers/some-offer-id")));

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        Response response = new Response(Status.CREATED);
        response.setEntity(expected);

        given(promise.getOrThrow()).willReturn(response);
        given(handler.handle(any(), captor.capture())).willReturn(promise);

        // When
        JsonValue result = service.createCredentialOfferRequest(accessToken, worker, pingOneUserId,
                                                                Collections.emptyList(), Collections.emptyList());

        // Then
        Request request = captor.getAllValues().get(0);
        assertThat(request.getUri().toString()).isEqualTo("https://api.pingone.com/v1/environments/" +
                                                          "some-environment-id/users/some-pingone-userid" +
                                                          "/openid4vciOffers");
        assertThat(request.getMethod()).isEqualTo("POST");

        JsonValue body = json(Json.readJson(request.getEntity().getString()));
        assertThat(body.keys()).isEmpty();
        assertThat(body.isDefined(Constants.OFFER_CREDENTIALS_KEY)).isFalse();
        assertThat(body.isDefined(Constants.OFFER_GRANT_TYPES_KEY)).isFalse();

        assertThat(result.get("id").asString()).isEqualTo("some-offer-id");
        assertThat(result.get("status").asString()).isEqualTo("CREATED");
    }

    @Test
    public void testCreateCredentialOfferRequestFailureThrowsServiceException() throws Exception {
        // Given
        String pingOneUserId = "some-pingone-userid";

        Response response = new Response(Status.BAD_REQUEST);
        response.setEntity(json(object(field("code", "INVALID_DATA"))));

        given(promise.getOrThrow()).willReturn(response);
        given(handler.handle(any(), any())).willReturn(promise);

        // When / Then
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> service.createCredentialOfferRequest(accessToken, worker, pingOneUserId,
                                                           List.of("some-credential-type-id"), null))
            .isInstanceOf(PingOneCredentialsServiceException.class);
    }

    @Test
    public void testCredentialUpdateRequest() throws Exception {
        // Given
        String pingOneUserId = "some-pingone-userid";
        String credentialType = "some-credential-type";
        String credentialId = "some-credential";

        JsonValue attributes = json(object(
            field("firstName", "some-first-name"),
            field("lastName", "some-last-name")));

        JsonValue expected = json(object(
            field("_links", object(
                field("self", object(
                    field("href", "https://api.pingone.com/v1/environments/some-environment-id/users/" +
                                  "some-pingone-userid/credentials/some-credential"))))),
            field("id", "some-credential"),
            field("createdAt", "2023-02-10T16:55:46.541Z"),
            field("updatedAt", "2023-02-10T16:56:51.605Z"),
            field("status", "REVOKED"),
            field("title", "Wallet Developers"),
            field("environment", object(
                field("id", "some-environment-id"))),
            field("user", object(
                field("id", "some-pingone-userid"))),
            field("credentialType", object(
                field("id", "edc25883-a7f8-44e3-83eb-3c15a7b58de4")))));

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        Response response = new Response(Status.OK);
        response.setEntity(expected);

        given(promise.getOrThrow()).willReturn(response);
        given(handler.handle(any(), captor.capture())).willReturn(promise);

        // When
        JsonValue result = service.credentialUpdateRequest(accessToken, worker, pingOneUserId, credentialType,
                                                           credentialId, attributes);

        // Then
        Request request = captor.getAllValues().get(0);
        assertThat(request.getUri().toString()).isEqualTo("https://api.pingone.com/v1/environments/" +
                                                          "some-environment-id/users/some-pingone-userid/credentials/" +
                                                          "some-credential");
        assertThat(request.getMethod()).isEqualTo("PUT");
        assertThat(request.getHeaders().getFirst("Authorization")).isEqualTo("Bearer some-access-token");
        assertThat(result.get("id").asString()).isEqualTo("some-credential");
        assertThat(result.get("status").asString()).isEqualTo("REVOKED");
        assertThat(result.get("credentialType").get("id").asString())
            .isEqualTo("edc25883-a7f8-44e3-83eb-3c15a7b58de4");
    }

    @Test
    public void testCreateDigitalWalletRequest() throws Exception {
        // Given
        String pingOneUserId = "some-pingone-userid";
        String digitalWalletApplicationId = "some-wallet-app-id";

        List<String> notificationList = new ArrayList<String>();

        notificationList.add(Constants.PairingDeliveryMethod.SMS.name());
        notificationList.add(Constants.PairingDeliveryMethod.EMAIL.name());

        JsonValue expected = json(object(
            field("_links", object(
                field("self", object(
                    field("href", "https://api.pingone.com/v1/environments/some-environment-id/users/" +
                                  "some-pingone-userid/digitalWallets/some-wallet-id"))),
                field("appOpen", object(
                    field("href", "https://credentials.customer.com?u=https%3A%2F%2Fapi.pingone.com%2Fv1%2Fdistributedid" +
                                  "%2Frequests%2F4766467d-2dd8-4cba-a9b7-10ba09b97354"))),
                field("qrUrl", object(
                    field("href", "https://api.pingone.com/v1/distributedid/requests/4766467d-2dd8-4cba-a9b7-10ba09b97354")))
                )),
            field("id", "some-wallet-id"),
            field("createdAt", "2023-02-10T16:55:46.541Z"),
            field("updatedAt", "2023-02-10T16:56:51.605Z"),
            field("status", "PAIRING_REQUIRED"),
            field("pairingSession", object(
                field("id", "37d2b506-0ea7-4697-8884-c66ada3f4c48"),
                field("challenge", "48p16h6eouv734ob"),
                field("qrUrl", "https://api.pingone.com/v1/distributedid/requests/4766467d-2dd8-4cba-a9b7-10ba09b97354"),
                field("status", "INITIAL"),
                field("environment", object(
                    field("id", "some-environment-id"))),
                field("user", object(
                    field("id", "some-pingone-userid")))))));

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        Response response = new Response(Status.OK);
        response.setEntity(expected);

        given(promise.getOrThrow()).willReturn(response);
        given(handler.handle(any(), captor.capture())).willReturn(promise);

        // When
        JsonValue result = service.createDigitalWalletRequest(accessToken, worker, pingOneUserId,
                                                              digitalWalletApplicationId, notificationList);

        // Then
        Request request = captor.getAllValues().get(0);
        assertThat(request.getUri().toString()).isEqualTo("https://api.pingone.com/v1/environments/" +
                                                          "some-environment-id/users/some-pingone-userid/digitalWallets");
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getHeaders().getFirst("Authorization")).isEqualTo("Bearer some-access-token");
        assertThat(result.get("id").asString()).isEqualTo("some-wallet-id");
        assertThat(result.get("status").asString()).isEqualTo("PAIRING_REQUIRED");
        assertThat(result.get("pairingSession").get("status").asString())
            .isEqualTo("INITIAL");
    }

    @Test
    public void testReadDigitalWalletRequest() throws Exception {
        // Given
        String pingOneUserId = "some-pingone-userid";
        String digitalWalletId = "some-wallet-id";

        JsonValue expected = json(
                    object(
                        field("_links", object(
                            field("self", object(
                                field("href", "https://api.pingone.com/v1/environments/abfba8f6-49eb-49f5-a5d9-80ad5c98f9f6" +
                                              "/users/some-pingone-userid/digitalWallets" +
                                              "/some-wallet-id2"))))),
                        field("id", "some-wallet-id"),
                        field("createdAt", "2023-02-10T14:46:24.669Z"),
                        field("updatedAt", "2023-02-13T15:24:32.401Z"),
                        field("status", "ACTIVE"),
                        field("environment", object(
                            field("id", "some-environment-id"))),
                        field("user", object(
                            field("id", "some-pingone-userid"))),
                        field("digitalWalletApplication", object(
                            field("id", "6815c8a6-bc0b-4105-8f37-50f6c35583d7"))),
                        field("applicationInstance", object(
                            field("id", "2327b41e-996e-4228-9b8f-60279e91d14a")))
                          ));


        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        Response response = new Response(Status.OK);
        response.setEntity(expected);

        given(promise.getOrThrow()).willReturn(response);
        given(handler.handle(any(), captor.capture())).willReturn(promise);

        // When
        JsonValue result = service.readDigitalWallet(accessToken, worker, pingOneUserId, digitalWalletId);

        // Then
        Request request = captor.getAllValues().get(0);
        assertThat(request.getUri().toString()).isEqualTo("https://api.pingone.com/v1/environments/" +
                                                          "some-environment-id/users/some-pingone-userid/digitalWallets" +
                                                          "/some-wallet-id");
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getHeaders().getFirst("Authorization")).isEqualTo("Bearer some-access-token");
        assertThat(result.get("id").asString()).isEqualTo("some-wallet-id");
        assertThat(result.get("status").asString()).isEqualTo("ACTIVE");
        assertThat(result.get("digitalWalletApplication").get("id").asString())
            .isEqualTo("6815c8a6-bc0b-4105-8f37-50f6c35583d7");
    }

    @Test
    public void testCreateVerificationRequest() throws Exception {
        // Given
        String message = "some-message";
        String credentialType = "some-credential-type";

        Optional<String> digitalWalletApplicationId = Optional.of("some-digital-wallet-application-id");

        List<String> attributeKeys = new ArrayList<String>();

        attributeKeys.add("firstName");
        attributeKeys.add("lastName");

        JsonValue customCredentialsPayload = json(object());

        JsonValue expected = json(object(
            field("_links", object(
                field("self", object(
                    field("href", "https://api.pingone.com/v1/environments/some-environment-id/" +
                                  "presentationSessions/some-presentation-id"))),
                field("appOpen", object(
                    field("href", "https://shocard.pingone.com/appopen?u=https%3A%2F%2Fapi.pingone.com%2Fv1%2F" +
                                  "distributedid%2Frequests%2Fe4974bd1-0094-4586-8e43-28c4409d4bd7s"))),
                field("qrUrl", object(
                    field("href", "https://api.pingone.com/v1/distributedid/requests/e4974bd1-0094-4586-8e43-28c4409d4bd7")))
                                  )),
            field("id", "some-session-id"),
            field("status", "INITIAL")));

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        Response response = new Response(Status.OK);
        response.setEntity(expected);

        given(promise.getOrThrow()).willReturn(response);
        given(handler.handle(any(), captor.capture())).willReturn(promise);

        // When
        JsonValue result = service.createVerificationRequest(accessToken, worker, message, credentialType,
                                                             digitalWalletApplicationId,
                                                             attributeKeys, customCredentialsPayload);

        // Then
        Request request = captor.getAllValues().get(0);
        assertThat(request.getUri().toString()).isEqualTo("https://api.pingone.com/v1/environments/" +
                                                          "some-environment-id/presentationSessions");
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getHeaders().getFirst("Authorization")).isEqualTo("Bearer some-access-token");
        assertThat(result.get("id").asString()).isEqualTo("some-session-id");
        assertThat(result.get("status").asString()).isEqualTo("INITIAL");
    }

    @Test
    public void testCreateVerificationPushRequest() throws Exception {
        // Given
        String message = "some-message";
        String credentialType = "some-credential-type";
        String applicationInstanceId = "some-application-id";
        String digitalWalletApplicationId = "some-digital-wallet-application-id";

        List<String> attributeKeys = new ArrayList<String>();

        attributeKeys.add("firstName");
        attributeKeys.add("lastName");

        JsonValue customCredentialsPayload = json(object());

        JsonValue expected = json(object(
            field("_links", object(
                field("self", object(
                    field("href", "https://api.pingone.com/v1/environments/some-environment-id/" +
                                  "presentationSessions/some-presentation-id"))),
                field("appOpen", object(
                    field("href", "https://shocard.pingone.com/appopen?u=https%3A%2F%2Fapi.pingone.com%2Fv1%2F" +
                                  "distributedid%2Frequests%2Fe4974bd1-0094-4586-8e43-28c4409d4bd7s"))),
                field("qrUrl", object(
                    field("href", "https://api.pingone.com/v1/distributedid/requests/e4974bd1-0094-4586-8e43-28c4409d4bd7")))
                                  )),
            field("id", "some-session-id"),
            field("status", "INITIAL")));

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        Response response = new Response(Status.OK);
        response.setEntity(expected);

        given(promise.getOrThrow()).willReturn(response);
        given(handler.handle(any(), captor.capture())).willReturn(promise);

        // When
        JsonValue result = service.createVerificationRequestPush(accessToken, worker, message, credentialType,
                                                                 attributeKeys, applicationInstanceId,
                                                                 digitalWalletApplicationId, customCredentialsPayload);

        // Then
        Request request = captor.getAllValues().get(0);
        assertThat(request.getUri().toString()).isEqualTo("https://api.pingone.com/v1/environments/" +
                                                          "some-environment-id/presentationSessions");
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getHeaders().getFirst("Authorization")).isEqualTo("Bearer some-access-token");
        assertThat(result.get("id").asString()).isEqualTo("some-session-id");
        assertThat(result.get("status").asString()).isEqualTo("INITIAL");
    }

    @Test
    public void testCreateVerificationRequestSerializedBodyRemainsNative() throws Exception {
        // Given
        String message = "some-message";
        String credentialType = "some-credential-type";

        Optional<String> digitalWalletApplicationId = Optional.of("some-digital-wallet-application-id");

        List<String> attributeKeys = List.of("firstName", "lastName");

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        Response response = new Response(Status.OK);
        response.setEntity(json(object(
            field("id", "some-session-id"),
            field("status", "INITIAL"))));

        given(promise.getOrThrow()).willReturn(response);
        given(handler.handle(any(), captor.capture())).willReturn(promise);

        // When
        service.createVerificationRequest(accessToken, worker, message, credentialType,
                                          digitalWalletApplicationId, attributeKeys, null);

        // Then
        Request request = captor.getAllValues().get(0);

        JsonValue body = json(Json.readJson(request.getEntity().getString()));
        assertThat(body.get("message").asString()).isEqualTo("some-message");
        assertThat(body.get("protocol").asString()).isEqualTo("NATIVE");
        assertThat(body.get("digitalWalletApplication").get("id").asString())
            .isEqualTo("some-digital-wallet-application-id");

        assertThat(body.get("requestedCredentials").size()).isEqualTo(1);
        assertThat(body.get("requestedCredentials").get(0).get("type").asString()).isEqualTo("some-credential-type");
        assertThat(body.get("requestedCredentials").get(0).get("keys").asList())
            .containsExactly("firstName", "lastName");
    }

    @Test
    public void testCreateVerificationRequestSerializedBodyOmitsUnconfiguredWalletApplication() throws Exception {
        // Given: the optional wallet application is the documented omission case;
        // no digitalWalletApplication member is serialized when unconfigured.
        String message = "some-message";
        String credentialType = "some-credential-type";

        List<String> attributeKeys = List.of("firstName", "lastName");

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        Response response = new Response(Status.OK);
        response.setEntity(json(object(
            field("id", "some-session-id"),
            field("status", "INITIAL"))));

        given(promise.getOrThrow()).willReturn(response);
        given(handler.handle(any(), captor.capture())).willReturn(promise);

        // When
        service.createVerificationRequest(accessToken, worker, message, credentialType,
                                          Optional.empty(), attributeKeys, null);

        // Then
        Request request = captor.getAllValues().get(0);

        JsonValue body = json(Json.readJson(request.getEntity().getString()));
        assertThat(body.keys()).containsExactlyInAnyOrder("message", "protocol", "requestedCredentials");
        assertThat(body.isDefined("digitalWalletApplication")).isFalse();
        assertThat(body.get("protocol").asString()).isEqualTo("NATIVE");
        assertThat(body.get("requestedCredentials").size()).isEqualTo(1);
        assertThat(body.get("requestedCredentials").get(0).get("type").asString()).isEqualTo("some-credential-type");
        assertThat(body.get("requestedCredentials").get(0).get("keys").asList())
            .containsExactly("firstName", "lastName");
    }

    @Test
    public void testCreateVerificationRequestOid4vpWithOptionalFields() throws Exception {
        // Given
        String message = "some-message";
        String credentialType = "some-credential-type";
        String protocolVersion = "JWT-VC-Presentation-Profile-v0.0.1";
        String didMethod = "JWK";
        List<String> issuerFilterDids = List.of("did:web:some-issuer.example", "did:ion:some-long-form-did");
        Integer timeoutSeconds = 30;

        JsonValue expected = json(object(
            field("_links", object(
                field("self", object(
                    field("href", "https://api.pingone.com/v1/environments/some-environment-id/" +
                                  "presentationSessions/some-presentation-id"))),
                field("qr", object(
                    field("href", "data:image/png;base64,some-qr-code"))),
                field("appOpenUrl", object(
                    field("href", "openid-vc://?request_uri=https%3A%2F%2Fapi.pingone.com%2Fv1%2F" +
                                  "presentationSessions%2Fsome-presentation-id"))))),
            field("id", "some-session-id"),
            field("createdAt", "2026-09-29T10:00:00.000Z"),
            field("expiresAt", "2026-09-29T10:00:30.000Z"),
            field("status", "INITIAL")));

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        Response response = new Response(Status.OK);
        response.setEntity(expected);

        given(promise.getOrThrow()).willReturn(response);
        given(handler.handle(any(), captor.capture())).willReturn(promise);

        // When
        JsonValue result = service.createVerificationRequestOid4vp(accessToken, worker, message, credentialType,
                                                                   protocolVersion, didMethod, issuerFilterDids, List.of("some-environment-id"),
                                                                   timeoutSeconds);

        // Then
        Request request = captor.getAllValues().get(0);
        assertThat(request.getUri().toString()).isEqualTo("https://api.pingone.com/v1/environments/" +
                                                          "some-environment-id/presentationSessions");
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getHeaders().getFirst("Authorization")).isEqualTo("Bearer some-access-token");
        assertThat(request.getHeaders().getFirst("Content-Type")).isEqualTo("application/json; charset=UTF-8");

        JsonValue body = json(Json.readJson(request.getEntity().getString()));

        // Only the documented required and configured-optional members are
        // serialized; no unsupported fields are invented.
        assertThat(body.keys()).containsExactlyInAnyOrder("message", "protocol", "protocolVersion",
                                                          "didMethod", "issuerFilter", "timeoutSeconds",
                                                          "requestedCredentials");
        assertThat(body.get("message").asString()).isEqualTo("some-message");
        assertThat(body.get("protocol").asString()).isEqualTo("OPENID4VP");
        assertThat(body.get("protocolVersion").asString()).isEqualTo("JWT-VC-Presentation-Profile-v0.0.1");
        assertThat(body.get("didMethod").asString()).isEqualTo("JWK");
        assertThat(body.get("issuerFilter").get("dids").asList())
            .containsExactly("did:web:some-issuer.example", "did:ion:some-long-form-did");
        assertThat(body.get("timeoutSeconds").asInteger()).isEqualTo(30);

        // Exactly one requested credential type, and no Native "keys" semantics.
        assertThat(body.get("requestedCredentials").size()).isEqualTo(1);
        assertThat(body.get("requestedCredentials").get(0).get("type").asString()).isEqualTo("some-credential-type");
        assertThat(body.get("requestedCredentials").get(0).isDefined("keys")).isFalse();
        assertThat(body.get("requestedCredentials").get(0).keys()).doesNotContain("keys");
        assertThat(body.keys()).doesNotContain("keys");

        assertThat(result.get("id").asString()).isEqualTo("some-session-id");
        assertThat(result.get("status").asString()).isEqualTo("INITIAL");
    }

    @Test
    public void testCreateVerificationRequestOid4vpOmitsUnconfiguredOptionalFields() throws Exception {
        // Given
        String message = "some-message";
        String credentialType = "some-credential-type";

        JsonValue expected = json(object(
            field("id", "some-session-id"),
            field("status", "INITIAL")));

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        Response response = new Response(Status.OK);
        response.setEntity(expected);

        given(promise.getOrThrow()).willReturn(response);
        given(handler.handle(any(), captor.capture())).willReturn(promise);

        // When
        service.createVerificationRequestOid4vp(accessToken, worker, message, credentialType,
                                                null, null, Collections.emptyList(), Collections.emptyList(), null);

        // Then
        Request request = captor.getAllValues().get(0);
        assertThat(request.getUri().toString()).isEqualTo("https://api.pingone.com/v1/environments/" +
                                                          "some-environment-id/presentationSessions");
        assertThat(request.getMethod()).isEqualTo("POST");

        JsonValue body = json(Json.readJson(request.getEntity().getString()));
        assertThat(body.keys()).containsExactlyInAnyOrder("message", "protocol", "requestedCredentials");
        assertThat(body.isDefined("protocolVersion")).isFalse();
        assertThat(body.isDefined("didMethod")).isFalse();
        assertThat(body.isDefined("issuerFilter")).isFalse();
        assertThat(body.isDefined("timeoutSeconds")).isFalse();

        assertThat(body.get("protocol").asString()).isEqualTo("OPENID4VP");
        assertThat(body.get("requestedCredentials").size()).isEqualTo(1);
        assertThat(body.get("requestedCredentials").get(0).get("type").asString()).isEqualTo("some-credential-type");
    }

    @Test
    public void testReadCredentialOfferRequest() throws Exception {
        // Given
        JsonValue expected = json(object(
            field("id", "some-offer-id"),
            field("status", "ISSUING"),
            field("credentialOfferUrl", "https://auth.pingone.com/some-environment-id/credentialOffer/some-offer-id"),
            field("expiresAt", "2026-09-29T10:30:00.000Z")));

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        Response response = new Response(Status.OK);
        response.setEntity(expected);

        given(promise.getOrThrow()).willReturn(response);
        given(handler.handle(any(), captor.capture())).willReturn(promise);

        // When
        JsonValue result = service.readCredentialOfferRequest(accessToken, worker, "some-pingone-userid",
                                                              "some-offer-id");

        // Then
        Request request = captor.getAllValues().get(0);
        assertThat(request.getUri().toString()).isEqualTo("https://api.pingone.com/v1/environments/" +
                                                          "some-environment-id/users/some-pingone-userid/" +
                                                          "openid4vciOffers/some-offer-id");
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getHeaders().getFirst("Authorization")).isEqualTo("Bearer some-access-token");
        assertThat(request.getEntity().getString()).isEmpty();

        assertThat(result.get("id").asString()).isEqualTo("some-offer-id");
        assertThat(result.get("status").asString()).isEqualTo("ISSUING");
    }

    @Test
    public void testReadCredentialOfferRequestFailureThrowsServiceException() throws Exception {
        // Given
        Response response = new Response(Status.NOT_FOUND);
        response.setEntity(json(object(field("code", "NOT_FOUND"))));

        given(promise.getOrThrow()).willReturn(response);
        given(handler.handle(any(), any())).willReturn(promise);

        // When / Then
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> service.readCredentialOfferRequest(accessToken, worker, "some-pingone-userid", "some-offer-id"))
            .isInstanceOf(PingOneCredentialsServiceException.class);
    }

    @Test
    public void testCreateVerificationRequestOid4vpSerializesIssuerFilterEnvironmentIdsOnly() throws Exception {
        // Given
        JsonValue expected = json(object(
            field("id", "some-session-id"),
            field("status", "INITIAL")));

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        Response response = new Response(Status.OK);
        response.setEntity(expected);

        given(promise.getOrThrow()).willReturn(response);
        given(handler.handle(any(), captor.capture())).willReturn(promise);

        // When
        service.createVerificationRequestOid4vp(accessToken, worker, "some-message", "some-credential-type",
                                                null, null, Collections.emptyList(),
                                                List.of("some-environment-id-1", "some-environment-id-2"), null);

        // Then
        JsonValue body = json(Json.readJson(captor.getAllValues().get(0).getEntity().getString()));
        assertThat(body.keys()).containsExactlyInAnyOrder("message", "protocol", "issuerFilter",
                                                          "requestedCredentials");
        assertThat(body.get("issuerFilter").keys()).containsExactly("environmentIds");
        assertThat(body.get("issuerFilter").get("environmentIds").asList())
            .containsExactly("some-environment-id-1", "some-environment-id-2");
    }

    @Test
    public void testCreateVerificationRequestOid4vpSerializesIssuerFilterDidsAndEnvironmentIds() throws Exception {
        // Given
        JsonValue expected = json(object(
            field("id", "some-session-id"),
            field("status", "INITIAL")));

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        Response response = new Response(Status.OK);
        response.setEntity(expected);

        given(promise.getOrThrow()).willReturn(response);
        given(handler.handle(any(), captor.capture())).willReturn(promise);

        // When
        service.createVerificationRequestOid4vp(accessToken, worker, "some-message", "some-credential-type",
                                                null, null, List.of("did:web:some-issuer.example"),
                                                List.of("some-environment-id"), null);

        // Then
        JsonValue body = json(Json.readJson(captor.getAllValues().get(0).getEntity().getString()));
        assertThat(body.get("issuerFilter").keys()).containsExactlyInAnyOrder("dids", "environmentIds");
        assertThat(body.get("issuerFilter").get("dids").asList()).containsExactly("did:web:some-issuer.example");
        assertThat(body.get("issuerFilter").get("environmentIds").asList()).containsExactly("some-environment-id");
    }

    @Test
    public void testCreateVerificationRequestOid4vpFailureThrowsServiceException() throws Exception {
        // Given
        Response response = new Response(Status.BAD_REQUEST);
        response.setEntity(json(object(field("code", "INVALID_DATA"))));

        given(promise.getOrThrow()).willReturn(response);
        given(handler.handle(any(), any())).willReturn(promise);

        // When / Then
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> service.createVerificationRequestOid4vp(accessToken, worker, "some-message",
                                                              "some-credential-type", null, null,
                                                              Collections.emptyList(), Collections.emptyList(), null))
            .isInstanceOf(PingOneCredentialsServiceException.class);
    }

    @Test
    public void testReadVerificationSession() throws Exception {
        // Given
        String sessionId = "some-session-id";

        JsonValue expected = json(
            object(
                field("_links", object(
                    field("self", object(
                        field("href", "https://api.pingone.com/v1/environments/abfba8f6-49eb-49f5-a5d9-80ad5c98f9f6/" +
                                      "presentationSessions/a8104f80-1954-41b6-b325-23992d0c66d6/sessionData"))),
                    field("appOpenUrl", object(
                        field("href", "https://shocard.pingone.com/appopen?u=https%3A%2F%2Fapi.pingone.com%2Fv1%2F" +
                                        "distributedid%2Frequests%2F6a38c17f-38ab-44af-bfcc-20258e3ed142"))))),
                field("id", "some-session-id"),
                field("status", "VERIFICATION_SUCCESSFUL"),
                field("applicationInstance", object(
                    field("id", "some-application-instance-id"))),
                field("verifiedData", array(object(
                    field("type", array("NonVerifiedEmployee")),
                    field("data", object(
                        field("mail", "example@email.com"),
                        field("surname", "Example Surname"),
                        field("givenName", "Example Given Name"))))))));

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        Response response = new Response(Status.OK);
        response.setEntity(expected);

        given(promise.getOrThrow()).willReturn(response);
        given(handler.handle(any(), captor.capture())).willReturn(promise);

        // When
        JsonValue result = service.readVerificationSession(accessToken, worker, sessionId);

        // Then
        Request request = captor.getAllValues().get(0);
        assertThat(request.getUri().toString()).isEqualTo("https://api.pingone.com/v1/environments/" +
                                                          "some-environment-id/presentationSessions/some-session-id" +
                                                          "/sessionData");
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getHeaders().getFirst("Authorization")).isEqualTo("Bearer some-access-token");
        assertThat(result.get("id").asString()).isEqualTo("some-session-id");
        assertThat(result.get("status").asString()).isEqualTo("VERIFICATION_SUCCESSFUL");
        assertThat(result.get("verifiedData").get(0).get("data").get("mail").asString())
            .isEqualTo("example@email.com");
    }

    @Test
    public void testReadVerificationCredentialData() throws Exception {
        // Given
        String sessionId = "some-session-id";

        JsonValue expected = json(
            object(
                field("_links", object(
                    field("self", object(
                        field("href", "https://api.pingone.com/v1/environments/abfba8f6-49eb-49f5-a5d9-80ad5c98f9f6/" +
                                      "presentationSessions/a8104f80-1954-41b6-b325-23992d0c66d6/credentialData"))))),
                field("id", "some-session-id"),
                field("status", "VERIFICATION_SUCCESSFUL"),
                field("credentialData", array(object(
                    field("type", "VerifiedEmployee"),
                    field("data", array(
                        object(
                            field("key", "displayName"),
                            field("value", "John Doe")),
                        object(
                            field("key", "mail"),
                            field("value", "johndoe@example.com")))),
                    field("issuerId", "9536b3bf-556c-4ed5-a357-5da900ce17d1"),
                    field("issuerName", "Issuer Name"))))));

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        Response response = new Response(Status.OK);
        response.setEntity(expected);

        given(promise.getOrThrow()).willReturn(response);
        given(handler.handle(any(), captor.capture())).willReturn(promise);

        // When
        JsonValue result = service.readVerificationCredentialData(accessToken, worker, sessionId);

        // Then
        Request request = captor.getAllValues().get(0);
        assertThat(request.getUri().toString()).isEqualTo("https://api.pingone.com/v1/environments/" +
                                                          "some-environment-id/presentationSessions/some-session-id" +
                                                          "/credentialData");
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getHeaders().getFirst("Authorization")).isEqualTo("Bearer some-access-token");
        assertThat(result.get("id").asString()).isEqualTo("some-session-id");
        assertThat(result.get("status").asString()).isEqualTo("VERIFICATION_SUCCESSFUL");
        assertThat(result.get("credentialData").get(0).get("type").asString()).isEqualTo("VerifiedEmployee");
        assertThat(result.get("credentialData").get(0).get("data").get(0).get("key").asString())
            .isEqualTo("displayName");
        assertThat(result.get("credentialData").get(0).get("data").get(0).get("value").asString())
            .isEqualTo("John Doe");
        assertThat(result.get("credentialData").get(0).get("data").get(1).get("value").asString())
            .isEqualTo("johndoe@example.com");
        assertThat(result.get("credentialData").get(0).get("issuerName").asString()).isEqualTo("Issuer Name");
    }

    @Test
    public void testReadVerificationCredentialDataFailureThrowsServiceException() throws Exception {
        // Given
        Response response = new Response(Status.NOT_FOUND);
        response.setEntity(json(object(field("code", "NOT_FOUND"))));

        given(promise.getOrThrow()).willReturn(response);
        given(handler.handle(any(), any())).willReturn(promise);

        // When / Then
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> service.readVerificationCredentialData(accessToken, worker, "some-session-id"))
            .isInstanceOf(PingOneCredentialsServiceException.class);
    }

    @Test
    public void testDeleteWalletRequest() throws Exception {
        // Given
        String pingOneUserId = "some-pingone-userid";
        String digitalWalletId = "some-wallet-id";

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        Response response = new Response(Status.NO_CONTENT);

        given(promise.getOrThrow()).willReturn(response);
        given(handler.handle(any(), captor.capture())).willReturn(promise);

        // When
        boolean result = service.deleteWalletRequest(accessToken, worker, pingOneUserId, digitalWalletId);

        // Then
        Request request = captor.getAllValues().get(0);
        assertThat(request.getUri().toString()).isEqualTo("https://api.pingone.com/v1/environments/" +
                                                          "some-environment-id/users/some-pingone-userid/digitalWallets" +
                                                          "/some-wallet-id");
        assertThat(request.getMethod()).isEqualTo("DELETE");
        assertThat(request.getHeaders().getFirst("Authorization")).isEqualTo("Bearer some-access-token");
        assertThat(result);
    }

    @Test
    public void testRevokeCredentialRequest() throws Exception {
        // Given
        String pingOneUserId = "some-pingone-userid";
        String credentialId = "some-credential-id";

        JsonValue expected = json(
            object(
                field("_links", object(
                    field("self", object(
                        field("href", "https://api.pingone.com/v1/environments/abfba8f6-49eb-49f5-a5d9-80ad5c98f9f6/" +
                                      "presentationSessions/a8104f80-1954-41b6-b325-23992d0c66d6/sessionData"))))),
                field("id", "some-credential-id"),
                field("status", "REVOKED"),
                field("createdAt", "2023-02-10T16:55:46.541Z"),
                field("updatedAt", "2023-02-10T16:56:51.605Z"),
                field("status", "REVOKED"),
                field("title", "Wallet Developers"),
                field("environment", object(
                    field("id", "some-environment-id"))),
                field("user", object(
                    field("id", "some-pingone-userid"))),
                field("credentialType", object(
                    field("id", "edc25883-a7f8-44e3-83eb-3c15a7b58de4")))));

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        Response response = new Response(Status.OK);
        response.setEntity(expected);

        given(promise.getOrThrow()).willReturn(response);
        given(handler.handle(any(), captor.capture())).willReturn(promise);

        // When
        Constants.RevokeResult result = service.revokeCredentialRequest(accessToken, worker, pingOneUserId, credentialId);

        // Then
        Request request = captor.getAllValues().get(0);
        assertThat(request.getUri().toString()).isEqualTo("https://api.pingone.com/v1/environments/" +
                                                          "some-environment-id/users/some-pingone-userid/credentials" +
                                                          "/some-credential-id");
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getHeaders().getFirst("Authorization")).isEqualTo("Bearer some-access-token");
        assertThat(result.equals(Constants.RevokeResult.REVOKED));

    }
}