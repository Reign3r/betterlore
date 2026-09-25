import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Executed from verifyReleaseJars with only public release archives available. */
public final class PackagedParserCheck {
    private record Glyph(int codePoint, int color, int flags) {}
    private record Example(String source, String migrated, String text, List<Integer> colors) {}

    // Frozen historical outputs; never compute expected colors using the implementation under test.
    private static final List<Example> EXAMPLES = List.of(
        new Example("<gr #ff6600 #aa1208>Titanfall</gr>",
            "<gr type:legacy_oklab #ff6600 #aa1208>Titanfall</gr>", "Titanfall",
            List.of(16672256, 16079105, 15420674, 14828035, 14170116, 13577989, 12985862, 12328711, 11737095)),
        new Example("<gr type:hvs #ff6600 #aa1208>ABCDEF</gr>",
            "<gr type:legacy_hsv #ff6600 #aa1208>ABCDEF</gr>", "ABCDEF",
            List.of(16737536, 15749889, 14828291, 13906949, 12986118, 12065799)),
        new Example("<hgr red blue green>ABCDE</hgr>",
            "<gr type:legacy_hard #ff5555 #5555ff #55ff55>ABCDE</gr>", "ABCDE",
            List.of(16733525, 16733525, 5592575, 5592575, 5635925)),
        new Example("<rb>A\uD83D\uDE00BCD</rb>",
            "<rb type:legacy_hsv>A\uD83D\uDE00BCD</rb>", "A\uD83D\uDE00BCD",
            List.of(16711680, 16776960, 65280, 65535, 255)),
        new Example("<gr #ff6600 #aa1208>A<gr blue green>BC</gr>D</gr>",
            "<gr type:legacy_oklab #ff6600 #aa1208>A<gr type:legacy_oklab blue green>BC</gr>D</gr>",
            "ABCD", List.of(16672256, 5592318, 5546666, 12460038))
    );

    public static void main(String[] args) throws Exception {
        int targets = 0;
        int assertions = 0;
        for (String row : Files.readAllLines(Path.of(args[0]))) {
            String[] target = row.split("\t", -1);
            if (target.length != 3) throw new AssertionError("Invalid target row: " + row);
            // No development classes or game libraries can accidentally satisfy a reference.
            try (var loader = new URLClassLoader(new URL[] {Path.of(target[1]).toUri().toURL()},
                    ClassLoader.getPlatformClassLoader())) {
                Class<?> engine = Class.forName(target[2], true, loader);
                assertions += check(engine);
                targets++;
                System.out.println("PASS packaged parser " + target[0]);
            } catch (Throwable error) {
                System.err.println("FAIL packaged parser " + target[0]);
                throw error;
            }
        }
        if (targets == 0) throw new AssertionError("No packaged implementations tested");
        System.out.println("Packaged parser checks passed: " + targets + " target selections, " + assertions + " cases.");
    }

    private static int check(Class<?> engine) throws Exception {
        int cases = 0;
        Method migrate = engine.getMethod("migrationCandidates", String.class, boolean.class);
        for (boolean name : List.of(false, true)) {
            Method parse = engine.getMethod(name ? "parseName" : "parseLore", String.class);
            for (Example example : EXAMPLES) {
                List<Glyph> expected = expected(example.text(), example.colors(), 1);
                equal(expected, glyphs(invoke(parse, "<b>" + example.migrated() + "</b>")), example.migrated());
                List<?> candidates = (List<?>) invoke(migrate, "<b>" + example.source() + "</b>", name);
                boolean matched = false;
                for (Object candidate : candidates) {
                    if (glyphs(invoke(parse, candidate)).equals(expected)) matched = true;
                }
                require(matched, "Missing faithful migration for " + example.source());
                cases += 2;
            }
            equal(expected("ABC", List.of(0xff0000, 0x8c53a2, 0x0000ff), 0),
                glyphs(invoke(parse, "<gr #ff0000 #0000ff>ABC</gr>")), "modern gradient");
            cases++;

            for (String malformed : List.of("<gr #ff6600 #aa1208>AB</wrong>",
                    "<gr #ff6600 #aa1208>AB", "<b><gr #ff6600 #aa1208>A</b>B</gr>",
                    "<rb>AB</wrong>", "<gr 0>hello</gr>", "<gr #ff6600 0 #aa1208>hello</gr>",
                    "before</gr>after", "<unknown>x</unknown>after")) {
                glyphs(invoke(parse, malformed));
                List<?> candidates = (List<?>) invoke(migrate, malformed, name);
                require(!candidates.isEmpty(), "No bounded migration candidate: " + malformed);
                for (Object candidate : candidates) glyphs(invoke(parse, candidate));
                cases++;
            }
            for (String literal : List.of("<gr 0>hello</gr>", "before</gr>after", "<unknown>x</unknown>after")) {
                String text = glyphs(invoke(parse, literal)).stream()
                    .collect(StringBuilder::new, (s, g) -> s.appendCodePoint(g.codePoint()), StringBuilder::append).toString();
                equal(literal, text, "malformed literal preservation");
                cases++;
            }
            Object oversized = invoke(parse, "x".repeat(4097));
            require(!(boolean) property(oversized, "isSuccess"), "Oversized input accepted");
            require(((List<?>) invoke(migrate, "x".repeat(4097), name)).isEmpty(), "Oversized input migrated");
            cases++;
        }
        return cases;
    }

    private static List<Glyph> expected(String text, List<Integer> colors, int flags) {
        int[] codePoints = text.codePoints().toArray();
        equal(codePoints.length, colors.size(), "fixture length");
        var output = new ArrayList<Glyph>();
        for (int i = 0; i < codePoints.length; i++) output.add(new Glyph(codePoints[i], colors.get(i), flags));
        return output;
    }

    private static List<Glyph> glyphs(Object result) throws Exception {
        require((boolean) property(result, "isSuccess"), "Parse failed: " + result);
        var output = new ArrayList<Glyph>();
        for (Object line : (List<?>) property(property(result, "document"), "lines")) {
            for (Object run : (List<?>) property(line, "runs")) {
                int color = (int) property(run, "rgb"), flags = (int) property(run, "flags");
                ((String) property(run, "text")).codePoints().forEach(cp -> output.add(new Glyph(cp, color, flags)));
            }
        }
        return output;
    }

    private static Object property(Object target, String name) throws Exception {
        return target.getClass().getMethod(name).invoke(target);
    }

    private static Object invoke(Method method, Object... args) throws Exception {
        try {
            return method.invoke(null, args);
        } catch (InvocationTargetException error) {
            if (error.getCause() instanceof Error cause) throw cause;
            if (error.getCause() instanceof Exception cause) throw cause;
            throw error;
        }
    }

    private static void equal(Object expected, Object actual, String context) {
        require(expected.equals(actual), context + ": expected " + expected + ", got " + actual);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
