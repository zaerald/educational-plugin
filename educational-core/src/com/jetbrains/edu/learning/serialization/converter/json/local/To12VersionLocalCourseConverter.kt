package com.jetbrains.edu.learning.serialization.converter.json.local

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.jetbrains.edu.coursecreator.actions.mixins.JsonMixinNames.FEEDBACK_LINK
import com.jetbrains.edu.learning.stepik.api.LINK
import com.jetbrains.edu.learning.stepik.api.LINK_TYPE

class To12VersionLocalCourseConverter : JsonLocalCourseConverterBase() {

  override fun convertTaskObject(taskObject: ObjectNode, language: String) {
    convertTaskObject(taskObject)
  }

  companion object {
    @JvmStatic
    fun convertTaskObject(taskObject: ObjectNode) {
      val mapper = ObjectMapper()
      val feedbackLink = taskObject.remove(FEEDBACK_LINK) as? ObjectNode ?:  mapper.createObjectNode()
      val feedbackLinkType = feedbackLink.get(LINK_TYPE).asText() ?: ""
      if (FeedbackLink.LinkType.CUSTOM.name == feedbackLinkType) {
        val customLink = feedbackLink.get(LINK).asText() ?: return
        taskObject.put(FEEDBACK_LINK, customLink)
      }
    }
  }
}