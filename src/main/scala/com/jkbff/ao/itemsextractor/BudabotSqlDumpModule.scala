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
    val itemEntries = getEntries(db, indexFile, IndexRecordType.AODB_ITEM_TYPE)
    val nanoEntries = getEntries(db, indexFile, IndexRecordType.AODB_NANO_TYPE)

    new IdMatcher().writeSqlFile(itemEntries, nanoEntries, aoPath)
  }

  def getEntries(db: MultiRandomAccessFile, indexFile: RandomAccessFile, resourceType: Long): Seq[RDBItem] = {
    val records: Seq[IndexRecord] = new RDBIndexReader(indexFile).getRecords()
    val rdbExtractor = new RDBExtractor()

    log.debug("reading index file entries")
    val entries = records.filter(_.resourceType == resourceType).map(rdbExtractor.readItem(db, _, extractFunctions = true))
    log.debug("read " + entries.size + " entries")
    entries
  }
}
