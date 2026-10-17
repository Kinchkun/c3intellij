package org.c3lang.intellij;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads the {@code project.json} manifest of a C3 project.
 */
public final class C3ProjectManifest
{
    private C3ProjectManifest()
    {
    }

    /**
     * Returns the names of all targets of type {@code "executable"} declared in the
     * {@code project.json} found in the given directory. Returns an empty list if the
     * manifest is missing or cannot be parsed.
     */
    public static List<String> findExecutableTargets(String projectDirectory)
    {
        List<String> result = new ArrayList<>();
        if (projectDirectory == null || projectDirectory.isEmpty()) return result;

        Path manifest = Path.of(projectDirectory, "project.json");
        if (!Files.isRegularFile(manifest)) return result;

        try
        {
            JsonElement root = JsonParser.parseString(Files.readString(manifest));
            if (!root.isJsonObject()) return result;

            JsonElement targets = root.getAsJsonObject().get("targets");
            if (targets == null || !targets.isJsonObject()) return result;

            for (Map.Entry<String, JsonElement> entry : targets.getAsJsonObject().entrySet())
            {
                JsonElement value = entry.getValue();
                if (!value.isJsonObject()) continue;

                JsonObject target = value.getAsJsonObject();
                JsonElement type = target.get("type");
                if (type != null && type.isJsonPrimitive() && "executable".equals(type.getAsString()))
                {
                    result.add(entry.getKey());
                }
            }
        }
        catch (Exception ignored)
        {
            // Malformed or unreadable manifest: fall back to whatever we collected.
        }

        return result;
    }

    /**
     * The target of {@code project.json} that compiles {@code file} (a path relative to the project
     * directory): the first target of the given {@code type} (e.g. {@code "executable"}, or
     * {@code null} for any) whose own {@code "sources"} — or {@code "test-sources"} when
     * {@code testSources} is set — match it, as in a workspace whose apps live in
     * {@code apps/<name>/src/**}. Returns {@code null} when no target claims the file (e.g. it is only
     * covered by the top-level sources) so {@code c3c} falls back to its default target.
     */
    public static @Nullable String findTargetForFile(String projectDirectory, @NotNull String file,
                                                     @Nullable String type, boolean testSources)
    {
        if (projectDirectory == null || projectDirectory.isEmpty()) return null;

        Path manifest = Path.of(projectDirectory, "project.json");
        if (!Files.isRegularFile(manifest)) return null;

        Path relative = Path.of(file.replace('\\', '/')).normalize();
        try
        {
            JsonElement root = JsonParser.parseString(Files.readString(manifest));
            if (!root.isJsonObject()) return null;

            JsonElement targets = root.getAsJsonObject().get("targets");
            if (targets == null || !targets.isJsonObject()) return null;

            for (Map.Entry<String, JsonElement> entry : targets.getAsJsonObject().entrySet())
            {
                if (!entry.getValue().isJsonObject()) continue;
                JsonObject target = entry.getValue().getAsJsonObject();

                JsonElement targetType = target.get("type");
                if (type != null && (targetType == null || !targetType.isJsonPrimitive()
                        || !type.equals(targetType.getAsString()))) continue;

                for (String pattern : stringArray(target.get(testSources ? "test-sources" : "sources")))
                {
                    if (matchesSourcePattern(pattern, relative)) return entry.getKey();
                }
            }
        }
        catch (Exception ignored)
        {
            // Malformed or unreadable manifest: no target.
        }

        return null;
    }

    /** Whether a {@code project.json} source pattern ({@code src/**}, {@code src/*.c3}, a plain path) matches {@code file}. */
    private static boolean matchesSourcePattern(@NotNull String pattern, @NotNull Path file)
    {
        String glob = pattern.replace('\\', '/');
        if (glob.startsWith("./")) glob = glob.substring(2);
        if (FileSystems.getDefault().getPathMatcher("glob:" + glob).matches(file)) return true;
        // A directory entry (`src` or `src/`) covers everything below it.
        String dir = glob.endsWith("/") ? glob.substring(0, glob.length() - 1) : glob;
        return !dir.contains("*") && file.startsWith(Path.of(dir));
    }

