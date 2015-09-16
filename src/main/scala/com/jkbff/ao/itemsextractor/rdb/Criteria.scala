package com.jkbff.ao.itemsextractor.rdb

class Criteria(val statNum: Long, val value: Long, val opNum: Long) {
	override def toString() = (statNum, value, opNum).toString()
}
