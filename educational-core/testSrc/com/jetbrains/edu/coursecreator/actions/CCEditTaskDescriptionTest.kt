package com.jetbrains.edu.coursecreator.actions

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.jetbrains.edu.coursecreator.SynchronizeTaskDescription
import com.jetbrains.edu.learning.EduNames
import com.jetbrains.edu.learning.EduTestCase
import com.jetbrains.edu.learning.courseDir
import com.jetbrains.edu.learning.courseFormat.CourseMode
import com.jetbrains.edu.learning.courseGeneration.GeneratorUtils.getInternalTemplateText
import com.jetbrains.edu.learning.testAction


class CCEditTaskDescriptionTest : EduTestCase() {

  override fun createCourse() {
    courseWithFiles(courseMode = CourseMode.EDUCATOR) {
      lesson {
        eduTask {
          taskFile("task.txt", "task file text")
        }
      }
    }
  }

  fun `test is invisible for student`() {
    getCourse().courseMode = CourseMode.STUDENT
    doOpenTaskDescription(shouldBeEnabled = false)
  }

  fun `test task description file opened`() {
    doOpenTaskDescription()

    val descriptionDocument = FileDocumentManager.getInstance().getDocument(findTaskDescriptionFile())
    assertEquals(descriptionDocument?.text, getCurrentlyOpenedText())
  }

  fun `test missing task description created`() {
    removeTaskDescriptionFile()
    doOpenTaskDescription()

    assertEquals("solve task", getCurrentlyOpenedText())
  }

  fun `test default task description created`() {
    removeTaskDescriptionFile()
    findTask(0, 0).descriptionText = ""
    doOpenTaskDescription()

    val defaultTaskDescriptionText = getInternalTemplateText(EduNames.TASK_MD)
    assertEquals(defaultTaskDescriptionText, getCurrentlyOpenedText())
  }

  fun `test synchronization`() {
    EditorFactory.getInstance().eventMulticaster.addDocumentListener(SynchronizeTaskDescription(project), testRootDisposable)

    val taskDescriptionFile = findTaskDescriptionFile()

    val newText = "new text"
    runWriteAction {
      FileDocumentManager.getInstance().getDocument(taskDescriptionFile)!!.setText(newText)
    }

    assertEquals(newText, findTask(0, 0).descriptionText)
  }

  private fun getCurrentlyOpenedText() = FileEditorManager.getInstance(project).selectedTextEditor?.document?.text ?: error(
    "No selected editor")

  private fun doOpenTaskDescription(shouldBeEnabled: Boolean = true) {
    val virtualFile = findFileInTask(0, 0, "task.txt")
    myFixture.openFileInEditor(virtualFile)
    testAction(CCEditTaskDescription.ACTION_ID, shouldBeEnabled = shouldBeEnabled)
  }

  private fun removeTaskDescriptionFile() {
    val descriptionFile = findTaskDescriptionFile()
    runWriteAction { descriptionFile.delete(CCEditTaskDescriptionTest::class) }
    assertFalse("Task Description file wasn't deleted", descriptionFile.exists())
  }

  private fun findTaskDescriptionFile() = findTask(0, 0).getDir(project.courseDir)?.findChild(EduNames.TASK_MD)
                                          ?: error("Task description file not found")
}