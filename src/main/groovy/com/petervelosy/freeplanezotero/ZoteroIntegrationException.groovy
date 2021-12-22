package com.petervelosy.freeplanezotero

class ZoteroIntegrationException extends Exception {
    ZoteroIntegrationException(String message) {
        super(message)
    }
    ZoteroIntegrationException(String message, Throwable cause) {
        super(message, cause)
    }
}