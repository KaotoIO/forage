package io.kaoto.forage.maven.catalog;

import java.util.Set;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaId;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

/**
 * JSON Schema Draft-07 validation helpers for the generated {@code propertiesSchema}.
 */
final class Draft07 {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final JsonSchemaFactory FACTORY = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
    private static final JsonSchema META_SCHEMA = FACTORY.getSchema(SchemaLocation.of(SchemaId.V7));

    private Draft07() {}

    /**
     * Validates a schema against the Draft-07 meta-schema.
     */
    static Set<ValidationMessage> metaSchemaErrors(Object schema) {
        return META_SCHEMA.validate(MAPPER.valueToTree(schema));
    }

    /**
     * Validates an instance against a schema.
     */
    static Set<ValidationMessage> errors(Object schema, Object instance) {
        JsonNode schemaNode = MAPPER.valueToTree(schema);
        return FACTORY.getSchema(schemaNode).validate(MAPPER.valueToTree(instance));
    }
}
