package com.jkbff.ao.itemsextractor
import java.sql.ResultSet

class Item(val lowId: Int, val highId: Int, val lowQl: Int, val highQl: Int, val name: String, val icon: Int) {
	def this(rs: ResultSet) = this(rs.getInt("lowid"), rs.getInt("highid"), rs.getInt("lowql"), rs.getInt("highql"), rs.getString("name"), rs.getInt("icon"))

	override def toString() = Seq(lowId, highId, lowQl, highQl, name, icon).toString()
}