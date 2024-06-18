package org.forgerock.am.marketplace.pingonecredentials;

import static org.forgerock.am.marketplace.pingonecredentials.Constants.RESPONSE_STATUS;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.REVOKED;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.REVOKE_CONTENT_TYPE;
import static org.forgerock.am.marketplace.pingonecredentials.Constants.RevokeResult;
import static org.forgerock.json.JsonValue.json;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;


import javax.inject.Inject;
import javax.inject.Singleton;

import org.forgerock.http.HttpApplicationException;
import org.forgerock.http.handler.HttpClientHandler;
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
import org.forgerock.services.context.RootContext;
import org.forgerock.util.thread.listener.ShutdownManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Helper {
	private final Logger logger = LoggerFactory.getLogger(Helper.class);
	private final String loggerPrefix = "[PingOne Credentials Helper]" + PingOneCredentialsPlugin.logAppender;
	private final HttpClientHandler handler;

	@Inject
	public Helper(ShutdownManager shutdownManager) throws HttpApplicationException{
	    this.handler = new HttpClientHandler();
	    shutdownManager.addShutdownListener(() -> {
	      try {
	        handler.close();
	      } catch (IOException e) {
	        logger.error(loggerPrefix + " Could not close HTTP client", e);
	      }
	    });
	}

	protected JsonValue findWalletRequest(AccessToken accessToken, String domainSuffix,
	                                    String environmentId, String pingOneUID) throws Exception {
		Request request;

		try {
			String theURI = Constants.P1_BASE_URL +
			                domainSuffix +
			                "/v1/environments/" +
			                environmentId +
			                "/users/" +
			                pingOneUID +
			                "/digitalWallets";

			URI uri = URI.create(theURI);

			request = new Request();
			request.setUri(uri).setMethod(HttpConstants.Methods.GET);

			addAuthorizationHeader(request, accessToken);
			Response response = handler.handle(new RootContext(), request).getOrThrow();

			if (response.getStatus().isSuccessful()) {
				return json(response.getEntity().getJson());
			}
			else if(response.getStatus().equals(Status.NOT_FOUND)) {
				return null;
			} else {
				throw new Exception("PingOne Credentials Create a User Credential" + response.getStatus() + "-" + response.getEntity().getString());
			}
		} catch (Exception e) {
			throw new Exception("Failed PingOne Credentials", e);
		}
	}

	protected JsonValue credentialIssueRequest(AccessToken accessToken, String domainSuffix,
	                                           String environmentId, String pingOneUID, String credentialTypeId,
	                                           JsonValue attributes) throws Exception {
		Request request;

		try {
			String theURI = Constants.P1_BASE_URL +
			                domainSuffix +
			                "/v1/environments/" +
			                environmentId +
			                "/users/" +
			                pingOneUID +
			                "/credentials";

			logger.error("Attributes: " + attributes);

			URI uri = URI.create(theURI);

			JsonValue credentialBody = new JsonValue(new LinkedHashMap<String, Object>(1));

			JsonValue credentialTypeIdBody = new JsonValue(new LinkedHashMap<String, Object>(1));
			credentialTypeIdBody.add("id", credentialTypeId);

			credentialBody.put("credentialType", credentialTypeIdBody);
			credentialBody.put("data", attributes);

			request = new Request();
			request.setUri(uri).setMethod(HttpConstants.Methods.POST);

			if (credentialBody.isNotNull())
				request.getEntity().setJson(credentialBody);

			addAuthorizationHeader(request, accessToken);
			Response response = handler.handle(new RootContext(), request).getOrThrow();

			if (response.getStatus().isSuccessful()) {
				return json(response.getEntity().getJson());
			} else {
				throw new Exception("PingOne Credentials Create a User Credential" + response.getStatus() +
				                    "-" + response.getEntity().getString());
			}
		} catch (Exception e) {
			throw new Exception("Failed PingOne Credentials", e);
		}
	}

	protected JsonValue credentialUpdateRequest(AccessToken accessToken, String domainSuffix,
	                                           String environmentId, String pingOneUID, String credentialTypeId,
	                                           String credentialId, JsonValue attributes) throws Exception {
		Request request;

		try {
			String theURI = Constants.P1_BASE_URL +
			                domainSuffix +
			                "/v1/environments/" +
			                environmentId +
			                "/users/" +
			                pingOneUID +
			                "/credentials/" +
			                credentialId;

			logger.error("Attributes: " + attributes);

			URI uri = URI.create(theURI);

			JsonValue credentialBody = new JsonValue(new LinkedHashMap<String, Object>(1));

			JsonValue credentialTypeIdBody = new JsonValue(new LinkedHashMap<String, Object>(1));
			credentialTypeIdBody.add("id", credentialTypeId);

			credentialBody.put("credentialType", credentialTypeIdBody);
			credentialBody.put("data", attributes);

			request = new Request();
			request.setUri(uri).setMethod(HttpConstants.Methods.PUT);

			if (credentialBody.isNotNull())
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

	protected JsonValue createDigitalWalletRequest(AccessToken accessToken, String domainSuffix, String environmentId,
	                                        String pingOneUserId, String digitalWalletApplicationId,
	                                        List<String> notificationList) throws Exception {
		Request request;

		try {
			String theURI = Constants.P1_BASE_URL +
			                domainSuffix +
			                "/v1/environments/" +
			                environmentId +
			                "/users/" +
			                pingOneUserId +
			                "/digitalWallets";

			URI uri = URI.create(theURI);

			JsonValue body = new JsonValue(new LinkedHashMap<String, Object>(1));

			// Digital Wallet Application ID
			JsonValue applicationId = new JsonValue(new LinkedHashMap<String, Object>(1));
			applicationId.put("id", digitalWalletApplicationId);

			body.put("digitalWalletApplication", applicationId);

			if(!notificationList.isEmpty()) {
				JsonValue notification = new JsonValue(new LinkedHashMap<String, Object>(1));
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
				throw new Exception("PingOne Credentials Create a User Credential" +
				                    response.getStatus() + "-" + response.getEntity().getString());
			}
		} catch (Exception e) {
			throw new Exception("Failed PingOne Credentials", e);
		}
	}



	protected JsonValue readDigitalWallet(AccessToken accessToken, String domainSuffix, String environmentId,
	                                        String pingOneUserId, String digitalWalletId) throws Exception {
		Request request;

		try {
			String theURI = Constants.P1_BASE_URL +
			                domainSuffix +
			                "/v1/environments/" +
			                environmentId +
			                "/users/" +
			                pingOneUserId +
			                "/digitalWallets/" +
			                digitalWalletId;

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

	protected JsonValue createVerificationRequest(AccessToken accessToken, String domainSuffix, String environmentId,
	                                              String message, String credentialType,
	                                              List<String> attributeKeys) throws Exception {
		Request request;

		try {
			String theURI = Constants.P1_BASE_URL +
			                domainSuffix +
			                "/v1/environments/" +
			                environmentId +
			                "/presentationSessions";

			URI uri = URI.create(theURI);

			JsonValue body = new JsonValue(new LinkedHashMap<String, Object>(1));

			body.put("message", message);
			body.put("protocol", "NATIVE");

			JsonValue credential = new JsonValue(new LinkedHashMap<String, Object>(1));

			credential.put("type", credentialType);
			credential.put("keys", attributeKeys);

			JsonValue requestedCredentials = new JsonValue(new ArrayList<JsonValue>(1));
			requestedCredentials.add(credential);

			body.put("requestedCredentials", requestedCredentials);

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

	protected JsonValue createVerificationRequestPush(AccessToken accessToken, String domainSuffix, String environmentId,
	                                                  String message, String credentialType,
	                                                  List<String> attributeKeys, String applicationInstanceId,
	                                                  String digitalWalletApplicationId) throws Exception {
		Request request;

		try {
			String theURI = Constants.P1_BASE_URL +
			                domainSuffix +
			                "/v1/environments/" +
			                environmentId +
			                "/presentationSessions";

			URI uri = URI.create(theURI);

			logger.error("Application Instance ID: " + applicationInstanceId);
			logger.error("Digital Wallet Application ID: " + digitalWalletApplicationId);

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

			JsonValue requestedCredentials = new JsonValue(new ArrayList<JsonValue>(1));
			requestedCredentials.add(credential);

			body.put("requestedCredentials", requestedCredentials);

			logger.error("Body: " + body.toString());

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

	protected JsonValue readVerificationSession(AccessToken accessToken, String domainSuffix, String environmentId,
	                                            String sessionId) throws Exception {
		Request request;

		try {
			String theURI = Constants.P1_BASE_URL +
			                domainSuffix +
			                "/v1/environments/" +
			                environmentId +
			                "/presentationSessions/" +
			                sessionId;

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

	protected boolean deleteWalletRequest(AccessToken accessToken, String domainSuffix, String environmentId,
	                                      String pingOneUserId, String digitalWalletId) throws Exception {
		Request request;
		try {
			String theURI = Constants.P1_BASE_URL +
			                domainSuffix +
			                "/v1/environments/" +
			                environmentId +
			                "/users/" +
			                pingOneUserId +
			                "/digitalWallets/" +
			                digitalWalletId;

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
				throw new Exception("PingOne Credentials Create a User Credential" + response.getStatus() + "-" + response.getEntity().getString());
			}
		} catch (Exception e) {
			throw new Exception("Failed PingOne Credentials", e);
		}
	}

	protected RevokeResult revokeCredentialRequest(AccessToken accessToken, String domainSuffix, String environmentId,
	                                                         String pingOneUserId, String credentialId) throws Exception {
		Request request;
		try {

			String theURI = Constants.P1_BASE_URL +
			                domainSuffix +
			                "/v1/environments/" +
			                environmentId +
			                "/users/" +
			                pingOneUserId +
			                "/credentials/" +
			                credentialId;

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

	protected static void addAuthorizationHeader(Request request, AccessToken accessToken) throws MalformedHeaderException {
		AuthorizationHeader header = new AuthorizationHeader();
		BearerToken bearerToken = new BearerToken(accessToken.getTokenId());
		header.setRawValue(BearerToken.NAME + " " + bearerToken);
		request.addHeaders(header);
	}
	public static void main(String[] args) {}
}
