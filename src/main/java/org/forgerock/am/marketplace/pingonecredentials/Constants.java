package org.forgerock.am.marketplace.pingonecredentials;

public class Constants {

	public static final String PINGONE_USER_ID_KEY = "pingOneUserId";
	public static final String PINGONE_PAIRING_DELIVERY_METHOD_KEY = "pingOneWalletPairingDeliveryMethod";
	public static final String PINGONE_PAIRING_TIMEOUT_KEY = "pingOnePairingTimeout";
	public static final String PINGONE_APPOPEN_URL_KEY = "pingOneAppOpenURL";
	public static final String PINGONE_PAIRING_WALLET_ID_KEY = "pingOnePairingWalletId";

	public static final String PINGONE_WALLET_ID_KEY = "pingOneWalletId";
	public static final String PINGONE_WALLET_DATA_KEY = "pingOneWalletData";
	public static final String PINGONE_ACTIVE_WALLETS_DATA_KEY = "pingOneActiveWallets";

	public static final String PINGONE_APPLICATION_INSTANCE_ID_KEY = "pingOneApplicationInstanceId";

	public static final String PINGONE_VERIFICATION_DELIVERY_METHOD_KEY = "pingOneVerificationDeliveryMethod";
	public static final String PINGONE_VERIFICATION_SESSION_KEY = "pingOneVerificationSessionId";
	public static final String PINGONE_VERIFICATION_TIMEOUT_KEY = "pingOneVerificationTimeout";
	public static final String PINGONE_CREDENTIAL_VERIFICATION_KEY = "pingOneCredentialVerification";

	/**
	 * Additive shared-state/output names for OpenID4VP presentation verification:
	 * the configured protocol, the OID4VP optional protocol values echoed for the
	 * session, and the verified data returned by successful presentations.
	 */
	public static final String PINGONE_VERIFICATION_PROTOCOL_KEY = "pingOneVerificationProtocol";
	public static final String PINGONE_VERIFICATION_PROTOCOL_VERSION_KEY = "pingOneVerificationProtocolVersion";
	public static final String PINGONE_VERIFICATION_DID_METHOD_KEY = "pingOneVerificationDidMethod";
	public static final String PINGONE_VERIFICATION_ISSUER_FILTER_KEY = "pingOneVerificationIssuerFilter";
	public static final String PINGONE_VERIFIED_DATA_KEY = "pingOneVerifiedData";

	public static final String PINGONE_CREDENTIAL_UPDATE_KEY = "pingOneCredentialUpdate";
	public static final String PINGONE_CREDENTIAL_ID_KEY = "pingOneCredentialId";
	public static final String PINGONE_CREDENTIAL_TYPE_KEY = "pingOneCredentialType";

	/**
	 * Additive shared-state/output names for the OpenID4VCI credential offer:
	 * the offer URI/URL, its QR-code link, the offer lifecycle status, and the
	 * full offer response.
	 */
	public static final String PINGONE_CREDENTIAL_OFFER_URL_KEY = "pingOneCredentialOfferUrl";
	public static final String PINGONE_CREDENTIAL_OFFER_QR_CODE_URL_KEY = "pingOneCredentialOfferQrCodeUrl";
	public static final String PINGONE_CREDENTIAL_OFFER_STATUS_KEY = "pingOneCredentialOfferStatus";
	public static final String PINGONE_CREDENTIAL_OFFER_KEY = "pingOneCredentialOffer";

	public static final String ENVIRONMENTS_PATH = "/environments/";
	public static final String USERS_PATH = "/users/";
	public static final String DIGITAL_WALLETS_PATH = "/digitalWallets";
	public static final String CREDENTIALS_PATH = "/credentials";
	public static final String PRESENTATION_SESSIONS_PATH = "/presentationSessions";
	public static final String SESSION_DATA_PATH = "/sessionData";

	/**
	 * OpenID4VCI credential offer path, appended to
	 * {@code /environments/{envID}/users/{userID}} for the
	 * POST openid4vciOffers operation.
	 */
	public static final String OPENID4VCI_OFFERS_PATH = "/openid4vciOffers";

	public static final String RESPONSE_ID = "id";
	public static final String RESPONSE_STATUS = "status";

	public static final String RESPONSE_LINKS = "_links";
	public static final String RESPONSE_APPOPEN = "appOpen";
	public static final String RESPONSE_APPOPENURL = "appOpenUrl";
	public static final String RESPONSE_HREF = "href";
	public static final String RESPONSE_EMBEDDED = "_embedded";
	public static final String RESPONSE_DIGITALWALLETS = "digitalWallets";
	public static final String RESPONSE_APPLICATION_INSTANCE = "applicationInstance";

	/**
	 * OpenID4VCI offer response members: the credential offer URI/URL, its
	 * QR-code link, and the offer lifecycle status. The offer URI is also
	 * exposed under {@code _links} with the {@code openid-credential-offer} rel.
	 */
	public static final String RESPONSE_CREDENTIAL_OFFER_URL = "credentialOfferUrl";
	public static final String RESPONSE_OPENID_CREDENTIAL_OFFER_REL = "openid-credential-offer";
	public static final String RESPONSE_QRCODE = "qrCode";

	/**
	 * OpenID4VP presentation session response members: the documented QR link
	 * and the verified data returned by successful presentations.
	 */
	public static final String RESPONSE_QR = "qr";
	public static final String RESPONSE_VERIFIED_DATA = "verifiedData";

