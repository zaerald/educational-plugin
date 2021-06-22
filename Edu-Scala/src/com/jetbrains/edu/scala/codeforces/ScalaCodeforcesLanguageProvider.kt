package com.jetbrains.edu.scala.codeforces

import com.jetbrains.edu.jvm.JdkProjectSettings
import com.jetbrains.edu.learning.EduNames
import com.jetbrains.edu.learning.codeforces.CodeforcesLanguageProvider
import com.jetbrains.edu.learning.configuration.EduConfigurator
import com.jetbrains.edu.scala.gradle.ScalaGradleConfigurator
import icons.EducationalCoreIcons
import javax.swing.Icon

class ScalaCodeforcesLanguageProvider : CodeforcesLanguageProvider {
  override val codeforcesLanguageNamings: List<String> = listOf("Scala")
  override val configurator: EduConfigurator<JdkProjectSettings> = ScalaGradleConfigurator()
  override val languageId: String = EduNames.SCALA
  override val templateFileName: String = "codeforces.Scala Main.scala"
  override val templateName: String = "Main.scala"
  override val languageIcon: Icon = EducationalCoreIcons.ScalaLogo
}