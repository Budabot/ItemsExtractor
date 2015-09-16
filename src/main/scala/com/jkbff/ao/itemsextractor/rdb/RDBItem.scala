package com.jkbff.ao.itemsextractor.rdb

class RDBItem(val id: Long,
			  val name: String,
			  val description: String,
			  val attributes: Map[Long, Long],
			  val attackDefense: List[AttackDefense],
			  val functions: List[Function],
			  val criteria: Seq[Criteria]) {

	override def toString() = toString("")

	def toString(prefix: String) = {
		"Id: " + id +
				"\nName: " + name +
				"\nDescription: " + description +
				"\nAttributes:" + attributes.map("\n  " + _.toString).mkString +
				(if (attackDefense != null) "\nAttackDefense:" + attackDefense.map("\n  " + _.toString).mkString else "") +
				(if (functions != null) "\nFunctions:\n" + functions.map(_.toString(prefix + "  ")).mkString("\n\n") else "") +
				(if (criteria != null) "\nCriteria:" + criteria.map("\n  " + _.toString).mkString else "")
	}
}