    /**
     * The {@code "output"} directory declared in {@code project.json} (where {@code c3c} writes built
     * binaries), or {@code "build"} when unspecified or unreadable — the C3 default.
     */
    public static @NotNull String outputDirectory(String projectDirectory)
    {
        String fallback = "build";
        if (projectDirectory == null || projectDirectory.isEmpty()) return fallback;

        Path manifest = Path.of(projectDirectory, "project.json");
        if (!Files.isRegularFile(manifest)) return fallback;

        try
        {
            JsonElement root = JsonParser.parseString(Files.readString(manifest));
            if (root.isJsonObject())
            {
                JsonElement output = root.getAsJsonObject().get("output");
                if (output != null && output.isJsonPrimitive() && !output.getAsString().isBlank())
                {
                    return output.getAsString();
                }
            }
        }
        catch (Exception ignored)
        {
            // Fall through to the default.
        }

        return fallback;
    }

    /**
     * The libraries listed under {@code "dependencies"} in {@code project.json}, located the way
     * {@code c3c} does: {@code <dir>/<name>.c3l} for each {@code "dependency-search-paths"} entry
     * (default {@code ["lib"]}), resolved against the project directory. A {@code .c3l} may be a ZIP
     * archive or a plain directory (e.g. {@code ../mystd/mystd.c3l}). Dependencies that can't be
     * found are skipped; the first search path that has a dependency wins.
     */
    public static @NotNull List<Path> dependencyLibraries(String projectDirectory)
    {
        List<Path> result = new ArrayList<>();
        if (projectDirectory == null || projectDirectory.isEmpty()) return result;

        Path base = Path.of(projectDirectory);
        Path manifest = base.resolve("project.json");
        if (!Files.isRegularFile(manifest)) return result;

        try
        {
            JsonElement root = JsonParser.parseString(Files.readString(manifest));
            if (!root.isJsonObject()) return result;
            JsonObject object = root.getAsJsonObject();

            List<String> searchPaths = stringArray(object.get("dependency-search-paths"));
            if (searchPaths.isEmpty()) searchPaths = List.of("lib");

            for (String dependency : stringArray(object.get("dependencies")))
            {
                for (String searchPath : searchPaths)
                {
                    Path library = base.resolve(searchPath).resolve(dependency + ".c3l").normalize();
                    if (Files.exists(library))
                    {
                        if (!result.contains(library)) result.add(library);
                        break;
                    }
                }
            }
        }
        catch (Exception ignored)
        {
            // Malformed or unreadable manifest: fall back to whatever we collected.
        }

        return result;
    }

    /** A target of {@code project.json}: its name, {@code "type"} and own (test) source patterns. */
    public record Target(@NotNull String name, @NotNull String type,
                         @NotNull List<String> sources, @NotNull List<String> testSources)
    {
        public boolean isExecutable()
        {
            return "executable".equals(type);
        }
    }

    /**
     * All targets declared in the {@code project.json} of {@code projectDirectory}, in declaration
     * order. A target without its own {@code "test-sources"} inherits the top-level ones, as in
     * {@code c3c}. Returns an empty list if the manifest is missing or cannot be parsed.
     */
    public static @NotNull List<Target> targets(String projectDirectory)
    {
        List<Target> result = new ArrayList<>();
        JsonObject root = readManifest(projectDirectory);
        if (root == null) return result;

        JsonElement targets = root.get("targets");
        if (targets == null || !targets.isJsonObject()) return result;

        List<String> defaultTestSources = stringArray(root.get("test-sources"));
        for (Map.Entry<String, JsonElement> entry : targets.getAsJsonObject().entrySet())
        {
            if (!entry.getValue().isJsonObject()) continue;
            JsonObject target = entry.getValue().getAsJsonObject();
            JsonElement type = target.get("type");
            List<String> testSources = target.has("test-sources")
                ? stringArray(target.get("test-sources"))
                : defaultTestSources;
            result.add(new Target(
                entry.getKey(),
                type != null && type.isJsonPrimitive() ? type.getAsString() : "",
                stringArray(target.get("sources")),
                testSources));
        }
        return result;
    }

