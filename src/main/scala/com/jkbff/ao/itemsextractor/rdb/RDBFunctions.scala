package com.jkbff.ao.itemsextractor.rdb

import org.apache.poi.util.LittleEndian
import java.io.DataInput

object RDBFunctions {
	def readMiddleEndianLong(in: DataInput): Long = {
		readLittleEndianInt(in) * (2 ^ 32) + readLittleEndianInt(in)
	}

	def readLittleEndianInt(in: DataInput): Long = {
		val b = new Array[Byte](4)
		in.readFully(b)
		LittleEndian.getUInt(b)
	}

	def readLittleEndianSignedInt(in: DataInput): Int = {
		val b = new Array[Byte](4)
		in.readFully(b)
		LittleEndian.getInt(b)
	}

	def readLittleEndianShort(in: DataInput): Int = {
		val b = new Array[Byte](2)
		in.readFully(b)
		LittleEndian.getUShort(b)
	}

	def int3F1(n: Long): Long = {
		//n >> 10
		(n / 1009) - 1
	}

	def readString(in: DataInput, length: Int): String = {
		new String(read(in, length))
	}
	
	def read(in: DataInput, length: Int): Array[Byte] = {
		val b = new Array[Byte](length)
		in.readFully(b)
		b
	}
}