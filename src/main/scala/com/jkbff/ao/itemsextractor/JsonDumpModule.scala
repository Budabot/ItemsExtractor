package com.jkbff.ao.itemsextractor

import java.io.{File, PrintWriter, RandomAccessFile}

import com.jkbff.ao.itemsextractor.rdb.constants.IndexRecordType
import com.jkbff.ao.itemsextractor.rdb.index.{IndexRecord, RDBIndexReader}
import com.jkbff.ao.itemsextractor.rdb.{MultiRandomAccessFile, RDBExtractor, RDBItem}
import io.circe.generic.auto._
import io.circe.syntax._

class JsonDumpModule {
  def execute(db: MultiRandomAccessFile, indexFile: RandomAccessFile, aoPath: String): Unit = {
    val records: Seq[IndexRecord] = new RDBIndexReader(indexFile).getRecords()
    val rdbExtractor = new RDBExtractor()

    val entries = records.filter(_.resourceType == IndexRecordType.AODB_ITEM_TYPE).map{ entry =>
      try {
        rdbExtractor.readItem(db, entry, extractFunctions = true)
      } catch {
        case e: Exception =>
          e.printStackTrace()
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
