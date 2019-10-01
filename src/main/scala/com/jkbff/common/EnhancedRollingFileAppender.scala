package com.jkbff.common

import org.apache.log4j.RollingFileAppender

class EnhancedRollingFileAppender extends RollingFileAppender {
  protected var rollOnStartup = false

  override def activateOptions(): Unit = {
    if (rollOnStartup) {
      super.rollOver()
    }
    super.activateOptions()
  }

  def getRollOnStartup(): Boolean = {
    rollOnStartup
  }

  def setRollOnStartup(rollOnStartup: Boolean): Unit = {
    this.rollOnStartup = rollOnStartup
  }
}