/*
 * This code is to be used exclusively in connection with Ping Identity Corporation software or services.
 * Ping Identity Corporation only offers such software or services to legal entities who have entered into
 * a binding license agreement with Ping Identity Corporation.
 *
 * Copyright 2024 Ping Identity Corporation. All Rights Reserved
 */

package org.forgerock.am.marketplace.pingonecredentials;

import static org.forgerock.am.marketplace.pingonecredentials.Constants.CREDENTIAL_DATA_PATH;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.CREDENTIALS_PATH;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.DIGITAL_WALLETS_PATH;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.ENVIRONMENTS_PATH;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.OID4VP_DID_METHOD_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.OID4VP_ISSUER_FILTER_DIDS_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.OID4VP_ISSUER_FILTER_ENVIRONMENT_IDS_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.OID4VP_ISSUER_FILTER_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.OID4VP_PROTOCOL_VERSION_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.OID4VP_TIMEOUT_SECONDS_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.OFFER_CREDENTIALS_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.OFFER_GRANT_TYPES_KEY;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.OPENID4VCI_OFFERS_PATH;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PRESENTATION_SESSIONS_PATH;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PROTOCOL_OPENID4VP;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.RESPONSE_STATUS;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.REVOKED;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.REVOKE_CONTENT_TYPE;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.RevokeResult;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.SESSION_DATA_PATH;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.USERS_PATH;
import static org.forgerock.json.JsonValue.array;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.object;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.forgerock.http.Handler;
import org.forgerock.http.header.AuthorizationHeader;
import org.forgerock.http.header.ContentTypeHeader;
import org.forgerock.http.header.MalformedHeaderException;
import org.forgerock.http.header.authorization.BearerToken;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.json.JsonValue;
import org.forgerock.oauth2.core.AccessToken;

import org.forgerock.openam.http.HttpConstants;
import org.forgerock.openam.integration.pingone.api.PingOneWorkerService;
import org.forgerock.services.context.RootContext;

/**
 * Service to integrate with PingOne Credentials APIs.
 */
@Singleton
public class PingOneCredentialsService {
	private final Handler handler;

	@Inject
	public PingOneCredentialsService(@Named("CloseableHttpClientHandler") org.forgerock.http.Handler handler) {
	    this.handler = handler;
	}

	/**
	 * the GET /environments/{{envID}}/users/{{userID}}/digitalWallets operation to find all the
	 * digital wallets for the user
	 *
	 * @param accessToken The {@link AccessToken}
	 * @param worker The worker {@link PingOneWorkerService}
	 * @param pingOneUID The PingOne user ID
	 * @return Json containing the response from the operation
	 * @throws PingOneCredentialsServiceException When API response != 201
	 */
	JsonValue findWalletRequest(String accessToken, PingOneWorkerService.Worker worker, String pingOneUID)
		throws PingOneCredentialsServiceException {

		Request request;

		try {
			String theURI = worker.apiUrl() +
			                ENVIRONMENTS_PATH + worker.environmentId() +
			                USERS_PATH + pingOneUID +
			                DIGITAL_WALLETS_PATH;

			URI uri = URI.create(theURI);

			request = new Request();
			request.setUri(uri).setMethod(HttpConstants.Methods.GET);

			return getResponse(request, accessToken, "PingOne Credentials Find Wallet");
		} catch (Exception e) {
			throw new PingOneCredentialsServiceException("Failed PingOne Credentials" + e.getMessage());
		}
	}

