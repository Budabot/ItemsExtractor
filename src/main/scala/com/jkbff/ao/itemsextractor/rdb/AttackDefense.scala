package com.jkbff.ao.itemsextractor.rdb

class AttackDefense(keyType: Long, skill: Long, value: Long) {
	override def toString() = (keyType, skill, value).toString
}
