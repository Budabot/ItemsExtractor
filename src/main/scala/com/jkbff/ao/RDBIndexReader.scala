package com.jkbff.ao
import java.io.FileInputStream
import java.io.DataInputStream
import org.apache.poi.util.LittleEndian
import java.io.RandomAccessFile
import com.sun.xml.internal.ws.api.pipe.NextAction

object RDBIndexReader extends App {
	getResourceTypeMap("ResourceDatabase.idx")
	
	val AODB_ITEM_TYPE = 0x000f4254
	
	class Record(val resourceType: Long, val resourceId: Long, val offset: Long) {
		override def toString() = "resourceType: " + resourceType + " resourceId: " + resourceId + " offset: " + offset
	}
	
	class Index(val offset: Long, val nextBlock: Long, val previousBlock: Long, val records: List[Record]) {
		
		val hi = records.foldLeft("") { (output, record) => record.toString }
		override def toString() = {
			"offset: " + offset +
			"\nnextBlock: " + nextBlock +
			"\npreviousBlock: " + previousBlock +
			"\nrecords:" + records.foldLeft("")((output, record) => output + "\n  " + record.toString)
		}
	}
	
	def getResourceTypeMap(filename: String): Map[Long, List[Record]] = {
		val in = new RandomAccessFile(filename, "r")
		
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
	
	def readLittleEndianInt(in: RandomAccessFile): Long = {
		val b = new Array[Byte](4)
		in.read(b)
		LittleEndian.getUInt(b)
	}
	
	def readLittleEndianShort(in: RandomAccessFile): Long = {
		val b = new Array[Byte](2)
		in.read(b)
		LittleEndian.getUShort(b)
	}
	
	def readIndexBlock(in: RandomAccessFile): Index = {
		val offset = in.getFilePointer()
		val nextBlock = readLittleEndianInt(in)
		val prevBlock = readLittleEndianInt(in)

		val count = readLittleEndianShort(in)
		
		// skip block header
		in.skipBytes(8)
		val records = (1L to count).foldLeft(List[Record]()) { (list, number) =>
			readRecord(in) :: list
		}
		
		new Index(offset, nextBlock, prevBlock, records.reverse)
	}
	
	def readRecord(in: RandomAccessFile): Record = {
		val offset = readLittleEndianInt(in)
		val resourceType = in.readInt()
		val resourceId = in.readInt()
		
		new Record(resourceType, resourceId, offset)
	}
}