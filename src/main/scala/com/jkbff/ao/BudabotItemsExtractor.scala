package com.jkbff.ao
import scala.util.parsing.input.StreamReader
import javax.sql.DataSource
import org.apache.commons.dbcp.BasicDataSource
import org.apache.log4j.Logger
import com.jkbff.ao.tyrbot.jdbc.ScalaJdbcTemplate
import com.jkbff.ao.tyrbot.jdbc.StringRowMapper
import com.twitter.conversions.string
import java.sql.ResultSet
import com.jkbff.ao.tyrbot.jdbc.FunctionalRowMapper
import java.util.Hashtable
import com.jkbff.ao.tyrbot.jdbc.GenericRowMapper
import scala.io.Source
import java.nio.file.Path
import java.io.File
import java.io.PrintWriter
import com.jkbff.ao.tyrbot.jdbc.Helper

object BudabotItemsExtractor extends App {

	class Entry(val id: Int, val ql: Int, val name: String, val iconId: Int, val itemType: String) {
		def this(rs: ResultSet) = this(rs.getInt("aoid"), rs.getInt("ql"), rs.getString("name"), rs.getInt("icon"), rs.getString("itemtype"))
		
		override def toString() = List(id, ql, name, iconId, itemType).toString
	}
	
	class Entry2(val id: Int, val ql: Int, val name: String, val iconId: Int, val itemType: String) {
		def this(rs: ResultSet) = this(rs.getInt("aoid"), rs.getInt("ql"), rs.getString("name"), rs.getInt("icon"), rs.getString("type"))
	}

	val log = Logger.getLogger(this.getClass())
	

	val sqliteDs = {
		val ds = new BasicDataSource()
		ds.setDriverClassName("org.sqlite.JDBC")
		ds.setUrl("jdbc:sqlite::memory:")
		ds.setUsername("")
		ds.setPassword("")
		ds
	}
	
	val h2Ds = {
		val ds = new BasicDataSource()
		ds.setDriverClassName("org.h2.Driver")
		//ds.setUrl("jdbc:h2:mem:db1;IGNORECASE=true")
		ds.setUrl("jdbc:h2:mem:db1")
		ds.setUsername("")
		ds.setPassword("")
		ds
	}
	
	val outputdb = new DB(h2Ds)
	val file = "output.sql"

	val elapsed = Helper.stopwatch{
		outputdb.update("DROP TABLE IF EXISTS entries")
		outputdb.update("DROP TABLE IF EXISTS aodb")
		outputdb.update("CREATE TABLE entries (aoid INT, ql INT, name TEXT, icon INT, itemtype TEXT, hash TEXT)")
		outputdb.update("CREATE TABLE aodb (lowid INT, highid INT, lowql INT, highql INT, name VARCHAR(150), icon INT)")
		
		val sourceDb = new ScalaJdbcTemplate(getDatasource("jdbc:sqlite:aoitems.db"))
		val entries = sourceDb.query("SELECT * FROM tblAO", new GenericRowMapper[Entry2])
	
		writeEntriesToDb(outputdb, entries)
		
		outputdb.update("CREATE INDEX idx_name ON entries (name)")
		outputdb.update("CREATE INDEX idx_aoid ON entries (aoid)")
		
		processStaticList(outputdb, readEntriesFromFile("static_list.txt"))
		processDeleteList(outputdb, readEntriesFromFile("delete_list.txt"))
		processNameSeparations(outputdb, readEntriesFromFile("nameseperation_list.txt"))
		processRemaingingEntries(outputdb)
		outputSqlFile(outputdb, file)
	}
	log.info("Elapsed time: %ds".format(elapsed / 1000))

	def getDatasource(url: String): DataSource = {
		val ds = new BasicDataSource()
		ds.setDriverClassName("org.sqlite.JDBC")
		ds.setUrl(url)
		ds.setUsername("")
		ds.setPassword("")
		ds
	}

