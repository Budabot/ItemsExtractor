package com.jkbff.ao.itemsextractor.rdb

class RDBItem(val id: Long,
			  val name: String,
			  val description: String,
			  val attributes: Map[Long, Long],
			  val attackDefense: Seq[AttackDefense],
			  val events: Seq[Event],
			  val criteria: Seq[Criteria]) {

	override def toString(): String = toString("")

	def toString(prefix: String): String = {
		"Id: " + id +
				"\nName: " + name +
				"\nDescription: " + description +
				"\nAttributes:" + attributes.map("\n  " + _.toString).mkString +
				(if (attackDefense != null) "\nAttackDefense:" + attackDefense.map("\n  " + _.toString).mkString else "") +
				events.map{ event => "\nEvent:\n" + event.toString(prefix) }.mkString +
				(if (criteria != null) "\nCriteria:" + criteria.map("\n  " + _.toString).mkString else "")
	}
}