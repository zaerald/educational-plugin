package com.jetbrains.edu.learning.stepik.hyperskill.metrics

import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.StatusBar
import com.intellij.ui.BalloonLayout
import com.intellij.util.xmlb.XmlSerializer
import com.jetbrains.edu.learning.EduTestCase
import com.jetbrains.edu.learning.MockResponseFactory
import com.jetbrains.edu.learning.courseFormat.tasks.Task
import com.jetbrains.edu.learning.stepik.hyperskill.HYPERSKILL_PROBLEMS
import com.jetbrains.edu.learning.stepik.hyperskill.api.*
import com.jetbrains.edu.learning.stepik.hyperskill.courseFormat.HyperskillCourse
import com.jetbrains.edu.learning.stepik.hyperskill.hyperskillCourseWithFiles
import java.awt.Rectangle
import java.nio.file.Paths
import java.util.*
import javax.swing.JComponent

class HyperskillMetricsTest : EduTestCase() {
  private val metricsService: HyperskillMetricsService get() = HyperskillMetricsService.getInstance()
  private val mockConnector: MockHyperskillConnector get() = HyperskillConnector.getInstance() as MockHyperskillConnector

  override fun tearDown() {
    try {
      metricsService.allFrontendEvents(emptyQueue = true)
      metricsService.allTimeSpentEvents(reset = true)
    }
    finally {
      super.tearDown()
    }
  }

  fun `test current serialization format`() {
    val hyperskillCourse = createHyperskillCourse()

    val addedEvents = addViewEvents(hyperskillCourse, listOf(findTask(0, 0), findTask(1, 0)))

    val firstStepId = 123
    metricsService.taskStarted(firstStepId)

    val secondStepId = 124
    metricsService.taskStarted(secondStepId)
    metricsService.taskStopped()

    serializeAndDeserializeBack()

    val deserializedEvents = metricsService.allFrontendEvents(emptyQueue = false)
    compareFrontendEvents(addedEvents, deserializedEvents)

    assertEquals(listOf(firstStepId, secondStepId), metricsService.allTimeSpentEvents(reset = false).map { it.step })
  }

  /**
   * This test is needed to make sure that original xml format can be deserialized properly
   * If xml format changes, please add a new test, do not simply modify this one
   *
   * [test current serialization format] checks if current format can be deserialized
   */
  fun `test frontend events deserialization from xml`() {
    val stateFromFile = deserializeFromFile("hyperskill_events.xml")

    val expectedFrontendEvents = listOf(createHyperskillFrontendEvent("/projects/41/stages/214/implement", Date(111)),
                                        createHyperskillFrontendEvent("/learn/step/123", Date(222)))

    compareFrontendEvents(expectedFrontendEvents, stateFromFile.events)
  }

  fun `test time spent events deserialization from xml`() {
    val stateFromFile = deserializeFromFile("hyperskill_time_spent_events.xml")
    assertEquals(mapOf(123 to 40.0, 124 to 20.5), stateFromFile.timeSpentEvents)
  }

  fun `test time spent events switching between tasks`() {
    createHyperskillCourse()
    val id1 = findTask(0, 0).id
    val id2 = findTask(0, 1).id

    metricsService.taskStarted(id1)
    metricsService.taskStarted(id2)
    metricsService.taskStopped()

    metricsService.allTimeSpentEvents(reset = false).find { it.step == id1 } ?: error("No time spent event for $id1")
  }

  fun `test time spent events with frame activation`() {
    createHyperskillCourse()
    val id = findTask(0, 0).id

    // start solving a task
    val file = findFileInTask(0, 0, "src/Task.kt")
    myFixture.openFileInEditor(file)
    metricsService.taskStarted(id)

    // mock leaving the IDE -> stopping work on the task, time spent event must have appeared
    ApplicationManager.getApplication().messageBus.syncPublisher(ApplicationActivationListener.TOPIC).applicationDeactivated(MockIdeFrame())
    val timeSpentEvent = metricsService.allTimeSpentEvents(reset = false).find { it.step == id } ?: error("No time spent event for $id")
    val oldDuration = timeSpentEvent.duration

    // mock return to the IDE -> wait for some time -> stop working on the task
    ApplicationManager.getApplication().messageBus.syncPublisher(ApplicationActivationListener.TOPIC).applicationActivated(MockIdeFrame())
    metricsService.taskStopped()

    // since we worked on the task more, time spent duration must have been increased
    val newTimeSpentEvent = metricsService.allTimeSpentEvents(reset = false).find { it.step == id } ?: error("No time spent event for $id")
    assertTrue(newTimeSpentEvent.duration > oldDuration)
  }

