package com.jkbff.ao.itemsextractor.rdb

case class Function(functionNum: Long,
										iterationCount: Long,
										iterationDelay: Long,
										target: Long,
										requirements: Seq[FunctionRequirement],
										params: Seq[String]) {

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