	def processNameSeparations(db: DB, nameSeparationList: List[String]) {
		//db.startTransaction()
		nameSeparationList foreach { line =>
			val parms = line.split(',')
			log.debug("Processing name separation: " + line)
			val commonNames = db.query(
				"SELECT DISTINCT replace(replace(name, ?, ''), ?, '') AS common_name " +
					"FROM entries WHERE (name LIKE ? OR name LIKE ?) " +
					"AND itemtype = ? ORDER BY common_name",
				Seq(
					parms(1).replace("%", ""),
					parms(0).replace("%", ""),
					parms(0),
					parms(1),
					parms(2)
				), new StringRowMapper
			)

			commonNames foreach { commonName =>
				val entries = db.query(
					"SELECT * FROM (" +
					"SELECT *, replace(replace(name, ?, ''), ?, '') AS common_name " +
						"FROM entries) t WHERE (name LIKE ? OR name LIKE ?) " +
						"AND common_name = ? AND itemtype = ? " +
						"ORDER BY ql ASC",
					Seq(
						parms(1).replace("%", ""),
						parms(0).replace("%", ""),
						parms(0),
						parms(1),
						commonName,
						parms(2)
					),
					new GenericRowMapper[Entry]
				)

				if (entries.size > 0) {
					matchEntries(db, entries)
				}
			}
		}

		// delete name separation entries so they are not processed again
		nameSeparationList foreach { line =>
			val parms = line.split(',')
			db.update(
				"DELETE FROM entries WHERE (name LIKE ? OR name LIKE ?) AND itemtype = ?",
				Seq(
					parms(0),
					parms(1),
					parms(2)
				)
			)
		}

		//db.commitTransaction()
	}

	def processDeleteList(db: DB, deleteList: List[String]) {
		//db.startTransaction()
		deleteList foreach { line =>
			log.debug("Deleting where " + line)
			val matches = db.query("SELECT aoid, name FROM entries WHERE " + line, new FunctionalRowMapper({ rs => (rs.getInt("aoid"), rs.getString("name"))}))
			matches foreach(x => log.debug("Deleting item " + x))
			db.update("DELETE FROM entries WHERE " + line)
		}
		//db.commitTransaction()
	}

	def processStaticList(db: DB, staticList: List[String]) {
		val list = staticList map (_.split(','))

		//db.startTransaction()
		list foreach { parms =>
			log.debug("Processing static entry: '" + parms(0) + "', '" + parms(1) + "', '" + parms(2) + "', '" + parms(3) + "'")
			val low = db.queryForObject(
				"SELECT * FROM entries WHERE aoid = ? AND ql = ?",
				Seq(
					parms(0),
					parms(2)
				),
				new GenericRowMapper[Entry]
			)

			if (low.isDefined) {
				val high = new Entry(parms(1).toInt, parms(3).toInt, "", 0, "")
				addItem(db, low.get, high)
			} else {
				log.debug("ERROR-Could not find item id '" + parms(0) + "' at ql '" + parms(2) + "'")
			}
		}

		// delete name separation entries so they are not processed again
		list foreach { parms =>
			db.update(
				"DELETE FROM entries WHERE aoid = ? AND ql = ?",
				Seq(
					parms(0),
					parms(2)
				)
			)
		}

		//db.commitTransaction()
	}

	def processRemaingingEntries(db: DB) {
		//db.startTransaction()
		val mapper = new FunctionalRowMapper({ rs =>
			Map("name" -> rs.getString("name"), "itemtype" -> rs.getString("itemtype"), "icon" -> rs.getInt("icon"))
		})
		val distinctNames = db.query("SELECT DISTINCT name, itemtype, icon FROM entries ORDER BY name", mapper)

		distinctNames foreach { ht =>
			val entries = db.query(
				"SELECT * FROM entries WHERE name = ? AND itemtype = ? AND icon = ? ORDER BY ql ASC",
				ht("name") :: ht("itemtype") :: ht("icon") :: Nil,
				new GenericRowMapper[Entry]
			)

			var sequential = true
			var i = 0
			while (i < entries.size - 1 && sequential) {
				if (entries(i).ql >= entries(i + 1).ql) {
					sequential = false
				}
				i += 1
			}

			if (sequential) {
				log.debug("Sequential ql handling for: '" + ht("name") + "'")
				matchEntries(db, entries)
			} else {
				log.debug("AOID handling for: '" + ht("name") + "'")
				val entries2 = db.query(
					"SELECT * FROM entries WHERE name = ? AND itemtype = ? AND icon = ? ORDER BY aoid ASC",
					ht("name") :: ht("itemtype") :: ht("icon") :: Nil,
					new GenericRowMapper[Entry]
				)

				var tempEntries = List[Entry]()
				var currentQl = 0
				entries2 foreach { entry =>
					if (currentQl >= entry.ql) {
						log.debug("Processing temp entries")
						matchEntries(db, tempEntries.reverse)
						tempEntries = List[Entry]()
					}

					log.debug("Adding to temp entries: '" + entry.name + "' '" + entry.id + "' '" + entry.ql + "'")
					currentQl = entry.ql
					tempEntries = entry :: tempEntries
				}
				log.debug("Processing temp entries")
				matchEntries(db, tempEntries.reverse)
			}
		}
		//db.commitTransaction()
	}

