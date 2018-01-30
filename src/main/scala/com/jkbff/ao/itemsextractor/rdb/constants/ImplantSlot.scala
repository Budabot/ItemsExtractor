package com.jkbff.ao.itemsextractor.rdb.constants

object ImplantSlot {
  val EYES = 1
  val HEAD = 4
  val EARS = 8
  val RIGHT_ARM = 16
  val CHEST = 32
  val LEFT_ARM = 64
  val RIGHT_WRIST = 128
  val WAIST = 256
  val LEFT_WRIST = 512
  val RIGHT_HAND = 1024
  val LEGS = 2048
  val LEFT_HAND = 4096
  val FEET = 8192

  val allSlots = Seq((EYES, "Eyes"), (HEAD, "Head"), (EARS, "Ears"), (RIGHT_ARM, "Right Arm"), (CHEST, "Chest"),
    (LEFT_ARM, "Left Arm"), (RIGHT_WRIST, "Right Wrist"), (WAIST, "Wast"), (LEFT_WRIST, "Left Wrist"),
    (RIGHT_HAND, "Right Hand"), (LEGS, "Legs"), (LEFT_HAND, "Left Hand"), (FEET, "Feet"))

  def getSlots(placement: Int): Seq[String] = {
    allSlots.flatMap { case (value, label) =>
      if ((placement & value) == value) {
        Seq(label)
      } else {
        Seq()
      }
    }
  }
}
