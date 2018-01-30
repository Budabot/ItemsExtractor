package com.jkbff.ao.itemsextractor.rdb.constants

object ArmorSlot {
  val NECK = 2
  val HEAD = 4
  val BACK = 8
  val RIGHT_SHOULDER = 16
  val CHEST = 32
  val LEFT_SHOULDER = 64
  val RIGHT_ARM = 128
  val HANDS = 256
  val LEFT_ARM = 512
  val RIGHT_WRIST = 1024
  val LEGS = 2048
  val LEFT_WRIST = 4096
  val RIGHT_FINGER = 8192
  val FEET = 16384
  val LEFT_FINGER = 32768

  val allSlots = Seq((NECK, "Neck"), (HEAD, "Head"), (BACK, "Back"), (RIGHT_SHOULDER, "Right Shoulder"),
    (CHEST, "Chest"), (LEFT_SHOULDER, "Left Shoulder"), (RIGHT_ARM, "Right Arm"), (HANDS, "Hands"),
    (LEFT_ARM, "Left Arm"), (RIGHT_WRIST, "Right Wrist"), (LEGS, "Legs"), (LEFT_WRIST, "Left Wrist"),
    (RIGHT_FINGER, "Right Finger"), (FEET, "Feet"), (LEFT_FINGER, "Left Finger"))

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
