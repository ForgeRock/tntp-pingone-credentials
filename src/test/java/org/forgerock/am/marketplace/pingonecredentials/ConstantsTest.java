/*
 * Copyright 2024 ForgeRock AS. All Rights Reserved
 *
 * Use of this code requires a commercial software license with ForgeRock AS.
 * or with one of its affiliates. All use shall be exclusively subject
 * to such license between the licensee and ForgeRock AS.
 */

package org.forgerock.am.marketplace.pingonecredentials;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Tests the constants and configuration surface added for the documented
 * OpenID4VCI offer and OpenID4VP verification behavior, and proves the
 * existing NATIVE identifiers, defaults, and property values remain unchanged.
 */
public class ConstantsTest {

    // ------------------------------------------------------------------
    // Existing NATIVE identifiers, defaults, and behavior remain unchanged
    // ------------------------------------------------------------------

    @Test
    public void nativeSharedStateKeysAreUnchanged() {
        assertThat(Constants.PINGONE_USER_ID_KEY).isEqualTo("pingOneUserId");
        assertThat(Constants.PINGONE_PAIRING_DELIVERY_METHOD_KEY).isEqualTo("pingOneWalletPairingDeliveryMethod");
        assertThat(Constants.PINGONE_PAIRING_TIMEOUT_KEY).isEqualTo("pingOnePairingTimeout");
        assertThat(Constants.PINGONE_APPOPEN_URL_KEY).isEqualTo("pingOneAppOpenURL");
        assertThat(Constants.PINGONE_PAIRING_WALLET_ID_KEY).isEqualTo("pingOnePairingWalletId");

        assertThat(Constants.PINGONE_WALLET_ID_KEY).isEqualTo("pingOneWalletId");
        assertThat(Constants.PINGONE_WALLET_DATA_KEY).isEqualTo("pingOneWalletData");
        assertThat(Constants.PINGONE_ACTIVE_WALLETS_DATA_KEY).isEqualTo("pingOneActiveWallets");

        assertThat(Constants.PINGONE_APPLICATION_INSTANCE_ID_KEY).isEqualTo("pingOneApplicationInstanceId");

        assertThat(Constants.PINGONE_VERIFICATION_DELIVERY_METHOD_KEY).isEqualTo("pingOneVerificationDeliveryMethod");
        assertThat(Constants.PINGONE_VERIFICATION_SESSION_KEY).isEqualTo("pingOneVerificationSessionId");
        assertThat(Constants.PINGONE_VERIFICATION_TIMEOUT_KEY).isEqualTo("pingOneVerificationTimeout");
        assertThat(Constants.PINGONE_CREDENTIAL_VERIFICATION_KEY).isEqualTo("pingOneCredentialVerification");

        assertThat(Constants.PINGONE_CREDENTIAL_UPDATE_KEY).isEqualTo("pingOneCredentialUpdate");
        assertThat(Constants.PINGONE_CREDENTIAL_ID_KEY).isEqualTo("pingOneCredentialId");
        assertThat(Constants.PINGONE_CREDENTIAL_TYPE_KEY).isEqualTo("pingOneCredentialType");
    }

    @Test
    public void nativePathAndResponseIdentifiersAreUnchanged() {
        assertThat(Constants.ENVIRONMENTS_PATH).isEqualTo("/environments/");
        assertThat(Constants.USERS_PATH).isEqualTo("/users/");
        assertThat(Constants.DIGITAL_WALLETS_PATH).isEqualTo("/digitalWallets");
        assertThat(Constants.CREDENTIALS_PATH).isEqualTo("/credentials");
        assertThat(Constants.PRESENTATION_SESSIONS_PATH).isEqualTo("/presentationSessions");
        assertThat(Constants.SESSION_DATA_PATH).isEqualTo("/sessionData");

        assertThat(Constants.RESPONSE_ID).isEqualTo("id");
        assertThat(Constants.RESPONSE_STATUS).isEqualTo("status");
        assertThat(Constants.RESPONSE_LINKS).isEqualTo("_links");
        assertThat(Constants.RESPONSE_APPOPEN).isEqualTo("appOpen");
        assertThat(Constants.RESPONSE_APPOPENURL).isEqualTo("appOpenUrl");
        assertThat(Constants.RESPONSE_HREF).isEqualTo("href");
        assertThat(Constants.RESPONSE_EMBEDDED).isEqualTo("_embedded");
        assertThat(Constants.RESPONSE_DIGITALWALLETS).isEqualTo("digitalWallets");
        assertThat(Constants.RESPONSE_APPLICATION_INSTANCE).isEqualTo("applicationInstance");
    }

