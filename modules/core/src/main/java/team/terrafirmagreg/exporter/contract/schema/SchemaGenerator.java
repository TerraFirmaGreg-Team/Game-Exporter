package team.terrafirmagreg.exporter.contract.schema;

import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.classmate.members.RawMethod;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.victools.jsonschema.generator.CustomDefinition;
import com.github.victools.jsonschema.generator.MemberScope;
import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonOption;
import com.github.victools.jsonschema.module.jackson.JacksonSchemaModule;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import team.terrafirmagreg.exporter.contract.id.ResourceId;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

public final class SchemaGenerator {
    private static final String DRAFT_2020_12 = "https://json-schema.org/draft/2020-12/schema";
    private static final String NULLABLE_SUFFIX = "-nullable";

    private final com.github.victools.jsonschema.generator.SchemaGenerator generator;

    public SchemaGenerator() {
        SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(
                        SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON)
                .with(
                        Option.DEFINITIONS_FOR_ALL_OBJECTS,
                        Option.PLAIN_DEFINITION_KEYS,
                        Option.FORBIDDEN_ADDITIONAL_PROPERTIES_BY_DEFAULT,
                        Option.MAP_VALUES_AS_ADDITIONAL_PROPERTIES,
                        Option.NONSTATIC_NONVOID_NONGETTER_METHODS,
                        Option.FIELDS_DERIVED_FROM_ARGUMENTFREE_METHODS)
                .with(new JacksonSchemaModule(
                        JacksonOption.FLATTENED_ENUMS_FROM_JSONVALUE,
                        JacksonOption.RESPECT_JSONPROPERTY_ORDER,
                        JacksonOption.RESPECT_JSONPROPERTY_REQUIRED,
                        JacksonOption.INCLUDE_ONLY_JSONPROPERTY_ANNOTATED_METHODS));
        configBuilder.forTypesInGeneral().withCustomDefinitionProvider((type, context) -> {
            if (JsonNode.class.isAssignableFrom(type.getErasedType())) {
                ObjectNode node = context.getGeneratorConfig().createObjectNode();
                node.put("additionalProperties", true);
                return new CustomDefinition(node);
            }
            if (ResourceId.class.isAssignableFrom(type.getErasedType())) {
                ObjectNode node = context.getGeneratorConfig().createObjectNode();
                node.put("type", "string");
                node.put("pattern", "^[^:]+:.+$");
                return new CustomDefinition(node);
            }
            if (type.isInstanceOf(Map.class)) {
                ObjectNode node = context.getGeneratorConfig().createObjectNode();
                node.put("type", "object");
                return new CustomDefinition(
                        node, CustomDefinition.DefinitionType.INLINE, CustomDefinition.AttributeInclusion.YES);
            }
            return null;
        });
        configBuilder.forTypesInGeneral().withTypeAttributeOverride((node, scope, context) -> {
            if (hasAnyGetter(scope.getType())) {
                node.put("additionalProperties", true);
            }
        });
        configBuilder.forFields().withIgnoreCheck(SchemaGenerator::isIgnored);
        configBuilder.forMethods().withIgnoreCheck(method -> {
            JsonProperty property = method.getAnnotation(JsonProperty.class);
            if (property != null && property.access() == JsonProperty.Access.WRITE_ONLY) {
                return true;
            }
            return method.getAnnotation(JsonIgnore.class) != null;
        });
        configBuilder.forFields().withNullableCheck(SchemaGenerator::isNullable);
        configBuilder.forMethods().withNullableCheck(SchemaGenerator::isNullable);
        configBuilder.forFields().withRequiredCheck(SchemaGenerator::isRequired);
        configBuilder.forMethods().withRequiredCheck(SchemaGenerator::isRequired);
        generator = new com.github.victools.jsonschema.generator.SchemaGenerator(configBuilder.build());
    }

    private static boolean isIgnored(MemberScope<?, ?> member) {
        JsonProperty property = member.getAnnotationConsideringFieldAndGetter(JsonProperty.class);
        if (property != null && property.access() == JsonProperty.Access.WRITE_ONLY) {
            return true;
        }
        if (member.getAnnotationConsideringFieldAndGetter(JsonAnyGetter.class) != null) {
            return true;
        }
        return member.getAnnotationConsideringFieldAndGetter(JsonIgnore.class) != null;
    }

    private static boolean hasAnyGetter(ResolvedType type) {
        if (type == null) {
            return false;
        }
        for (RawMethod method : type.getMemberMethods()) {
            if (method.getRawMember().getAnnotation(JsonAnyGetter.class) != null) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNullable(MemberScope<?, ?> member) {
        if (member.getAnnotationConsideringFieldAndGetter(Nullable.class) != null) {
            return true;
        }
        AnnotatedType annotated = null;
        Member raw = member.getRawMember();
        if (raw instanceof Field field) {
            annotated = field.getAnnotatedType();
        } else if (raw instanceof Method method) {
            annotated = method.getAnnotatedReturnType();
        }
        return annotated != null && annotated.getAnnotation(Nullable.class) != null;
    }

    private static boolean isRequired(MemberScope<?, ?> member) {
        if (member.getType().getErasedType() == Optional.class || isNullable(member)) {
            return false;
        }
        JsonInclude include = member.getAnnotationConsideringFieldAndGetter(JsonInclude.class);
        if (include == null) {
            include = member.getDeclaringType().getErasedType().getAnnotation(JsonInclude.class);
        }
        return include == null
                || include.value() == JsonInclude.Include.ALWAYS
                || include.value() == JsonInclude.Include.USE_DEFAULTS;
    }

    public JsonNode generate(DumpSchema schema) {
        ObjectNode result = generator.generateSchema(schema.type());
        inlineNullableDefinitions(result);
        result.put("$schema", DRAFT_2020_12);
        result.put("$id", schema.id());
        if (schema.schemaConst() != null) {
            JsonNode properties = result.get("properties");
            if (properties instanceof ObjectNode objectProperties) {
                ObjectNode schemaProperty = result.objectNode();
                schemaProperty.put("const", schema.schemaConst());
                objectProperties.set("schema", schemaProperty);
            }
        }
        return result;
    }

    private static void inlineNullableDefinitions(ObjectNode root) {
        JsonNode defs = root.get("$defs");
        if (!(defs instanceof ObjectNode definitions)) {
            return;
        }
        for (String name : new ArrayList<>(definitions.propertyNames())) {
            JsonNode definition = definitions.get(name);
            if (!name.endsWith(NULLABLE_SUFFIX) || !(definition instanceof ObjectNode wrapper)) {
                continue;
            }
            JsonNode anyOf = wrapper.get("anyOf");
            if (!(anyOf instanceof ArrayNode)) {
                continue;
            }
            replaceRef(root, "#/$defs/" + name, wrapper);
            definitions.remove(name);
        }
    }

    private static void replaceRef(JsonNode node, String ref, ObjectNode replacement) {
        if (node instanceof ObjectNode object) {
            JsonNode refValue = object.get("$ref");
            if (refValue != null && ref.equals(refValue.asString()) && object.size() == 1) {
                object.removeAll();
                object.setAll(replacement);
                return;
            }
            object.properties().forEach(entry -> replaceRef(entry.getValue(), ref, replacement));
        } else if (node instanceof ArrayNode array) {
            array.forEach(child -> replaceRef(child, ref, replacement));
        }
    }
}
