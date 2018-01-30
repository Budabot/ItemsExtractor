package com.jkbff.ao.itemsextractor.rdb.constants

object ItemType {
	val MISC = 0
	val WEAPON = 1
	val ARMOR = 2
	val IMPLANT = 3
	val TEMPLATE = 4
	val SPIRIT = 5

	def getType(itemTypeId: Long): Option[String] = {
		itemTypeId match {
			case MISC => Some("Misc")
			case WEAPON => Some("Weapon")
			case ARMOR => Some("Armor")
			case IMPLANT => Some("Implant")
			case TEMPLATE => Some("Template")
			case SPIRIT => Some("Spirit")
			case _ => None
		}
	}
}