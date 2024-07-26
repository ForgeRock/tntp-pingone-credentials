package org.forgerock.am.marketplace.pingonecredentials;

/**
 * PingOne Credentials Exception.
 */
public class PingOneCredentialsException extends Exception {

    /**
     * Exception constructor with error message.
     *
     * @param message The error message.
     */
    public PingOneCredentialsException(String message) {
        super(message);
    }
}