	/**
	 * the POST /environments/{{envID}}/users/{{userID}}/credentials to issue a new credential to a PingOne user
	 *
	 * @param accessToken The {@link AccessToken}
	 * @param worker The worker {@link PingOneWorkerService}
	 * @param pingOneUID The PingOne user ID
	 * @param credentialTypeId The credential type ID
	 * @param attributes The attributes to add to the credential
	 * @return Json containing the response from the operation
	 * @throws PingOneCredentialsServiceException When API response != 201
	 */
	JsonValue credentialIssueRequest(String accessToken, PingOneWorkerService.Worker worker, String pingOneUID,
	                                 String credentialTypeId, JsonValue attributes)
		throws PingOneCredentialsServiceException {

		Request request;

		try {
			String theURI = worker.apiUrl() +
			                ENVIRONMENTS_PATH + worker.environmentId() +
			                USERS_PATH + pingOneUID +
			                CREDENTIALS_PATH;

			URI uri = URI.create(theURI);

			JsonValue credentialTypeIdBody = json(object(
				field("id", credentialTypeId)));

			JsonValue credentialBody = json(object(
				field("credentialType", credentialTypeIdBody),
				field("data", attributes)));

			request = new Request();
			request.setUri(uri).setMethod(HttpConstants.Methods.POST);
			request.getEntity().setJson(credentialBody);

			return getResponse(request, accessToken, "PingOne Credentials Issue a User Credential");
		} catch (Exception e) {
			throw new PingOneCredentialsServiceException("Failed PingOne Credentials" + e.getMessage());
		}
	}

	/**
	 * the POST /environments/{{envID}}/users/{{userID}}/openid4vciOffers operation to initiate an OpenID4VCI
	 * credential offer for a PingOne user
	 *
	 * @param accessToken The {@link AccessToken}
	 * @param worker The worker {@link PingOneWorkerService}
	 * @param pingOneUID The PingOne user ID
	 * @param credentialTypeIds The optional credential type IDs to include in the offer; omitted when null or empty
	 * @param grantTypes The optional OAuth2 grant types for the offer; omitted when null or empty
	 * @return Json containing the response from the operation
	 * @throws PingOneCredentialsServiceException When API response is not successful
	 */
	JsonValue createCredentialOfferRequest(String accessToken, PingOneWorkerService.Worker worker, String pingOneUID,
	                                       List<String> credentialTypeIds, List<String> grantTypes)
		throws PingOneCredentialsServiceException {

		Request request;

		try {
			String theURI = worker.apiUrl() +
			                ENVIRONMENTS_PATH + worker.environmentId() +
			                USERS_PATH + pingOneUID +
			                OPENID4VCI_OFFERS_PATH;

			URI uri = URI.create(theURI);

			JsonValue body = json(object());

			if (credentialTypeIds != null && !credentialTypeIds.isEmpty()) {
				body.put(OFFER_CREDENTIALS_KEY, credentialTypeIds);
			}

			if (grantTypes != null && !grantTypes.isEmpty()) {
				body.put(OFFER_GRANT_TYPES_KEY, grantTypes);
			}

			request = new Request();
			request.setUri(uri).setMethod(HttpConstants.Methods.POST);
			request.getEntity().setJson(body);

			return getResponse(request, accessToken, "PingOne Credentials Create an OpenID4VCI Offer");
		} catch (Exception e) {
			throw new PingOneCredentialsServiceException("Failed PingOne Credentials" + e.getMessage());
		}
	}

	/**
	 * the GET /environments/{{envID}}/users/{{userID}}/openid4vciOffers/{{credentialOfferID}} operation to read one
	 * OpenID4VCI credential offer, including its lifecycle status
	 *
	 * @param accessToken The {@link AccessToken}
	 * @param worker The worker {@link PingOneWorkerService}
	 * @param pingOneUID The PingOne user ID
	 * @param offerId The OpenID4VCI credential offer ID
	 * @return Json containing the response from the operation
	 * @throws PingOneCredentialsServiceException When API response is not successful
	 */
	JsonValue readCredentialOfferRequest(String accessToken, PingOneWorkerService.Worker worker, String pingOneUID,
	                                     String offerId)
		throws PingOneCredentialsServiceException {

		Request request;

		try {
			String theURI = worker.apiUrl() +
			                ENVIRONMENTS_PATH + worker.environmentId() +
			                USERS_PATH + pingOneUID +
			                OPENID4VCI_OFFERS_PATH + "/" + offerId;

			URI uri = URI.create(theURI);

			request = new Request();
			request.setUri(uri).setMethod(HttpConstants.Methods.GET);

			return getResponse(request, accessToken, "PingOne Credentials Read One OpenID4VCI Offer");
		} catch (Exception e) {
			throw new PingOneCredentialsServiceException("Failed PingOne Credentials" + e.getMessage());
		}
	}

