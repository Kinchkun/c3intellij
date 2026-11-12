package org.c3lang.intellij.settings;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import org.c3lang.intellij.C3SettingsState;
import org.c3lang.intellij.C3StdlibLocator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.nio.file.Path;

public class C3SettingsComponent
{
    private final JPanel mainPanel = new JPanel(new GridBagLayout());
    private final TextFieldWithBrowseButton stdlibPathField = new TextFieldWithBrowseButton();
    private final TextFieldWithBrowseButton projectStdlibPathField = new TextFieldWithBrowseButton();
    /** The path the Detect button last filled in, so {@code apply} can store it as detected rather than hand-entered. */
    private @Nullable String detectedPath;

    public C3SettingsComponent(@NotNull Project project)
    {
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        FileChooserDescriptor directories = new FileChooserDescriptor(false, true, false, false, false, false);
        stdlibPathField.addBrowseFolderListener(project, directories);
        projectStdlibPathField.addBrowseFolderListener(project, directories);

        JButton detectButton = new JButton("Detect");
        detectButton.setToolTipText("Ask the configured C3 compiler (c3c compile --build-env), then C3C_LIB, then the directories next to the compiler");
        detectButton.addActionListener(e -> detect());
        JPanel stdlibRow = new JPanel(new BorderLayout(JBUI.scale(5), 0));
        stdlibRow.add(stdlibPathField, BorderLayout.CENTER);
        stdlibRow.add(detectButton, BorderLayout.EAST);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = JBUI.insetsBottom(5);

        gbc.gridy = 0;
        mainPanel.add(new JLabel("Stdlib Path:"), gbc);
        gbc.gridy = 1;
        mainPanel.add(stdlibRow, gbc);
        gbc.gridy = 2;
        mainPanel.add(hint("for example: .../c3c/lib/ — Detect asks the compiler of the C3 Compiler settings page"), gbc);

        gbc.gridy = 3;
        gbc.insets = JBUI.insets(15, 0, 5, 0);
        mainPanel.add(new JLabel("Stdlib Path for This Project (optional):"), gbc);
        gbc.gridy = 4;
        gbc.insets = JBUI.insetsBottom(5);
        mainPanel.add(projectStdlibPathField, gbc);
        gbc.gridy = 5;
        mainPanel.add(hint("overrides the path above for this project only, like c3c --stdlib; stored in .idea/c3.xml"), gbc);

        gbc.gridy = 6;
        gbc.weighty = 1.0;
        mainPanel.add(Box.createVerticalGlue(), gbc);
    }

    private static @NotNull JLabel hint(@NotNull String text)
    {
        JLabel label = new JLabel(text);
        label.setForeground(JBColor.GRAY);
        label.setFont(label.getFont().deriveFont((float) label.getFont().getSize() - 1.0f));
        return label;
    }

    private void detect()
    {
        String compiler = C3SettingsState.getInstance().sdk;
        Path[] result = new Path[1];
        ProgressManager.getInstance().runProcessWithProgressSynchronously(
            () -> result[0] = C3StdlibLocator.detect(compiler == null ? "" : compiler),
            "Detecting C3 Standard Library", true, null, mainPanel);
        if (result[0] == null)
        {
            Messages.showWarningDialog(mainPanel,
                "No C3 standard library found with the compiler '" + compiler + "'.\n"
                    + "Check Build, Execution, Deployment → C3 Compiler, or choose the directory by hand.",
                "Detect C3 Standard Library");
            return;
        }
        detectedPath = result[0].toString();
        setStdlibPath(detectedPath);
    }

    public JPanel getMainPanel()
    {
        return mainPanel;
    }

    public @NotNull String getStdlibPath()
    {
        return stdlibPathField.getTextField().getText().trim();
    }

    public void setStdlibPath(String path)
    {
        stdlibPathField.getTextField().setText(path);
    }

    public @NotNull String getProjectStdlibPath()
    {
        return projectStdlibPathField.getTextField().getText().trim();
    }

    public void setProjectStdlibPath(String path)
    {
        projectStdlibPathField.getTextField().setText(path);
    }

    /** The path the Detect button produced, or null when it was not used. */
    public @Nullable String getDetectedPath()
    {
        return detectedPath;
    }
}
