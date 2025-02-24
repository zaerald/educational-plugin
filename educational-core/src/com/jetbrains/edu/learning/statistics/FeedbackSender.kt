package com.jetbrains.edu.learning.statistics

import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.notification.impl.NotificationFullContent
import com.intellij.openapi.project.Project
import com.intellij.util.PlatformUtils
import com.jetbrains.edu.learning.EduBrowser
import com.jetbrains.edu.learning.courseFormat.Course
import com.jetbrains.edu.learning.courseFormat.CourseMode
import javax.swing.event.HyperlinkEvent

fun showNotification(student : Boolean, course: Course, project: Project) {
  val feedbackUrl = feedbackUrlTemplate
      .replace("\$PRODUCT", productMap[PlatformUtils.getPlatformPrefix()] ?:
                                             PlatformUtils.getPlatformPrefix())
      .replace("\$COURSE", course.name)
      .replace("\$MODE", if (course.courseMode == CourseMode.STUDENT) "Learner" else "Educator")

  val product = if (PlatformUtils.isPyCharmEducational()) "PyCharm Edu" else "EduTools"
  val language = course.languageID

  var content = if (student) studentTemplate else creatorTemplate

  content = content.replace("\$PRODUCT", product)
                   .replace("\$URL", feedbackUrl)
                   .replace("\$LANGUAGE", language.toLowerCase().capitalize())
  val notification = MyNotification(content, feedbackUrl)
  PropertiesComponent.getInstance().setValue(feedbackAsked, true)
  notification.notify(project)
}

class MyNotification(content: String, feedbackUrl: String) :
  Notification("EduTools", "Congratulations", content, NotificationType.INFORMATION),
  NotificationFullContent {
  init {
    setListener(object : NotificationListener.Adapter() {
      override fun hyperlinkActivated(notification: Notification, e: HyperlinkEvent) {
        EduBrowser.getInstance().browse(feedbackUrl)
      }
    })
  }
}

fun isFeedbackAsked() : Boolean = PropertiesComponent.getInstance().getBoolean(feedbackAsked)

const val feedbackAsked = "askFeedbackNotification"

const val feedbackUrlTemplate = "https://www.jetbrains.com/feedback/feedback.jsp?" +
                                "product=EduTools&ide=\$PRODUCT&course=\$COURSE&mode=\$MODE"

const val creatorTemplate = "<html>You’ve just created your first tasks with \$PRODUCT!\n" +
                            "Please take a moment to <a href=\"\$URL\">share</a> your experience and help us make teaching \$LANGUAGE better.</html>"

const val studentTemplate = "<html>You’ve just completed your first lesson with \$PRODUCT!\n" +
                            "Please take a moment to <a href=\"\$URL\">share</a> your experience and help us make learning \$LANGUAGE better.</html>"

val productMap = hashMapOf(
    Pair(PlatformUtils.PYCHARM_CE_PREFIX, "PCC"),
    Pair(PlatformUtils.PYCHARM_PREFIX, "PCP"),
    Pair(PlatformUtils.PYCHARM_EDU_PREFIX, "PCE"),
    Pair(PlatformUtils.IDEA_CE_PREFIX, "IIC"),
    Pair(PlatformUtils.IDEA_PREFIX, "IIU"),
    Pair("AndroidStudio", "AI"),
    Pair(PlatformUtils.WEB_PREFIX, "WS"),
    Pair(PlatformUtils.PHP_PREFIX, "PS"),
    Pair(PlatformUtils.APPCODE_PREFIX, "AC"),
    Pair(PlatformUtils.CLION_PREFIX, "CL"),
    Pair(PlatformUtils.DBE_PREFIX, "DG"),
    Pair(PlatformUtils.GOIDE_PREFIX, "GO"),
    Pair(PlatformUtils.RIDER_PREFIX, "RD"),
    Pair(PlatformUtils.RUBY_PREFIX, "RM"))
