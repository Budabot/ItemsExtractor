package com.jkbff.ao.itemsextractor

import java.io.File
import java.io.RandomAccessFile
import scala.io.Source
import org.apache.log4j.Logger

class Runner (aoPath: String) {
	val log = Logger.getLogger(this.getClass())
	
	lazy val db = new MultiRandomAccessFile(getDatabaseFiles())
	lazy val indexFile = new RandomAccessFile(aoPath + "cd_image/data/db/ResourceDatabase.idx", "r")

	val AODB_ITEM_TYPE = 0x000f4254
	val AODB_TYP_NANO = 0x000fde85
	
	def run(): Unit = {
		new IdMatcher().writeSqlFile(getEntries, "aodb" + getVersion(aoPath) + ".sql")
	}
	
	def getEntries(): List[Entry] = {
		val nullAttribute = new RDBAttribute(0, 0)
		log.debug("reading index file entries")
		new RDBIndexReader(indexFile).resourceTypeMap(AODB_ITEM_TYPE) map { x =>
			val item = new ItemExtractor().readItem(db, x)
			val iconAttribute = item.attributes.find(_.id == 79)
			val qlAttribute = item.attributes.find(_.id == 54)
			val itemTypeAttribute = item.attributes.find(_.id == 76)
			
			new Entry(item.id.toInt, qlAttribute.getOrElse(nullAttribute).value.toInt, item.name, iconAttribute.getOrElse(nullAttribute).value.toInt, RDBFunctions.getItemType(itemTypeAttribute.getOrElse(nullAttribute).value))
		}
	}
	
	def getSingleItem(id: Long): RDBItem = {
		val record = new RDBIndexReader(indexFile).resourceTypeMap(AODB_ITEM_TYPE).find(_.resourceId == id).get
		new ItemExtractor().readItem(db, record)
	}
	
	def getVersion(aoPath: String): String = {
		val source = Source.fromFile(aoPath + "version.id")
		val pieces = source.mkString.trim.replace("_EP1", "").split("\\.").map(_.toInt)
		source.close()
		"%02d.%02d.%02d.%02d".format(pieces(0), pieces(1), pieces(2), 0)
	}
	
	def getDatabaseFiles(): Seq[String] = {
		new File(aoPath + "cd_image/data/db/").listFiles().collect { 
			case file: File if file.isFile() && file.getName().startsWith("ResourceDatabase.dat") => file.getCanonicalPath()
		}
	}
}