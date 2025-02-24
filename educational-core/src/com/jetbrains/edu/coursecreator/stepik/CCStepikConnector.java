package com.jetbrains.edu.coursecreator.stepik;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.coursecreator.AdditionalFilesUtils;
import com.jetbrains.edu.coursecreator.StudyItemType;
import com.jetbrains.edu.coursecreator.StudyItemTypeKt;
import com.jetbrains.edu.learning.EduBrowser;
import com.jetbrains.edu.learning.OpenApiExtKt;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.courseFormat.*;
import com.jetbrains.edu.learning.courseFormat.tasks.CodeTask;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import com.jetbrains.edu.learning.messages.EduCoreBundle;
import com.jetbrains.edu.learning.stepik.StepSource;
import com.jetbrains.edu.learning.stepik.StepikNames;
import com.jetbrains.edu.learning.stepik.api.*;
import org.apache.http.HttpStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.jetbrains.edu.coursecreator.CCNotificationUtils.*;
import static com.jetbrains.edu.coursecreator.CCUtils.checkIfAuthorizedToStepik;
import static com.jetbrains.edu.learning.stepik.StepikNames.STEPIK;

public class CCStepikConnector {
  private static final Logger LOG = Logger.getInstance(CCStepikConnector.class.getName());

  private CCStepikConnector() { }

  // POST methods:

  public static int postSectionForTopLevelLessons(@NotNull Project project, @NotNull EduCourse course) {
    Section section = new Section();
    section.setName(course.getName());
    int sectionId = postSectionInfo(project, section, 1);
    course.setSectionIds(Collections.singletonList(sectionId));
    return sectionId;
  }

  public static boolean postSection(@NotNull Project project, @NotNull Section section) {
    EduCourse course = (EduCourse)StudyTaskManager.getInstance(project).getCourse();
    assert course != null;
    final int sectionId = postSectionInfo(project, section, section.getIndex());
    return sectionId != -1 && postLessons(project, sectionId, section.getLessons());
  }

  public static int postSectionInfo(@NotNull Project project, @NotNull Section section, int sectionIndex) {
    if (!checkIfAuthorizedToStepik(project, StudyItemTypeKt.getUploadToStepikTitleMessage(StudyItemType.SECTION_TYPE))) return -1;

    final Section postedSection = StepikConnector.getInstance().postSection(section, sectionIndex);
    if (postedSection == null) {
      showFailedToPostItemNotification(project, section, true);
      return -1;
    }
    section.setId(postedSection.getId());
    section.setUpdateDate(postedSection.getUpdateDate());
    return postedSection.getId();
  }

  private static boolean postLessons(@NotNull Project project, int sectionId, @NotNull List<Lesson> lessons) {
    int position = 1;
    boolean success = true;
    for (Lesson lesson : lessons) {
      updateProgress(EduCoreBundle.message("course.creator.stepik.uploading.lesson", lesson.getIndex()));
      success = postLesson(project, lesson, position, sectionId) && success;
      checkCanceled();
      position += 1;
    }
    return success;
  }

  public static boolean postLesson(@NotNull final Project project, @NotNull final Lesson lesson, int position, int sectionId) {
    Lesson postedLesson = postLessonInfo(project, lesson, sectionId, position);
    if (postedLesson == null) return false;

    boolean success = true;
    for (Task task : lesson.getTaskList()) {
      checkCanceled();
      success = postTask(project, task, postedLesson.getId()) && success;
    }
    if (!updateLessonAdditionalInfo(lesson, project)) {
      showFailedToPostItemNotification(project, lesson, true);
      return false;
    }
    return success;
  }

  public static Lesson postLessonInfo(@NotNull Project project, @NotNull Lesson lesson, int sectionId, int position) {
    if (!checkIfAuthorizedToStepik(project, StudyItemTypeKt.getUploadToStepikTitleMessage(StudyItemType.LESSON_TYPE))) return null;
    Course course = StudyTaskManager.getInstance(project).getCourse();
    assert course != null;
    final Lesson postedLesson = StepikConnector.getInstance().postLesson(lesson);
    if (postedLesson == null) {
      showFailedToPostItemNotification(project, lesson, true);
      return null;
    }
    if (sectionId != -1) {
      postedLesson.setUnitId(postUnit(postedLesson.getId(), position, sectionId, project));
    }

    lesson.setId(postedLesson.getId());
    lesson.setUnitId(postedLesson.getUnitId());
    lesson.setUpdateDate(postedLesson.getUpdateDate());
    return postedLesson;
  }

