package com.jkbff.ao.itemsextractor.rdb

case class Criteria(statNum: Long, value: Long, opNum: Long) {
	override def toString(): String = (statNum, value, opNum).toString()
}