  fun `test frontend events serialization limit respected`() {
    val hyperskillCourse = createHyperskillCourse()

    val addedFrontendEvents = addViewEvents(hyperskillCourse, List(HyperskillMetricsService.FRONTEND_EVENTS_LIMIT + 1) { findTask(0, 0) })
    assertEquals(HyperskillMetricsService.FRONTEND_EVENTS_LIMIT + 1, addedFrontendEvents.size)

    serializeAndDeserializeBack()

    val deserializedFrontendEvents = metricsService.allFrontendEvents(emptyQueue = false)
    compareFrontendEvents(addedFrontendEvents.subList(0, HyperskillMetricsService.FRONTEND_EVENTS_LIMIT), deserializedFrontendEvents)
  }

  fun `test all frontend events sent`() {
    val hyperskillCourse = createHyperskillCourse()

    addViewEvents(hyperskillCourse, listOf(findTask(0, 0), findTask(1, 0)))

    mockConnector.withResponseHandler(testRootDisposable) { request ->
      MockResponseFactory.fromString(
        when (request.path) {
          "/api/frontend-events" -> """{"${FRONTEND_EVENTS}":[]}"""
          else -> return@withResponseHandler null
        }
      )
    }

    HyperskillMetricsScheduler.sendFrontendEvents()

    val pendingFrontendEvents = metricsService.allFrontendEvents(false)
    assertEmpty(pendingFrontendEvents)
  }

  fun `test all time spent events sent`() {
    createHyperskillCourse()

    metricsService.taskStarted(1)
    metricsService.taskStopped()

    mockConnector.withResponseHandler(testRootDisposable) { request ->
      MockResponseFactory.fromString(
        when (request.path) {
          "/api/time-spent-events" -> """{"${TIME_SPENT_EVENTS}":[]}"""
          else -> return@withResponseHandler null
        }
      )
    }

    HyperskillMetricsScheduler.sendTimeSpentEvents()

    val pendingEvents = metricsService.allTimeSpentEvents(reset = false)
    assertEmpty(pendingEvents)
  }

  fun `test no frontend events sent`() {
    val hyperskillCourse = createHyperskillCourse()

    val viewEvents = addViewEvents(hyperskillCourse, listOf(findTask(0, 0), findTask(1, 0)))
    HyperskillMetricsScheduler.sendFrontendEvents()

    val pendingFrontendEvents = metricsService.allFrontendEvents(false)
    compareFrontendEvents(viewEvents, pendingFrontendEvents)
  }

  fun `test no time spent events sent`() {
    createHyperskillCourse()
    metricsService.taskStarted(1)
    metricsService.taskStopped()

    HyperskillMetricsScheduler.sendFrontendEvents()

    val pendingEvents = metricsService.allTimeSpentEvents(reset = false)
    assertNotNull(pendingEvents.find { it.step == 1 })
  }

  fun `test frontend events sent in chunks`() {
    val hyperskillCourse = createHyperskillCourse()

    val eventsCount = 5000

    val expectedChunks = eventsCount / HyperskillMetricsScheduler.EVENTS_PER_REQUEST +
                         (if (eventsCount % HyperskillMetricsScheduler.EVENTS_PER_REQUEST == 0) 0 else 1)

    var chunksCount = 0
    addViewEvents(hyperskillCourse, List(eventsCount) { findTask(0, 0) })

    mockConnector.withResponseHandler(testRootDisposable) { request ->
      MockResponseFactory.fromString(
        when (request.path) {
          "/api/frontend-events" -> {
            chunksCount++
            """{"${FRONTEND_EVENTS}":[]}"""
          }
          else -> return@withResponseHandler null
        }
      )
    }

    HyperskillMetricsScheduler.sendFrontendEvents()
    assertEquals(expectedChunks, chunksCount)

    val pendingFrontendEvents = metricsService.allFrontendEvents(false)
    assertEmpty(pendingFrontendEvents)
  }

