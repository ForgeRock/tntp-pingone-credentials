package org.forgerock.am.marketplace.pingonecredentials;

import static org.forgerock.am.marketplace.pingonecredentials.Constants.CREDENTIALS_PATH;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.DIGITAL_WALLETS_PATH;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.ENVIRONMENTS_PATH;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.PRESENTATION_SESSIONS_PATH;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.RESPONSE_STATUS;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.REVOKED;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.REVOKE_CONTENT_TYPE;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.RevokeResult;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.SESSION_DATA_PATH;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.USERS_PATH;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.object;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

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
import org.forgerock.openam.integration.pingone.PingOneWorkerConfig;
import org.forgerock.services.context.RootContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Singleton
public class PingOneCredentialsService {
	private final Handler handler;

	private final Logger logger = LoggerFactory.getLogger(PingOneCredentialsService.class);

	@Inject
	public PingOneCredentialsService(@Named("CloseableHttpClientHandler") org.forgerock.http.Handler handler) {
	    this.handler = handler;
	}

	JsonValue findWalletRequest(AccessToken accessToken, PingOneWorkerConfig.Worker worker, String pingOneUID) throws Exception {
		Request request;

		try {
			String theURI = worker.apiUrl() +
			                ENVIRONMENTS_PATH + worker.environmentId() +
			                USERS_PATH + pingOneUID +
			                DIGITAL_WALLETS_PATH;

			URI uri = URI.create(theURI);

			request = new Request();
			request.setUri(uri).setMethod(HttpConstants.Methods.GET);

			addAuthorizationHeader(request, accessToken);
			Response response = handler.handle(new RootContext(), request).getOrThrow();

			logger.debug("response: " + response.getEntity().getJson());

			if (response.getStatus().isSuccessful()) {
				return json(response.getEntity().getJson());
			} else {
				throw new Exception("PingOne Credentials Find a Wallet" + response.getStatus() +
				                    "-" + response.getEntity().getString());
			}
		} catch (Exception e) {
			throw new Exception("Failed PingOne Credentials", e);
		}
	}