    @Test
    public void nativeStatusValuesAreUnchanged() {
        assertThat(Constants.ACTIVE).isEqualTo("ACTIVE");
        assertThat(Constants.PAIRING_REQUIRED).isEqualTo("PAIRING_REQUIRED");
        assertThat(Constants.EXPIRED).isEqualTo("EXPIRED");
        assertThat(Constants.VERIFICATION_SUCCESSFUL).isEqualTo("VERIFICATION_SUCCESSFUL");
        assertThat(Constants.REVOKED).isEqualTo("REVOKED");
        assertThat(Constants.INITIAL).isEqualTo("INITIAL");
    }

    @Test
    public void oid4vpStatusesMatchDocumentedContract() {
        assertThat(Constants.VERIFICATION_FAILED).isEqualTo("VERIFICATION_FAILED");
        assertThat(Constants.WAITING).isEqualTo("WAITING");
        // The in-progress statuses and the terminal OID4VP statuses are distinct values.
        assertThat(Constants.WAITING).isNotEqualTo(Constants.INITIAL);
        assertThat(Constants.VERIFICATION_FAILED).isNotEqualTo(Constants.VERIFICATION_SUCCESSFUL);
        assertThat(Constants.EXPIRED).isNotEqualTo(Constants.VERIFICATION_FAILED);
    }

    @Test
    public void nativeRequestMembersAndOutcomesAreUnchanged() {
        assertThat(Constants.OBJECT_ATTRIBUTES).isEqualTo("objectAttributes");
        assertThat(Constants.REQUESTED_CREDENTIALS).isEqualTo("requestedCredentials");

        assertThat(Constants.SUCCESS_OUTCOME_ID).isEqualTo("success");
        assertThat(Constants.SUCCESS_MULTI_OUTCOME_ID).isEqualTo("successMulti");
        assertThat(Constants.ERROR_OUTCOME_ID).isEqualTo("error");
        assertThat(Constants.TIMEOUT_OUTCOME_ID).isEqualTo("timeout");
        assertThat(Constants.NOT_FOUND_OUTCOME_ID).isEqualTo("notFound");
    }

    @Test
    public void nativeDeliveryMethodEnumsAreUnchanged() {
        assertThat(Arrays.stream(Constants.PairingDeliveryMethod.values()).map(Enum::name))
            .containsExactly("QRCODE", "EMAIL", "SMS");
        assertThat(Constants.PairingDeliveryMethod.fromIndex(0)).isEqualTo(Constants.PairingDeliveryMethod.QRCODE);

        assertThat(Arrays.stream(Constants.VerificationDeliveryMethod.values()).map(Enum::name))
            .containsExactly("QRCODE", "PUSH");
        assertThat(Constants.VerificationDeliveryMethod.fromIndex(0)).isEqualTo(Constants.VerificationDeliveryMethod.QRCODE);
        assertThat(Constants.VerificationDeliveryMethod.fromIndex(1)).isEqualTo(Constants.VerificationDeliveryMethod.PUSH);

        assertThat(Arrays.stream(Constants.RevokeResult.values()).map(Enum::name))
            .containsExactly("REVOKED", "NOT_FOUND");
    }

    // ------------------------------------------------------------------
    // Documented OID4VCI offer and OID4VP verification identifiers
    // ------------------------------------------------------------------

