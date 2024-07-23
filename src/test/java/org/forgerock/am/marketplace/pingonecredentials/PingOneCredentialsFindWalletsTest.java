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
import static org.forgerock.am.marketplace.pingonecredentials.Constants.FAILURE_OUTCOME_ID;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PINGONE_USER_ID_KEY;
import static org.forgerock.json.JsonValue.array;
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
import java.util.List;
import java.util.Optional;

import org.forgerock.json.JsonValue;
import org.forgerock.oauth2.core.AccessToken;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.ExternalRequestContext;
import org.forgerock.openam.auth.node.api.TreeContext;
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
public class PingOneCredentialsFindWalletsTest {

    @RegisterExtension
    public LoggerExtension loggerExtension = new LoggerExtension(PingOneCredentialsFindWallets.class);

    @Mock
    PingOneCredentialsFindWallets.Config config;

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

    PingOneCredentialsFindWallets node;

    private static final String USER = "testUser";

    @BeforeEach
    public void setup() throws Exception {
        given(pingOneWorkerService.getWorker(any(), anyString())).willReturn(Optional.of(worker));
        given(pingOneWorkerService.getAccessToken(any(), any())).willReturn(accessToken);

        node = spy(new PingOneCredentialsFindWallets(config, realm, pingOneWorkerService,
                                                     client));
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
        "0,N/A,notFound",
        "1,ACTIVE,success",
        "2,INACTIVE,notFound",
        "2,ACTIVE,successMulti",
    })
    public void testReturnOutcomeFindWallets(String numWallets, String status, String expectedOutcome) throws Exception {
        // Given
        JsonValue sharedState = json(object(
            field(REALM, "/realm"),
            field(PINGONE_USER_ID_KEY, "some-user-id")));

        given(config.pingOneUserIdAttribute()).willReturn(PINGONE_USER_ID_KEY);

        String walletId = "some-wallet-id";

        JsonValue response = null;

        if(numWallets.equals("0")) {
           response = json(object());
        } else if(numWallets.equals("1")) {
            response = json(object(
                field("_embedded", object(
                      field("digitalWallets", array(
                          object(
                              field("id", walletId),
                              field("status", status)),
                          object(
                              field("id", walletId),
                              field("status", "INACTIVE"))
                                                   ))))));
        } else if(numWallets.equals("2")) {
            if(status.equals("INACTIVE")) {
                response = json(object(
                    field("_embedded", object(
                          field("digitalWallets", array(
                              object(
                                  field("id", walletId),
                                  field("status", "INACTIVE")),
                              object(
                                  field("id", walletId),
                                  field("status", "INACTIVE"))
                                                       ))))));
            } else {
                response = json(object(
                    field("_embedded", object(
                          field("digitalWallets", array(
                              object(
                                  field("id", walletId),
                                  field("status", "ACTIVE")),
                              object(
                                  field("id", walletId),
                                  field("status", "ACTIVE"))
                                                       ))))));
            }
        }

        when(client.findWalletRequest(any(), any(), anyString())).thenReturn(response);

        // When
        Action result = node.process(getContext(sharedState, json(object()), emptyList()));

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