package com.jetbrains.edu.coursecreator.stepik.update

import com.jetbrains.edu.learning.course
import com.jetbrains.edu.learning.courseFormat.CourseMode
import com.jetbrains.edu.learning.courseFormat.EduCourse
import com.jetbrains.edu.learning.fileTree
import com.jetbrains.edu.learning.update.CourseUpdateTestBase

class CCStepikCourseUpdateTest : CourseUpdateTestBase() {
  override val defaultSettings: Unit get() = Unit

  fun `test task with placeholders added`() {
    val course = course(courseMode = CourseMode.EDUCATOR) {
      lesson {
        eduTask(stepId = 1) {
          taskFile("TaskFile1.kt")
        }
      }
    } as EduCourse

    val serverCourse = course(courseMode = CourseMode.EDUCATOR) {
      lesson {
        eduTask(stepId = 1) {
          taskFile("TaskFile1.kt")
        }
        eduTask(stepId = 2) {
          taskFile("TaskFile2.kt", """
            fun foo(): String = <p>TODO()</p>
            fun bar(): Int = <p>TODO()</p>
            fun baz(): Boolean = <p>TODO()</p>
          """) {
            placeholder(0, "\"\"")
            placeholder(1, "0")
            placeholder(2, "false")
          }
        }
      }
    } as EduCourse

    val expectedStructure = fileTree {
      dir("lesson1") {
        dir("task1") {
          file("TaskFile1.kt")
          file("task.md")
        }
        dir("task2") {
          file("TaskFile2.kt", """
            fun foo(): String = ""
            fun bar(): Int = 0
            fun baz(): Boolean = false
          """)
          file("task.md")
        }
      }
    }

    doTest(course, serverCourse, expectedStructure)
  }
}
