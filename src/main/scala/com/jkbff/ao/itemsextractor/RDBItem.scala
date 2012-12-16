package com.jkbff.ao.itemsextractor

class RDBItem(val id: Long, val name: String, val description: String, val attributes: List[RDBAttribute]) {
	override def toString() =
		"Name: " + name +
		"\nDescription: " + description +
		"\nAttributes:" + attributes.foldLeft("")((output, attribute) => output + "\n  " + attribute.toString)
}