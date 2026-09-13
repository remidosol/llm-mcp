package com.remidosol.llmmcp.infra.google;

import io.cdktn.providers.google.sql_database.SqlDatabase;
import io.cdktn.providers.google.sql_database_instance.SqlDatabaseInstance;
import io.cdktn.providers.google.sql_database_instance.SqlDatabaseInstanceSettings;
import io.cdktn.providers.google.sql_user.SqlUser;
import software.constructs.Construct;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Managed Postgres alternative to the in-cluster CloudNativePG cluster (ADR-0020): one Cloud SQL
 * instance, one database + owner role per service, exactly like the reference project keeps its
 * database initialization in the common stack. Opt-in ({@code INFRA_CLOUD_SQL=true}); the
 * private-IP/VPC wiring and CNPG→Cloud SQL cut-over are out of scope for this project.
 */
public class CloudSqlPostgres extends Construct {

    private final SqlDatabaseInstance instance;
    private final Map<String, SqlUser> users = new LinkedHashMap<>();

    public CloudSqlPostgres(Construct scope, String id, String region, List<String> services, Map<String, String> passwords) {
        super(scope, id);
        this.instance = SqlDatabaseInstance.Builder.create(this, "instance")
                .name("llm-mcp")
                .region(region)
                .databaseVersion("POSTGRES_17")
                .deletionProtection(false)
                .settings(SqlDatabaseInstanceSettings.builder()
                        .tier("db-f1-micro")                          // demo: the smallest shared-core tier
                        .availabilityType("ZONAL")
                        .diskSize(10)
                        .build())
                .build();
        for (String service : services) {                             // job, credit, llm
            SqlUser user = SqlUser.Builder.create(this, service + "-user")
                    .instance(instance.getName())
                    .name(service)
                    .password(passwords.get(service))
                    .build();
            users.put(service, user);
            SqlDatabase.Builder.create(this, service + "-db")
                    .instance(instance.getName())
                    .name(service + "_db")
                    .dependsOn(List.of(user))
                    .build();
        }
    }

    public SqlDatabaseInstance instance() {
        return instance;
    }
}