	public static final String ACTIVE = "ACTIVE";
	public static final String PAIRING_REQUIRED = "PAIRING_REQUIRED";
	public static final String EXPIRED = "EXPIRED";
	public static final String VERIFICATION_SUCCESSFUL = "VERIFICATION_SUCCESSFUL";
	public static final String REVOKED = "REVOKED";

	/** Verification/lifecycle statuses for OpenID4VP presentation sessions. */
	public static final String VERIFICATION_FAILED = "VERIFICATION_FAILED";

	/** In-progress presentation-session status; like INITIAL, it means the verification is not yet complete. */
	public static final String WAITING = "WAITING";

	public final static String INITIAL = "INITIAL";

	public final static String OBJECT_ATTRIBUTES = "objectAttributes";
	public final static String REQUESTED_CREDENTIALS = "requestedCredentials";

	// Protocol identifiers
	/** Native PingOne Credentials presentation protocol (the existing default). */
	public static final String PROTOCOL_NATIVE = "NATIVE";
	/** OpenID4VP presentation verification protocol. */
	public static final String PROTOCOL_OPENID4VP = "OPENID4VP";

	// OpenID4VCI offer request members
	/** Optional array of credential type identifiers to include in the OID4VCI offer. */
	public static final String OFFER_CREDENTIALS_KEY = "credentials";
	/** Optional array of OAuth2 grant types for the OID4VCI offer. */
	public static final String OFFER_GRANT_TYPES_KEY = "grantTypes";
	/** Supported OpenID4VCI grant type values. */
	public static final String GRANT_TYPE_AUTHORIZATION_CODE = "authorization_code";
	public static final String GRANT_TYPE_PRE_AUTHORIZED_CODE = "urn:ietf:params:oauth:grant-type:pre-authorized_code";

	// OpenID4VP presentation session request members
	/** OpenID4VP protocol version, for example "1.0". */
	public static final String OID4VP_PROTOCOL_VERSION_KEY = "protocolVersion";
	/** The DID method used to resolve the verifier's DID. */
	public static final String OID4VP_DID_METHOD_KEY = "didMethod";
	/** The issuer filter applied to verifiable presentations; an object holding the optional members below. */
	public static final String OID4VP_ISSUER_FILTER_KEY = "issuerFilter";
	/** The issuer-filter member listing the Decentralized Identifiers of acceptable credential issuers. */
	public static final String OID4VP_ISSUER_FILTER_DIDS_KEY = "dids";
	/** The number of seconds the presentation session remains available. */
	public static final String OID4VP_TIMEOUT_SECONDS_KEY = "timeoutSeconds";

	/** Native-only selective-disclosure request member; never serialized for OID4VP. */
	public static final String NATIVE_KEYS_KEY = "keys";

	// Outcomes
	public static final String SUCCESS_OUTCOME_ID = "success";
	public static final String SUCCESS_MULTI_OUTCOME_ID = "successMulti";
	public static final String ERROR_OUTCOME_ID = "error";
	public static final String TIMEOUT_OUTCOME_ID = "timeout";
	public static final String NOT_FOUND_OUTCOME_ID = "notFound";

	protected final static String REVOKE_CONTENT_TYPE = "application/vnd.pingidentity.validations.revokeCredential+json";

	/**
	 * Verification protocol selection. NATIVE preserves the existing PingOne
	 * presentation behavior and remains the default; OPENID4VP selects the
	 * documented OpenID for Verifiable Presentations protocol.
	 */
	public enum VerificationProtocol {
		/** Existing PingOne Native verification protocol (default). */
		NATIVE,
		/** OpenID for Verifiable Presentations protocol. */
		OPENID4VP;

		/**
		 * Get the VerificationProtocol from the index.
		 *
		 * @param index The index of the VerificationProtocol.
		 * @return The VerificationProtocol.
		 */
		public static VerificationProtocol fromIndex(int index) {
			return VerificationProtocol.values()[index];
		}
	}

	/**
	 * Credential issuance mode selection. NATIVE preserves the existing
	 * credential issuance operation and remains the default; OID4VCI creates a
	 * documented OpenID4VCI credential offer.
	 */
	public enum CredentialIssuanceMode {
		/** Existing legacy PingOne credential issuance (default). */
		NATIVE,
		/** OpenID4VCI credential offer issuance. */
		OID4VCI;

		/**
		 * Get the CredentialIssuanceMode from the index.
		 *
		 * @param index The index of the CredentialIssuanceMode.
		 * @return The CredentialIssuanceMode.
		 */
		public static CredentialIssuanceMode fromIndex(int index) {
			return CredentialIssuanceMode.values()[index];
		}
	}

	public enum PairingDeliveryMethod {

		/**
		 * QR code.
		 */
		QRCODE,
		/**
		 * E-mail.
		 */
		EMAIL,
		/**
		 * SMS.
		 */
		SMS;
		/**
		 * Get the DeliveryMethod from the index.
		 *
		 * @param index The index of the DeliveryMethod.
		 * @return The DeliveryMethod.
		 */
		public static PairingDeliveryMethod fromIndex(int index) {
			return PairingDeliveryMethod.values()[index];
		}
	}

	public enum VerificationDeliveryMethod {

		/**
		 * QR code.
		 */
		QRCODE,
		/**
		 * Push.
		 */
		PUSH;
		/**
		 * Get the DeliveryMethod from the index.
		 *
		 * @param index The index of the DeliveryMethod.
		 * @return The DeliveryMethod.
		 */
		public static VerificationDeliveryMethod fromIndex(int index) {
			return VerificationDeliveryMethod.values()[index];
		}
	}

	public enum RevokeResult {
		REVOKED,
		NOT_FOUND;
	}
}
