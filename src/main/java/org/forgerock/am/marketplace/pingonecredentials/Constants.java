package org.forgerock.am.marketplace.pingonecredentials;

public class Constants {

	public static final String PINGONE_USER_ID_KEY = "pingOneUserId";
	public static final String PINGONE_PAIRING_DELIVERY_METHOD_KEY = "pingOneWalletPairingDeliveryMethod";
	public static final String PINGONE_PAIRING_TIMEOUT_KEY = "pingOnePairingTimeout";
	public static final String PINGONE_WALLET_ID_KEY = "pingOneWalletId";
	public static final String PINGONE_WALLET_KEY = "pingOneWallet";

	public static final String PINGONE_VERIFICATION_DELIVERY_METHOD_KEY = "pingOneVerificationDeliveryMethod";
	public static final String PINGONE_VERIFICATION_SESSION_KEY = "pingOneVerificationSessionId";
	public static final String PINGONE_VERIFICATION_TIMEOUT_KEY = "pingOneVerificationTimeout";
	public static final String PINGONE_CREDENTIAL_VERIFICATION_KEY = "pingOneCredentialVerification";

	public static final String PINGONE_CREDENTIAL_UPDATE_KEY = "pingOneCredentialUpdate";
	public static final String PINGONE_CREDENTIAL_ID_KEY = "pingOneCredentialId";

	public static final String RESPONSE_ID = "id";
	public static final String RESPONSE_STATUS = "status";
	public static final String RESPONSE_PAIRING_SESSION = "pairingSession";
	public static final String RESPONSE_QR_URL = "qrUrl";

	public static final String RESPONSE_LINKS = "_links";
	public static final String RESPONSE_QR = "qr";
	public static final String RESPONSE_HREF = "href";

	public static final String ACTIVE = "ACTIVE";
	public static final String PAIRING_REQUIRED = "PAIRING_REQUIRED";
	public static final String EXPIRED = "EXPIRED";
	public static final String VERIFICATION_SUCCESSFUL = "VERIFICATION_SUCCESSFUL";
	public static final String REVOKED = "REVOKED";

	public final static String INITIAL = "INITIAL";

	protected final static String KEY = "key";
	protected final static String VALUE = "value";

	// Outcomes
	public static final String SUCCESS_OUTCOME_ID = "success";
	public static final String FAILURE_OUTCOME_ID = "failure";
	public static final String TIMEOUT_OUTCOME_ID = "timeout";
	public static final String NOT_FOUND_OUTCOME_ID = "not_found";
	
	protected final static String P1_BASE_URL = "https://api.pingone";
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
		SMS,
		/**
		 * E-mail and SMS
		 */
		EMAIL_SMS;


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
