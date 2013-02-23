package com.jkbff.ao.itemsextractor
import java.io.RandomAccessFile
import scala.annotation.tailrec
import java.io.DataInput
import RDBFunctions._

class MultiRandomAccessFile(files: Seq[String]) {
	val inputs = files map (new RandomAccessFile(_, "r"))
	var currentFile = inputs(0)
	
	def seek(pos: Long) {
		val x = findInput(pos, inputs)
		currentFile = x._1
		currentFile.seek(x._2)
	}
	
	@tailrec
	private def findInput(pos: Long, files: Seq[RandomAccessFile]): (RandomAccessFile, Long) = {
		val file = files.head
		if (pos > file.length()) {
			findInput(pos - file.length() + 4096, files.tail)
		} else {
			(file, pos)
		}
	}
	
	def skipBytes(num: Int) = currentFile.skipBytes(num)
	
	def readIntLittleEndian() = readLittleEndianInt(currentFile)
	def readShortLittleEndian() = readLittleEndianShort(currentFile)
	
	def readString(length: Int): String = {
		val b = new Array[Byte](length)
		currentFile.readFully(b)
		new String(b)
	}
	
	def read(length: Int): Array[Byte] = {
		val b = new Array[Byte](length)
		currentFile.readFully(b)
		b
	}
}