	/**
	 * the PUT /environments/{{envID}}/users/{{userID}}/credentials/{{credentialId}} to update an existing
	 * credential of a PingOne user
	 *
	 * @param accessToken The {@link AccessToken}
	 * @param worker The worker {@link PingOneWorkerService}
	 * @param pingOneUID The PingOne user ID
	 * @param credentialTypeId The credential type ID
     * @param credentialId The credential ID
	 * @param attributes The attributes to add to the credential
	 * @return Json containing the response from the operation
	 * @throws PingOneCredentialsServiceException When API response != 201
	 */
	JsonValue credentialUpdateRequest(String accessToken, PingOneWorkerService.Worker worker, String pingOneUID,
	                                  String credentialTypeId, String credentialId,
	                                  JsonValue attributes) throws PingOneCredentialsServiceException {
		Request request;

		try {
			String theURI = worker.apiUrl() +
			                ENVIRONMENTS_PATH + worker.environmentId() +
			                USERS_PATH + pingOneUID +
			                CREDENTIALS_PATH + "/" + credentialId;

			URI uri = URI.create(theURI);

			JsonValue credentialTypeIdBody = json(object(
				field("id", credentialTypeId)));

			JsonValue credentialBody = json(object(
				field("credentialType", credentialTypeIdBody),
				field("data", attributes)));

			request = new Request();
			request.setUri(uri).setMethod(HttpConstants.Methods.PUT);
			request.getEntity().setJson(credentialBody);

			return getResponse(request, accessToken, "PingOne Credentials Update a User Credential");
		} catch (Exception e) {
			throw new PingOneCredentialsServiceException("Failed PingOne Credentials" + e.getMessage());
		}
	}

	/**
	 * the POST /environments/{{envID}}/users/{{userID}}/digitalWallets to create a digital wallet pairing request
	 * for a PingOne user
	 *
	 * @param accessToken The {@link AccessToken}
	 * @param worker The worker {@link PingOneWorkerService}
	 * @param pingOneUserId The PingOne user ID
	 * @param digitalWalletApplicationId The digital wallet application ID
	 * @param notificationList The list of types of notification to deliver the pairing URL
	 * @return Json containing the response from the operation
	 * @throws PingOneCredentialsServiceException When API response != 201
	 */
	JsonValue createDigitalWalletRequest(String accessToken, PingOneWorkerService.Worker worker,
	                                     String pingOneUserId, String digitalWalletApplicationId,
	                                     List<String> notificationList) throws PingOneCredentialsServiceException {
		Request request;

		try {
			String theURI = worker.apiUrl() +
			                ENVIRONMENTS_PATH + worker.environmentId() +
			                USERS_PATH + pingOneUserId +
			                DIGITAL_WALLETS_PATH;

			URI uri = URI.create(theURI);

			JsonValue body = json(object(1));

			// Digital Wallet Application ID
			JsonValue applicationId = json(object(1));
			applicationId.put("id", digitalWalletApplicationId);

			body.put("digitalWalletApplication", applicationId);

			if(!notificationList.isEmpty()) {
				JsonValue notification = json(object(1));
				notification.put("methods", notificationList);
				body.put("notification", notification);
			}

			request = new Request();
			request.setUri(uri).setMethod(HttpConstants.Methods.POST);

			if (body.isNotNull())
				request.getEntity().setJson(body);

			return getResponse(request, accessToken, "PingOne Credentials Create a Digital Wallet");
		} catch (Exception e) {
			throw new PingOneCredentialsServiceException("Failed PingOne Credentials" + e.getMessage());
		}
	}

