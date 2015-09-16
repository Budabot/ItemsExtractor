package com.jkbff.ao.itemsextractor.rdb

class Function(val functionNum: Long,
			   val iterationCount: Long,
			   val iterationDelay: Long,
			   val target: Long,
			   val requirements: List[FunctionRequirement],
			   val params: List[Any]) {

	override def toString() = toString("")

	def toString(prefix: String) = {
		prefix + "Number: " + functionNum +
				"\n" + prefix + "Iteration Count: " + iterationCount +
				"\n" + prefix + "Iteration Delay: " + iterationDelay +
				"\n" + prefix + "Target: " + target +
				"\n" + prefix + "Requirements:" + requirements.map("\n  " + _.toString).mkString +
				"\n" + prefix + "Parameters: " + params
	}
}
