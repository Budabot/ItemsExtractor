package com.jkbff.ao.itemsextractor.rdb

import java.io.RandomAccessFile

import com.jkbff.ao.itemsextractor.rdb.RDBFunctions._
import com.jkbff.ao.itemsextractor.rdb.constants.FunctionSets
import com.jkbff.ao.itemsextractor.rdb.index.IndexRecord

import scala.util.control.Breaks._

class RDBExtractor {
	def readItem(in: MultiRandomAccessFile, record: IndexRecord, extractFunctions: Boolean): RDBItem = {
		val file = in.seek(record.offset)
		file.skipBytes(2)
		
		val length = readLittleEndianInt(file)
		
		// taken from: http://aodevs.com/forums/index.php/topic,785.msg3039.html#msg3039
		file.skipBytes(44)
		
		val numAttributes = int3F1(readLittleEndianInt(file))
		val attributes = (1L to numAttributes).foldLeft(Map[Long, Long]()) ( (map, x) => map + parseAttribute(file))
		file.skipBytes(8)
		
		val nameLength = readLittleEndianShort(file)
		val descriptionLength = readLittleEndianShort(file)
		val name = readString(file, nameLength)
		val description = readString(file, descriptionLength)

		var functionList: List[Function] = null
		var attackDefenseList: List[AttackDefense] = null
		var criteriaList: Seq[Criteria] = null

		if (extractFunctions) {
			breakable {
				while (file.getFilePointer() < record.offset + length) {
					val setType = readLittleEndianInt(file)
					println("setType: " + setType)
					setType match {
						case 2 =>
							functionList = functions(file)

						case 4 =>
							attackDefenseList = attackDefense(file)

						case 6 =>
							unknown(file)

						case 14 =>
							// animation

						case 22 =>
							criteriaList = parseCriteria(file)

						case 23 =>
							// shop

						case _ =>
							break
					}
				}
			}
		}
		println
		new RDBItem(record.resourceId, name, description, attributes, attackDefenseList, functionList, criteriaList)
	}

	def parseCriteria(file: RandomAccessFile): Seq[Criteria] = {
		file.skipBytes(12)
		val criteriaCount = int3F1(readLittleEndianInt(file))

		(1L to criteriaCount).map{ x =>
			new Criteria(readLittleEndianInt(file), readLittleEndianInt(file), readLittleEndianInt(file))
		}
	}
	
	def parseAttribute(file: RandomAccessFile): (Long, Long) = {
		val id = readLittleEndianInt(file)
		val value = readLittleEndianInt(file)
		(id, value)
	}
	
	def functions(file: RandomAccessFile): List[Function] = {
		val eventNum = readLittleEndianInt(file)
		println("eventNum", eventNum)
		val funcCount = int3F1(readLittleEndianInt(file))
		println("funcCount", funcCount)

		(1L to funcCount).foldLeft(List[Function]()) { (list, num) =>
			val functionNum = readLittleEndianInt(file)
			println("functionNum", functionNum)
			file.skipBytes(8) // 0, maybe a long instead of two ints?
			//println(readLittleEndianInt(file))
			//println(readLittleEndianInt(file))
			val requirementsCount = readLittleEndianInt(file)
			println("requirementsCount", requirementsCount)
			val requirements = (1L to requirementsCount).foldLeft(List[FunctionRequirement]()) { (list, count) =>
				val requiredAttributeNumber = readLittleEndianInt(file)
				val requiredAttributeValue = readLittleEndianInt(file)
				val requiredAttributeOperator = readLittleEndianInt(file)
				println("requiredAttributeNumber", requiredAttributeNumber)
				println("requiredAttributeValue", requiredAttributeValue)
				println("requiredAttributeOperator", requiredAttributeOperator)

				new FunctionRequirement(requiredAttributeNumber, requiredAttributeValue, requiredAttributeOperator) :: list
			}.reverse

			val FunctionItterationCount = readLittleEndianInt(file)
			val FunctionItterationDelay = readLittleEndianInt(file)
			val FunctionTarget = readLittleEndianInt(file)
			println("FunctionItterationCount", FunctionItterationCount)
			println("FunctionItterationDelay", FunctionItterationDelay)
			println("FunctionTarget", FunctionTarget)
			file.skipBytes(4)

			val functionParams = FunctionSets.getParamsById(functionNum).foldLeft(List[Any]()) { case (list, (num, paramType)) =>
				paramType match {
					case "n" =>
						(1 to num).foldLeft(list) { (l, count) =>
							readLittleEndianInt(file) :: l
						}
					case "h" =>
						readString(file, 4) :: list
					case "s" =>
						readString(file, readLittleEndianInt(file).toInt).trim :: list
					case "x" =>
						file.skipBytes(num)
						list
				}
			}.reverse
			println(functionParams)
			new Function(functionNum, FunctionItterationCount, FunctionItterationDelay, FunctionTarget, requirements, functionParams) :: list
		}
	}
	
	def attackDefense(file: RandomAccessFile): List[AttackDefense] = {
		file.skipBytes(4)
		val maxSets = int3F1(readLittleEndianInt(file))
		(1L to maxSets).foldLeft(List[AttackDefense]()) { (list, count) =>
			val keyType = readLittleEndianInt(file)
			val setCount = int3F1(readLittleEndianInt(file))
			
			list ::: (1L to setCount).foldLeft(list) { (list2, count2) =>
				val statNumber = readLittleEndianInt(file)
				val statValue = readLittleEndianInt(file)
				new AttackDefense(keyType, statNumber, statValue) :: list2
			}
		}
	}
	
	def unknown(file: RandomAccessFile): Unit = {
		file.skipBytes(4)
		//println(readLittleEndianInt(file))
		val pairs = int3F1(readLittleEndianInt(file))
		//println(pairs)
		file.skipBytes(pairs.toInt * 8)
		//(1L to pairs).foreach{x =>
			//println(readLittleEndianInt(file))
			//println(readLittleEndianInt(file))
		//}
	}
}