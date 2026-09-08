package com.jkbff.ao.itemsextractor.rdb

import java.io.{DataInput, RandomAccessFile}

import com.jkbff.ao.itemsextractor.rdb.RDBFunctions._
import com.jkbff.ao.itemsextractor.rdb.constants.FunctionSets
import com.jkbff.ao.itemsextractor.rdb.index.IndexRecord

import org.slf4j.LoggerFactory
import scala.util.control.Breaks._

class RDBDataInput(bytes: Array[Byte]) extends DataInput {
	private var pos: Int = 0

	def getFilePointer(): Long = pos.toLong
	def seek(newPos: Long): Unit = { pos = Math.min(newPos.toInt, bytes.length) }
	def length(): Long = bytes.length.toLong

	override def readFully(b: Array[Byte]): Unit = readFully(b, 0, b.length)
	override def readFully(b: Array[Byte], off: Int, len: Int): Unit = {
		if (len > 0) {
			if (pos + len > bytes.length) {
				throw new java.io.EOFException(s"End of item data reached: pos $pos + len $len > ${bytes.length}")
			}
			System.arraycopy(bytes, pos, b, off, len)
			pos += len
		}
	}

	override def skipBytes(n: Int): Int = {
		val skipped = Math.min(n, bytes.length - pos)
		pos += skipped
		skipped
	}

	override def readBoolean(): Boolean = { val v = bytes(pos) != 0; pos += 1; v }
	override def readByte(): Byte = { val v = bytes(pos); pos += 1; v }
	override def readUnsignedByte(): Int = { val v = bytes(pos) & 0xFF; pos += 1; v }
	override def readShort(): Short = {
		val v = ((bytes(pos) & 0xFF) << 8) | (bytes(pos + 1) & 0xFF)
		pos += 2
		v.toShort
	}
	override def readUnsignedShort(): Int = {
		val v = ((bytes(pos) & 0xFF) << 8) | (bytes(pos + 1) & 0xFF)
		pos += 2
		v
	}
	override def readChar(): Char = readShort().toChar
	override def readInt(): Int = {
		val v = ((bytes(pos) & 0xFF) << 24) |
				((bytes(pos + 1) & 0xFF) << 16) |
				((bytes(pos + 2) & 0xFF) << 8) |
				(bytes(pos + 3) & 0xFF)
		pos += 4
		v
	}
	override def readLong(): Long = {
		(readInt().toLong << 32) | (readInt().toLong & 0xFFFFFFFFL)
	}
	override def readFloat(): Float = java.lang.Float.intBitsToFloat(readInt())
	override def readDouble(): Double = java.lang.Double.longBitsToDouble(readLong())
	override def readLine(): String = throw new UnsupportedOperationException
	override def readUTF(): String = throw new UnsupportedOperationException
}

class RDBExtractor {
	private val log = LoggerFactory.getLogger(this.getClass)

	def readItem(in: MultiRandomAccessFile, record: IndexRecord, extractFunctions: Boolean): RDBItem = {
		val raf = in.seek(record.offset)
		raf.skipBytes(2)
		
		val length = readLittleEndianInt(raf)
		val remaining = (length - 6L).toInt
		val itemBytes = new Array[Byte](remaining)
		raf.readFully(itemBytes)

		val file = new RDBDataInput(itemBytes)
		val endOffset = remaining.toLong
		
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

		if (extractFunctions) {
			breakable {
				while (endOffset - file.getFilePointer() > 12L) {
					val setType = readLittleEndianInt(file)
					//log.debug("setType {}", setType)
					try {
						setType match {
							case 2 =>
								events = events.+:(processEvent(file))

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
								log.debug(s"record: $record, Breaking for setType $setType, Total bytes: $length, Remaining bytes: ${endOffset - file.getFilePointer}, Last setType: $oldSetType")
								consumeRemainingBytes(file, endOffset)
						}
						oldSetType = setType
					} catch {
						case e: Exception =>
							log.debug(s"Failed parsing setType $setType for record $record (last setType: $oldSetType, remaining bytes: ${endOffset - file.getFilePointer})", e)
							consumeRemainingBytes(file, endOffset)
					}
				}
			}
		}
		RDBItem(record.resourceId, name, description, attributes, attackDefenseList, events.reverse, criteriaList)
	}

	def consumeRemainingBytes(file: RDBDataInput, endOffset: Long): Unit = {
		if (log.isDebugEnabled) {
			val remaining = (endOffset - file.getFilePointer).toInt
			if (remaining > 0) {
				val b = new Array[Byte](math.min(remaining, (file.length() - file.getFilePointer).toInt))
				file.readFully(b)
				log.debug(s"Consumed ${b.length} remaining bytes: ${b.take(64).map(x => "%02x".format(x)).mkString(" ")}")
			}
		}
		file.seek(math.min(endOffset, file.length()))
	}

