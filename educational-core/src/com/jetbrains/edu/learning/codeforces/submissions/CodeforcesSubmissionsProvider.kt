package com.jetbrains.edu.learning.codeforces.submissions

import com.intellij.openapi.project.Project
import com.jetbrains.edu.learning.codeforces.CodeforcesNames
import com.jetbrains.edu.learning.codeforces.CodeforcesSettings
import com.jetbrains.edu.learning.codeforces.api.CodeforcesConnector
import com.jetbrains.edu.learning.codeforces.authorization.LoginDialog
import com.jetbrains.edu.learning.codeforces.courseFormat.CodeforcesCourse
import com.jetbrains.edu.learning.courseFormat.Course
import com.jetbrains.edu.learning.courseFormat.ext.allTasks
import com.jetbrains.edu.learning.courseFormat.tasks.Task
import com.jetbrains.edu.learning.onError
import com.jetbrains.edu.learning.stepik.api.StepikBasedSubmission
import com.jetbrains.edu.learning.submissions.SubmissionsProvider

class CodeforcesSubmissionsProvider : SubmissionsProvider {
  override fun loadAllSubmissions(project: Project, course: Course): Map<Int, List<StepikBasedSubmission>> {
    if (!areSubmissionsAvailable(course) || !isLoggedIn()) return emptyMap()
    return loadSubmissions(course.allTasks, course.id)
  }

  override fun loadSubmissions(tasks: List<Task>, courseId: Int): Map<Int, List<StepikBasedSubmission>> {
    val (csrfToken, jSessionID) = CodeforcesConnector.getInstance().getCSRFTokenWithJSessionID().onError { return emptyMap() }
    return CodeforcesConnector.getInstance().getUserSubmissions(courseId, tasks, csrfToken, jSessionID)
  }

  override fun areSubmissionsAvailable(course: Course): Boolean = course is CodeforcesCourse

  override fun isLoggedIn(): Boolean = CodeforcesSettings.getInstance().isLoggedIn()

  override fun getPlatformName(): String = CodeforcesNames.CODEFORCES

  override fun doAuthorize() = LoginDialog().show()

}