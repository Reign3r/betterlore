package com.reign.betterlore.lore;

import com.google.gson.*;
import net.minecraft.network.chat.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Frozen outputs from the former Fabric parser, without loading or depending on it in tests. */
class LegacyGradientCompatibilityTest {
    record Example(String source, List<Component> lines) {
        @Override public String toString() { return source; }
    }
    static Stream<Example> examples() {
        List<Example> examples = new ArrayList<>();
        for (JsonElement element : JsonParser.parseString(SNAPSHOTS).getAsJsonArray()) {
            JsonObject item = element.getAsJsonObject();
            List<Component> lines = new ArrayList<>();
            MutableComponent line = Component.empty();
            for (JsonElement value : item.getAsJsonArray("characters")) {
                JsonArray character = value.getAsJsonArray();
                int cp = character.get(0).getAsInt(), rgb = character.get(1).getAsInt(), flags = character.get(2).getAsInt();
                if (cp == 10) { lines.add(line); line = Component.empty(); continue; }
                line.append(Component.literal(Character.toString(cp)).withStyle(Style.EMPTY.withColor(rgb)
                        .withBold((flags & 1) != 0).withItalic((flags & 2) != 0).withUnderlined((flags & 4) != 0)
                        .withStrikethrough((flags & 8) != 0).withObfuscated((flags & 16) != 0)));
            }
            lines.add(line);
            examples.add(new Example(item.get("source").getAsString(), List.copyOf(lines)));
        }
        return examples.stream();
    }
    @ParameterizedTest
    @MethodSource("examples")
    void historicalGradientKeepsEveryCodePointAndColor(Example example) {
        String migrated = LoreMigration.matchingMarkup(example.source(), example.lines(), false);
        assertNotNull(migrated, example.source());
        assertTrue(migrated.contains("<gr") || migrated.contains("<hgr") || migrated.contains("<rb"));
        assertTrue(LoreMigration.equivalent(example.lines(), LoreComponents.toComponents(LoreMarkupParser.parse(migrated).document())));
        assertEquals(migrated, LoreMigration.matchingMarkup(migrated, example.lines(), false));
    }
    private static final String SNAPSHOTS =
            "[{\"source\":\"<gr #ff6600 #aa1208>Titanfall</gr>\",\"characters\":[[84,16672256,0],[105,16079105,0],[116,15420674,0],[97,14828035,0],[110,14170116,0],[102,13577989,0],[97,12985862,0],[108,12328711,0],[108,11737095,0]]}" +
            ",{\"source\":\"<gr type:hsv #ff6600 #aa1208>ABCDEF</gr>\",\"characters\":[[65,16672256,0],[66,15749890,0],[67,14828035,0],[68,13906693,0],[69,12985862,0],[70,12000007,0]]}" +
            ",{\"source\":\"<gr type:hvs #ff6600 #aa1208>ABCDEF</gr>\",\"characters\":[[65,16737536,0],[66,15749889,0],[67,14828291,0],[68,13906949,0],[69,12986118,0],[70,12065799,0]]}" +
            ",{\"source\":\"<hgr red blue green>ABCDE</hgr>\",\"characters\":[[65,16733525,0],[66,16733525,0],[67,5592575,0],[68,5592575,0],[69,5635925,0]]}" +
            ",{\"source\":\"<rb>A\\ud83d\\ude00BCD</rb>\",\"characters\":[[65,16711680,0],[128512,16776960,0],[66,65280,0],[67,65535,0],[68,255,0]]}" +
            ",{\"source\":\"<gr #ff6600 #aa1208>A\\nB</gr>\",\"characters\":[[65,16672256,0],[10,14828035,0],[66,12985862,0]]}" +
            ",{\"source\":\"<gr #ff6600 #aa1208>A\\n</gr>B\",\"characters\":[[65,16672256,0],[10,13906693,0],[66,11184810,0]]}" +
            ",{\"source\":\"<gr #ff6600 #aa1208>A<c gray>\\ud83d\\ude00B</c>CD</gr>\",\"characters\":[[65,16672256,0],[128512,11184810,0],[66,11184810,0],[67,12197127,0],[68,11145479,0]]}" +
            ",{\"source\":\"<gr red blue green>ABCDEF</gr>\",\"characters\":[[65,16667988,0],[66,12343430,0],[67,8673470,0],[68,5592318,0],[69,5539526,0],[70,5554062,0]]}" +
            ",{\"source\":\"<gr #ff6600 #aa1208>A<c gray>B</c>C</gr>\",\"characters\":[[65,16672256,0],[66,11184810,0],[67,12985862,0]]}" +
            ",{\"source\":\"<gr #ff6600 #aa1208>A<gr blue green>BC</gr>D</gr>\",\"characters\":[[65,16672256,0],[66,5592318,0],[67,5546666,0],[68,12460038,0]]}" +
            ",{\"source\":\"<rb f:0.5 s:0.6 o:0.2>ABCD</rb>\",\"characters\":[[65,14745445,0],[66,8716133,0],[67,6684579,0],[68,6684671,0]]}]";
}