	/**
	 * the GET /environments/{{envID}}/users/{{userID}}/digitalWallets/{{digitalWalletID}} operation reads the
	 * digital wallet by id of the PingOne user
	 *
	 * @param accessToken The {@link AccessToken}
	 * @param worker The worker {@link PingOneWorkerService}
	 * @param pingOneUserId The PingOne user ID
	 * @param digitalWalletId The digital wallet ID
	 * @return Json containing the response from the operation
	 * @throws PingOneCredentialsServiceException When API response != 201
	 */
	JsonValue readDigitalWallet(String accessToken, PingOneWorkerService.Worker worker,
	                            String pingOneUserId, String digitalWalletId)
		throws PingOneCredentialsServiceException {

		Request request;

		try {
			String theURI = worker.apiUrl() +
			                ENVIRONMENTS_PATH + worker.environmentId() +
			                USERS_PATH + pingOneUserId +
			                DIGITAL_WALLETS_PATH + "/" + digitalWalletId;

			URI uri = URI.create(theURI);

			request = new Request();
			request.setUri(uri).setMethod(HttpConstants.Methods.GET);

			return getResponse(request, accessToken, "PingOne Credentials Read a Digital Wallet");
		} catch (Exception e) {
			throw new PingOneCredentialsServiceException("Failed PingOne Credentials" + e.getMessage());
		}
	}

	/**
	 * the POST /environments/{{envID}}/presentationSessions operation begins a verification presentation session
	 * for a credential using the QR Code notification method
	 *
	 * @param accessToken The {@link AccessToken}
	 * @param worker The worker {@link PingOneWorkerService}
	 * @param message The message to display during verification
	 * @param attributeKeys The attributes to include in selected disclosure
	 * @param customCredentialsPayload A custom credential payload
	 * @return Json containing the response from the operation
	 * @throws PingOneCredentialsServiceException When API response != 201
	 */
	JsonValue createVerificationRequest(String accessToken, PingOneWorkerService.Worker worker,
	                                    String message, String credentialType, Optional<String> digitalWalletApplicationId,
	                                    List<String> attributeKeys, JsonValue customCredentialsPayload)
		throws PingOneCredentialsServiceException {

		Request request;

		try {
			String theURI = worker.apiUrl() +
			                ENVIRONMENTS_PATH + worker.environmentId() +
			                PRESENTATION_SESSIONS_PATH;

			URI uri = URI.create(theURI);

			JsonValue body = json(object(1));

			body.put("message", message);
			body.put("protocol", "NATIVE");

			if(digitalWalletApplicationId.isPresent()) {
				JsonValue digitalWalletApplication = json(object(1));

				digitalWalletApplication.put("id", digitalWalletApplicationId.get());
				body.put("digitalWalletApplication", digitalWalletApplication);
			}

			JsonValue credential = json(object(1));

			credential.put("type", credentialType);
			credential.put("keys", attributeKeys);

			if(customCredentialsPayload != null && customCredentialsPayload.isNotNull()) {
				body.put("requestedCredentials", customCredentialsPayload);
			} else {
				JsonValue requestedCredentials = json(array());
				requestedCredentials.add(credential);

				body.put("requestedCredentials", requestedCredentials);
			}

			request = new Request();
			request.setUri(uri).setMethod(HttpConstants.Methods.POST);

			if (body.isNotNull())
				request.getEntity().setJson(body);


			return getResponse(request, accessToken, "PingOne Credentials Create Verification session");
		} catch (Exception e) {
			throw new PingOneCredentialsServiceException("Failed PingOne Credentials" + e.getMessage());
		}
	}

