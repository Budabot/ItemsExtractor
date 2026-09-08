package com.jkbff.ao.itemsextractor.rdb.index

import java.io.RandomAccessFile
import java.nio.ByteBuffer
import org.apache.poi.util.LittleEndian
import org.slf4j.LoggerFactory
import com.jkbff.ao.itemsextractor.rdb.RDBFunctions._
import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer

class RDBIndexReader(in: RandomAccessFile) {
	private val log = LoggerFactory.getLogger(this.getClass)

	case class IndexBlock(offset: Long, nextBlock: Long, previousBlock: Long, records: Seq[IndexRecord]) {
		
		override def toString(): String = {
			"offset: " + offset +
			"\nnextBlock: " + nextBlock +
			"\npreviousBlock: " + previousBlock +
			"\nrecords:" + records.foldLeft("")((output, record) => output + "\n  " + record.toString)
		}
	}
	
	def getRecords(): List[IndexRecord] = {
		log.info("Buffering index file into memory...")
		val fileLength = in.length()
		val bytes = new Array[Byte](fileLength.toInt)
		in.seek(0)
		in.readFully(bytes)
		log.info(s"Buffered ${bytes.length} bytes into memory. Parsing index records...")

		val buffer = ByteBuffer.wrap(bytes)

		val b4 = new Array[Byte](4)
		val b2 = new Array[Byte](2)

		buffer.position(72)
		buffer.get(b4)
		val dataStart = LittleEndian.getUInt(b4)

		val records = new ListBuffer[IndexRecord]()
		var curr = dataStart

		while (curr != 0) {
			val pos = curr.toInt
			buffer.position(pos)
			buffer.get(b4)
			val nextBlock = LittleEndian.getUInt(b4)
			buffer.get(b4)
			val prevBlock = LittleEndian.getUInt(b4)
			buffer.get(b2)
			val count = LittleEndian.getUShort(b2)

			// skip block header
			buffer.position(pos + 28)

			var i = 0
			while (i < count) {
				buffer.get(b4)
				val high = LittleEndian.getUInt(b4)
				buffer.get(b4)
				val low = LittleEndian.getUInt(b4)
				val offset = (high << 32) + low
				val resourceType = buffer.getInt()
				val resourceId = buffer.getInt()
				records += IndexRecord(resourceType, resourceId, offset)
				i += 1
			}

			curr = nextBlock
		}

		log.info(s"Successfully parsed ${records.size} index records.")
		records.toList
	}
	
	@tailrec
	final def readIndexes(in: RandomAccessFile, dataStart: Long, list: List[IndexBlock] = Nil): List[IndexBlock] = {
		if (dataStart == 0) {
			list.reverse
		} else {
			in.seek(dataStart)
			val indexBlock = readIndexBlock(in)
			readIndexes(in, indexBlock.nextBlock, indexBlock :: list)
		}
	}
	
	def readIndexBlock(in: RandomAccessFile): IndexBlock = {
		val offset = in.getFilePointer()
		val nextBlock = readLittleEndianInt(in)
		val prevBlock = readLittleEndianInt(in)

		val count = readLittleEndianShort(in)
		
		// skip block header
		in.skipBytes(18)

		val records = (1 to count).map( _ => readRecord(in))
		
		IndexBlock(offset, nextBlock, prevBlock, records)
	}
	
	def readRecord(in: RandomAccessFile): IndexRecord = {
		val offset = readMiddleEndianLong(in)
		val resourceType = in.readInt()
		val resourceId = in.readInt()
		
		IndexRecord(resourceType, resourceId, offset)
	}
}