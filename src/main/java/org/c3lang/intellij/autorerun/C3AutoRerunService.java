package org.c3lang.intellij.autorerun;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.Executor;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.Alarm;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.c3lang.intellij.C3ConfigurationType;
import org.jetbrains.annotations.NotNull;

/**
 * Per-project state and engine for "auto-rerun the selected C3 run/test configuration when sources
 * change". The on/off flag is persisted in the workspace file; the rerun itself is debounced and
 * routed through {@link ExecutionManager#restartRunProfile} so an in-flight run is stopped first.
 */
@State(name = "C3AutoRerun", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class C3AutoRerunService
	implements PersistentStateComponent<C3AutoRerunService.State>, Disposable
{
	private static final Logger LOG = Logger.getInstance(C3AutoRerunService.class);
	private static final int DEBOUNCE_MS = 400;
	private static final int AUTO_SAVE_DEBOUNCE_MS = 500;

	public static final class State
	{
		public boolean enabled = false;
	}

	private final Project project;
	private final Alarm alarm;
	private final Alarm autoSaveAlarm;
	private State state = new State();

	public C3AutoRerunService(@NotNull Project project)
	{
		this.project = project;
		// SWING_THREAD: requests fire on the EDT, where saving documents and restartRunProfile must run.
		this.alarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);
		this.autoSaveAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);
	}

	public static C3AutoRerunService getInstance(@NotNull Project project)
	{
		return project.getService(C3AutoRerunService.class);
	}

	public boolean isEnabled()
	{
		return state.enabled;
	}

	public void setEnabled(boolean enabled)
	{
		state.enabled = enabled;
	}

	/** True if the project's selected run configuration is a C3 one (the only kind we auto-rerun). */
	public static boolean hasC3ConfigurationSelected(@NotNull Project project)
	{
		RunnerAndConfigurationSettings selected = RunManager.getInstance(project).getSelectedConfiguration();
		return selected != null && selected.getType() instanceof C3ConfigurationType;
	}

	/** Debounced request to rerun the selected configuration; coalesces bursts of file changes. */
	public void scheduleRerun()
	{
		if (!state.enabled || project.isDisposed()) return;
		alarm.cancelAllRequests();
		alarm.addRequest(this::rerunSelected, DEBOUNCE_MS);
	}

	/**
	 * Debounced request, triggered while typing in a C3 file: save the unsaved documents (so {@code
	 * c3c} sees the latest source) and then rerun. Coalesces bursts of keystrokes.
	 */
	public void scheduleAutoSave()
	{
		if (!state.enabled || project.isDisposed()) return;
		autoSaveAlarm.cancelAllRequests();
		autoSaveAlarm.addRequest(this::autoSaveAndRerun, AUTO_SAVE_DEBOUNCE_MS);
	}

	private void autoSaveAndRerun()
	{
		if (!state.enabled || project.isDisposed()) return;
		// Saving dirty documents emits VFS content-change events, which the file listener turns into a
		// rerun; the explicit scheduleRerun() below covers the case where nothing needed saving.
		FileDocumentManager.getInstance().saveAllDocuments();
		scheduleRerun();
	}

	/** Test hook: whether a debounced rerun is currently queued. */
	@org.jetbrains.annotations.TestOnly
	public boolean isRerunPending()
	{
		return alarm.getActiveRequestCount() > 0;
	}

	/** Test hook: whether a debounced auto-save is currently queued. */
	@org.jetbrains.annotations.TestOnly
	public boolean isAutoSavePending()
	{
		return autoSaveAlarm.getActiveRequestCount() > 0;
	}

	/** Test hook: clear any queued work (the light test project reuses this service across methods). */
	@org.jetbrains.annotations.TestOnly
	public void cancelPendingForTest()
	{
		alarm.cancelAllRequests();
		autoSaveAlarm.cancelAllRequests();
	}

	private void rerunSelected()
	{
		if (!state.enabled || project.isDisposed()) return;

		RunnerAndConfigurationSettings settings = RunManager.getInstance(project).getSelectedConfiguration();
		if (settings == null || !(settings.getType() instanceof C3ConfigurationType)) return;

		Executor executor = DefaultRunExecutor.getRunExecutorInstance();
		try
		{
			ExecutionEnvironment environment = ExecutionEnvironmentBuilder.create(executor, settings).build();
			// restartRunProfile stops a running instance of the same configuration before starting.
			ExecutionManager.getInstance(project).restartRunProfile(environment);
		}
		catch (ExecutionException e)
		{
			LOG.warn("C3 auto-rerun failed for '" + settings.getName() + "'", e);
		}
	}

	@Override
	public @NotNull State getState()
	{
		return state;
	}

	@Override
	public void loadState(@NotNull State state)
	{
		XmlSerializerUtil.copyBean(state, this.state);
	}

	@Override
	public void dispose()
	{
	}
}
