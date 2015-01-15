package com.jkbff.ao.itemsextractor

import java.io.File
import java.io.RandomAccessFile
import scala.Array.canBuildFrom
import scala.io.Source
import org.apache.log4j.Logger
import com.jkbff.ao.itemsextractor.idmatcher.IdMatcher
import com.jkbff.ao.itemsextractor.rdb.MultiRandomAccessFile
import com.jkbff.ao.itemsextractor.rdb.RDBExtractor
import com.jkbff.ao.itemsextractor.rdb.RDBItem
import com.jkbff.ao.itemsextractor.rdb.index.RDBIndexReader

class Runner (aoPath: String) {
	val log = Logger.getLogger(this.getClass())
	
	lazy val db = new MultiRandomAccessFile(getDatabaseFiles())
	lazy val indexFile = new RandomAccessFile(aoPath + "cd_image/data/db/ResourceDatabase.idx", "r")

	val AODB_ITEM_TYPE = 0x000f4254
	val AODB_TYP_NANO = 0x000fde85
	
	val rdbExtractor = new RDBExtractor()
	
	def run(): Unit = {
		new IdMatcher().writeSqlFile(getEntries, aoPath)
	}
	
	def getEntries(): Seq[RDBItem] = {
		log.debug("reading index file entries")
		val entries = new RDBIndexReader(indexFile).resourceTypeMap(AODB_ITEM_TYPE) map { rdbExtractor.readItem(db, _) }
		log.debug("read " + entries.size + " entries")
		entries
	}
	
	def getSingleItem(id: Long): RDBItem = {
		val record = new RDBIndexReader(indexFile).resourceTypeMap(AODB_ITEM_TYPE).find(_.resourceId == id).get
		rdbExtractor.readItem(db, record)
	}
	
	def getDatabaseFiles(): Seq[String] = {
		new File(aoPath + "cd_image/data/db/").listFiles().collect {
			case file: File if file.isFile() && file.getName().startsWith("ResourceDatabase.dat") => file.getCanonicalPath()
		}
	}
}