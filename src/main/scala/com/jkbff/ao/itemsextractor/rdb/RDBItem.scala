package com.jkbff.ao.itemsextractor.rdb

case class RDBItem(id: Long,
									 name: String,
									 description: String,
									 attributes: Map[Long, Long],
									 attackDefense: Seq[AttackDefense],
									 events: Seq[Event],
									 criteria: Seq[Criteria]) {

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