    @Test
    public void protocolPathConstantsMatchDocumentedEndpoints() {
        assertThat(Constants.OPENID4VCI_OFFERS_PATH).isEqualTo("/openid4vciOffers");
        assertThat(Constants.PRESENTATION_SESSIONS_PATH).isEqualTo("/presentationSessions");
    }

    @Test
    public void protocolIdentifiersMatchDocumentedValues() {
        assertThat(Constants.PROTOCOL_NATIVE).isEqualTo("NATIVE");
        assertThat(Constants.PROTOCOL_OPENID4VP).isEqualTo("OPENID4VP");
    }

    @Test
    public void openid4vciOfferRequestMembersMatchDocumentedContract() {
        assertThat(Constants.OFFER_CREDENTIALS_KEY).isEqualTo("credentials");
        assertThat(Constants.OFFER_GRANT_TYPES_KEY).isEqualTo("grantTypes");
        assertThat(Constants.GRANT_TYPE_AUTHORIZATION_CODE).isEqualTo("authorization_code");
        assertThat(Constants.GRANT_TYPE_PRE_AUTHORIZED_CODE)
            .isEqualTo("urn:ietf:params:oauth:grant-type:pre-authorized_code");
    }

    @Test
    public void openid4vciOfferResponseMembersMatchDocumentedContract() {
        assertThat(Constants.RESPONSE_CREDENTIAL_OFFER_URL).isEqualTo("credentialOfferUrl");
        assertThat(Constants.RESPONSE_OPENID_CREDENTIAL_OFFER_REL).isEqualTo("openid-credential-offer");
        assertThat(Constants.RESPONSE_QRCODE).isEqualTo("qrCode");
    }

    @Test
    public void openid4vpRequestMembersMatchDocumentedContract() {
        assertThat(Constants.OID4VP_PROTOCOL_VERSION_KEY).isEqualTo("protocolVersion");
        assertThat(Constants.OID4VP_DID_METHOD_KEY).isEqualTo("didMethod");
        assertThat(Constants.OID4VP_ISSUER_FILTER_KEY).isEqualTo("issuerFilter");
        assertThat(Constants.OID4VP_ISSUER_FILTER_DIDS_KEY).isEqualTo("dids");
        assertThat(Constants.OID4VP_TIMEOUT_SECONDS_KEY).isEqualTo("timeoutSeconds");
    }

    @Test
    public void oid4vpResponseMembersAndStatusesMatchDocumentedContract() {
        assertThat(Constants.RESPONSE_QR).isEqualTo("qr");
        assertThat(Constants.RESPONSE_VERIFIED_DATA).isEqualTo("verifiedData");

        assertThat(Constants.VERIFICATION_SUCCESSFUL).isEqualTo("VERIFICATION_SUCCESSFUL");
        assertThat(Constants.VERIFICATION_FAILED).isEqualTo("VERIFICATION_FAILED");
        assertThat(Constants.EXPIRED).isEqualTo("EXPIRED");
    }

    @Test
    public void protocolEnumsPreserveNativeAsFirstValue() {
        // Index 0 keeps the pre-existing default behavior for enum-backed config attributes.
        assertThat(Constants.VerificationProtocol.NATIVE.ordinal()).isZero();
        assertThat(Arrays.stream(Constants.VerificationProtocol.values()).map(Enum::name))
            .containsExactly("NATIVE", "OPENID4VP");
        assertThat(Constants.VerificationProtocol.fromIndex(0)).isEqualTo(Constants.VerificationProtocol.NATIVE);
        assertThat(Constants.VerificationProtocol.fromIndex(1)).isEqualTo(Constants.VerificationProtocol.OPENID4VP);

        assertThat(Constants.CredentialIssuanceMode.NATIVE.ordinal()).isZero();
        assertThat(Arrays.stream(Constants.CredentialIssuanceMode.values()).map(Enum::name))
            .containsExactly("NATIVE", "OID4VCI");
        assertThat(Constants.CredentialIssuanceMode.fromIndex(0)).isEqualTo(Constants.CredentialIssuanceMode.NATIVE);
        assertThat(Constants.CredentialIssuanceMode.fromIndex(1)).isEqualTo(Constants.CredentialIssuanceMode.OID4VCI);
    }

