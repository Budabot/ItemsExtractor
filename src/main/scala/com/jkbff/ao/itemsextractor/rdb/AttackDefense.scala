package com.jkbff.ao.itemsextractor.rdb

case class AttackDefense(keyType: Long, skill: Long, value: Long) {
	override def toString(): String = (keyType, skill, value).toString
}
