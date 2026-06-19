package org.c3lang.intellij;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;


public class C3BuildRunEditor extends SettingsEditor<C3BuildRunConfiguration>
{
    JPanel panel;
    TextFieldWithBrowseButton workingDirectoryField;
    ComboBox<String> targetField;
    JTextField argsField;
    JTextField programArgsField;

    public C3BuildRunEditor()
    {
        createUIComponents();

        panel = FormBuilder.createFormBuilder()
                           .addLabeledComponent("Working directory", workingDirectoryField)
                           .addLabeledComponent("Target", targetField)
                           .addLabeledComponent("Additional arguments", argsField)
                           .addLabeledComponent("Program arguments", programArgsField)
                           .getPanel();
    }

    @Override protected void resetEditorFrom(@NotNull C3BuildRunConfiguration configuration)
    {
        // This function is called each time the run configuration form is shown,
        // i.e. both when its first created and when it's being edited

        String workingDirectory;
        if (configuration.getWorkingDirectory().isEmpty())
        {
            // By default, fill the workingDirectory field with the project's base path
            workingDirectory = configuration.getProject().getBasePath();
        }
        else
        {
            // Otherwise (when editing the configuration), set its value to the one that was stored
            workingDirectory = configuration.getWorkingDirectory();
        }
        workingDirectoryField.setText(workingDirectory);

        // Offer the executable targets declared in the project's project.json.
        targetField.removeAllItems();
        List<String> executableTargets = C3ProjectManifest.findExecutableTargets(workingDirectory);
        for (String target : executableTargets)
        {
            targetField.addItem(target);
        }

        String storedTarget = configuration.getTarget();
        if (storedTarget != null && !storedTarget.isEmpty())
        {
            targetField.setSelectedItem(storedTarget);
        }
        else if (!executableTargets.isEmpty())
        {
            // Preselect the first executable target for new configurations.
            targetField.setSelectedItem(executableTargets.get(0));
        }

        argsField.setText(configuration.getArgs());
        programArgsField.setText(configuration.getProgramArgs());
    }

    @Override protected void applyEditorTo(@NotNull C3BuildRunConfiguration configuration) throws ConfigurationException
    {
        if (workingDirectoryField.getText().isEmpty())
        {
            throw new ConfigurationException("You must provide a working directory.");
        }

        configuration.setWorkingDirectory(workingDirectoryField.getText());

        Object selectedTarget = targetField.getSelectedItem();
        configuration.setTarget(selectedTarget == null ? "" : selectedTarget.toString().trim());

        configuration.setArgs(argsField.getText());
        configuration.setProgramArgs(programArgsField.getText());
    }

    @Override protected @NotNull JComponent createEditor()
    {
        return panel;
    }

    private void createUIComponents()
    {
        workingDirectoryField = new TextFieldWithBrowseButton();
        TextBrowseFolderListener listener =
                new TextBrowseFolderListener(FileChooserDescriptorFactory.createSingleFolderDescriptor()
                                                                         .withTitle("Select Working Directory"));
        workingDirectoryField.addBrowseFolderListener(listener);

        // Editable so a target can still be typed when project.json can't be parsed.
        targetField = new ComboBox<>();
        targetField.setEditable(true);

        argsField = new JTextField();
        programArgsField = new JTextField();
    }
}