    // ------------------------------------------------------------------
    // Additive shared-state/output names for later node work
    // ------------------------------------------------------------------

    @Test
    public void additiveOfferStateKeysAreDefined() {
        assertThat(Constants.PINGONE_CREDENTIAL_OFFER_URL_KEY).isEqualTo("pingOneCredentialOfferUrl");
        assertThat(Constants.PINGONE_CREDENTIAL_OFFER_QR_CODE_URL_KEY).isEqualTo("pingOneCredentialOfferQrCodeUrl");
        assertThat(Constants.PINGONE_CREDENTIAL_OFFER_STATUS_KEY).isEqualTo("pingOneCredentialOfferStatus");
        assertThat(Constants.PINGONE_CREDENTIAL_OFFER_KEY).isEqualTo("pingOneCredentialOffer");
    }

    @Test
    public void additiveOid4vpStateKeysAreDefined() {
        assertThat(Constants.PINGONE_VERIFICATION_PROTOCOL_KEY).isEqualTo("pingOneVerificationProtocol");
        assertThat(Constants.PINGONE_VERIFICATION_PROTOCOL_VERSION_KEY).isEqualTo("pingOneVerificationProtocolVersion");
        assertThat(Constants.PINGONE_VERIFICATION_DID_METHOD_KEY).isEqualTo("pingOneVerificationDidMethod");
        assertThat(Constants.PINGONE_VERIFICATION_ISSUER_FILTER_KEY).isEqualTo("pingOneVerificationIssuerFilter");
        assertThat(Constants.PINGONE_VERIFIED_DATA_KEY).isEqualTo("pingOneVerifiedData");
    }

    @Test
    public void nativeKeysSemanticsRemainNativeOnly() {
        // Native selective disclosure uses "keys"; this member must never be
        // reused as OID4VP behavior by later tasks.
        assertThat(Constants.NATIVE_KEYS_KEY).isEqualTo("keys");
        assertThat(Constants.NATIVE_KEYS_KEY).isNotEqualTo(Constants.PROTOCOL_OPENID4VP);
    }

    @Test
    public void protocolStateKeysDoNotCollideWithNativeKeys() {
        Set<String> allKeys = new HashSet<>(Arrays.asList(
            Constants.PINGONE_USER_ID_KEY,
            Constants.PINGONE_PAIRING_DELIVERY_METHOD_KEY,
            Constants.PINGONE_PAIRING_TIMEOUT_KEY,
            Constants.PINGONE_APPOPEN_URL_KEY,
            Constants.PINGONE_PAIRING_WALLET_ID_KEY,
            Constants.PINGONE_WALLET_ID_KEY,
            Constants.PINGONE_WALLET_DATA_KEY,
            Constants.PINGONE_ACTIVE_WALLETS_DATA_KEY,
            Constants.PINGONE_APPLICATION_INSTANCE_ID_KEY,
            Constants.PINGONE_VERIFICATION_DELIVERY_METHOD_KEY,
            Constants.PINGONE_VERIFICATION_SESSION_KEY,
            Constants.PINGONE_VERIFICATION_TIMEOUT_KEY,
            Constants.PINGONE_CREDENTIAL_VERIFICATION_KEY,
            Constants.PINGONE_CREDENTIAL_UPDATE_KEY,
            Constants.PINGONE_CREDENTIAL_ID_KEY,
            Constants.PINGONE_CREDENTIAL_TYPE_KEY,
            Constants.PINGONE_CREDENTIAL_OFFER_URL_KEY,
            Constants.PINGONE_CREDENTIAL_OFFER_QR_CODE_URL_KEY,
            Constants.PINGONE_CREDENTIAL_OFFER_STATUS_KEY,
            Constants.PINGONE_CREDENTIAL_OFFER_KEY,
            Constants.PINGONE_VERIFICATION_PROTOCOL_KEY,
            Constants.PINGONE_VERIFICATION_PROTOCOL_VERSION_KEY,
            Constants.PINGONE_VERIFICATION_DID_METHOD_KEY,
            Constants.PINGONE_VERIFICATION_ISSUER_FILTER_KEY,
            Constants.PINGONE_VERIFIED_DATA_KEY));

        assertThat(allKeys).hasSize(25);
    }

