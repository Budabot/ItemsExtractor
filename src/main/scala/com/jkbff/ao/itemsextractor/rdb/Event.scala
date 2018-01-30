package com.jkbff.ao.itemsextractor.rdb

case class Event(eventType: Long, functions: Seq[Function]) {
  def toString(prefix: String): String = {
    prefix + "  " + "EventType: " + eventType +
      "\n" + prefix + "  " + "Functions:\n" + functions.map(_.toString(prefix + "    ")).mkString("\n")
  }
}