  public static int postUnit(int lessonId, int position, int sectionId, @NotNull Project project) {
    if (!checkIfAuthorizedToStepik(project, StudyItemTypeKt.getUploadToStepikTitleMessage(StudyItemType.LESSON_TYPE))) return lessonId;

    final StepikUnit unit = StepikConnector.getInstance().postUnit(lessonId, position, sectionId);
    if (unit == null || unit.getId() == null) {
      showErrorNotification(project, EduCoreBundle.message("course.creator.stepik.failed.to.post.unit"));
      return -1;
    }
    return unit.getId();
  }

  public static boolean postTask(@NotNull final Project project, @NotNull final Task task, final int lessonId) {
    if (!checkIfAuthorizedToStepik(project, StudyItemTypeKt.getUploadToStepikTitleMessage(StudyItemType.TASK_TYPE))) return false;
    // TODO: add meaningful comment to final Success notification that Code tasks were not pushed
    if (task instanceof CodeTask) return true;

    final StepSource stepSource = StepikConnector.getInstance().postTask(project, task, lessonId);
    if (stepSource == null) {
      showFailedToPostItemNotification(project, task, true);
      return false;
    }
    task.setId(stepSource.getId());
    task.setUpdateDate(stepSource.getUpdateDate());
    return true;
  }

  // UPDATE methods:

  public static boolean updateCourseInfo(@NotNull final Project project, @NotNull final EduCourse course) {
    if (!checkIfAuthorizedToStepik(project, EduCoreBundle.message("item.update.on.0.course.title", STEPIK))) return false;
    // Course info parameters such as isPublic() and isCompatible can be changed from Stepik site only
    // so we get actual info here
    EduCourse courseInfo = StepikConnector.getInstance().getCourseInfo(course.getId());
    if (courseInfo != null) {
      course.setStepikPublic(courseInfo.isStepikPublic());
      course.setCompatible(courseInfo.isCompatible());
    }
    else {
      LOG.warn("Failed to get current course info");
    }
    int responseCode = StepikConnector.getInstance().updateCourse(course);

    if (responseCode == HttpStatus.SC_FORBIDDEN) {
      showNoRightsToUpdateOnStepikNotification(project);
      return false;
    }
    if (responseCode != HttpStatus.SC_OK) {
      showErrorNotification(project, EduCoreBundle.message("notification.course.creator.failed.to.update.course.title"), null,
                            getShowLogAction());
      return false;
    }
    return true;
  }

  public static boolean updateCourseAdditionalInfo(@NotNull Project project, @NotNull Course course) {
    if (!checkIfAuthorizedToStepik(project, EduCoreBundle.message("action.update.additional.materials.text"))) return false;

    EduCourse courseInfo = StepikConnector.getInstance().getCourseInfo(course.getId());
    assert courseInfo != null;
    updateProgress(EduCoreBundle.message("course.creator.stepik.uploading.additional.data"));
    String errors = AdditionalFilesUtils.checkIgnoredFiles(project);
    if (errors != null) {
      showErrorNotification(project, EduCoreBundle.message("course.creator.stepik.failed.to.update.additional.files"), errors);
      return false;
    }
    final List<TaskFile> additionalFiles = AdditionalFilesUtils.collectAdditionalFiles(courseInfo, project);
    CourseAdditionalInfo courseAdditionalInfo = new CourseAdditionalInfo(additionalFiles, course.getSolutionsHidden());
    return StepikConnector.getInstance().updateCourseAttachment(courseAdditionalInfo, courseInfo) == HttpStatus.SC_CREATED;
  }

  public static boolean updateSectionForTopLevelLessons(@NotNull EduCourse course) {
    Section section = new Section();
    section.setName(course.getName());
    section.setPosition(1);
    section.setIndex(1);
    section.setId(course.getSectionIds().get(0));
    return updateSectionInfo(section);
  }

  public static boolean updateSectionInfo(@NotNull Section section) {
    section.setPosition(section.getIndex());
    return StepikConnector.getInstance().updateSection(section) != null;
  }

  public static boolean updateLesson(@NotNull final Project project,
                                     @NotNull final Lesson lesson,
                                     boolean showNotification,
                                     int sectionId) {
    Lesson postedLesson = updateLessonInfo(project, lesson, showNotification, sectionId);
    return postedLesson != null &&
           updateLessonTasks(project, lesson, postedLesson.getSteps()) &&
           updateLessonAdditionalInfo(lesson, project);
  }