    // ------------------------------------------------------------------
    // Node configuration property surface
    // ------------------------------------------------------------------

    @Test
    public void issuePropertiesExposeOid4vciConfigurationWithoutChangingNativeEntries() throws Exception {
        Properties issueProperties = loadProperties("PingOneCredentialsIssue.properties");

        // New OID4VCI offer configuration keys
        assertThat(issueProperties.stringPropertyNames())
            .contains("credentialIssuanceMode", "credentialIssuanceMode.NATIVE", "credentialIssuanceMode.OID4VCI",
                      "credentialOfferCredentials", "credentialOfferGrantTypes");

        assertThat(issueProperties.getProperty("credentialIssuanceMode")).isEqualTo("Credential issuance protocol");
        assertThat(issueProperties.getProperty("credentialIssuanceMode.NATIVE")).isEqualTo("Native");
        assertThat(issueProperties.getProperty("credentialIssuanceMode.OID4VCI")).isEqualTo("OpenID4VCI");
        assertThat(issueProperties.getProperty("credentialOfferCredentials"))
            .isEqualTo("OID4VCI credentials");
        assertThat(issueProperties.getProperty("credentialOfferGrantTypes")).isEqualTo("OID4VCI grant types");

        // Existing NATIVE property values remain unchanged
        assertThat(issueProperties.getProperty("nodeDescription")).isEqualTo("PingOne Credentials Issue");
        assertThat(issueProperties.getProperty("credentialTypeId")).isEqualTo("Credential Type Id");
        assertThat(issueProperties.getProperty("attributes")).isEqualTo("Attribute map");
        assertThat(issueProperties.getProperty("successOutcome")).isEqualTo("Success");
        assertThat(issueProperties.getProperty("errorOutcome")).isEqualTo("Error");
    }

    @Test
    public void verificationPropertiesExposeOid4vpConfigurationWithoutChangingNativeEntries() throws Exception {
        Properties verificationProperties = loadProperties("PingOneCredentialsVerification.properties");

        // New OID4VP configuration keys
        assertThat(verificationProperties.stringPropertyNames())
            .contains("protocol", "protocol.NATIVE", "protocol.OPENID4VP",
                      "protocolVersion", "didMethod", "issuerFilter", "oid4vpTimeoutSeconds");

        assertThat(verificationProperties.getProperty("protocol")).isEqualTo("Verification protocol");
        assertThat(verificationProperties.getProperty("protocol.NATIVE")).isEqualTo("Native");
        assertThat(verificationProperties.getProperty("protocol.OPENID4VP")).isEqualTo("OpenID4VP");
        assertThat(verificationProperties.getProperty("protocolVersion")).isEqualTo("OID4VP protocol version");
        assertThat(verificationProperties.getProperty("didMethod")).isEqualTo("OID4VP DID method");
        assertThat(verificationProperties.getProperty("issuerFilter")).isEqualTo("OID4VP issuer filter DIDs");
        assertThat(verificationProperties.getProperty("oid4vpTimeoutSeconds")).isEqualTo("OID4VP session timeout");

        // Existing NATIVE property values remain unchanged
        assertThat(verificationProperties.getProperty("nodeDescription")).isEqualTo("PingOne Credentials Verification");
        assertThat(verificationProperties.getProperty("credentialType")).isEqualTo("Credential Type");
        assertThat(verificationProperties.getProperty("attributeKeys")).isEqualTo("Disclosure Attribute Keys");
        assertThat(verificationProperties.getProperty("applicationInstanceAttribute")).isEqualTo("Application Instance ID");
        assertThat(verificationProperties.getProperty("digitalWalletApplicationId")).isEqualTo("Digital Wallet Application ID");
        assertThat(verificationProperties.getProperty("deliveryMethod")).isEqualTo("Verification URL delivery method");
        assertThat(verificationProperties.getProperty("deliveryMethod.QRCODE")).isEqualTo("QR Code");
        assertThat(verificationProperties.getProperty("deliveryMethod.PUSH")).isEqualTo("Push");
        assertThat(verificationProperties.getProperty("allowDeliveryMethodSelection"))
            .isEqualTo("Allows user to choose the URL delivery method");
        assertThat(verificationProperties.getProperty("timeout")).isEqualTo("Verification Timeout");
        assertThat(verificationProperties.getProperty("storeVerificationResponse"))
            .isEqualTo("Store Credential Verification Response");
        assertThat(verificationProperties.getProperty("customCredentialsPayload")).isEqualTo("Custom Requested Credentials");
        assertThat(verificationProperties.getProperty("default.pushMessage")).isEqualTo("Verification Credential Request");
        assertThat(verificationProperties.getProperty("successOutcome")).isEqualTo("Success");
        assertThat(verificationProperties.getProperty("errorOutcome")).isEqualTo("Error");
        assertThat(verificationProperties.getProperty("timeoutOutcome")).isEqualTo("Time Out");
    }

