package com.jetbrains.edu.python.learning.checkio

import com.intellij.openapi.project.Project
import com.jetbrains.edu.EducationalCoreIcons
import com.jetbrains.edu.learning.LoginWidget
import com.jetbrains.edu.learning.checkio.account.CheckiOAccount
import com.jetbrains.edu.learning.checkio.utils.CheckiONames
import com.jetbrains.edu.python.learning.checkio.connectors.PyCheckiOOAuthConnector
import com.jetbrains.edu.python.learning.checkio.utils.profileUrl
import com.jetbrains.edu.python.learning.messages.EduPythonBundle

class PyCheckiOWidget(project: Project) : LoginWidget<CheckiOAccount>(project,
                                                                      EduPythonBundle.message("checkio.widget.title"),
                                                                      EduPythonBundle.message("checkio.widget.tooltip"),
                                                                      EducationalCoreIcons.CheckiO) {
  override val account: CheckiOAccount?
    get() = PyCheckiOSettings.getInstance().account

  override val platformName: String
    get() = CheckiONames.PY_CHECKIO

  override fun profileUrl(account: CheckiOAccount): String = account.profileUrl

  override fun ID() = "PyCheckiOAccountWidget"

  override fun authorize() {
    PyCheckiOOAuthConnector.doAuthorize()
  }

  override fun resetAccount() {
    PyCheckiOSettings.getInstance().account = null
  }
}