    /**
     * Adds a target to the {@code project.json} of {@code projectDirectory}, keeping all other
     * entries. The manifest is rewritten pretty-printed.
     *
     * @throws IOException when the manifest is missing, unreadable or already declares {@code name}
     */
    public static void addTarget(@NotNull String projectDirectory, @NotNull Target target) throws IOException
    {
        JsonObject root = requireManifest(projectDirectory);
        JsonElement targets = root.get("targets");
        if (targets == null || !targets.isJsonObject())
        {
            targets = new JsonObject();
            root.add("targets", targets);
        }
        if (targets.getAsJsonObject().has(target.name()))
        {
            throw new IOException("Target '" + target.name() + "' already exists in project.json");
        }

        JsonObject entry = new JsonObject();
        entry.addProperty("type", target.type());
        entry.add("sources", toArray(target.sources()));
        if (!target.testSources().isEmpty()) entry.add("test-sources", toArray(target.testSources()));
        targets.getAsJsonObject().add(target.name(), entry);
        writeManifest(projectDirectory, root);
    }

    /** Adds {@code library} to the {@code "dependencies"} of {@code project.json}, unless already listed. */
    public static void addDependency(@NotNull String projectDirectory, @NotNull String library) throws IOException
    {
        JsonObject root = requireManifest(projectDirectory);
        List<String> dependencies = stringArray(root.get("dependencies"));
        if (dependencies.contains(library)) return;
        dependencies.add(library);
        root.add("dependencies", toArray(dependencies));
        writeManifest(projectDirectory, root);
    }

    /** The {@code "dependency-search-paths"} of {@code project.json}, or {@code ["lib"]} when unspecified. */
    public static @NotNull List<String> dependencySearchPaths(String projectDirectory)
    {
        JsonObject root = readManifest(projectDirectory);
        List<String> paths = root != null ? stringArray(root.get("dependency-search-paths")) : new ArrayList<>();
        return paths.isEmpty() ? List.of("lib") : paths;
    }

    private static @Nullable JsonObject readManifest(String projectDirectory)
    {
        if (projectDirectory == null || projectDirectory.isEmpty()) return null;
        try
        {
            return requireManifest(projectDirectory);
        }
        catch (Exception ignored)
        {
            return null;
        }
    }

    private static @NotNull JsonObject requireManifest(@NotNull String projectDirectory) throws IOException
    {
        Path manifest = Path.of(projectDirectory, "project.json");
        if (!Files.isRegularFile(manifest)) throw new IOException("No project.json in " + projectDirectory);
        try
        {
            JsonElement root = JsonParser.parseString(Files.readString(manifest));
            if (!root.isJsonObject()) throw new IOException("project.json is not a JSON object");
            return root.getAsJsonObject();
        }
        catch (RuntimeException e)
        {
            throw new IOException("Cannot parse project.json: " + e.getMessage(), e);
        }
    }

    private static void writeManifest(@NotNull String projectDirectory, @NotNull JsonObject root) throws IOException
    {
        String json = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root);
        Files.writeString(Path.of(projectDirectory, "project.json"), json + "\n");
    }

    private static @NotNull JsonArray toArray(@NotNull List<String> values)
    {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }

    private static @NotNull List<String> stringArray(JsonElement element)
    {
        List<String> result = new ArrayList<>();
        if (element == null || !element.isJsonArray()) return result;
        for (JsonElement item : element.getAsJsonArray())
        {
            if (item.isJsonPrimitive() && !item.getAsString().isBlank()) result.add(item.getAsString());
        }
        return result;
    }
}
