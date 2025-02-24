package com.jetbrains.edu.learning.stepik.hyperskill

import com.jetbrains.edu.learning.*
import com.jetbrains.edu.learning.courseFormat.tasks.CodeTask
import com.jetbrains.edu.learning.courseFormat.tasks.Task
import com.jetbrains.edu.learning.courseFormat.tasks.TheoryTask
import com.jetbrains.edu.learning.courseFormat.tasks.choice.ChoiceTask
import com.jetbrains.edu.learning.courseGeneration.ProjectOpener
import com.jetbrains.edu.learning.messages.EduCoreBundle
import com.jetbrains.edu.learning.stepik.StepikTaskBuilder
import com.jetbrains.edu.learning.stepik.hyperskill.api.HyperskillConnector
import com.jetbrains.edu.learning.stepik.hyperskill.api.HyperskillStepSource
import com.jetbrains.edu.learning.stepik.hyperskill.api.MockHyperskillConnector
import com.jetbrains.edu.learning.stepik.hyperskill.projectOpen.HyperskillProjectOpenerTestBase.Companion.StepInfo
import com.jetbrains.edu.learning.stepik.hyperskill.projectOpen.HyperskillProjectOpenerTestBase.Companion.TopicInfo

class HyperskillNextActivityTest : EduTestCase() {
  private val mockConnector: MockHyperskillConnector get() = HyperskillConnector.getInstance() as MockHyperskillConnector

  override fun setUp() {
    super.setUp()
    logInFakeHyperskillUser()
  }

  override fun tearDown() {
    logOutFakeHyperskillUser()
    super.tearDown()
  }

  fun `test activity not available`() {
    createHyperskillProblemsProject()
    withNotificationCheck(project, testRootDisposable, { shown, content ->
      assertEquals(true, shown)
      assertEquals(EduCoreBundle.message("notification.hyperskill.no.next.activity.content", stepLink(step2.id)), content)
    }) {
      openNextActivity(project, findTask(0, 0, 1))
    }
  }

  fun `test activity not available without login`() {
    logOutFakeHyperskillUser()
    createHyperskillProblemsProject()
    withNotificationCheck(project, testRootDisposable, { shown, content ->
      assertEquals(true, shown)
      assertEquals(EduCoreBundle.message("notification.hyperskill.no.next.activity.login.content"), content)
    }) {
      openNextActivity(project, findTask(0, 0, 1))
    }
  }

  fun `test topic is completed`() {
    createHyperskillProblemsProject()
    val task = findTask(0, 0, 1)

    mockConnector.configureResponses(
      StepMockResponse(step2.path, task) { topic = topic1.id },
      StepMockResponse(topic1.path, TheoryTask()) {
        topic = topic1.id
        isCompleted = true
        id = step5.id
        block!!.name = TheoryTask.THEORY_TASK_TYPE
      }
    )

    openNextActivity(project, task)
    assertEquals(topicCompletedLink(topic1.id), (EduBrowser.getInstance() as MockEduBrowser).lastVisitedUrl)
  }

  fun `test next activity unsupported in IDE`() {
    createHyperskillProblemsProject()
    val task = findTask(0, 0, 1)

    mockConnector.configureResponses(
      StepMockResponse(step2.path, task) { topic = topic1.id },
      StepMockResponse(topic1.path, ChoiceTask()) {
        topic = topic1.id
        id = step5.id
        block!!.name = StepikTaskBuilder.StepikTaskType.TABLE.type
      }
    )

    openNextActivity(project, task)
    assertEquals(stepLink(5), (EduBrowser.getInstance() as MockEduBrowser).lastVisitedUrl)
  }

  fun `test open next step in IDE`() {
    createHyperskillProblemsProject()
    val task = findTask(0, 0, 1)
    val nextStepId = step5.id

    mockConnector.configureResponses(
      StepMockResponse(step2.path, task) {
        topic = topic1.id
        id = step2.id
        topicTheory = step11.id
        title = step2.title
      },
      StepMockResponse(step5.path, task) {
        block!!.name = CodeTask.CODE_TASK_TYPE
        topic = topic1.id
        id = nextStepId
        topicTheory = step11.id
        title = step5.title
        isRecommended = true
      },
      StepMockResponse(step11.path, TheoryTask()) {
        block!!.name = TheoryTask.THEORY_TASK_TYPE
        topic = topic1.id
        id = step11.id
        topicTheory = step11.id
        title = step11.title
        isRecommended = true
      }
    )
    mockConnector.withResponseHandler(testRootDisposable) { request ->
      if (request.path.endsWith(topic1.path)) {
        mockResponse(topic1.file)
      }
      else null
    }

    (ProjectOpener.getInstance() as MockProjectOpener).project = project
    openNextActivity(project, task)
    assertEquals(nextStepId, findTask(0, 0, 2).id)
  }

  private fun createHyperskillProblemsProject() {
    hyperskillCourseWithFiles {
      section(HYPERSKILL_TOPICS) {
        lesson(step11.title) {
          theoryTask("Theory", stepId = step11.id) {
            taskFile("Task.txt")
          }
          codeTask(step2.title, stepId = step2.id) {
            taskFile("Task.txt")
          }
        }
      }
    }
  }

  private data class StepMockResponse(val path: String, val task: Task, val initStepSource: HyperskillStepSource.() -> Unit)

  private fun MockHyperskillConnector.configureResponses(vararg responses: StepMockResponse) {
    withResponseHandler(testRootDisposable) { request ->
      responses.find { request.path == it.path }?.let {
        mockResponseFromTask(it.task, it.initStepSource)
      }
    }
  }

  override fun getTestDataPath(): String = super.getTestDataPath() + "/stepik/hyperskill/"

  companion object {
    private val step2 = StepInfo(2, "Test Code Task")
    private val step5 = StepInfo(5, "Test Code Task 2")
    private val step11 = StepInfo(11, "Test Topic Name")
    private val topic1 = TopicInfo(1)
  }
}