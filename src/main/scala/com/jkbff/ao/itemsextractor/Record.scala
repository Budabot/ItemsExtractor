package com.jkbff.ao.itemsextractor

class Record(val resourceType: Long, val resourceId: Long, val offset: Long) {
	override def toString() = "resourceType: " + resourceType + " resourceId: " + resourceId + " offset: " + offset
}