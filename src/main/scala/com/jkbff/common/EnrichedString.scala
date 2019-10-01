package com.jkbff.common

object EnrichedString {
  implicit def stringToEnrichedString(f: String) = new EnrichedString(f)

  class EnrichedString(f: String) {
    def ucfirst(): String = f.toLowerCase().capitalize

    def addEndingIfNeeded(ending: String): String = {
      if (f.endsWith(ending)) {
        f
      } else {
        f + ending
      }
    }
  }

}