	def matchEntries(db: DB, entries: List[Entry]) {
		if (entries.size == 0) {
			return 
		}

		log.debug("Matching for: '" + entries(0).name + "' count: " + entries.size)
		// if there is only one item, match it to itself
		if (entries.size == 1) {
			log.debug("One entry for: '" + entries(0).name + "'")
			addItem(db, entries(0), entries(0))
			return
		}

		var tempEntries = entries(0) :: Nil
		var i = 1
		while (i < entries.size - 1) {
			if (entries(i).ql == entries(i + 1).ql - 1) {
				log.debug("QLs already split for: '" + entries(i).ql + "' and '" + entries(i + 1).ql + "'")
				tempEntries = entries(i) :: tempEntries
				tempEntries = entries(i + 1) :: tempEntries
				i += 2
			} else {
				log.debug("Splitting QLs for: '" + entries(i).ql + "'")
				val entry = new Entry(entries(i).id, entries(i).ql - 1, entries(i).name, entries(i).iconId, entries(i).itemType)
				tempEntries = entry :: tempEntries
				tempEntries = entries(i) :: tempEntries
				i += 1
			}
		}
		if (i < entries.size) {
			tempEntries = entries(entries.size - 1) :: tempEntries
		}
		
		addEntryItems(db, tempEntries.reverse)
	}
	
	def addEntryItems(db: DB,entries: List[Entry]) {
		var j = 0
		while (j < entries.size - 1) {
			if (entries(j).ql == entries(j + 1).ql - 1) {
				addItem(db, entries(j), entries(j))
				addItem(db, entries(j + 1), entries(j + 1))
			} else {
				addItem(db, entries(j), entries(j + 1))
			}
			j += 2
		}
		if (j < entries.size) {
			addItem(db, entries(entries.size - 1), entries(entries.size - 1))
		}
	}

	def addItem(db: DB, low: Entry, high: Entry) {
		if (low.ql > high.ql) {
			log.error("Invalid item! low ql is greater than high ql: " + low + " " + high)
		}
		
		val sql = "INSERT INTO aodb (lowid, highid, lowql, highql, name, icon) VALUES (?, ?, ?, ?, ?, ?)"
		val params = Seq(
			low.id,
			high.id,
			low.ql,
			high.ql,
			low.name,
			low.iconId
		)
		db.update(sql, params)
		//log.debug(sql)
	}

	def writeEntriesToDb(db: DB, entries: List[Entry2]) {
		//db.startTransaction()
		log.debug("writing %d entries".format(entries.size))
		entries foreach { entry =>
			db.update("INSERT INTO entries (aoid, ql, name, icon, itemtype) VALUES (?, ?, ?, ?, ?)",
				Seq(
					entry.id,
					entry.ql,
					entry.name,
					entry.iconId,
					entry.itemType
				)
			)
		}
		log.debug("finished writing entries")
		//db.commitTransaction()
	}

	def outputSqlFile(db: DB, file: String) {
		val writer = new PrintWriter(file)
		
		writer.println("DROP TABLE IF EXISTS aodb;")
        writer.println("CREATE TABLE aodb (lowid INT, highid INT, lowql INT, highql INT, name VARCHAR(150), icon INT);")
        
        class Item(val lowId: Int, val highId: Int, val lowQl: Int, val highQl: Int, val name: String, val icon: Int) {
        	def this(rs: ResultSet) = this(rs.getInt("lowid"), rs.getInt("highid"), rs.getInt("lowql"), rs.getInt("highql"), rs.getString("name"), rs.getInt("icon"))
        	
        	override def toString() = List(lowId, highId, lowQl, highQl, name, icon).toString()
        }

        val items = db.query("SELECT * FROM aodb ORDER BY name, lowql, lowid", new GenericRowMapper[Item])

        items foreach { item =>
            writer.println("INSERT INTO aodb VALUES (%d, %d, %d, %d, '%s', %d);".format(item.lowId, item.highId, item.lowQl, item.highQl, item.name.replace("'", "''").trim, item.icon))
        }
        writer.close()
	}

	def readEntriesFromFile(file: String): List[String] = {
		log.debug("reading entries from file: " + file)
		val source = Source.fromFile(file)
		val lines = source.getLines.toList map (_.trim) filter (_ != "")
		source.close()
		lines
	}
}