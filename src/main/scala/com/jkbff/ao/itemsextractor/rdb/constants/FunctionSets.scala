package com.jkbff.ao.itemsextractor.rdb.constants

import org.ini4j.Wini

import scala.collection.JavaConverters._

// function ids and params taken from source code for Xyphos' XRDB tool (GPLv3)
object FunctionSets {
	val ini = new Wini(getClass().getResourceAsStream("/FunctionSets.cfg"))

	val functionSets = ini.
			entrySet.
			asScala.
			toList.
			sortBy(_.getKey).
			foldLeft(Map[Long, String]()) { (m, header) =>
				header.getValue.entrySet().asScala.foldLeft(m) { (m2, value) =>
					m2 + (value.getKey.toLong -> value.getValue)
				}
			}

	def getParamsById(id: Long): Seq[(Int, String)] = {
		functionSets(id).split(',').map{ x =>
			"(\\d+)([n|h|s|x])".r.findFirstMatchIn(x) match {
				case Some(m) => (m.group(1).toInt, m.group(2))
				case None => throw new Exception("'%s' does not conform to expected format for function sets".format(x))
			}
		}
	}
}
