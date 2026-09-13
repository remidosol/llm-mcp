package com.remidosol.llmmcp.infra;

/** Deployment environment identifiers used for resource naming, labels and OTel annotations. */
public enum Environment {
    DEVELOPMENT("development"), STAGING("staging"), PRODUCTION("production");

    private final String value;

    Environment(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
