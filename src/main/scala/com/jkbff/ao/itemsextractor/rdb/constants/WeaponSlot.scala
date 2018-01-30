package com.jkbff.ao.itemsextractor.rdb.constants

object WeaponSlot {
  val HUD1 = 2
  val HUD3 = 4
  val UTIL1 = 8
  val UTIL2 = 16
  val UTIL3 = 32
  val RIGHT_HAND = 64
  val BELT = 128
  val LEFT_HAND = 256
  val DECK1 = 512
  val DECK2 = 1024
  val DECK3 = 2048
  val DECK4 = 4096
  val DECK5 = 8192
  val DECK6 = 16384
  val HUD2 = 32768

  val allSlots = Seq((HUD1, "Hud 1"), (HUD2, "Hud 2"), (HUD3, "Hud 3"), (UTIL1, "Util 1"), (UTIL2, "Util 2"),
    (UTIL3, "Util 3"), (RIGHT_HAND, "Right Hand"), (BELT, "Belt"), (LEFT_HAND, "Left Hand"), (DECK1, "Deck 1"),
    (DECK2, "Deck 2"), (DECK3, "Deck 3"), (DECK4, "Deck 4"), (DECK5, "Deck 5"), (DECK6, "Deck 6"))

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
