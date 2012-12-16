package com.jkbff.ao.itemsextractor

import java.io.RandomAccessFile
import RDBFunctions._

class ItemExtractor {
	def readItem(in: MultiRandomAccessFile, record: Record): RDBItem = {
		in.seek(record.offset)
		
		// taken from: http://aodevs.com/forums/index.php/topic,785.msg3039.html#msg3039
		in.skipBytes(50)
		
		val numAttributes = int3F1(in.readIntLittleEndian())
		val attributes = (1L to numAttributes).foldLeft(List[RDBAttribute]()) ( (list, x) => parseAttribute(in.currentFile) :: list).reverse
		in.skipBytes(8)
		
		val nameLength = in.readShortLittleEndian()
		val descriptionLength = in.readShortLittleEndian()
		val name = in.readString(nameLength)
		val description = in.readString(descriptionLength)
		
		new RDBItem(record.resourceId, name, description, attributes)
	}
	
	def parseAttribute(in: RandomAccessFile): RDBAttribute = {
		val id = readLittleEndianInt(in)
		val value = readLittleEndianInt(in)
		new RDBAttribute(id, value)
	}
}