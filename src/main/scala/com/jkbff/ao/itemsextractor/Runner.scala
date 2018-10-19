package com.jkbff.ao.itemsextractor

import java.io.{File, RandomAccessFile}

import com.jkbff.ao.itemsextractor.idmatcher.IdMatcher
import com.jkbff.ao.itemsextractor.rdb.constants.IndexRecordType
import com.jkbff.ao.itemsextractor.rdb.index.{IndexRecord, RDBIndexReader}
import com.jkbff.ao.itemsextractor.rdb.{MultiRandomAccessFile, RDBExtractor, RDBItem}
import com.jkbff.common.DB
import com.jkbff.common.Helper._
import org.apache.commons.dbcp.BasicDataSource
import org.apache.log4j.Logger

import scala.io.Source

class Runner (aoPath: String) {
	private val log = Logger.getLogger(this.getClass())
	
	lazy val indexFile = new RandomAccessFile(aoPath + "cd_image/data/db/ResourceDatabase.idx", "r")

	val rdbExtractor = new RDBExtractor()
	lazy val records: Seq[IndexRecord] = new RDBIndexReader(indexFile).getRecords()
	
	def run(): Unit = {
		using(new MultiRandomAccessFile(getDatabaseFiles())) { db =>
			new IdMatcher().writeSqlFile(getEntries(IndexRecordType.AODB_ITEM_TYPE), getEntries(IndexRecordType.AODB_NANO_TYPE), aoPath)
		}
	}

  lazy val h2Ds: BasicDataSource = init(new BasicDataSource()) { ds =>
    ds.setDriverClassName("org.h2.Driver")
    //ds.setUrl("jdbc:h2:mem:db1;IGNORECASE=true")
    ds.setUrl("jdbc:h2:mem:db1")
    ds.setUsername("")
    ds.setPassword("")
  }

  def run2(): Unit = {
    val outputdb = new DB(h2Ds)
    using(Source.fromInputStream(getClass.getClassLoader.getResourceAsStream("nanos.sql"))) { source =>
      source.getLines().foreach(outputdb.update)
    }
    using(new MultiRandomAccessFile(getDatabaseFiles())) { db =>
      outputdb.query("SELECT * FROM nanos", rs => rs.getInt("lowid")).foreach { id =>
        try {
          val nano_crystal_record = getIndexRecord(id, IndexRecordType.AODB_ITEM_TYPE).get
          val nano_crystal_item = rdbExtractor.readItem(db, nano_crystal_record, extractFunctions = true)
          val nano_id = nano_crystal_item.events(0).functions.find(_.functionNum == 53019).get.params(0).asInstanceOf[Int]
          val nano_record = getIndexRecord(nano_id, IndexRecordType.AODB_NANO_TYPE).get
          val nano_item = rdbExtractor.readItem(db, nano_record, extractFunctions = false)
          //println(nano_id + ", \"" + nano_item.name + "\"")
        } catch {
          case _: NoSuchElementException => println("Could not find record for: " + id)
        }
      }
    }

    // load nano.sql
    // grab full entry for each nano crystal item id
    // figure out function name
    // extract function
  }
	
	def getEntries(resourceType: Long): Seq[RDBItem] = {
		using(new MultiRandomAccessFile(getDatabaseFiles())) { db: MultiRandomAccessFile =>
			log.debug("reading index file entries")
			val entries = records.filter(_.resourceType == resourceType).map(rdbExtractor.readItem(db, _, extractFunctions = true))
			log.debug("read " + entries.size + " entries")
			entries
		}
	}
	
	def getSingleItem(id: Long): Option[RDBItem] = {
		using(new MultiRandomAccessFile(getDatabaseFiles())) { db =>
			val recordOpt = getIndexRecord(id, IndexRecordType.AODB_ITEM_TYPE)
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