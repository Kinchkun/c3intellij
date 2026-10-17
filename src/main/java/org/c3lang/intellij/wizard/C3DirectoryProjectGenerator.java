package org.c3lang.intellij.wizard;

import com.intellij.ide.util.projectWizard.SettingsStep;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.DirectoryProjectGeneratorBase;
import com.intellij.platform.ProjectGeneratorPeer;
import com.intellij.util.ui.FormBuilder;
import org.c3lang.intellij.C3Icons;
import org.c3lang.intellij.C3SettingsState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.nio.file.Path;

/**
 * "New Project" entries for CLion and other IDEs whose New Project dialog is built from the
 * {@code directoryProjectGenerator} extension point (IntelliJ IDEA uses the
 * {@code newProjectWizard.languageGenerator} wizards instead).
 */
public abstract class C3DirectoryProjectGenerator extends DirectoryProjectGeneratorBase<C3DirectoryProjectGenerator.Settings>
{
    private static final Logger LOG = Logger.getInstance(C3DirectoryProjectGenerator.class);

    public record Settings(@NotNull String stdlibPath)
    {
    }

    /** Creates the files under {@code baseDir}; returns the relative path of the file to open. */
    protected abstract @NotNull String createFiles(@NotNull Path baseDir, @NotNull String projectName) throws IOException;

    @Override
    public @NotNull ProjectGeneratorPeer<Settings> createPeer()
    {
        return new Peer();
    }

    @Override
    public void generateProject(@NotNull Project project, @NotNull VirtualFile baseDir, @NotNull Settings settings, @NotNull Module module)
    {
        String fileToOpen;
        try
        {
            fileToOpen = createFiles(baseDir.toNioPath(), baseDir.getName());
        }
        catch (IOException e)
        {
            LOG.error("Unable to create C3 project in " + baseDir.getPath(), e);
            return;
        }

        if (!settings.stdlibPath().isBlank())
        {
            C3SettingsState.getInstance().stdlibPath = settings.stdlibPath();
        }

        VfsUtil.markDirtyAndRefresh(false, true, true, baseDir);
        VirtualFile file = baseDir.findFileByRelativePath(fileToOpen);
        if (file != null)
        {
            ApplicationManager.getApplication().invokeLater(
                    () -> FileEditorManager.getInstance(project).openFile(file, true),
                    project.getDisposed());
        }
    }

    public static final class Executable extends C3DirectoryProjectGenerator
    {
        @Override
        public @NotNull String getName()
        {
            return "C3 Project";
        }

        @Override
        public @NotNull String getDescription()
        {
            return "Executable C3 project with a project.json and a Hello World main.";
        }

        @Override
        public @NotNull Icon getLogo()
        {
            return C3Icons.LOGO;
        }

        @Override
        protected @NotNull String createFiles(@NotNull Path baseDir, @NotNull String projectName) throws IOException
        {
            return C3ProjectTemplates.createProject(baseDir, projectName);
        }
    }

    public static final class Library extends C3DirectoryProjectGenerator
    {
        @Override
        public @NotNull String getName()
        {
            return "C3 Library";
        }

        @Override
        public @NotNull String getDescription()
        {
            return "C3 library with a manifest.json, an interface file and per-target directories.";
        }

        @Override
        public @NotNull Icon getLogo()
        {
            return C3Icons.LIB_FILE;
        }

        @Override
        protected @NotNull String createFiles(@NotNull Path baseDir, @NotNull String projectName) throws IOException
        {
            return C3ProjectTemplates.createLibrary(baseDir, projectName);
        }
    }

    private static final class Peer implements ProjectGeneratorPeer<Settings>
    {
        private final TextFieldWithBrowseButton stdlibPath = new TextFieldWithBrowseButton();

        Peer()
        {
            stdlibPath.setText(C3SettingsState.getInstance().stdlibPath);
            stdlibPath.addBrowseFolderListener(null, FileChooserDescriptorFactory.createSingleFolderDescriptor()
                    .withTitle("C3 Standard Library"));
        }

        @Override
        public @NotNull JComponent getComponent(@NotNull TextFieldWithBrowseButton projectLocation, @NotNull Runnable checkValid)
        {
            return FormBuilder.createFormBuilder()
                    .addLabeledComponent("Stdlib:", stdlibPath)
                    .getPanel();
        }

        @Override
        public void buildUI(@NotNull SettingsStep settingsStep)
        {
            settingsStep.addSettingsField("Stdlib:", stdlibPath);
        }

        @Override
        public @NotNull Settings getSettings()
        {
            return new Settings(stdlibPath.getText().trim());
        }

        @Override
        public @Nullable ValidationInfo validate()
        {
            return null;
        }

        @Override
        public boolean isBackgroundJobRunning()
        {
            return false;
        }
    }
}
