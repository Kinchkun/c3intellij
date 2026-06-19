package org.c3lang.intellij.autorerun;

import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * On project open, force-creates {@link C3AutoRerunService} and registers the typing listener that
 * drives live auto-save + rerun. The listener is scoped to the service, so it is removed when the
 * project closes.
 */
public final class C3AutoRerunStartupActivity implements ProjectActivity
{
	@Nullable
	@Override
	public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation)
	{
		C3AutoRerunService service = C3AutoRerunService.getInstance(project);
		EditorFactory.getInstance().getEventMulticaster()
			.addDocumentListener(new C3AutoSaveDocumentListener(project), service);
		return Unit.INSTANCE;
	}
}
