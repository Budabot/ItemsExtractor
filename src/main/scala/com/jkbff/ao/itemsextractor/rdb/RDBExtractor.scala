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
		val endOffset = file.getFilePointer + length - 6L
		
		// taken from: http://aodevs.com/forums/index.php/topic,785.msg3039.html#msg3039
		file.skipBytes(44)
		
		val numAttributes = int3F1(readLittleEndianInt(file))
		val attributes = (for (_ <- 1L to numAttributes) yield parseAttribute(file)).toMap
		file.skipBytes(8)
		
		val nameLength = readLittleEndianShort(file)
		val descriptionLength = readLittleEndianShort(file)
		val name = readString(file, nameLength)
		val description = readString(file, descriptionLength)

		var events = Seq[Event]()
		var attackDefenseList: Seq[AttackDefense] = Seq()
		var criteriaList: Seq[Criteria] = Seq()

		var oldSetType = -1L
		val printValues = false

		if (extractFunctions) {
			breakable {
				while (endOffset - file.getFilePointer() > 12L) {
					val setType = readLittleEndianInt(file)
					if (printValues) println("setType " + setType)
					try {
						setType match {
							case 2 =>
								events = events.+:(processEvent(file, printValues))

							case 4 =>
								attackDefenseList = attackDefense(file)

							case 5 =>
								unknown5(file)

							case 6 =>
								unknown6(file)

							case 14 =>
							// animation

							case 22 =>
								criteriaList = parseCriteria(file)

							case 23 =>
								shop(file)

							case 20 =>
								unknown20(file)

							case 19 =>
								unknown19(file)

							case 37 =>
								unknown37(file)

							case 136 =>
								playshiftRequirements(file)

							case 1035 =>
								unknown1035(file)

							case _ =>
								if (printValues) {
									println("record " + record)
									println("Breaking for setType " + setType)
									println("Total bytes: " + length)
									println("Remaining bytes: " + (endOffset - file.getFilePointer))
									println("Last setType: " + oldSetType)
								}
								consumeRemainingBytes(file, endOffset, printValues)
								if (printValues) println
								//throw new Exception("halting")
						}
						oldSetType = setType
					} catch {
						case e: Exception =>
							println("record " + record)
							println("Breaking for setType " + setType)
							println("Total bytes: " + length)
							println("Remaining bytes: " + (endOffset - file.getFilePointer))
							println("Last setType: " + oldSetType)
							consumeRemainingBytes(file, endOffset, printValues)
							e.printStackTrace()
					}
				}
			}
		}
		RDBItem(record.resourceId, name, description, attributes, attackDefenseList, events.reverse, criteriaList)
	}

	def consumeRemainingBytes(file: RandomAccessFile, endOffset: Long, printValues: Boolean): Unit = {
		while (endOffset - file.getFilePointer() >= 4L) {
			val byte = readLittleEndianInt(file)
			if (printValues) print(byte + " ")
		}
		if (printValues) println
	}

	def parseCriteria(file: RandomAccessFile): Seq[Criteria] = {
		val header = readLittleEndianInt(file)
		if (header != 36) {
			//throw new Exception("Expecting '36' for parseCriteria but got: " + header)
		}
		file.skipBytes(8)
		val criteriaCount = int3F1(readLittleEndianInt(file))

		(1L to criteriaCount).map(_ => Criteria(readLittleEndianInt(file), readLittleEndianInt(file), readLittleEndianInt(file)))
	}
	
	def parseAttribute(file: RandomAccessFile): (Long, Long) = {
		val id = readLittleEndianInt(file)
		val value = readLittleEndianInt(file)
		(id, value)
	}
	
	def processEvent(file: RandomAccessFile, printValues: Boolean): Event = {
		val eventNum = readLittleEndianInt(file)
		if (printValues) println("eventNum", eventNum)
		val funcCount = int3F1(readLittleEndianInt(file))
		if (printValues) println("funcCount", funcCount)

		val functions = (1L to funcCount).map{ _ =>
			val functionNum = readLittleEndianInt(file)
			if (printValues) println("functionNum", functionNum)
			file.skipBytes(8) // 0
			//println(readLittleEndianInt(file))
			//println(readLittleEndianInt(file))
			val requirementsCount = readLittleEndianInt(file)
			//println("requirementsCount", requirementsCount)
			val requirements = (1L to requirementsCount).map { _ =>
				val requiredAttributeNumber = readLittleEndianInt(file)
				val requiredAttributeValue = readLittleEndianInt(file)
				val requiredAttributeOperator = readLittleEndianInt(file)
				//println("requiredAttributeNumber", requiredAttributeNumber)
				//println("requiredAttributeValue", requiredAttributeValue)
				//println("requiredAttributeOperator", requiredAttributeOperator)

				FunctionRequirement(requiredAttributeNumber, requiredAttributeValue, requiredAttributeOperator)
			}

			val hits = readLittleEndianInt(file)
			val delay = readLittleEndianInt(file)
			val target = readLittleEndianInt(file)
			//println("FunctionItterationCount", FunctionItterationCount)
			//println("FunctionItterationDelay", FunctionItterationDelay)
			//println("FunctionTarget", FunctionTarget)
			file.skipBytes(4)

			val functionParams = FunctionSets.getParamsById(functionNum).flatMap { case (num, paramType) =>
				if (printValues) println(num, paramType)
				paramType match {
					case "n" =>
						(1 to num).map { _ =>
							readLittleEndianSignedInt(file).toString
						}
					case "h" =>
						(1 to num).map { _ =>
							readString(file, 4)
						}
					case "s" =>
						(1 to num).map { _ =>
							val strSize = readLittleEndianInt(file).toInt
							val str = readString(file, strSize)
							str.trim
						}
					case "x" =>
						file.skipBytes(num)
						Seq()
				}
			}
			Function(functionNum, hits, delay, target, requirements, functionParams)
		}

		Event(eventNum, functions)
	}

	def attackDefense(file: RandomAccessFile): Seq[AttackDefense] = {
		val header = readLittleEndianInt(file)
		if (header != 4) {
			//throw new Exception("Expecting '4' for attackDefense but got: " + header)
		}
		val maxSets = int3F1(readLittleEndianInt(file))
		(1L to maxSets).flatMap { _ =>
			val keyType = readLittleEndianInt(file)
			val setCount = int3F1(readLittleEndianInt(file))
			
			(1L to setCount).map { _ =>
				val statNumber = readLittleEndianInt(file)
				val statValue = readLittleEndianInt(file)
				AttackDefense(keyType, statNumber, statValue)
			}
		}
	}

	def shop(file: RandomAccessFile): Unit = {
		val header = readLittleEndianInt(file)
		if (header != 37) {
			throw new Exception("Expecting '37' for shop but got: " + header)
		}
		val count = int3F1(readLittleEndianInt(file))
		file.skipBytes(count.toInt * 13)
	}
	
	def unknown5(file: RandomAccessFile): Unit = {
		// always seem to be 2018 0 0 99
		file.skipBytes(16)
	}

	def unknown6(file: RandomAccessFile): Unit = {
		val header = readLittleEndianInt(file)
		if (header != 27) {
			//throw new Exception("Expecting '27' for unknown6 but got: " + header)
		}
		//println(readLittleEndianInt(file))
		val pairs = int3F1(readLittleEndianInt(file))
		//println("unknown num pairs: " + pairs)
		file.skipBytes(pairs.toInt * 8)
		/*(1L to pairs).foreach{ _ =>
			println("unknown1: " + readLittleEndianInt(file))
			println("unknown2: " + readLittleEndianInt(file))
		}*/
	}

	def unknown19(file: RandomAccessFile): Unit = {
		file.skipBytes(16)
	}

	def unknown20(file: RandomAccessFile): Unit = {
		file.skipBytes(4)
		val count = int3F1(readLittleEndianInt(file))

		(1L to count).foreach{ _ =>
			file.skipBytes(4)
			val count2 = int3F1(readLittleEndianInt(file))
			(1L to count2).foreach { _ =>
				file.skipBytes(4)
			}
		}
	}

	def unknown37(file: RandomAccessFile): Unit = {
		val count = int3F1(readLittleEndianInt(file))
		file.skipBytes(4)
		file.skipBytes(count.toInt * 4 * 4)
	}

	def unknown1035(file: RandomAccessFile): Unit = {
		file.skipBytes(8)
	}

	def playshiftRequirements(file: RandomAccessFile): Unit = {
		val count = int3F1(readLittleEndianInt(file))
		file.skipBytes(count.toInt * 4 * 3)
	}
}