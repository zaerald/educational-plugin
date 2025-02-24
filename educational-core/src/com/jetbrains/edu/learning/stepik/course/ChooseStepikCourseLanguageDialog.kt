package com.jetbrains.edu.learning.stepik.course

import com.intellij.openapi.ui.DialogWrapper
import com.jetbrains.edu.learning.EduLanguage
import javax.swing.JComponent

class ChooseStepikCourseLanguageDialog(languages: List<EduLanguage>, name: String) : DialogWrapper(false) {
    private val panel: ChooseStepikCourseLanguagePanel = ChooseStepikCourseLanguagePanel(languages, name)

    override fun createCenterPanel(): JComponent? = panel.constructMainPanel()

    init {
        title = "Choose Course Language"
        init()
    }

    fun selectedLanguage(): EduLanguage = panel.selectedLanguage
}