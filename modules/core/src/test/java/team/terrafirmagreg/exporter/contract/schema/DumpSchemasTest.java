package team.terrafirmagreg.exporter.contract.schema;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class DumpSchemasTest {

    private static final Set<String> DOCUMENTED = Set.of(
            "manifest",
            "meta/report",
            "registry/items",
            "registry/items/icons",
            "registry/items/tooltips",
            "registry/items/uses",
            "registry/blocks",
            "registry/blocks/icons",
            "registry/blocks/tooltips",
            "registry/blocks/uses",
            "registry/fluids",
            "registry/fluids/icons",
            "registry/fluids/tooltips",
            "registry/fluids/uses",
            "registry/entities",
            "registry/entities/tooltips",
            "registry/entities/uses",
            "registry/tabs",
            "registry/tags",
            "registry/recipe_categories",
            "registry/groups",
            "registry/recipes",
            "registry/catalog",
            "lang/catalog",
            "assets/models/model",
            "data/recipes/recipe",
            "data/index/recipes",
            "data/quests/data",
            "data/quests/chapter_groups",
            "data/quests/chapter",
            "data/guides/book",
            "data/guides/category",
            "data/guides/entry",
            "lang",
            "keybinds",
            "names");

    @Test
    void coversEveryDocumentedSchemaPath() {
        Set<String> emitted = DumpSchemas.all().stream().map(DumpSchema::id).collect(Collectors.toSet());
        assertEquals(DOCUMENTED, emitted);
    }

    @Test
    void idsAreUnique() {
        long distinct =
                DumpSchemas.all().stream().map(DumpSchema::id).distinct().count();
        assertEquals(DumpSchemas.all().size(), distinct);
    }

    @Test
    void generatesForEverySchema() {
        SchemaGenerator generator = new SchemaGenerator();
        for (DumpSchema schema : DumpSchemas.all()) {
            assertNotNull(generator.generate(schema), schema.id());
        }
    }
}