	def parseCriteria(file: RDBDataInput): Seq[Criteria] = {
		val header = readLittleEndianInt(file)
		if (header != 36) {
			//throw new Exception("Expecting '36' for parseCriteria but got: " + header)
		}
		file.skipBytes(8)
		val criteriaCount = int3F1(readLittleEndianInt(file))

		(1L to criteriaCount).map(_ => Criteria(readLittleEndianInt(file), readLittleEndianInt(file), readLittleEndianInt(file)))
	}
	
	def parseAttribute(file: RDBDataInput): (Long, Long) = {
		val id = readLittleEndianInt(file)
		val value = readLittleEndianInt(file)
		(id, value)
	}
	
	def processEvent(file: RDBDataInput): Event = {
		val eventNum = readLittleEndianInt(file)
		//log.debug("eventNum: {}", eventNum)
		val funcCount = int3F1(readLittleEndianInt(file))
		//log.debug("funcCount: {}", funcCount)

		val functions = (1L to funcCount).map{ _ =>
			val functionNum = readLittleEndianInt(file)
			//log.debug("functionNum: {}", functionNum)
			file.skipBytes(8) // 0
			//log.debug("{}", readLittleEndianInt(file))
			//log.debug("{}", readLittleEndianInt(file))
			val requirementsCount = readLittleEndianInt(file)
			//log.debug("requirementsCount: {}", requirementsCount)
			val requirements = (1L to requirementsCount).map { _ =>
				val requiredAttributeNumber = readLittleEndianInt(file)
				val requiredAttributeValue = readLittleEndianInt(file)
				val requiredAttributeOperator = readLittleEndianInt(file)
				//log.debug("requiredAttributeNumber: {}", requiredAttributeNumber)
				//log.debug("requiredAttributeValue: {}", requiredAttributeValue)
				//log.debug("requiredAttributeOperator: {}", requiredAttributeOperator)

				FunctionRequirement(requiredAttributeNumber, requiredAttributeValue, requiredAttributeOperator)
			}

			val hits = readLittleEndianInt(file)
			val delay = readLittleEndianInt(file)
			val target = readLittleEndianInt(file)
			//log.debug("FunctionItterationCount: {}", hits)
			//log.debug("FunctionItterationDelay: {}", delay)
			//log.debug("FunctionTarget: {}", target)
			file.skipBytes(4)

			val functionParams = FunctionSets.getParamsById(functionNum).flatMap { case (num, paramType) =>
				//log.debug(s"param: $num $paramType")
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

	def attackDefense(file: RDBDataInput): Seq[AttackDefense] = {
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

	def shop(file: RDBDataInput): Unit = {
		val header = readLittleEndianInt(file)
		if (header != 37) {
			throw new Exception("Expecting '37' for shop but got: " + header)
		}
		val count = int3F1(readLittleEndianInt(file))
		file.skipBytes(count.toInt * 13)
	}
	
	def unknown5(file: RDBDataInput): Unit = {
		// always seem to be 2018 0 0 99
		file.skipBytes(16)
	}

	def unknown6(file: RDBDataInput): Unit = {
		val header = readLittleEndianInt(file)
		if (header != 27) {
			//throw new Exception("Expecting '27' for unknown6 but got: " + header)
		}
		//log.debug("{}", readLittleEndianInt(file))
		val pairs = int3F1(readLittleEndianInt(file))
		//log.debug("unknown num pairs: {}", pairs)
		file.skipBytes(pairs.toInt * 8)
		/*(1L to pairs).foreach{ _ =>
			log.debug("unknown1: " + readLittleEndianInt(file))
			log.debug("unknown2: " + readLittleEndianInt(file))
		}*/
	}

	def unknown19(file: RDBDataInput): Unit = {
		file.skipBytes(16)
	}

	def unknown20(file: RDBDataInput): Unit = {
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

	def unknown37(file: RDBDataInput): Unit = {
		val count = int3F1(readLittleEndianInt(file))
		file.skipBytes(4)
		file.skipBytes(count.toInt * 4 * 4)
	}

	def unknown1035(file: RDBDataInput): Unit = {
		file.skipBytes(8)
	}

	def playshiftRequirements(file: RDBDataInput): Unit = {
		val count = int3F1(readLittleEndianInt(file))
		file.skipBytes(count.toInt * 4 * 3)
	}
}