	JsonValue credentialIssueRequest(AccessToken accessToken, PingOneWorkerConfig.Worker worker, String pingOneUID,
	                                 String credentialTypeId, JsonValue attributes) throws Exception {
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

			addAuthorizationHeader(request, accessToken);
			Response response = handler.handle(new RootContext(), request).getOrThrow();

			if (response.getStatus().isSuccessful()) {
				return json(response.getEntity().getJson());
			} else {
				throw new Exception("PingOne Credentials Issue a User Credential" + response.getStatus() +
				                    "-" + response.getEntity().getString());
			}
		} catch (Exception e) {
			throw new Exception("Failed PingOne Credentials", e);
		}
	}

	JsonValue credentialUpdateRequest(AccessToken accessToken, PingOneWorkerConfig.Worker worker, String pingOneUID,
	                                  String credentialTypeId, String credentialId,
	                                  JsonValue attributes) throws Exception {
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

			addAuthorizationHeader(request, accessToken);
			Response response = handler.handle(new RootContext(), request).getOrThrow();

			if (response.getStatus().isSuccessful()) {
				return json(response.getEntity().getJson());
			} else {
				throw new Exception("PingOne Credentials Update a User Credential" + response.getStatus() +
				                    "-" + response.getEntity().getString());
			}
		} catch (Exception e) {
			throw new Exception("Failed PingOne Credentials", e);
		}
	}

	JsonValue createDigitalWalletRequest(AccessToken accessToken, PingOneWorkerConfig.Worker worker,
	                                        String pingOneUserId, String digitalWalletApplicationId,
	                                        List<String> notificationList) throws Exception {
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

			addAuthorizationHeader(request, accessToken);
			Response response = handler.handle(new RootContext(), request).getOrThrow();

			if (response.getStatus().isSuccessful()) {
				return json(response.getEntity().getJson());
			} else {
				throw new Exception("PingOne Credentials Create a Digital Wallet" +
				                    response.getStatus() + "-" + response.getEntity().getString());
			}
		} catch (Exception e) {
			throw new Exception("Failed PingOne Credentials", e);
		}
	}

	JsonValue readDigitalWallet(AccessToken accessToken, PingOneWorkerConfig.Worker worker,
	                            String pingOneUserId, String digitalWalletId) throws Exception {
		Request request;

		try {
			String theURI = worker.apiUrl() +
			                ENVIRONMENTS_PATH + worker.environmentId() +
			                USERS_PATH + pingOneUserId +
			                DIGITAL_WALLETS_PATH + "/" + digitalWalletId;

			URI uri = URI.create(theURI);

			request = new Request();
			request.setUri(uri).setMethod(HttpConstants.Methods.GET);

			addAuthorizationHeader(request, accessToken);
			Response response = handler.handle(new RootContext(), request).getOrThrow();

			if (response.getStatus().isSuccessful()) {
				return json(response.getEntity().getJson());
			} else {
				throw new Exception("PingOne Credentials Read a Digital Wallet" + response.getStatus() + "-" + response.getEntity().getString());
			}
		} catch (Exception e) {
			throw new Exception("Failed PingOne Credentials", e);
		}
	}

	JsonValue createVerificationRequest(AccessToken accessToken, PingOneWorkerConfig.Worker worker,
	                                    String message, String credentialType, List<String> attributeKeys,
	                                    JsonValue customCredentialsPayload) throws Exception {
		Request request;

		try {
			String theURI = worker.apiUrl() +
			                ENVIRONMENTS_PATH + worker.environmentId() +
			                PRESENTATION_SESSIONS_PATH;

			URI uri = URI.create(theURI);

			JsonValue body = json(object(1));

			body.put("message", message);
			body.put("protocol", "NATIVE");

			JsonValue credential = json(object(1));

			credential.put("type", credentialType);
			credential.put("keys", attributeKeys);

			if(customCredentialsPayload != null && customCredentialsPayload.isNotNull()) {
				body.put("requestedCredentials", customCredentialsPayload);
			} else {
				JsonValue requestedCredentials = new JsonValue(new ArrayList<JsonValue>(1));
				requestedCredentials.add(credential);

				body.put("requestedCredentials", requestedCredentials);
			}

			request = new Request();
			request.setUri(uri).setMethod(HttpConstants.Methods.POST);

			if (body.isNotNull())
				request.getEntity().setJson(body);

			addAuthorizationHeader(request, accessToken);
			Response response = handler.handle(new RootContext(), request).getOrThrow();

			if (response.getStatus().isSuccessful()) {
				return json(response.getEntity().getJson());
			} else {
				throw new Exception("PingOne Credentials Create Verification session" +
				                    response.getStatus() + "-" + response.getEntity().getString());
			}
		} catch (Exception e) {
			throw new Exception("Failed PingOne Credentials", e);
		}
	}

	JsonValue createVerificationRequestPush(AccessToken accessToken, PingOneWorkerConfig.Worker worker,
	                                                  String message, String credentialType,
	                                                  List<String> attributeKeys, String applicationInstanceId,
	                                                  String digitalWalletApplicationId,
	                                                  JsonValue customCredentialsPayload) throws Exception {
		Request request;

		try {
			String theURI = worker.apiUrl() +
			                ENVIRONMENTS_PATH + worker.environmentId() +
			                PRESENTATION_SESSIONS_PATH;

			URI uri = URI.create(theURI);

			JsonValue body = new JsonValue(new LinkedHashMap<String, Object>(1));

			body.put("message", message);
			body.put("protocol", "NATIVE");

			JsonValue applicationInstance = new JsonValue(new LinkedHashMap<String, Object>(1));

			applicationInstance.put("id", applicationInstanceId);
			body.put("applicationInstance", applicationInstance);

			JsonValue digitalWalletApplication = new JsonValue(new LinkedHashMap<String, Object>(1));

			digitalWalletApplication.put("id", digitalWalletApplicationId);
			body.put("digitalWalletApplication", digitalWalletApplication);

			JsonValue credential = new JsonValue(new LinkedHashMap<String, Object>(1));

			credential.put("type", credentialType);
			credential.put("keys", attributeKeys);

			if(customCredentialsPayload.isNotNull()) {
				body.put("requestedCredentials", customCredentialsPayload);
			} else {
				JsonValue requestedCredentials = new JsonValue(new ArrayList<JsonValue>(1));
				requestedCredentials.add(credential);

				body.put("requestedCredentials", requestedCredentials);
			}

			request = new Request();
			request.setUri(uri).setMethod(HttpConstants.Methods.POST);

			if (body.isNotNull())
				request.getEntity().setJson(body);

			addAuthorizationHeader(request, accessToken);
			Response response = handler.handle(new RootContext(), request).getOrThrow();

			if (response.getStatus().isSuccessful()) {
				return json(response.getEntity().getJson());
			} else {
				throw new Exception("PingOne Credentials Create Push Verification session" +
				                    response.getStatus() + "-" + response.getEntity().getString());
			}
		} catch (Exception e) {
			throw new Exception("Failed PingOne Credentials", e);
		}
	}

	JsonValue readVerificationSession(AccessToken accessToken, PingOneWorkerConfig.Worker worker,
	                                  String sessionId) throws Exception {
		Request request;

		try {
			String theURI = worker.apiUrl() +
			                ENVIRONMENTS_PATH + worker.environmentId()  +
			                PRESENTATION_SESSIONS_PATH + "/" + sessionId +
			                SESSION_DATA_PATH;

			URI uri = URI.create(theURI);

			request = new Request();
			request.setUri(uri).setMethod(HttpConstants.Methods.GET);

			addAuthorizationHeader(request, accessToken);
			Response response = handler.handle(new RootContext(), request).getOrThrow();

			if (response.getStatus().isSuccessful()) {
				return json(response.getEntity().getJson());
			} else {
				throw new Exception("PingOne Credentials Read a Verification Session" +
				                    response.getStatus() + "-" + response.getEntity().getString());
			}
		} catch (Exception e) {
			throw new Exception("Failed PingOne Credentials", e);
		}
	}

	boolean deleteWalletRequest(AccessToken accessToken, PingOneWorkerConfig.Worker worker,
	                            String pingOneUserId, String digitalWalletId) throws Exception {
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
				throw new Exception("PingOne Credentials Delete a Digital Wallet" + response.getStatus() + "-" + response.getEntity().getString());
			}
		} catch (Exception e) {
			throw new Exception("Failed PingOne Credentials", e);
		}
	}

	RevokeResult revokeCredentialRequest(AccessToken accessToken, PingOneWorkerConfig.Worker worker,
	                                     String pingOneUserId, String credentialId) throws Exception {
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
					throw new Exception("PingOne Credentials Revoke a User's Credential" +
					                    response.getStatus() + "-" + response.getEntity().getString());
				}
			} else if(response.getStatus().equals(Status.NOT_FOUND)) {
				// For the Not found outcome
				return RevokeResult.NOT_FOUND;
			} else {
				throw new Exception("PingOne Credentials Revoke a User's Credential" + response.getStatus() +
				                    "-" + response.getEntity().getString());
			}
		} catch (Exception e) {
			throw new Exception("Failed PingOne Credentials", e);
		}
	}

	private static void addAuthorizationHeader(Request request, AccessToken accessToken) throws MalformedHeaderException {
		AuthorizationHeader header = new AuthorizationHeader();
		BearerToken bearerToken = new BearerToken(accessToken.getTokenId());
		header.setRawValue(BearerToken.NAME + " " + bearerToken);
		request.addHeaders(header);
	}
}
