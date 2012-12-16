package com.jkbff.ao.itemsextractor

import java.sql.ResultSet

class Entry(val id: Int, val ql: Int, val name: String, val iconId: Int, val itemType: String) {
	def this(rs: ResultSet) = this(rs.getInt("aoid"), rs.getInt("ql"), rs.getString("name"), rs.getInt("icon"), rs.getString("itemtype"))
	
	override def toString() = List(id, ql, name, iconId, itemType).toString
}