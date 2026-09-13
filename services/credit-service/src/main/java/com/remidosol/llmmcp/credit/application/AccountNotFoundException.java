package com.remidosol.llmmcp.credit.application;

/** Lookup of an account that was never opened (no seed, no top-up); the API maps it to 404. */
public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(String userId) {
        super("Credit account not found: " + userId);
    }
}
