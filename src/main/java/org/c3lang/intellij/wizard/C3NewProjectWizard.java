package org.c3lang.intellij.wizard;

import com.intellij.ide.wizard.AbstractNewProjectWizardStep;
import com.intellij.ide.wizard.NewProjectWizardStep;
import com.intellij.ide.wizard.language.LanguageGeneratorNewProjectWizard;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.module.WebModuleBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.dsl.builder.Panel;
import org.apache.commons.lang3.Validate;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.c3lang.intellij.C3Icons;
import org.c3lang.intellij.C3SettingsState;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;

public class C3NewProjectWizard implements LanguageGeneratorNewProjectWizard
{
    private static final Log log = LogFactory.getLog(C3NewProjectWizard.class);
    private static final C3SettingsState settings = C3SettingsState.getInstance();

    @Override
    public int getOrdinal()
    {
        return 0;
    }

    @NotNull
    @Override
    public String getName()
    {
        return "C3 Project";
    }

    @Override
    public @NotNull Icon getIcon()
    {
        return C3Icons.LOGO;
    }

    @Override
    public @NotNull NewProjectWizardStep createStep(@NotNull NewProjectWizardStep newProjectWizardStep)
    {
        return new Step(newProjectWizardStep);
    }

    static class Step extends AbstractNewProjectWizardStep
    {
        private final NewProjectWizardStep parent;
        private final TextFieldWithBrowseButton stdlibPath;

        public Step(NewProjectWizardStep parent)
        {
            super(parent);

            Project project = null;

            Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
            if (openProjects.length > 0)
            {
                project = openProjects[0];
            }

            this.parent = parent;
            this.stdlibPath = new TextFieldWithBrowseButton();
            stdlibPath.setText(settings.stdlibPath);
            stdlibPath.setPreferredSize(new Dimension(500, stdlibPath.getPreferredSize().height));
            stdlibPath.addBrowseFolderListener(project, new FileChooserDescriptor(false, true, false, false, false, false));
        }

        @Override
        public void setupUI(@NotNull Panel builder)
        {
            builder.row("stdlib", row ->
            {
                row.cell(stdlibPath);
                return null;
            });
        }

        @Override
        public void setupProject(@NotNull Project project)
        {
            Validate.notNull(project.getBasePath(), "Project base path cannot be null");

            WebModuleBuilder<?> builder = new WebModuleBuilder<>();
            builder.setName(parent.getContext().getProjectName());
            builder.setContentEntryPath(parent.getContext().getProjectFileDirectory());
            builder.commit(project);

            try
            {
                C3ProjectTemplates.createProject(Path.of(project.getBasePath()), project.getName());
            } catch (IOException e)
            {
                log.error(e.getMessage(), e);
            }

            settings.stdlibPath = stdlibPath.getText();
        }
    }
}