    @Test
    public void verificationPropertiesDoNotExposeNativeKeysSemanticsAsOid4vpBehavior() throws Exception {
        Properties verificationProperties = loadProperties("PingOneCredentialsVerification.properties");

        // attributeKeys remains the Native selective-disclosure property; the
        // OID4VP property set must not reuse it under an OID4VP-labeled name.
        List<String> propertyNames = List.copyOf(verificationProperties.stringPropertyNames());

        assertThat(propertyNames).noneMatch(name -> name.toLowerCase().startsWith("oid4vp")
                                                                        && name.toLowerCase().contains("key"));
        assertThat(propertyNames).noneMatch(name -> name.toLowerCase().startsWith("oid4vp")
                                                                        && name.toLowerCase().contains("keys"));
    }

    @Test
    public void propertiesExposeNoSelfSigningConfiguration() throws Exception {
        Properties issueProperties = loadProperties("PingOneCredentialsIssue.properties");
        Properties verificationProperties = loadProperties("PingOneCredentialsVerification.properties");

        for (Properties properties : Arrays.asList(issueProperties, verificationProperties)) {
            assertThat(properties.stringPropertyNames())
                .noneMatch(name -> name.toLowerCase().contains("sign")
                                        && !name.toLowerCase().contains("design"));
            assertThat(properties.stringPropertyNames())
                .noneMatch(name -> name.toLowerCase().contains("private"));
        }
    }

    @Test
    public void issueHelpTextDocumentsTheOid4vciOperationalConsequences() throws Exception {
        Properties issueProperties = loadProperties("PingOneCredentialsIssue.properties");

        // An empty OID4VCI credentials list omits the field, so PingOne
        // provisions every credential type of the user.
        assertThat(issueProperties.getProperty("credentialIssuanceMode.help"))
            .contains("PingOne provisions")
            .contains("all of the user's credential types");

        // The configured credential type is a NATIVE-issuance setting; the
        // OID4VCI offer request has no configured-credential-type member.
        assertThat(issueProperties.getProperty("credentialTypeId.help"))
            .contains("NATIVE")
            .contains("OID4VCI");
    }

    private Properties loadProperties(String bundleName) throws Exception {
        Properties properties = new Properties();
        try (InputStream inputStream = getClass().getResourceAsStream(bundleName)) {
            assertThat(inputStream).isNotNull();
            properties.load(inputStream);
        }
        return properties;
    }
}
