// taken from: https://stackoverflow.com/a/32203808/280574
package com.jkbff.common

import ch.qos.logback.core.rolling.TriggeringPolicyBase

import java.io.File

class RollOncePerSessionTriggeringPolicy[E] extends TriggeringPolicyBase[E] {
  private var doRolling = true

  override def isTriggeringEvent(activeFile: File, event: E): Boolean = {
    // roll the first time when the event gets called
    if (doRolling) {
      doRolling = false
      return true
    }
    false
  }
}
