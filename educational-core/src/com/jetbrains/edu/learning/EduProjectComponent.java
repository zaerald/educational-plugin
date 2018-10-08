package com.jetbrains.edu.learning;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.containers.hash.HashMap;
import com.intellij.util.messages.MessageBusConnection;
import com.jetbrains.edu.learning.actions.*;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.ext.CourseExt;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import com.jetbrains.edu.learning.gradle.generation.EduGradleUtils;
import com.jetbrains.edu.learning.handlers.UserCreatedFileListener;
import com.jetbrains.edu.learning.newproject.CourseProjectGenerator;
import com.jetbrains.edu.learning.projectView.CourseViewPane;
import com.jetbrains.edu.learning.statistics.EduUsagesCollector;
import com.jetbrains.edu.learning.stepik.StepikConnector;
import com.jetbrains.edu.learning.stepik.courseFormat.StepikCourse;
import com.jetbrains.edu.learning.stepik.courseFormat.ext.StepikCourseExt;
import com.jetbrains.edu.learning.ui.taskDescription.TaskDescriptionView;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.jetbrains.edu.learning.EduUtils.*;

public class EduProjectComponent implements ProjectComponent {
  private static final Logger LOG = Logger.getInstance(EduProjectComponent.class.getName());
  private final Project myProject;
  private final Map<Keymap, List<Pair<String, String>>> myDeletedShortcuts = new HashMap<>();
  private MessageBusConnection myBusConnection;

  private EduProjectComponent(@NotNull final Project project) {
    myProject = project;
  }

  @Override
  public void projectOpened() {
    if (myProject.isDisposed() || !isStudyProject(myProject)) {
      return;
    }

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      ToolWindowManager.getInstance(myProject).invokeLater(() -> selectProjectView());
    }
    StartupManager.getInstance(myProject).runWhenProjectIsInitialized(
      () -> {
        Course course = StudyTaskManager.getInstance(myProject).getCourse();
        if (course == null) {
          LOG.warn("Opened project is with null course");
          return;
        }

        if (course instanceof StepikCourse) {
          StepikConnector.updateCourseIfNeeded(myProject, (StepikCourse)course);
        }

        if (EduGradleUtils.isConfiguredWithGradle(myProject)) {
          setupGradleProject(course);
        }

        ApplicationManager.getApplication().invokeLater(() -> ApplicationManager.getApplication().runWriteAction(() -> {
            registerShortcuts();
            EduUsagesCollector.projectTypeOpened(course.getCourseMode());
          }));
      }
    );

