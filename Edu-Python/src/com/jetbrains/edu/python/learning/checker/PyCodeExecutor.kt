package com.jetbrains.edu.python.learning.checker

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.project.Project
import com.jetbrains.edu.learning.checker.DefaultCodeExecutor
import com.jetbrains.edu.learning.codeforces.run.CodeforcesRunConfiguration
import com.jetbrains.edu.python.learning.codeforces.PyCodeforcesRunConfiguration

class PyCodeExecutor : DefaultCodeExecutor() {
  override fun createRedirectInputConfiguration(project: Project, factory: ConfigurationFactory): CodeforcesRunConfiguration {
    return PyCodeforcesRunConfiguration(project, factory)
  }
}
