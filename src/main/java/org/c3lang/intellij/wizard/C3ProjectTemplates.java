package org.c3lang.intellij.wizard;

import com.intellij.openapi.util.io.FileUtil;
import org.c3lang.intellij.C3ProjectManifest;
import org.c3lang.intellij.C3Util;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Creates the on-disk layout of a new C3 project or library. Shared by the IntelliJ IDEA
 * wizards ({@link C3NewProjectWizard}, {@link C3NewLibraryWizard}) and the generators used by
 * CLion and other non-IDEA IDEs ({@link C3DirectoryProjectGenerator}). Existing files are never
 * overwritten, so generating into a non-empty directory is safe.
 */
public final class C3ProjectTemplates
{
    private static final List<String> PROJECT_DIRS = List.of(
            "build", "docs", "lib", "resources", "scripts", "src", "test");

    private static final List<String> LIBRARY_DIRS = List.of(
            "freebsd-x64", "linux-aarch64", "linux-riscv32", "linux-riscv64", "linux-x64", "linux-x86",
            "macos-aarch64", "macos-x64", "netbsd-x64", "openbsd-x64", "wasm32", "wasm64",
            "windows-aarch64", "windows-x64", "scripts");

    /** Path of the main source file of a project, relative to its base directory. */
    public static final String PROJECT_MAIN_FILE = "src/main.c3";

    private C3ProjectTemplates()
    {
    }

    /**
     * Turns an arbitrary project name into a valid C3 module name: lower case letters,
     * digits and underscores, starting with a letter (identifiers are {@code _*[a-z][a-z0-9_]*}).
     */
    public static @NotNull String toModuleName(@NotNull String projectName)
    {
        String name = projectName.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");
        name = name.replaceAll("^_+|_+$", "");
        if (name.isEmpty()) return "main";
        if (Character.isDigit(name.charAt(0))) name = "m_" + name;
        return name;
    }

    /** Creates an executable project; returns the relative path of the main source file. */
    public static @NotNull String createProject(@NotNull Path baseDir, @NotNull String projectName) throws IOException
    {
        String moduleName = toModuleName(projectName);
        createDirs(baseDir, PROJECT_DIRS);
        writeIfAbsent(baseDir.resolve("LICENSE"), "");
        writeIfAbsent(baseDir.resolve("README.md"), "# " + projectName + "\n");
        writeTemplateIfAbsent(baseDir.resolve("project.json"), "templates/project.json", moduleName);
        writeTemplateIfAbsent(baseDir.resolve(PROJECT_MAIN_FILE), "templates/main", moduleName);
        return PROJECT_MAIN_FILE;
    }

    /** Creates a library; returns the relative path of the library's interface file. */
    public static @NotNull String createLibrary(@NotNull Path baseDir, @NotNull String projectName) throws IOException
    {
        String moduleName = toModuleName(projectName);
        String interfaceFile = moduleName + ".c3i";
        createDirs(baseDir, LIBRARY_DIRS);
        writeIfAbsent(baseDir.resolve("LICENSE"), "");
        writeIfAbsent(baseDir.resolve("README.md"), "# " + projectName + "\n");
        writeTemplateIfAbsent(baseDir.resolve("manifest.json"), "templates/manifest.json", moduleName);
        writeTemplateIfAbsent(baseDir.resolve(interfaceFile), "templates/library", moduleName);
        return interfaceFile;
    }

    /**
     * Adds an executable to the workspace in {@code projectDir}: {@code apps/<name>/src/main.c3},
     * {@code apps/<name>/test/<module>_test.c3} and a matching target in {@code project.json}.
     * Returns the path of the main file, relative to {@code projectDir}.
     */
    public static @NotNull String createAppTarget(@NotNull Path projectDir, @NotNull String name) throws IOException
    {
        String moduleName = toModuleName(name);
        String app = "apps/" + name;
        C3ProjectManifest.addTarget(projectDir.toString(), new C3ProjectManifest.Target(
                name, "executable", List.of(app + "/src/**"), List.of(app + "/test/**")));
        String mainFile = app + "/src/main.c3";
        writeTemplateIfAbsent(projectDir.resolve(mainFile), "templates/main", moduleName);
        writeIfAbsent(projectDir.resolve(app + "/test/" + moduleName + "_test.c3"), testTemplate(moduleName));
        return mainFile;
    }

    /**
     * Adds a directory library to the workspace in {@code projectDir}: {@code lib/<name>.c3l} with
     * a {@code manifest.json}, {@code src/<module>.c3} and {@code test/<module>_test.c3}, listed in
     * the {@code "dependencies"} of {@code project.json}. Returns the path of the source file,
     * relative to {@code projectDir}.
     */
    public static @NotNull String createWorkspaceLibrary(@NotNull Path projectDir, @NotNull String name) throws IOException
    {
        String moduleName = toModuleName(name);
        String library = "lib/" + name + ".c3l";
        if (projectDir.resolve(library).toFile().exists())
        {
            throw new IOException("Library '" + library + "' already exists");
        }
        writeIfAbsent(projectDir.resolve(library + "/manifest.json"),
                "{\n  \"provides\": \"" + moduleName + "\",\n  \"sources\": [ \"src/**\" ],\n"
                + "  \"targets\": {\n"
                + "    \"macos-aarch64\": {},\n    \"macos-x64\": {},\n    \"linux-x64\": {},\n"
                + "    \"linux-aarch64\": {},\n    \"windows-x64\": {}\n  }\n}\n");
        String sourceFile = library + "/src/" + moduleName + ".c3";
        writeIfAbsent(projectDir.resolve(sourceFile), "module " + moduleName + ";\n");
        writeIfAbsent(projectDir.resolve(library + "/test/" + moduleName + "_test.c3"), testTemplate(moduleName));
        C3ProjectManifest.addDependency(projectDir.toString(), name);
        return sourceFile;
    }

    private static @NotNull String testTemplate(@NotNull String moduleName)
    {
        return "module " + moduleName + "_test @test;\n"
                + "import " + moduleName + ";\n\n"
                + "fn void it_works()\n{\n    assert(true);\n}\n";
    }

    private static void createDirs(@NotNull Path baseDir, @NotNull List<String> dirs)
    {
        for (String dir : dirs)
        {
            FileUtil.createDirectory(baseDir.resolve(dir).toFile());
        }
    }

    private static void writeIfAbsent(@NotNull Path path, @NotNull String content) throws IOException
    {
        File file = path.toFile();
        if (!file.exists()) FileUtil.writeToFile(file, content);
    }

    private static void writeTemplateIfAbsent(@NotNull Path path, @NotNull String template, @NotNull String moduleName)
    {
        File file = path.toFile();
        if (!file.exists()) C3Util.INSTANCE.writeToFile(moduleName, template, file);
    }
}