	/**
	 * the POST /environments/{{envID}}/presentationSessions operation begins a verification presentation session
	 * for a credential using the Push notification method
	 *
	 * @param accessToken The {@link AccessToken}
	 * @param worker The worker {@link PingOneWorkerService}
	 * @param message The message to display during verification
	 * @param attributeKeys The attributes to include in selected disclosure
	 * @param applicationInstanceId The application instance id
	 * @param digitalWalletApplicationId The digital wallet application instance id
	 * @param customCredentialsPayload A custom credential payload
	 * @return Json containing the response from the operation
	 * @throws PingOneCredentialsServiceException When API response != 201
	 */
	JsonValue createVerificationRequestPush(String accessToken, PingOneWorkerService.Worker worker,
	                                        String message, String credentialType,
	                                        List<String> attributeKeys, String applicationInstanceId,
	                                        String digitalWalletApplicationId,
	                                        JsonValue customCredentialsPayload)
		throws PingOneCredentialsServiceException {

		Request request;

		try {
			String theURI = worker.apiUrl() +
			                ENVIRONMENTS_PATH + worker.environmentId() +
			                PRESENTATION_SESSIONS_PATH;

			URI uri = URI.create(theURI);

			JsonValue body = json(object(1));

			body.put("message", message);
			body.put("protocol", "NATIVE");

			JsonValue applicationInstance = json(object(1));

			applicationInstance.put("id", applicationInstanceId);
			body.put("applicationInstance", applicationInstance);

			JsonValue digitalWalletApplication = json(object(1));

			digitalWalletApplication.put("id", digitalWalletApplicationId);
			body.put("digitalWalletApplication", digitalWalletApplication);

			JsonValue credential = json(object(1));

			credential.put("type", credentialType);
			credential.put("keys", attributeKeys);

			if(customCredentialsPayload != null && customCredentialsPayload.isNotNull()) {
				body.put("requestedCredentials", customCredentialsPayload);
			} else {
				JsonValue requestedCredentials = json(array());
				requestedCredentials.add(credential);

				body.put("requestedCredentials", requestedCredentials);
			}

			request = new Request();
			request.setUri(uri).setMethod(HttpConstants.Methods.POST);

			if (body.isNotNull())
				request.getEntity().setJson(body);

			return getResponse(request, accessToken, "PingOne Credentials Create Push Verification session");
		} catch (Exception e) {
			throw new PingOneCredentialsServiceException("Failed PingOne Credentials" + e.getMessage());
		}
	}

	/**
	 * the POST /environments/{{envID}}/presentationSessions operation begins a verification presentation session
	 * using the OpenID4VP protocol. OpenID4VP permits exactly one requested credential type and does not use the
	 * Native selective-disclosure {@code keys} semantics.
	 *
	 * @param accessToken The {@link AccessToken}
	 * @param worker The worker {@link PingOneWorkerService}
	 * @param message The message to display during verification
	 * @param credentialType The single credential type to verify
	 * @param protocolVersion The optional OpenID4VP protocol version; omitted when null or blank
	 * @param didMethod The optional DID method; omitted when null or blank
	 * @param issuerFilterDids The optional issuer-filter Decentralized Identifiers; omitted when null or empty
	 * @param issuerFilterEnvironmentIds The optional issuer-filter PingOne environment IDs; omitted when null or empty
	 * @param timeoutSeconds The optional number of seconds the session remains available; omitted when null
	 * @return Json containing the response from the operation
	 * @throws PingOneCredentialsServiceException When API response is not successful
	 */
	JsonValue createVerificationRequestOid4vp(String accessToken, PingOneWorkerService.Worker worker,
	                                          String message, String credentialType, String protocolVersion,
	                                          String didMethod, List<String> issuerFilterDids,
	                                          List<String> issuerFilterEnvironmentIds,
	                                          Integer timeoutSeconds)
		throws PingOneCredentialsServiceException {

		Request request;

		try {
			String theURI = worker.apiUrl() +
			                ENVIRONMENTS_PATH + worker.environmentId() +
			                PRESENTATION_SESSIONS_PATH;

			URI uri = URI.create(theURI);

			JsonValue body = json(object(1));

			body.put("message", message);
			body.put("protocol", PROTOCOL_OPENID4VP);

			if (StringUtils.isNotBlank(protocolVersion)) {
				body.put(OID4VP_PROTOCOL_VERSION_KEY, protocolVersion);
			}

			if (StringUtils.isNotBlank(didMethod)) {
				body.put(OID4VP_DID_METHOD_KEY, didMethod);
			}

			JsonValue issuerFilter = json(object(2));

			if (issuerFilterDids != null && !issuerFilterDids.isEmpty()) {
				issuerFilter.put(OID4VP_ISSUER_FILTER_DIDS_KEY, issuerFilterDids);
			}

			if (issuerFilterEnvironmentIds != null && !issuerFilterEnvironmentIds.isEmpty()) {
				issuerFilter.put(OID4VP_ISSUER_FILTER_ENVIRONMENT_IDS_KEY, issuerFilterEnvironmentIds);
			}

			if (!issuerFilter.asMap().isEmpty()) {
				body.put(OID4VP_ISSUER_FILTER_KEY, issuerFilter);
			}

			if (timeoutSeconds != null) {
				body.put(OID4VP_TIMEOUT_SECONDS_KEY, timeoutSeconds);
			}

			// OpenID4VP permits only one credential type per request, and its
			// requestedCredentials member carries no Native "keys" semantics.
			JsonValue credential = json(object(1));
			credential.put("type", credentialType);

			JsonValue requestedCredentials = json(array());
			requestedCredentials.add(credential);

			body.put("requestedCredentials", requestedCredentials);

			request = new Request();
			request.setUri(uri).setMethod(HttpConstants.Methods.POST);

			request.getEntity().setJson(body);

			return getResponse(request, accessToken, "PingOne Credentials Create OpenID4VP Verification session");
		} catch (Exception e) {
			throw new PingOneCredentialsServiceException("Failed PingOne Credentials" + e.getMessage());
		}
	}

