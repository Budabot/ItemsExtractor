package com.jkbff.ao.itemsextractor.rdb

import java.io.RandomAccessFile

import com.jkbff.ao.itemsextractor.rdb.index.IndexRecord

import RDBFunctions._

class RDBExtractor {
	def readItem(in: MultiRandomAccessFile, record: IndexRecord): RDBItem = {
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
		
		new RDBItem(record.resourceId, name, description, attributes)
	}
	
	def parseAttribute(file: RandomAccessFile): (Long, Long) = {
		val id = readLittleEndianInt(file)
		val value = readLittleEndianInt(file)
		(id, value)
	}
}