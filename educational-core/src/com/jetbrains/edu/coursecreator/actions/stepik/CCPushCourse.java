package com.jetbrains.edu.coursecreator.actions.stepik;

import com.intellij.ide.IdeView;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task.Modal;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.jetbrains.edu.coursecreator.CCUtils;
import com.jetbrains.edu.coursecreator.stepik.StepikCourseUploader;
import com.jetbrains.edu.learning.EduNames;
import com.jetbrains.edu.learning.EduVersions;
import com.jetbrains.edu.learning.PluginUtils;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.EduCourse;
import com.jetbrains.edu.learning.courseFormat.ext.CourseExt;
import com.jetbrains.edu.learning.messages.EduCoreBundle;
import com.jetbrains.edu.learning.statistics.EduCounterUsageCollector;
import com.jetbrains.edu.learning.stepik.StepikNames;
import com.jetbrains.edu.learning.stepik.api.StepikConnector;
import com.jetbrains.edu.learning.yaml.YamlFormatSynchronizer;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;

import static com.jetbrains.edu.coursecreator.CCUtils.askToWrapTopLevelLessons;
import static com.jetbrains.edu.coursecreator.CCUtils.checkIfAuthorizedToStepik;
import static com.jetbrains.edu.coursecreator.StudyItemType.COURSE_TYPE;
import static com.jetbrains.edu.coursecreator.StudyItemTypeKt.*;

@SuppressWarnings("ComponentNotRegistered") // educational-core.xml
public class CCPushCourse extends DumbAwareAction {
  @Nls(capitalization = Nls.Capitalization.Title)
  public static String getUpdateTitleText() {
    return getUpdateOnStepikTitleMessage(COURSE_TYPE);
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  public static String getUploadTitleText() {
    return getUploadToStepikTitleMessage(COURSE_TYPE);
  }

  public CCPushCourse() {
    super(getUpdateTitleText(), getUpdateOnStepikMessage(COURSE_TYPE), null);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    Project project = e.getProject();
    presentation.setEnabledAndVisible(false);
    if (project == null || !CCUtils.isCourseCreator(project)) {
      return;
    }
    final Course course = StudyTaskManager.getInstance(project).getCourse();
    if (!(course instanceof EduCourse) || !course.isStepikRemote()) {
      return;
    }
    presentation.setEnabledAndVisible(true);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final IdeView view = e.getData(LangDataKeys.IDE_VIEW);
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (view == null || project == null) {
      return;
    }
    final Course course = StudyTaskManager.getInstance(project).getCourse();
    if (!(course instanceof EduCourse)) {
      return;
    }

    if (CourseExt.getHasSections(course) && CourseExt.getHasTopLevelLessons(course)) {
      if (!askToWrapTopLevelLessons(project, (EduCourse)course, EduCoreBundle.message("label.wrap.and.post"))) {
        return;
      }
    }

    if (!checkIfAuthorizedToStepik(project, e.getPresentation().getText())) {
      return;
    }

    doPush(project, (EduCourse)course);
    // we set course inside postCourse method that's why have to get it here again
    Course uploadedCourse = StudyTaskManager.getInstance(project).getCourse();
    if (uploadedCourse != null) {
      YamlFormatSynchronizer.saveRemoteInfo(uploadedCourse);
    }
    else {
      throw new IllegalStateException("Course is null while pushing course to stepik");
    }
  }

  public static void doPush(Project project, @NotNull EduCourse course) {
    if (!course.isStepikRemote()) {
      return;
    }
    EduCourse courseInfo = StepikConnector.getInstance().getCourseInfo(course.getId(), null, true);
    if (courseInfo == null) {
      Notification notification =
        new Notification("EduTools", EduCoreBundle.message("error.failed.to.update"),
                         EduCoreBundle.message("error.failed.to.update.no.course", StepikNames.STEPIK, getUploadTitleText()),
                         NotificationType.ERROR);
      notification.notify(project);
      return;
    }
    if (courseInfo.getFormatVersion() < EduVersions.JSON_FORMAT_VERSION) {
      Notification notification =
        new Notification("EduTools", EduCoreBundle.message("error.mismatch.format.version"),
                         EduCoreBundle.message("error.mismatch.format.version.invalid.plugin.version",
                                               PluginUtils.pluginVersion(EduNames.PLUGIN_ID), getUpdateTitleText()),
                         NotificationType.WARNING, createUpdateCourseNotificationListener(project, course));
      notification.notify(project);
      return;
    }
    updateCourse(project, course);
  }

  @NotNull
  private static NotificationListener createUpdateCourseNotificationListener(Project project, @NotNull EduCourse course) {
    return new NotificationListener() {
      @Override
      public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
        notification.expire();
        updateCourse(project, course);
      }
    };
  }

  private static void updateCourse(Project project, @NotNull EduCourse course) {
    ProgressManager.getInstance().run(new Modal(project, EduCoreBundle.message("action.push.course.updating"), true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setIndeterminate(false);
        new StepikCourseUploader(project, course).updateCourse();
        EduCounterUsageCollector.updateCourse();
      }
    });
  }
}
