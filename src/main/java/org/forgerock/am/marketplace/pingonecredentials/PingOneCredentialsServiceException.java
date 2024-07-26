package org.forgerock.am.marketplace.pingonecredentials;

/**
 * PingOne Credentials Service Exception.
 */
public class PingOneCredentialsServiceException extends Exception {

    /**
     * Exception constructor with error message.
     *
     * @param message The error message.
     */
    public PingOneCredentialsServiceException(String message) {
        super(message);
    }
}