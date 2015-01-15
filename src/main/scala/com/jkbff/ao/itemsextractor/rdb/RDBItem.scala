package com.jkbff.ao.itemsextractor.rdb

class RDBItem(val id: Long, val name: String, val description: String, val attributes: Map[Long, Long]) {
	override def toString() =
		"Name: " + name +
		"\nDescription: " + description +
		"\nAttributes:" + attributes.foldLeft("")((output, attribute) => output + "\n  " + attribute.toString)
}