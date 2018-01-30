package com.jkbff.ao.itemsextractor.rdb

import java.io.RandomAccessFile
import scala.annotation.tailrec
import RDBFunctions._

class MultiRandomAccessFile(files: Seq[String]) {
	val inputs = files map (new RandomAccessFile(_, "r"))
	
	def seek(pos: Long): RandomAccessFile = {
		val x = findInput(pos, inputs)
		x._1.seek(x._2)
		x._1
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

	def close(): Unit = {
		inputs.foreach(_.close())
	}
}
