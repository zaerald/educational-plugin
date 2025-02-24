package com.jetbrains.edu.learning.courseFormat

import com.jetbrains.edu.learning.EduUtils

abstract class ItemContainer : StudyItem() {
  var items: List<StudyItem>
    get() = _items
    set(value) {
      _items = value.toMutableList()
    }

  private var _items = mutableListOf<StudyItem>()

  fun getItem(name: String): StudyItem? {
    return items.firstOrNull { it.name == name }
  }

  fun addItem(item: StudyItem) {
    _items.add(item)
  }

  fun addItem(index: Int, item: StudyItem) {
    _items.add(index, item)
  }

  fun removeItem(item: StudyItem) {
    _items.remove(item)
  }

  open fun sortItems() {
    _items.sortWith(EduUtils.INDEX_COMPARATOR)
  }
}
