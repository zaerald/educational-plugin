package com.jetbrains.edu.learning.stepik.hyperskill.api

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.jetbrains.edu.learning.EduNames
import com.jetbrains.edu.learning.courseFormat.CheckStatus
import com.jetbrains.edu.learning.courseFormat.Course
import com.jetbrains.edu.learning.courseFormat.Lesson
import com.jetbrains.edu.learning.courseFormat.Section
import com.jetbrains.edu.learning.courseFormat.ext.configurator
import com.jetbrains.edu.learning.courseFormat.tasks.CodeTask
import com.jetbrains.edu.learning.courseFormat.tasks.EduTask
import com.jetbrains.edu.learning.courseFormat.tasks.Task
import com.jetbrains.edu.learning.courseFormat.tasks.choice.ChoiceTask
import com.jetbrains.edu.learning.stepik.SolutionLoaderBase
import com.jetbrains.edu.learning.stepik.api.StepikBasedSubmission
import com.jetbrains.edu.learning.stepik.hyperskill.HyperskillConfigurator
import com.jetbrains.edu.learning.stepik.hyperskill.courseFormat.HyperskillCourse
import com.jetbrains.edu.learning.stepik.hyperskill.markStageAsCompleted
import com.jetbrains.edu.learning.stepik.hyperskill.openSelectedStage
import com.jetbrains.edu.learning.submissions.Submission

class HyperskillSolutionLoader(project: Project) : SolutionLoaderBase(project) {

  override fun loadSolution(task: Task, submissions: List<Submission>): TaskSolutions {
    val lastSubmission: Submission = submissions.firstOrNull { it.taskId == task.id } ?: return TaskSolutions.EMPTY
    if (lastSubmission !is StepikBasedSubmission) error("Hyperskill submission ${lastSubmission.id} for task ${task.name} is not instance of Submission class")

    val files: Map<String, Solution> = when (task) {
      is EduTask -> lastSubmission.eduTaskFiles
      is CodeTask -> lastSubmission.codeTaskFiles(task)
      is ChoiceTask -> emptyMap()
      else -> {
        LOG.warn("Solutions for task ${task.name} of type ${task::class.simpleName} not loaded")
        emptyMap()
      }
    }.filter { (_, solution) -> solution.isVisible }

    return if (files.isEmpty()) TaskSolutions.EMPTY
    else TaskSolutions(lastSubmission.time, lastSubmission.status.toCheckStatus(), files)
  }

  private val StepikBasedSubmission.eduTaskFiles: Map<String, Solution>
    get() = solutionFiles?.associate { it.name to Solution(it.text, it.isVisible, emptyList()) } ?: emptyMap()

  private fun StepikBasedSubmission.codeTaskFiles(task: CodeTask): Map<String, Solution> {
    val codeFromServer = reply?.code ?: return emptyMap()
    val configurator = task.course.configurator as? HyperskillConfigurator ?: return emptyMap()
    val taskFile = configurator.getCodeTaskFile(project, task) ?: return emptyMap()
    return mapOf(taskFile.name to Solution(codeFromServer, true, emptyList()))
  }

  override fun provideTasksToUpdate(course: Course): List<Task> {
    return course.items.asSequence().flatMap {
      when (it) {
        is Lesson -> sequenceOf(it)
        is Section -> it.items.asSequence().filterIsInstance<Lesson>()
        else -> emptySequence()
      }
    }.flatMap { it.taskList.asSequence() }.toList()
  }

  override fun updateTasks(course: Course,
                           tasks: List<Task>,
                           submissions: List<Submission>,
                           progressIndicator: ProgressIndicator?,
                           force: Boolean) {
    super.updateTasks(course, tasks, submissions, progressIndicator, force)
    runInEdt {
      openSelectedStage(course, project)
    }
  }

  override fun updateTask(project: Project, task: Task, submissions: List<Submission>, force: Boolean): Boolean {
    val course = task.course as HyperskillCourse
    if (course.isStudy && task.lesson == course.getProjectLesson() && submissions.any { it.taskId == task.id && it.status == EduNames.CORRECT }) {
      markStageAsCompleted(task)
    }
    return super.updateTask(project, task, submissions, force)
  }

  private fun String?.toCheckStatus(): CheckStatus = when (this) {
    EduNames.WRONG -> CheckStatus.Failed
    EduNames.CORRECT -> CheckStatus.Solved
    else -> CheckStatus.Unchecked
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): HyperskillSolutionLoader = project.service()

    private val LOG = Logger.getInstance(HyperskillSolutionLoader::class.java)
  }
}

