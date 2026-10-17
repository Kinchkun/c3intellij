package org.c3lang.intellij;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Files;

public class C3CompilerConfigurable implements SearchableConfigurable, Configurable.NoScroll
{
    private JPanel panel;
    private TextFieldWithBrowseButton pathField;
    private JCheckBox testWithAsanBox;

    public C3CompilerConfigurable()
    {
    }

    @Override public @NotNull @NonNls String getId()
    {
        return "org.c3lang.compiler";
    }

    @Override public @NlsContexts.ConfigurableName String getDisplayName()
    {
        return "C3 Compiler";
    }

    @Override public @Nullable JComponent createComponent()
    {
        panel = new JPanel(new BorderLayout(10, 5));
        final JPanel contentPanel = new JPanel(new BorderLayout(4, 0));
        final JPanel northPanel = new JPanel(new BorderLayout(0, 8));
        panel.add(northPanel, BorderLayout.NORTH);
        northPanel.add(contentPanel, BorderLayout.NORTH);
        testWithAsanBox = new JCheckBox("Run tests with AddressSanitizer (--sanitize=address)");
        northPanel.add(testWithAsanBox, BorderLayout.SOUTH);
        contentPanel.add(new JLabel("C3 Compiler"), BorderLayout.WEST);
        pathField = new TextFieldWithBrowseButton();
        contentPanel.add(pathField);
        pathField.addBrowseFolderListener(new TextBrowseFolderListener(
                new FileChooserDescriptor(true, false, false, false, false, false)
                        .withFileFilter((VirtualFile file) -> {
                            if (!Files.isExecutable(file.toNioPath())) return false;
                            return C3SdkType.isSdkHome(file.getParent().getPath());
                        }).withTitle("Select C3 Compiler")));
        return panel;
    }

    @Override public boolean isModified()
    {
        C3SettingsState settings = C3SettingsState.getInstance();
        return !pathField.getText().equals(settings.sdk) || testWithAsanBox.isSelected() != settings.testWithAsan;
    }


    @Override public void apply()
    {
        C3SettingsState settings = C3SettingsState.getInstance();
        settings.sdk = pathField.getText();
        settings.testWithAsan = testWithAsanBox.isSelected();
    }

    @Override public void reset()
    {
        C3SettingsState settings = C3SettingsState.getInstance();
        pathField.setText(settings.sdk);
        testWithAsanBox.setSelected(settings.testWithAsan);
    }
}
