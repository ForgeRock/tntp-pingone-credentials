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

	public static final String PINGONE_CREDENTIAL_UPDATE_KEY = "pingOneCredentialUpdate";
	public static final String PINGONE_CREDENTIAL_ID_KEY = "pingOneCredentialId";
	public static final String PINGONE_CREDENTIAL_TYPE_KEY = "pingOneCredentialType";

	public static final String ENVIRONMENTS_PATH = "/environments/";
	public static final String USERS_PATH = "/users/";
	public static final String DIGITAL_WALLETS_PATH = "/digitalWallets";
	public static final String CREDENTIALS_PATH = "/credentials";
	public static final String PRESENTATION_SESSIONS_PATH = "/presentationSessions";
	public static final String SESSION_DATA_PATH = "/sessionData";

	public static final String RESPONSE_ID = "id";
	public static final String RESPONSE_STATUS = "status";

	public static final String RESPONSE_LINKS = "_links";
	public static final String RESPONSE_APPOPEN = "appOpen";
	public static final String RESPONSE_APPOPENURL = "appOpenUrl";
	public static final String RESPONSE_HREF = "href";
	public static final String RESPONSE_EMBEDDED = "_embedded";
	public static final String RESPONSE_DIGITALWALLETS = "digitalWallets";
	public static final String RESPONSE_APPLICATION_INSTANCE = "applicationInstance";

	public static final String ACTIVE = "ACTIVE";
	public static final String PAIRING_REQUIRED = "PAIRING_REQUIRED";
	public static final String EXPIRED = "EXPIRED";
	public static final String VERIFICATION_SUCCESSFUL = "VERIFICATION_SUCCESSFUL";
	public static final String REVOKED = "REVOKED";

	public final static String INITIAL = "INITIAL";

	public final static String OBJECT_ATTRIBUTES = "objectAttributes";
	public final static String REQUESTED_CREDENTIALS = "requestedCredentials";

	// Outcomes
	public static final String SUCCESS_OUTCOME_ID = "success";
	public static final String SUCCESS_MULTI_OUTCOME_ID = "successMulti";
	public static final String ERROR_OUTCOME_ID = "error";
	public static final String TIMEOUT_OUTCOME_ID = "timeout";
	public static final String NOT_FOUND_OUTCOME_ID = "notFound";

	protected final static String REVOKE_CONTENT_TYPE = "application/vnd.pingidentity.validations.revokeCredential+json";

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