	/**
	 * the GET /environments/{{envID}}/presentationSessions/{{sessionID}}/sessionData operation retrieves the
	 * verification session data from the session ID.
	 *
	 * @param accessToken The {@link AccessToken}
	 * @param worker The worker {@link PingOneWorkerService}
	 * @param sessionId The verification session ID
	 * @return Json containing the response from the operation
	 * @throws PingOneCredentialsServiceException When API response != 201
	 */
	JsonValue readVerificationSession(String accessToken, PingOneWorkerService.Worker worker,
	                                  String sessionId) throws PingOneCredentialsServiceException {
		Request request;

		try {
			String theURI = worker.apiUrl() +
			                ENVIRONMENTS_PATH + worker.environmentId()  +
			                PRESENTATION_SESSIONS_PATH + "/" + sessionId +
			                SESSION_DATA_PATH;

			URI uri = URI.create(theURI);

			request = new Request();
			request.setUri(uri).setMethod(HttpConstants.Methods.GET);

			return getResponse(request, accessToken, "PingOne Credentials Read a Verification Session");
		} catch (Exception e) {
			throw new PingOneCredentialsServiceException("Failed PingOne Credentials" + e.getMessage());
		}
	}

	/**
	 * the GET /environments/{{envID}}/presentationSessions/{{sessionID}}/credentialData operation retrieves the
	 * credential data for the verification session, including per-credential claims and issuer details.
	 *
	 * @param accessToken The {@link AccessToken}
	 * @param worker The worker {@link PingOneWorkerService}
	 * @param sessionId The verification session ID
	 * @return Json containing the response from the operation
	 * @throws PingOneCredentialsServiceException When API response != 201
	 */
	JsonValue readVerificationCredentialData(String accessToken, PingOneWorkerService.Worker worker,
	                                         String sessionId) throws PingOneCredentialsServiceException {
		Request request;

		try {
			String theURI = worker.apiUrl() +
			                ENVIRONMENTS_PATH + worker.environmentId()  +
			                PRESENTATION_SESSIONS_PATH + "/" + sessionId +
			                CREDENTIAL_DATA_PATH;

			URI uri = URI.create(theURI);

			request = new Request();
			request.setUri(uri).setMethod(HttpConstants.Methods.GET);

			return getResponse(request, accessToken, "PingOne Credentials Read a Verification Session Credential Data");
		} catch (Exception e) {
			throw new PingOneCredentialsServiceException("Failed PingOne Credentials" + e.getMessage());
		}
	}

