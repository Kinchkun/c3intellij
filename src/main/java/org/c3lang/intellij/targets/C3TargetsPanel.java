package org.c3lang.intellij.targets;

import com.intellij.execution.Executor;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Alarm;
import com.intellij.util.ui.tree.TreeUtil;
import org.c3lang.intellij.C3Icons;
import org.c3lang.intellij.C3ProjectManifest;
import org.c3lang.intellij.C3ProjectManifestListener;
import org.c3lang.intellij.C3Projects;
import org.c3lang.intellij.C3TargetRunner;
import org.c3lang.intellij.targets.C3TargetEntry.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * The content of the C3 Targets tool window: a tree of the workspace's C3 projects, their
 * {@code project.json} targets and directory libraries, each with their tests. Double-click (or
 * Enter) runs the selected entry; the toolbar also debugs it and adds new targets.
 */
public final class C3TargetsPanel extends SimpleToolWindowPanel implements Disposable
{
	private final Project project;
	private final DefaultMutableTreeNode root = new DefaultMutableTreeNode();
	private final Tree tree = new Tree(new DefaultTreeModel(root));
	private final Alarm refreshAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);

	public C3TargetsPanel(@NotNull Project project)
	{
		super(true, true);
		this.project = project;

		tree.setRootVisible(false);
		tree.setShowsRootHandles(true);
		tree.setCellRenderer(new Renderer());
		setContent(ScrollPaneFactory.createScrollPane(tree));

		DefaultActionGroup actions = new DefaultActionGroup(
			new RunAction(DefaultRunExecutor.getRunExecutorInstance()),
			new RunAction(DefaultDebugExecutor.getDebugExecutorInstance()),
			Separator.getInstance(),
			new AddTargetAction(),
			new ReloadProjectAction());
		ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("C3Targets", actions, true);
		toolbar.setTargetComponent(tree);
		setToolbar(toolbar.getComponent());

		DefaultActionGroup popup = new DefaultActionGroup(
			new RunAction(DefaultRunExecutor.getRunExecutorInstance()),
			new RunAction(DefaultDebugExecutor.getDebugExecutorInstance()),
			Separator.getInstance(),
			new OpenManifestAction(),
			new AddTargetAction());
		PopupHandler.installPopupMenu(tree, popup, "C3TargetsPopup");

		new DoubleClickListener()
		{
			@Override
			protected boolean onDoubleClick(@NotNull MouseEvent event)
			{
				return run(selected(), DefaultRunExecutor.getRunExecutorInstance());
			}
		}.installOn(tree);
		tree.addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyPressed(KeyEvent e)
			{
				if (e.getKeyCode() == KeyEvent.VK_ENTER && run(selected(), DefaultRunExecutor.getRunExecutorInstance()))
				{
					e.consume();
				}
			}
		});

		project.getMessageBus().connect(this).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener()
		{
			@Override
			public void after(@NotNull List<? extends @NotNull VFileEvent> events)
			{
				for (VFileEvent event : events)
				{
					if (affectsTargets(event))
					{
						scheduleRefresh();
						return;
					}
				}
			}
		});

		refresh();
	}

	/** Manifest edits change targets; created/deleted/moved files can add or remove libraries and tests. */
	private static boolean affectsTargets(@NotNull VFileEvent event)
	{
		String path = event.getPath();
		if (path.endsWith("/project.json") || path.endsWith("/manifest.json")) return true;
		return !(event instanceof VFileContentChangeEvent);
	}

	private void scheduleRefresh()
	{
		refreshAlarm.cancelAllRequests();
		refreshAlarm.addRequest(this::refresh, 300);
	}

	/** Rebuilds the tree from disk, keeping the selection where possible. */
	public void refresh()
	{
		C3TargetEntry previouslySelected = selected();
		root.removeAllChildren();
		for (ProjectEntry entry : C3TargetsModel.build(project)) root.add(node(entry));
		((DefaultTreeModel) tree.getModel()).reload();
		TreeUtil.expandAll(tree);

		if (previouslySelected != null)
		{
			DefaultMutableTreeNode match = TreeUtil.findNode(root, node -> sameRow(previouslySelected, node.getUserObject()));
			if (match != null) TreeUtil.selectNode(tree, match);
		}
		if (tree.isSelectionEmpty()) TreeUtil.promiseSelectFirst(tree);
	}

	/** Rows are compared by what they point at, since a rebuild creates fresh entries. */
	private static boolean sameRow(@NotNull C3TargetEntry a, @Nullable Object b)
	{
		if (a instanceof TargetEntry x && b instanceof TargetEntry y)
		{
			return x.projectDir().equals(y.projectDir()) && x.target().name().equals(y.target().name());
		}
		if (a instanceof ProjectEntry x && b instanceof ProjectEntry y) return x.dir().equals(y.dir());
		if (a instanceof LibraryEntry x && b instanceof LibraryEntry y) return x.dir().equals(y.dir());
		return a.equals(b);
	}

	private static @NotNull DefaultMutableTreeNode node(@NotNull C3TargetEntry entry)
	{
		DefaultMutableTreeNode node = new DefaultMutableTreeNode(entry);
		List<C3TargetEntry> children = switch (entry)
		{
			case ProjectEntry p -> p.children();
			case TargetEntry t -> t.children();
			case LibrariesEntry l -> l.children();
			case LibraryEntry l -> l.children();
			default -> List.of();
		};
		for (C3TargetEntry child : children) node.add(node(child));
		return node;
	}

	private @Nullable C3TargetEntry selected()
	{
		TreePath path = tree.getSelectionPath();
		if (path == null) return null;
		Object node = path.getLastPathComponent();
		return node instanceof DefaultMutableTreeNode n && n.getUserObject() instanceof C3TargetEntry e ? e : null;
	}

	/** The C3 project of the selection, else the only one of the workspace. */
	@Nullable VirtualFile selectedProjectDir()
	{
		TreePath path = tree.getSelectionPath();
		if (path != null)
		{
			for (Object component : path.getPath())
			{
				if (component instanceof DefaultMutableTreeNode n && n.getUserObject() instanceof ProjectEntry p) return p.dir();
			}
		}
		List<VirtualFile> all = C3Projects.findAll(project);
		return all.size() == 1 ? all.getFirst() : null;
	}

	/** Whether {@code entry} can be launched with {@code executor}. */
	private static boolean canRun(@Nullable C3TargetEntry entry, @NotNull Executor executor)
	{
		boolean debug = DefaultDebugExecutor.EXECUTOR_ID.equals(executor.getId());
		return switch (entry)
		{
			case TargetEntry t -> t.target().isExecutable();
			case TargetTestsEntry ignored -> true;
			case LibraryTestsEntry ignored -> !debug; // compile-test isn't wired into the debugger
			case null, default -> false;
		};
	}

	private boolean run(@Nullable C3TargetEntry entry, @NotNull Executor executor)
	{
		if (!canRun(entry, executor)) return false;
		switch (entry)
		{
			case TargetEntry t -> C3TargetRunner.runTarget(project, t.projectDir(), t.target().name(), executor);
			case TargetTestsEntry t ->
				C3TargetRunner.runTests(project, t.projectDir(), t.target(), null, "Tests " + t.target(), executor);
			case LibraryTestsEntry l -> C3TargetRunner.runLibraryTests(project, l.library(), null, "Tests");
			default -> { return false; }
		}
		return true;
	}

	@Override
	public void dispose()
	{
	}

	private final class RunAction extends DumbAwareAction
	{
		private final Executor executor;

		RunAction(@NotNull Executor executor)
		{
			super(executor.getStartActionText(), null, executor.getIcon());
			this.executor = executor;
		}

		@Override
		public @NotNull ActionUpdateThread getActionUpdateThread()
		{
			return ActionUpdateThread.EDT;
		}

		@Override
		public void update(@NotNull AnActionEvent e)
		{
			e.getPresentation().setEnabled(canRun(selected(), executor));
		}

		@Override
		public void actionPerformed(@NotNull AnActionEvent e)
		{
			run(selected(), executor);
		}
	}

	private final class AddTargetAction extends DumbAwareAction
	{
		AddTargetAction()
		{
			super("Add Target…", "Add an executable (apps/<name>) or a library (lib/<name>.c3l)", AllIcons.General.Add);
		}

		@Override
		public @NotNull ActionUpdateThread getActionUpdateThread()
		{
			return ActionUpdateThread.EDT;
		}

		@Override
		public void update(@NotNull AnActionEvent e)
		{
			e.getPresentation().setEnabled(!C3Projects.findAll(project).isEmpty());
		}

		@Override
		public void actionPerformed(@NotNull AnActionEvent e)
		{
			C3AddTargetDialog dialog = new C3AddTargetDialog(project, C3Projects.findAll(project), selectedProjectDir());
			if (!dialog.showAndGet()) return;
			VirtualFile created = dialog.getCreatedFile();
			refresh();
			if (created != null) FileEditorManager.getInstance(project).openFile(created, true);
		}
	}

	private final class OpenManifestAction extends DumbAwareAction
	{
		OpenManifestAction()
		{
			super("Open Manifest", "Open project.json or the library's manifest.json", AllIcons.Actions.EditSource);
		}

		@Override
		public @NotNull ActionUpdateThread getActionUpdateThread()
		{
			return ActionUpdateThread.EDT;
		}

		@Override
		public void update(@NotNull AnActionEvent e)
		{
			e.getPresentation().setEnabled(manifest() != null);
		}

		@Override
		public void actionPerformed(@NotNull AnActionEvent e)
		{
			VirtualFile manifest = manifest();
			if (manifest != null) FileEditorManager.getInstance(project).openFile(manifest, true);
		}

		private @Nullable VirtualFile manifest()
		{
			C3TargetEntry entry = selected();
			VirtualFile library = switch (entry)
			{
				case LibraryEntry l -> l.dir();
				case LibraryTestsEntry l -> l.library();
				case null, default -> null;
			};
			if (library != null) return library.findChild("manifest.json");
			VirtualFile dir = selectedProjectDir();
			return dir != null ? dir.findChild(C3Projects.MANIFEST) : null;
		}
	}

	/**
	 * Like CMake's or Gradle's "Reload Project": picks up changes made outside the IDE, re-reads
	 * the manifests, re-indexes the dependency libraries and rebuilds the tree.
	 */
	private final class ReloadProjectAction extends DumbAwareAction
	{
		ReloadProjectAction()
		{
			super("Reload C3 Project", "Re-read project.json and the libraries from disk and re-index them",
				AllIcons.Actions.Refresh);
		}

		@Override
		public @NotNull ActionUpdateThread getActionUpdateThread()
		{
			return ActionUpdateThread.EDT;
		}

		@Override
		public void actionPerformed(@NotNull AnActionEvent e)
		{
			VirtualFile[] dirs = C3Projects.findAll(project).toArray(VirtualFile[]::new);
			// Re-scan from disk so manifests or libraries changed outside the IDE are seen, then
			// reload once that has finished.
			VfsUtil.markDirty(true, true, dirs);
			RefreshQueue.getInstance().refresh(true, true, () -> ApplicationManager.getApplication().invokeLater(() -> {
				C3ProjectManifestListener.reloadLibraries(project, true);
				refresh();
			}, project.getDisposed()), dirs);
		}
	}

	private static final class Renderer extends ColoredTreeCellRenderer
	{
		@Override
		public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected, boolean expanded,
		                                  boolean leaf, int row, boolean hasFocus)
		{
			if (!(value instanceof DefaultMutableTreeNode node) || !(node.getUserObject() instanceof C3TargetEntry entry)) return;
			switch (entry)
			{
				case ProjectEntry p ->
				{
					setIcon(C3Icons.FILE);
					append(p.dir().getName(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
				}
				case TargetEntry t ->
				{
					C3ProjectManifest.Target target = t.target();
					setIcon(target.isExecutable() ? AllIcons.RunConfigurations.Application : AllIcons.Nodes.PpLib);
					append(target.name());
					if (!target.type().isEmpty()) append("  " + target.type(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
				}
				case TargetTestsEntry ignored ->
				{
					setIcon(AllIcons.RunConfigurations.TestState.Run);
					append("Tests");
				}
				case LibrariesEntry ignored ->
				{
					setIcon(AllIcons.Nodes.Folder);
					append("Libraries", SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);
				}
				case LibraryEntry l ->
				{
					setIcon(C3Icons.LIB_FILE);
					append(l.dir().getNameWithoutExtension());
					append("  " + l.dir().getName(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
				}
				case LibraryTestsEntry ignored ->
				{
					setIcon(AllIcons.RunConfigurations.TestState.Run);
					append("Tests");
				}
			}
		}
	}
}
