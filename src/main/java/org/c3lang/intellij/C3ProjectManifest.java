package org.c3lang.intellij;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;

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
}