	/**
	 * the DELETE /environments/{{envID}}/users/{{userID}}/digitalWallets/{{digitalWalletId}} operation retrieves the
	 * verification session data from the session ID.
	 *
	 * @param accessToken The {@link AccessToken}
	 * @param worker The worker {@link PingOneWorkerService}
	 * @param pingOneUserId The PingOne user ID
	 * @param digitalWalletId The digital wallet ID
	 * @return Boolean true if the deletion was successful or false if the wallet doesn't exist
	 * @throws PingOneCredentialsServiceException When API response != 201
	 */
	boolean deleteWalletRequest(String accessToken, PingOneWorkerService.Worker worker,
	                            String pingOneUserId, String digitalWalletId) throws PingOneCredentialsServiceException {
		Request request;
		try {
			String theURI = worker.apiUrl() +
			                ENVIRONMENTS_PATH + worker.environmentId() +
			                USERS_PATH + pingOneUserId +
			                DIGITAL_WALLETS_PATH + "/" +digitalWalletId;

			URI uri = URI.create(theURI);

			request = new Request();
			request.setUri(uri).setMethod(HttpConstants.Methods.DELETE);

			addAuthorizationHeader(request, accessToken);
			Response response = handler.handle(new RootContext(), request).getOrThrow();

			if (response.getStatus().isSuccessful()) {
				return true;
			}
			else if(response.getStatus().equals(Status.NOT_FOUND)) {
				return false; // Wallet didn't exist
			} else {
				throw new PingOneCredentialsServiceException("PingOne Credentials Delete a Digital Wallet" +
				                                             response.getStatus() + "-" + response.getEntity().getString());
			}
		} catch (Exception e) {
			throw new PingOneCredentialsServiceException("Failed PingOne Credentials" + e.getMessage());
		}
	}

	/**
	 * the POST /environments/{{envID}}/users/{{userID}}/credentials/{{credentialId}} operation retrieves the
	 * verification session data from the session ID.
	 *
	 * @param accessToken The {@link AccessToken}
	 * @param worker The worker {@link PingOneWorkerService}
	 * @param pingOneUserId The PingOne user ID
	 * @param credentialId The digital wallet ID
	 * @return RevokeResult REVOKED if successfully revoked, NOT_FOUND if the wallet does not exist,
	 * @throws PingOneCredentialsServiceException When API response != 201
	 */
	RevokeResult revokeCredentialRequest(String accessToken, PingOneWorkerService.Worker worker,
	                                     String pingOneUserId, String credentialId)
		throws PingOneCredentialsServiceException {

		Request request;
		try {

			String theURI = worker.apiUrl() +
			                ENVIRONMENTS_PATH + worker.environmentId() +
			                USERS_PATH + pingOneUserId +
			                CREDENTIALS_PATH + "/" + credentialId;

			URI uri = URI.create(theURI);

			request = new Request();
			request.setUri(uri).setMethod(HttpConstants.Methods.POST);

			addAuthorizationHeader(request, accessToken);

			request.getHeaders().put(ContentTypeHeader.NAME, REVOKE_CONTENT_TYPE);

			Response response = handler.handle(new RootContext(), request).getOrThrow();

			if (response.getStatus().isSuccessful()) {
				JsonValue responseJSON = json(response.getEntity().getJson());
				if(responseJSON.get(RESPONSE_STATUS).asString().equals(REVOKED)) {
					return RevokeResult.REVOKED;
				} else {
					throw new PingOneCredentialsServiceException("PingOne Credentials Revoke a User's Credential" +
					                    response.getStatus() + "-" + response.getEntity().getString());
				}
			} else if(response.getStatus().equals(Status.NOT_FOUND)) {
				// For the Not found outcome
				return RevokeResult.NOT_FOUND;
			} else {
				throw new PingOneCredentialsServiceException("PingOne Credentials Revoke a User's Credential" +
				                                             response.getStatus() + "-" + response.getEntity().getString());
			}
		} catch (Exception e) {
			throw new PingOneCredentialsServiceException("Failed PingOne Credentials" + e.getMessage());
		}
	}

	private JsonValue getResponse(Request request, String accessToken, String x) throws Exception {
		addAuthorizationHeader(request, accessToken);
		Response response = handler.handle(new RootContext(), request).getOrThrow();

		if (response.getStatus().isSuccessful()) {
			return json(response.getEntity().getJson());
		} else {
			throw new Exception(x + response.getStatus() + "-" + response.getEntity().getString());
		}
	}

	private static void addAuthorizationHeader(Request request, String accessToken) throws MalformedHeaderException {
		AuthorizationHeader header = new AuthorizationHeader();
		BearerToken bearerToken = new BearerToken(accessToken);
		header.setRawValue(BearerToken.NAME + " " + bearerToken);
		request.addHeaders(header);
	}
}
