package org.c3lang.intellij;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class C3CommandRunEditor extends SettingsEditor<C3CommandRunConfiguration>
{
    JPanel panel;
    TextFieldWithBrowseButton workingDirectoryField;
    JTextField argsField;

    public C3CommandRunEditor()
    {
        createUIComponents();

        panel = FormBuilder.createFormBuilder()
                           .addLabeledComponent("Working directory", workingDirectoryField)
                           .addLabeledComponent("Additional arguments", argsField)
                           .getPanel();
    }

    @Override protected void resetEditorFrom(@NotNull C3CommandRunConfiguration configuration)
    {
        if (configuration.getWorkingDirectory().isEmpty())
        {
            workingDirectoryField.setText(configuration.getProject().getBasePath());
        }
        else
        {
            workingDirectoryField.setText(configuration.getWorkingDirectory());
        }

        argsField.setText(configuration.getArgs());
    }

    @Override protected void applyEditorTo(@NotNull C3CommandRunConfiguration configuration) throws ConfigurationException
    {
        if (workingDirectoryField.getText().isEmpty())
        {
            throw new ConfigurationException("You must provide a working directory.");
        }

        configuration.setWorkingDirectory(workingDirectoryField.getText());
        configuration.setArgs(argsField.getText());
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

        argsField = new JTextField();
    }
}
