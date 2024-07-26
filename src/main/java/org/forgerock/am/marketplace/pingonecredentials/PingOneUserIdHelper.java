package org.forgerock.am.marketplace.pingonecredentials;

import org.apache.commons.lang.StringUtils;
import org.forgerock.json.JsonValue;
import org.forgerock.openam.auth.node.api.NodeState;

import javax.inject.Singleton;

import static org.forgerock.am.marketplace.pingonecredentials.Constants.OBJECT_ATTRIBUTES;

@Singleton
public class PingOneUserIdHelper {

    String getPingOneUserId(NodeState nodeState, String pingOneUserIdAttribute) throws PingOneCredentialsException {
        // Check if PingOne User ID attribute is set in sharedState
        String pingOneUserId = nodeState.isDefined(pingOneUserIdAttribute)
                               ? nodeState.get(pingOneUserIdAttribute).asString()
                               : null;

        // Check if PingOne User ID attribute is in objectAttributes
        if (StringUtils.isBlank(pingOneUserId)) {
            if (nodeState.isDefined(OBJECT_ATTRIBUTES)) {
                JsonValue objectAttributes = nodeState.get(OBJECT_ATTRIBUTES);

                pingOneUserId = objectAttributes.isDefined(pingOneUserIdAttribute)
                                ? objectAttributes.get(pingOneUserIdAttribute).asString()
                                : null;
            }
        }

        if (StringUtils.isBlank(pingOneUserId)) {
            throw new PingOneCredentialsException("Expected PingOne User ID to be set in sharedState.");
        }

        return pingOneUserId;
    }
}
