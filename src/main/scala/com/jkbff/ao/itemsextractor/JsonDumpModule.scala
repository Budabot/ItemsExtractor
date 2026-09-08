package com.jkbff.ao.itemsextractor

import java.io.{File, PrintWriter, RandomAccessFile}

import com.jkbff.ao.itemsextractor.rdb.constants.IndexRecordType
import com.jkbff.ao.itemsextractor.rdb.index.{IndexRecord, RDBIndexReader}
import com.jkbff.ao.itemsextractor.rdb.{MultiRandomAccessFile, RDBExtractor, RDBItem}
import io.circe.generic.auto._
import io.circe.syntax._
import org.slf4j.LoggerFactory

class JsonDumpModule {
  private val log = LoggerFactory.getLogger(this.getClass)

  def execute(db: MultiRandomAccessFile, indexFile: RandomAccessFile, aoPath: String): Unit = {
    val records: Seq[IndexRecord] = new RDBIndexReader(indexFile).getRecords()
    val rdbExtractor = new RDBExtractor()

    val entries = records.filter(_.resourceType == IndexRecordType.AODB_ITEM_TYPE).map{ entry =>
      try {
        rdbExtractor.readItem(db, entry, extractFunctions = true)
      } catch {
        case e: Exception =>
          log.error(s"Failed to read item for entry $entry", e)
          null
      }
    }.filter(x => x != null)

    val pw = new PrintWriter(new File("items.json" ))

    pw.write("[\n")
    entries.foldLeft(true) { case (isFirst, entry) =>
      if (!isFirst) {
        pw.write(",\n")
      }
      pw.write(entry.asJson.spaces2)
      false
    }
    pw.write("]\n")

    pw.close()

  }
}
