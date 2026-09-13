package com.remidosol.llmmcp.contracts;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Loads the shipped JSON Schemas (draft 2020-12) and validates Jackson 3 trees against them. */
final class SchemaSupport {

    private static final SchemaRegistry REGISTRY = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);

    private SchemaSupport() {
    }

    static Schema envelopeSchema() {
        return load("envelope");
    }

    static Schema payloadSchema(String eventType) {
        return load(eventType);
    }

    static List<Error> validate(Schema schema, JsonNode node) {
        return schema.validate(node);
    }

    private static Schema load(String name) {
        // networknt 3.x: getSchema(String) takes the schema DOCUMENT, not a location
        String path = "/schemas/" + name + ".schema.json";
        try (InputStream in = SchemaSupport.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("missing schema: " + path);
            }
            return REGISTRY.getSchema(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
