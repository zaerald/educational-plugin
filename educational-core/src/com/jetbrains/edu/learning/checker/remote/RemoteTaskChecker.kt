package com.jetbrains.edu.learning.checker.remote

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.jetbrains.edu.learning.Result
import com.jetbrains.edu.learning.Ok
import com.jetbrains.edu.learning.checker.CheckResult
import com.jetbrains.edu.learning.courseFormat.tasks.Task

interface RemoteTaskChecker {

  fun canCheck(project: Project, task: Task): Boolean

  fun check(project: Project, task: Task, indicator: ProgressIndicator): CheckResult

  fun retry(task: Task): Result<Boolean, String>? {
    return null
  }
}
