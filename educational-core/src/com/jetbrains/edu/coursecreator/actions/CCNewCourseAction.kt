package com.jetbrains.edu.coursecreator.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.jetbrains.edu.coursecreator.ui.CCNewCourseDialog
import com.jetbrains.edu.learning.messages.EduCoreBundle
import com.jetbrains.edu.learning.newproject.ui.ContextHelpProvider
import com.jetbrains.edu.learning.statistics.EduCounterUsageCollector
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.util.function.Supplier

class CCNewCourseAction(
  title: Supplier<String> = EduCoreBundle.lazyMessage("action.new.course.default.text"),
  private val onOKAction: () -> Unit = {}
) : DumbAwareAction(title, EduCoreBundle.lazyMessage("action.new.course.description"), null), ContextHelpProvider {

  constructor(@Nls(capitalization = Nls.Capitalization.Title) title: String) : this(Supplier { title })

  override fun actionPerformed(e: AnActionEvent) {
    EduCounterUsageCollector.createNewCourseClicked(e.place)
    CCNewCourseDialog(EduCoreBundle.message("dialog.title.create.course"), EduCoreBundle.message("button.create"),
                      onOKAction = onOKAction).show()
  }

  override fun getHelpRelativePath(): String = "education/educator-start-guide.html"

  override fun getTooltipText(): String {
    return EduCoreBundle.message("course.dialog.create.course.help.tooltip")
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = CCPluginToggleAction.isCourseCreatorFeaturesEnabled
  }

  companion object {
    @NonNls
    const val ACTION_ID = "Educational.Educator.NewCourse"
  }
}