  public static Lesson updateLessonInfo(@NotNull final Project project,
                                        @NotNull final Lesson lesson,
                                        boolean showNotification, int sectionId) {
    if (!checkIfAuthorizedToStepik(project, StudyItemTypeKt.getUpdateOnStepikTitleMessage(StudyItemType.LESSON_TYPE))) return null;
    // TODO: support case when lesson was removed from Stepik

    final Lesson updatedLesson = StepikConnector.getInstance().updateLesson(lesson);
    if (updatedLesson == null && showNotification) {
      showFailedToPostItemNotification(project, lesson, false);
      return null;
    }
    if (sectionId != -1) {
      updateUnit(lesson.getUnitId(), lesson.getId(), lesson.getIndex(), sectionId, project);
    }

    return updatedLesson;
  }

  public static boolean updateLessonAdditionalInfo(@NotNull final Lesson lesson, @NotNull Project project) {
    if (!checkIfAuthorizedToStepik(project, StudyItemTypeKt.getUpdateOnStepikTitleMessage(StudyItemType.LESSON_TYPE))) return false;

    LessonAdditionalInfo info = AdditionalFilesUtils.collectAdditionalLessonInfo(lesson, project);
    if (info.isEmpty()) {
      StepikConnector.getInstance().deleteLessonAttachment(lesson.getId());
      return true;
    }
    updateProgress(EduCoreBundle.message("course.creator.stepik.progress.details.publishing.additional.data", lesson.getPresentableName()));
    return StepikConnector.getInstance().updateLessonAttachment(info, lesson) == HttpStatus.SC_CREATED;
  }

  public static void updateUnit(int unitId, int lessonId, int position, int sectionId, @NotNull Project project) {
    if (!checkIfAuthorizedToStepik(project, StudyItemTypeKt.getUpdateOnStepikTitleMessage(StudyItemType.LESSON_TYPE))) return;

    final StepikUnit unit = StepikConnector.getInstance().updateUnit(unitId, lessonId, position, sectionId);
    if (unit == null) {
      showErrorNotification(project, EduCoreBundle.message("course.creator.stepik.failed.to.update.unit"));
    }
  }

  private static boolean updateLessonTasks(@NotNull Project project, @NotNull Lesson localLesson, @NotNull List<Integer> steps) {
    final Set<Integer> localTasksIds = localLesson.getTaskList()
      .stream()
      .map(task -> task.getId())
      .filter(id -> id > 0)
      .collect(Collectors.toSet());

    final List<Integer> taskIdsToDelete = steps.stream()
      .filter(id -> !localTasksIds.contains(id))
      .collect(Collectors.toList());

    // Remove all tasks from Stepik which are not in our lessons now
    for (Integer step : taskIdsToDelete) {
      StepikConnector.getInstance().deleteTask(step);
    }

    boolean success = true;
    for (Task task : localLesson.getTaskList()) {
      checkCanceled();
      success = (task.getId() > 0 ? updateTask(project, task) : postTask(project, task, localLesson.getId())) && success;
    }
    return success;
  }

  public static boolean updateTask(@NotNull final Project project, @NotNull final Task task) {
    if (!checkIfAuthorizedToStepik(project, StudyItemTypeKt.getUpdateOnStepikTitleMessage(StudyItemType.TASK_TYPE))) return false;
    VirtualFile taskDir = task.getDir(OpenApiExtKt.getCourseDir(project));
    if (taskDir == null) return false;

    final int responseCode = StepikConnector.getInstance().updateTask(project, task);

    switch (responseCode) {
      case HttpStatus.SC_OK:
        StepSource step = StepikConnector.getInstance().getStep(task.getId());
        if (step != null) {
          task.setUpdateDate(step.getUpdateDate());
        }
        else {
          LOG.warn(String.format("Failed to get step for task '%s' with id %d while setting an update date", task.getName(), task.getId()));
        }
        return true;
      case HttpStatus.SC_NOT_FOUND:
        // TODO: support case when lesson was removed from Stepik too
        return postTask(project, task, task.getLesson().getId());
      case HttpStatus.SC_FORBIDDEN:
        showNoRightsToUpdateOnStepikNotification(project);
        return false;
      default:
        showFailedToPostItemNotification(project, task, false);
        return false;
    }
  }

  // GET methods:

  public static int getTaskPosition(final int taskId) {
    StepSource step = StepikConnector.getInstance().getStep(taskId);
    return step != null ? step.getPosition() : -1;
  }

  // helper methods:

  @SuppressWarnings("UnstableApiUsage")
  private static void updateProgress(@NlsContexts.ProgressDetails @NotNull String text) {
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.checkCanceled();
      indicator.setText2(text);
    }
  }

  public static AnAction openOnStepikAction(@NotNull @NonNls String url) {
    return new AnAction(EduCoreBundle.message("action.open.on.text", STEPIK)) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        EduBrowser.getInstance().browse(StepikNames.getStepikUrl() + url);
      }
    };
  }

  private static void checkCanceled() {
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.checkCanceled();
    }
  }
}
