package com.jetbrains.edu.learning.courseFormat

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.LayeredIcon
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.JBUI
import com.jetbrains.edu.learning.messages.EduCoreBundle
import javax.swing.Icon

sealed class CourseVisibility(private val weight: Int) : Comparable<CourseVisibility> {

  open val tooltipText : String? = null
  open val textAttributes: SimpleTextAttributes = SimpleTextAttributes.REGULAR_ATTRIBUTES

  open fun getDecoratedLogo(icon: Icon?): Icon? = icon

  object PrivateVisibility : CourseVisibility(0) {
    override val tooltipText = EduCoreBundle.message("course.visibility.private")

    override fun getDecoratedLogo(icon: Icon?): Icon {
      val layeredIcon = LayeredIcon(2)
      layeredIcon.setIcon(icon, 0, 0, 0)
      layeredIcon.setIcon(AllIcons.Ide.Readonly, 1, JBUI.scale(7), JBUI.scale(7))
      return layeredIcon
    }
  }

  object LocalVisibility : CourseVisibility(3)

  object PublicVisibility : CourseVisibility(4) {
    override val tooltipText = EduCoreBundle.message("course.visibility.public.not.approved")
    override val textAttributes: SimpleTextAttributes = SimpleTextAttributes.GRAYED_ATTRIBUTES

    override fun getDecoratedLogo(icon: Icon?) = icon?.let { IconLoader.getTransparentIcon(it) }
  }

  class FeaturedVisibility(internal val inGroup: Int) : CourseVisibility(1)

  class InProgressVisibility(internal val inGroup: Int) : CourseVisibility(2)

  override fun compareTo(other: CourseVisibility): Int {
    if (weight != other.weight) {
      return weight.compareTo(other.weight)
    }
    if (this is FeaturedVisibility && other is FeaturedVisibility) {
      return inGroup.compareTo(other.inGroup)
    }
    if (this is InProgressVisibility && other is InProgressVisibility) {
      return inGroup.compareTo(other.inGroup)
    }
    return 0
  }
}