    myBusConnection = ApplicationManager.getApplication().getMessageBus().connect();
    myBusConnection.subscribe(EditorColorsManager.TOPIC, new EditorColorsListener() {
      @Override
      public void globalSchemeChange(EditorColorsScheme scheme) {
        TaskDescriptionView.getInstance(myProject).updateTaskDescription();
      }
    });
  }

  private void setupGradleProject(@NotNull Course course) {
    EduConfigurator<?> configurator = CourseExt.getConfigurator(course);
    if (configurator == null) {
      LOG.warn(String.format("Failed to refresh gradle project: configurator for `%s` is null", course.getLanguageID()));
      return;
    }

    if (myProject.getUserData(CourseProjectGenerator.EDU_PROJECT_CREATED) == Boolean.TRUE) {
      configurator.getCourseBuilder().refreshProject(myProject);
    } else if (isAndroidStudio()) {
      // Unexpectedly, Android Studio corrupts content root paths after course project reopening
      // And project structure can't show project tree because of it.
      // We don't know better and cleaner way how to fix it than to refresh project.
      configurator.getCourseBuilder().refreshProject(myProject, new EduCourseBuilder.ProjectRefreshListener() {
        @Override
        public void onSuccess() {
          // We have to open current opened file in project view manually
          // because it can't restore previous state.
          VirtualFile[] files = FileEditorManager.getInstance(myProject).getSelectedFiles();
          for (VirtualFile file : files) {
            Task task = getTaskForFile(myProject, file);
            if (task != null) {
              ProjectView.getInstance(myProject).select(file, file, false);
            }
          }
        }

        @Override
        public void onFailure(@NotNull String errorMessage) {
          LOG.warn("Failed to refresh gradle project: " + errorMessage);
        }
      });
    }

    // Android Studio creates `gradlew` not via VFS so we have to refresh project dir
    VfsUtil.markDirtyAndRefresh(false, true, true, myProject.getBaseDir());
    String projectBasePath = myProject.getBasePath();
    if (projectBasePath != null) {
      // Android Studio creates non executable `gradlew`
      new File(FileUtil.toSystemDependentName(projectBasePath), "gradlew").setExecutable(true);
    }
  }

  private void selectProjectView() {
    ProjectView projectView = ProjectView.getInstance(myProject);
    if (projectView != null) {
      String selectedViewId = ProjectView.getInstance(myProject).getCurrentViewId();
      if (!CourseViewPane.ID.equals(selectedViewId)) {
        projectView.changeView(CourseViewPane.ID);
      }
    }
    else {
      LOG.warn("Failed to select Project View");
    }
  }

  private void registerShortcuts() {
    addShortcut(CheckAction.ACTION_ID, new String[]{CheckAction.SHORTCUT});
    addShortcut(RevertTaskAction.ACTION_ID, new String[]{RevertTaskAction.SHORTCUT});
    addShortcut(ShowHintAction.ACTION_ID, new String[]{ShowHintAction.SHORTCUT});
    addShortcut(NextPlaceholderAction.ACTION_ID, new String[]{NextPlaceholderAction.SHORTCUT, NextPlaceholderAction.SHORTCUT2});
    addShortcut(PrevPlaceholderAction.ACTION_ID, new String[]{PrevPlaceholderAction.SHORTCUT});
    addShortcut(NextTaskAction.ACTION_ID, new String[]{NextTaskAction.SHORTCUT});
    addShortcut(PreviousTaskAction.ACTION_ID, new String[]{PreviousTaskAction.SHORTCUT});
  }

  private void addShortcut(@NotNull final String actionIdString, @NotNull final String[] shortcuts) {
    KeymapManagerEx keymapManager = KeymapManagerEx.getInstanceEx();
    for (Keymap keymap : keymapManager.getAllKeymaps()) {
      List<Pair<String, String>> pairs = myDeletedShortcuts.get(keymap);
      if (pairs == null) {
        pairs = new ArrayList<>();
        myDeletedShortcuts.put(keymap, pairs);
      }
      for (String shortcutString : shortcuts) {
        Shortcut studyActionShortcut = new KeyboardShortcut(KeyStroke.getKeyStroke(shortcutString), null);
        String[] actionsIds = keymap.getActionIds(studyActionShortcut);
        for (String actionId : actionsIds) {
          pairs.add(Pair.create(actionId, shortcutString));
          keymap.removeShortcut(actionId, studyActionShortcut);
        }
        keymap.addShortcut(actionIdString, studyActionShortcut);
      }
    }
  }

  @Override
  public void projectClosed() {
    final Course course = StudyTaskManager.getInstance(myProject).getCourse();
    if (course != null) {
      KeymapManagerEx keymapManager = KeymapManagerEx.getInstanceEx();
      for (Keymap keymap : keymapManager.getAllKeymaps()) {
        List<Pair<String, String>> pairs = myDeletedShortcuts.get(keymap);
        if (pairs != null && !pairs.isEmpty()) {
          for (Pair<String, String> actionShortcut : pairs) {
            keymap.addShortcut(actionShortcut.first, new KeyboardShortcut(KeyStroke.getKeyStroke(actionShortcut.second), null));
          }
        }
      }
    }
  }

  @Override
  public void initComponent() {
    if (!OpenApiExtKt.isUnitTestMode() && isStudentProject(myProject)) {
      VirtualFileManager.getInstance().addVirtualFileListener(new UserCreatedFileListener(myProject));
    }
  }

  @Override
  public void disposeComponent() {
    if (myBusConnection != null) {
      myBusConnection.disconnect();
    }
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "StudyTaskManager";
  }
}
