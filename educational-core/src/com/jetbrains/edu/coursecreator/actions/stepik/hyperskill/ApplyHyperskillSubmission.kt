package com.jetbrains.edu.coursecreator.actions.stepik.hyperskill

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidatorEx
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.StringUtil
import com.jetbrains.edu.EducationalCoreIcons
import com.jetbrains.edu.learning.*
import com.jetbrains.edu.learning.EduExperimentalFeatures.CC_HYPERSKILL
import com.jetbrains.edu.learning.courseFormat.tasks.Task
import com.jetbrains.edu.learning.messages.EduCoreBundle
import com.jetbrains.edu.learning.stepik.api.StepikConnector
import com.jetbrains.edu.learning.stepik.hyperskill.HYPERSKILL
import com.jetbrains.edu.learning.stepik.hyperskill.api.HyperskillSolutionLoader
import com.jetbrains.edu.learning.stepik.hyperskill.courseFormat.HyperskillCourse
import org.jetbrains.annotations.NonNls

@Suppress("ComponentNotRegistered")
class ApplyHyperskillSubmission : DumbAwareAction(
  EduCoreBundle.lazyMessage("action.apply.submission.text", HYPERSKILL),
  EduCoreBundle.lazyMessage("action.apply.submission.description", HYPERSKILL),
  EducationalCoreIcons.JB_ACADEMY
) {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val task = getTask(project, e) ?: return

    val validator = object : InputValidatorEx {
      private var errorText: String? = null

      override fun checkInput(inputString: String?): Boolean {
        errorText = if (!StringUtil.isNotNegativeNumber(inputString))
          EduCoreBundle.message("error.submission.invalid.id")
        else null

        return errorText == null
      }

      override fun getErrorText(inputString: String?): String? {
        return errorText
      }

      override fun canClose(inputString: String?): Boolean {
        return checkInput(inputString)
      }
    }

    val idText = Messages.showInputDialog(project, EduCoreBundle.message("submission.id"),
                                          EduCoreBundle.message("action.apply.submission.text", HYPERSKILL),
                                          null, null, validator) ?: return

    val id = Integer.valueOf(idText) // valid int because of validator

    computeUnderProgress(project, EduCoreBundle.message("submission.applying"), false) {
      val submission = StepikConnector.getInstance().getSubmission(id).onError {
        runInEdt {
          Messages.showErrorDialog(EduCoreBundle.message("error.submission.failed.to.retrieve", id),
                                   EduCoreBundle.message("error.submission.not.applied"))
        }
        return@computeUnderProgress
      }
      // there is no information about step id in Stepik submissions, so we have to assume that it's a submission for current task
      submission.taskId = task.id
      HyperskillSolutionLoader.getInstance(project).updateTask(project, task, listOf(submission), true)
    }
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = false

    if (!isFeatureEnabled(CC_HYPERSKILL)) return

    val project = e.project ?: return
    e.presentation.isEnabledAndVisible = getTask(project, e) != null
  }

  private fun getTask(project: Project, e: AnActionEvent): Task? {
    val course = StudyTaskManager.getInstance(project).course as? HyperskillCourse ?: return null
    if (course.isStudy) {
      return EduUtils.getCurrentTask(project)
    }

    val selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
    if (selectedFiles == null || selectedFiles.size != 1) {
      return null
    }
    return selectedFiles[0].getTask(project)
  }

  companion object {
    @NonNls
    const val ACTION_ID = "Educational.Educator.ApplyHyperskillSubmission"
  }
}