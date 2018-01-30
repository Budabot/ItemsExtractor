package com.jkbff.ao.itemsextractor.rdb.index

import java.io.RandomAccessFile
import com.jkbff.ao.itemsextractor.rdb.RDBFunctions._
import scala.annotation.tailrec

class RDBIndexReader(in: RandomAccessFile) {
	case class IndexBlock(offset: Long, nextBlock: Long, previousBlock: Long, records: Seq[IndexRecord]) {
		
		override def toString(): String = {
			"offset: " + offset +
			"\nnextBlock: " + nextBlock +
			"\npreviousBlock: " + previousBlock +
			"\nrecords:" + records.foldLeft("")((output, record) => output + "\n  " + record.toString)
		}
	}
	
	def getRecords(): List[IndexRecord] = {
		//println("last offset: " + readLittleEndianInt(in))
		//println("data end: " + readLittleEndianInt(in))
		//println("block size: " + readLittleEndianInt(in))
		in.seek(72)
		val dataStart = readLittleEndianInt(in)
		//println("data start: " + dataStart)
		//println
		
		readIndexes(in, dataStart).flatMap(index => index.records)
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