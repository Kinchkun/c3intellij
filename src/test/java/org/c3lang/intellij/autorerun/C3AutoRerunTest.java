package org.c3lang.intellij.autorerun;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.c3lang.intellij.C3ConfigurationType;

import java.util.List;

/** Verifies the decision logic behind auto-rerun: relevance, project gating, persistence, scheduling. */
public class C3AutoRerunTest extends BasePlatformTestCase
{
	@Override
	protected void setUp() throws Exception
	{
		super.setUp();
		// The light fixture reuses one project (and thus one service) across methods — start clean.
		C3AutoRerunService service = C3AutoRerunService.getInstance(getProject());
		service.setEnabled(false);
		service.cancelPendingForTest();
	}

	public void testRelevantFileDetection()
	{
		assertTrue(C3AutoRerunFileListener.isRelevant("/p/src/main.c3"));
		assertTrue(C3AutoRerunFileListener.isRelevant("/p/src/a.c3t"));
		assertTrue(C3AutoRerunFileListener.isRelevant("/p/lib/x.c3i"));
		assertTrue(C3AutoRerunFileListener.isRelevant("/p/project.json"));
		assertFalse(C3AutoRerunFileListener.isRelevant("/p/notes.txt"));
		assertFalse(C3AutoRerunFileListener.isRelevant("/p/myproject.json.bak"));
	}

	public void testEnabledFlagPersists()
	{
		C3AutoRerunService service = C3AutoRerunService.getInstance(getProject());
		assertFalse("disabled by default", service.isEnabled());
		service.setEnabled(true);
		assertTrue(service.isEnabled());
		assertTrue("flag is what gets persisted", service.getState().enabled);
	}

	public void testC3ConfigurationGating()
	{
		assertFalse("no config selected", C3AutoRerunService.hasC3ConfigurationSelected(getProject()));

		RunManager runManager = RunManager.getInstance(getProject());
		ConfigurationFactory factory = C3ConfigurationType.getInstance().getConfigurationFactories()[0];
		RunnerAndConfigurationSettings settings = runManager.createConfiguration("c3", factory);
		runManager.addConfiguration(settings);
		runManager.setSelectedConfiguration(settings);

		assertTrue("a C3 configuration is selected", C3AutoRerunService.hasC3ConfigurationSelected(getProject()));
	}

	public void testScheduleOnlyWhenEnabled()
	{
		C3AutoRerunService service = C3AutoRerunService.getInstance(getProject());

		service.setEnabled(false);
		service.scheduleRerun();
		assertFalse("disabled: no rerun queued", service.isRerunPending());

		service.setEnabled(true);
		service.scheduleRerun();
		assertTrue("enabled: a rerun is queued", service.isRerunPending());

		// Disable so the queued request no-ops if it fires during teardown (instead of launching c3c).
		service.setEnabled(false);
	}

	public void testChangedFileBelongsToProject()
	{
		VirtualFile inProject = myFixture.configureByText("main.c3", "module app;\n").getVirtualFile();
		assertTrue("a project source file is in content",
			C3AutoRerunFileListener.belongsToProject(getProject(), List.of(inProject)));
	}

	public void testTypingInC3FileSchedulesAutoSaveWhenEnabled()
	{
		myFixture.configureByText("main.c3", "module app;\n");
		Document document = myFixture.getEditor().getDocument();

		EditorFactory.getInstance().getEventMulticaster()
			.addDocumentListener(new C3AutoSaveDocumentListener(getProject()), getTestRootDisposable());

		C3AutoRerunService service = C3AutoRerunService.getInstance(getProject());

		// Disabled: typing should not schedule anything.
		service.setEnabled(false);
		type(document, "\n// a");
		assertFalse("disabled: typing schedules nothing", service.isAutoSavePending());

		// Enabled: typing schedules a debounced auto-save.
		service.setEnabled(true);
		type(document, "\n// b");
		assertTrue("enabled: typing schedules an auto-save", service.isAutoSavePending());

		// Disable so the queued save+rerun no-ops if it fires during teardown.
		service.setEnabled(false);
	}

	private void type(Document document, String text)
	{
		WriteCommandAction.runWriteCommandAction(getProject(),
			() -> document.insertString(document.getTextLength(), text));
	}
}
