package com.jkbff.ao.itemsextractor

import java.io.RandomAccessFile

import com.jkbff.ao.itemsextractor.idmatcher.IdMatcher
import com.jkbff.ao.itemsextractor.rdb.constants.IndexRecordType
import com.jkbff.ao.itemsextractor.rdb.index.{IndexRecord, RDBIndexReader}
import com.jkbff.ao.itemsextractor.rdb.{MultiRandomAccessFile, RDBExtractor, RDBItem}
import org.slf4j.LoggerFactory

class BudabotSqlDumpModule {

  private val log = LoggerFactory.getLogger(this.getClass())

  def execute(db: MultiRandomAccessFile, indexFile: RandomAccessFile, aoPath: String): Unit = {
    log.info("Starting SQL dump generation...")
    val records: Seq[IndexRecord] = new RDBIndexReader(indexFile).getRecords()
    val itemEntries = getEntries(db, records, IndexRecordType.AODB_ITEM_TYPE)
    val nanoEntries = getEntries(db, records, IndexRecordType.AODB_NANO_TYPE)

    new IdMatcher().writeSqlFile(itemEntries, nanoEntries, aoPath)
    log.info("SQL dump completed successfully.")
  }

  def getEntries(db: MultiRandomAccessFile, indexFile: RandomAccessFile, resourceType: Long): Seq[RDBItem] = {
    val records: Seq[IndexRecord] = new RDBIndexReader(indexFile).getRecords()
    getEntries(db, records, resourceType)
  }

  def getEntries(db: MultiRandomAccessFile, records: Seq[IndexRecord], resourceType: Long): Seq[RDBItem] = {
    val rdbExtractor = new RDBExtractor()
    val filtered = records.filter(_.resourceType == resourceType)
    val total = filtered.size
    log.info(s"Extracting $total entries for resource type $resourceType...")
    var count = 0
    val entries = filtered.map { record =>
      val item = rdbExtractor.readItem(db, record, extractFunctions = true)
      count += 1
      if (count % 10000 == 0 || count == total) {
        log.info(s"Extracted $count / $total entries (${count * 100 / total}%)...")
      }
      item
    }
    log.info(s"Extracted ${entries.size} entries for resource type $resourceType.")
    entries
  }
}
