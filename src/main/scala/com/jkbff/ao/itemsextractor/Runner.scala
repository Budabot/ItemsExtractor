package com.jkbff.ao.itemsextractor

import java.io.{File, RandomAccessFile}

import com.jkbff.ao.itemsextractor.idmatcher.IdMatcher
import com.jkbff.ao.itemsextractor.rdb.index.{IndexRecord, RDBIndexReader}
import com.jkbff.ao.itemsextractor.rdb.{MultiRandomAccessFile, RDBExtractor, RDBItem}
import com.jkbff.common.Helper._
import org.apache.log4j.Logger

class Runner (aoPath: String) {
	private val log = Logger.getLogger(this.getClass())
	
	lazy val indexFile = new RandomAccessFile(aoPath + "cd_image/data/db/ResourceDatabase.idx", "r")

	val AODB_ITEM_TYPE: Long = 0x000f4254L
	val AODB_NANO_TYPE: Long = 0x000fde85L
	
	val rdbExtractor = new RDBExtractor()
	lazy val records: Seq[IndexRecord] = new RDBIndexReader(indexFile).getRecords()
	
	def run(): Unit = {
		using(new MultiRandomAccessFile(getDatabaseFiles())) { db =>
			new IdMatcher().writeSqlFile(getEntries(AODB_ITEM_TYPE), getEntries(AODB_NANO_TYPE), aoPath)
		}
	}
	
	def getEntries(resourceType: Long): Seq[RDBItem] = {
		using(new MultiRandomAccessFile(getDatabaseFiles())) { db =>
			log.debug("reading index file entries")
			val entries = records.filter(_.resourceType == resourceType).map(rdbExtractor.readItem(db, _, extractFunctions = true))
			log.debug("read " + entries.size + " entries")
			entries
		}
	}
	
	def getSingleItem(id: Long): Option[RDBItem] = {
		using(new MultiRandomAccessFile(getDatabaseFiles())) { db =>
			val recordOpt = getIndexRecord(id, AODB_ITEM_TYPE)
			recordOpt.map(record => rdbExtractor.readItem(db, record, extractFunctions = true))
		}
	}

	def getIndexRecord(id: Long, resourceType: Long): Option[IndexRecord] = {
		records.filter(_.resourceType == resourceType).find(_.resourceId == id)
	}
	
	def getDatabaseFiles(): Seq[String] = {
		new File(aoPath + "cd_image/data/db/").listFiles().collect {
			case file: File if file.isFile() && file.getName().startsWith("ResourceDatabase.dat") => file.getCanonicalPath()
		}
	}
}