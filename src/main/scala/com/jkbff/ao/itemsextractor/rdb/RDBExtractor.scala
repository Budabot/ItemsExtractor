package com.jkbff.ao.itemsextractor.rdb

import java.io.RandomAccessFile

import com.jkbff.ao.itemsextractor.rdb.index.IndexRecord

import RDBFunctions.int3F1
import RDBFunctions.readLittleEndianInt

class RDBExtractor {
	def readItem(in: MultiRandomAccessFile, record: IndexRecord): RDBItem = {
		in.seek(record.offset)
		
		// taken from: http://aodevs.com/forums/index.php/topic,785.msg3039.html#msg3039
		in.skipBytes(50)
		
		val numAttributes = int3F1(in.readIntLittleEndian())
		val attributes = (1L to numAttributes).foldLeft(Map[Long, Long]()) ( (map, x) => map + parseAttribute(in.currentFile))
		in.skipBytes(8)
		
		val nameLength = in.readShortLittleEndian()
		val descriptionLength = in.readShortLittleEndian()
		val name = in.readString(nameLength)
		val description = in.readString(descriptionLength)
		
		new RDBItem(record.resourceId, name, description, attributes)
	}
	
	def parseAttribute(in: RandomAccessFile): (Long, Long) = {
		val id = readLittleEndianInt(in)
		val value = readLittleEndianInt(in)
		(id, value)
	}
}