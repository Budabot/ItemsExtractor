package com.jkbff.ao.itemsextractor

import java.io.{File, RandomAccessFile}

import com.jkbff.ao.itemsextractor.rdb.{MultiRandomAccessFile, RDBExtractor, RDBItem}
import com.jkbff.ao.itemsextractor.rdb.index.{IndexRecord, RDBIndexReader}
import com.jkbff.common.Helper._
import org.apache.log4j.Logger

class Runner (aoPath: String) {
	val log = Logger.getLogger(this.getClass())
	
	lazy val db = new MultiRandomAccessFile(getDatabaseFiles())
	lazy val indexFile = new RandomAccessFile(aoPath + "cd_image/data/db/ResourceDatabase.idx", "r")

	val AODB_ITEM_TYPE = 0x000f4254
	val AODB_NANO_TYPE = 0x000fde85
	
	val rdbExtractor = new RDBExtractor()
	
	def run(): Unit = {
		//new IdMatcher().writeSqlFile(getEntries, aoPath)

		//val indexRecord = new IndexRecord(1000020,203860,272555198)
		//val indexRecord = getIndexRecord(203859, AODB_NANO_TYPE)
		//println(indexRecord)
		val indexRecord = new IndexRecord(1040005,203859,2197314000L)
		val item = rdbExtractor.readItem(db, indexRecord, true)
		println(item)

		/*
		{
			val record = new IndexRecord(1000020, 93135, 224668543)
			val item = rdbExtractor.readItem(db, record, true)
			//println(item)
		}

		//println(FunctionSets.getParamsById(53240))
		{
			val record = new IndexRecord(1040005, 93132, 2196125022L)
			val item = rdbExtractor.readItem(db, record, true)
			//println(item)
		}
		*/
		val typeMap = new RDBIndexReader(indexFile).resourceTypeMap

		val itemNano = typeMap(AODB_ITEM_TYPE).find(_.resourceId == 203860) |>
				(indexRecordCrystal => rdbExtractor.readItem(db, indexRecordCrystal.get, true)) |>
				(itemCrystal => itemCrystal.functions.find(_.functionNum == 53019)) |>
				(uploadFunction => uploadFunction.get.params(0).asInstanceOf[Long]) |>
				(nanoFormulaId => typeMap(AODB_NANO_TYPE).find(_.resourceId == nanoFormulaId)) |>
				(indexRecordNano => rdbExtractor.readItem(db, indexRecordNano.get, true))

		println(itemNano)

		val nanoline = itemNano.attributes.find(_._1 == 75).getOrElse((0, 0))._2
		val nano = itemNano.name
		val profession = itemNano.criteria.find(x => x.statNum == 368 || x.statNum == 60).get.value

		println((nano, nanoline, profession))
	}
	
	def getEntries(): Seq[RDBItem] = {
		log.debug("reading index file entries")
		val entries = new RDBIndexReader(indexFile).resourceTypeMap(AODB_ITEM_TYPE).map(rdbExtractor.readItem(db, _, false))
		log.debug("read " + entries.size + " entries")
		entries
	}
	
	def getSingleItem(id: Long): RDBItem = {
		val record = getIndexRecord(id, AODB_ITEM_TYPE)
		println(record)
		rdbExtractor.readItem(db, record, true)
	}

	def getIndexRecord(id: Long, resourceType: Long): IndexRecord = {
		new RDBIndexReader(indexFile).resourceTypeMap(resourceType).find(_.resourceId == id).get
	}
	
	def getDatabaseFiles(): Seq[String] = {
		new File(aoPath + "cd_image/data/db/").listFiles().collect {
			case file: File if file.isFile() && file.getName().startsWith("ResourceDatabase.dat") => file.getCanonicalPath()
		}
	}
}