  fun `test pending frontend events limit respected`() {
    val hyperskillCourse = createHyperskillCourse()

    val addedFrontendEvents = addViewEvents(hyperskillCourse, List(HyperskillMetricsService.FRONTEND_EVENTS_LIMIT + 1) { findTask(0, 0) })
    HyperskillMetricsScheduler.sendFrontendEvents()

    val pendingFrontendEvents = metricsService.allFrontendEvents(false)
    compareFrontendEvents(addedFrontendEvents.subList(0, HyperskillMetricsService.FRONTEND_EVENTS_LIMIT), pendingFrontendEvents)
  }

  fun `test no events sent for corrupted task with id = 0`() {
    val course = hyperskillCourseWithFiles {
      frameworkLesson("lesson1") {
        eduTask("task0", stepId = 0) {
          taskFile("src/Task.kt", "stage corrupted")
          taskFile("test/Tests1.kt", "stage corrupted test")
        }
      }
    }
    val task = course.findTask("lesson1", "task0")
    metricsService.viewEvent(task)
    metricsService.taskStopped()

    HyperskillMetricsScheduler.sendFrontendEvents()

    val pendingEvents = metricsService.allTimeSpentEvents(reset = false)
    assertEmpty(pendingEvents)
  }

  private fun createHyperskillCourse() = hyperskillCourseWithFiles {
    frameworkLesson("lesson1") {
      eduTask("task1", stepId = 1) {
        taskFile("src/Task.kt", "stage 1")
        taskFile("test/Tests1.kt", "stage 1 test")
      }
      eduTask("task2", stepId = 2) {
        taskFile("src/Task.kt", "stage 2")
        taskFile("test/Tests2.kt", "stage 2 test")
      }
    }

    lesson(HYPERSKILL_PROBLEMS) {
      codeTask(stepId = 4) {
        taskFile("task.txt", "file text")
      }
    }
  }

  private fun compareFrontendEvents(expectedEvents: List<HyperskillFrontendEvent>, actualEvents: List<HyperskillFrontendEvent>) {
    assertEquals(expectedEvents.size, actualEvents.size)
    actualEvents.forEachIndexed { index, actualEvent ->
      val expectedEvent = expectedEvents[index]

      assertEquals(expectedEvent.route, actualEvent.route)
      assertEquals(expectedEvent.action, actualEvent.action)
      assertEquals(expectedEvent.clientTime, actualEvent.clientTime)
    }
  }

  private fun serializeAndDeserializeBack() {
    val serialized = XmlSerializer.serialize(metricsService.state)
    metricsService.allFrontendEvents(emptyQueue = true)
    metricsService.loadState(XmlSerializer.deserialize(serialized, HyperskillMetricsService.State::class.java))
  }

  private fun addViewEvents(hyperskillCourse: HyperskillCourse, tasks: List<Task>): List<HyperskillFrontendEvent> {
    tasks.forEach { metricsService.doAddViewEvent(hyperskillCourse, it) }
    return metricsService.allFrontendEvents(emptyQueue = false)
  }

  private fun createHyperskillFrontendEvent(eventRoute: String,
                                            eventTime: Date,
                                            eventAction: HyperskillFrontendEventType = HyperskillFrontendEventType.VIEW): HyperskillFrontendEvent {
    return HyperskillFrontendEvent().apply {
      route = eventRoute
      action = eventAction
      clientTime = eventTime
    }
  }

  private fun deserializeFromFile(name: String): HyperskillMetricsService.State {
    val filePath = Paths.get(testDataPath).resolve(name)
    return XmlSerializer.deserialize(JDOMUtil.load(filePath), HyperskillMetricsService.State::class.java)
  }

  private inner class MockIdeFrame : IdeFrame {
    private fun notImplemented(): Nothing = error("Not Implemented")

    override fun getStatusBar(): StatusBar? = null

    override fun suggestChildFrameBounds(): Rectangle = notImplemented()

    override fun getProject(): Project? = myFixture.project

    override fun setFrameTitle(title: String?): Unit = notImplemented()

    override fun getComponent(): JComponent = notImplemented()

    override fun getBalloonLayout(): BalloonLayout = notImplemented()
  }

  override fun getTestDataPath(): String = "testData/stepik/hyperskill/metrics"
}