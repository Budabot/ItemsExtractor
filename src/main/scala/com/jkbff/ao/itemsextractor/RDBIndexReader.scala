package com.jkbff.ao.itemsextractor

import java.io.RandomAccessFile

import RDBFunctions._

class RDBIndexReader(in: RandomAccessFile) {
	val AODB_ITEM_TYPE = 0x000f4254
	
	class Index(val offset: Long, val nextBlock: Long, val previousBlock: Long, val records: List[Record]) {
		
		override def toString() = {
			"offset: " + offset +
			"\nnextBlock: " + nextBlock +
			"\npreviousBlock: " + previousBlock +
			"\nrecords:" + records.foldLeft("")((output, record) => output + "\n  " + record.toString)
		}
	}
	
	lazy val resourceTypeMap: Map[Long, List[Record]] = {
		//println("last offset: " + readLittleEndianInt(in))
		//println("data end: " + readLittleEndianInt(in))
		//println("block size: " + readLittleEndianInt(in))
		in.seek(72)
		val dataStart = readLittleEndianInt(in)
		//println("data start: " + dataStart)
		//println
		
		val indexes = readIndexes(in, dataStart)
		val records = indexes.flatMap(index => index.records).groupBy(x => x.resourceType)
		records
	}
	
	def readIndexes(in: RandomAccessFile, dataStart: Long): List[Index] = {
		var nextBlock = dataStart
		var indexes: List[Index] = Nil
		while (nextBlock != 0) {
			in.seek(nextBlock)
			val index = readIndexBlock(in)
			indexes = index :: indexes
			nextBlock = index.nextBlock
		}
		indexes.reverse
	}
	
	def readIndexBlock(in: RandomAccessFile): Index = {
		val offset = in.getFilePointer()
		val nextBlock = readLittleEndianInt(in)
		val prevBlock = readLittleEndianInt(in)

		val count = readLittleEndianShort(in)
		
		// skip block header
		in.skipBytes(18)
		val records = (1 to count).foldLeft(List[Record]()) { (list, number) =>
			readRecord(in) :: list
		}
		
		new Index(offset, nextBlock, prevBlock, records.reverse)
	}
	
	def readRecord(in: RandomAccessFile): Record = {
		val offset = readLittleEndianLong(in)
		val resourceType = in.readInt()
		val resourceId = in.readInt()
		
		new Record(resourceType, resourceId, offset)
	}
}