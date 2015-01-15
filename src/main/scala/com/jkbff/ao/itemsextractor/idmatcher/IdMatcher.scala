package com.jkbff.ao.itemsextractor.idmatcher

import java.io.PrintWriter
import java.sql.ResultSet
import scala.io.Source
import org.apache.commons.dbcp.BasicDataSource
import org.apache.log4j.Logger
import com.jkbff.ao.itemsextractor.rdb.RDBFunctions
import com.jkbff.ao.itemsextractor.rdb.RDBItem
import com.jkbff.ao.itemsextractor.rdb.constants.Attribute
import com.jkbff.common.DB
import com.jkbff.common.Helper._
import com.jkbff.ao.itemsextractor.rdb.constants.CanFlag

class IdMatcher {

	val log = Logger.getLogger(this.getClass())

	lazy val h2Ds = init(new BasicDataSource()) { ds =>
		ds.setDriverClassName("org.h2.Driver")
		//ds.setUrl("jdbc:h2:mem:db1;IGNORECASE=true")
		ds.setUrl("jdbc:h2:mem:db1")
		ds.setUsername("")
		ds.setPassword("")
	}

	def writeSqlFile(rdbItems: Seq[RDBItem], aoPath: String) {
		val entries = rdbItems.map { item =>
			val iconAttribute = item.attributes.getOrElse(Attribute.Icon, 0L)
			val qlAttribute = item.attributes.getOrElse(Attribute.Level, 0L)
			val itemTypeAttribute = item.attributes.getOrElse(Attribute.ItemClass, 0L)
				
			new Entry(item.id.toInt, qlAttribute.toInt, item.name, iconAttribute.toInt, RDBFunctions.getItemType(itemTypeAttribute))
		}
		
		val version = getVersion(aoPath)
		val outputdb = new DB(h2Ds)

		val elapsed = stopwatch {
			outputdb.update("DROP TABLE IF EXISTS entries")
			outputdb.update("DROP TABLE IF EXISTS aodb")
			outputdb.update("CREATE TABLE entries (aoid INT, ql INT, name TEXT, icon INT, itemtype TEXT, hash TEXT)")
			outputdb.update("CREATE TABLE aodb (lowid INT, highid INT, lowql INT, highql INT, name VARCHAR(150), icon INT)")

			writeEntriesToDb(outputdb, entries)

			outputdb.update("CREATE INDEX idx_name ON entries (name)")
			outputdb.update("CREATE INDEX idx_aoid ON entries (aoid)")

			processStaticList(outputdb, readEntriesFromFile("static_list.txt"))
			processDeleteList(outputdb, readEntriesFromFile("delete_list.txt"))
			processNameSeparations(outputdb, readEntriesFromFile("nameseparation_list.txt"))
			processRemaingingEntries(outputdb)
			
			val itemsDbFilename = "aodb" + version + ".sql"
			outputSqlFile(outputdb, itemsDbFilename)
			
			val weaponAttributesFilename = "weapon_attributes" + version + ".sql"
			outputWeaponAttributes(rdbItems, outputdb, weaponAttributesFilename)
		}
		log.info("Elapsed time: %ds".format(elapsed / 1000))
	}

	def processNameSeparations(db: DB, nameSeparationList: Seq[String]) {
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
				),
				{ rs => rs.getString("common_name") }
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
					{ rs => new Entry(rs) }
				)

				if (entries.size > 0) {
					pairEntries(db, entries)
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
			val matches = db.query("SELECT aoid, name FROM entries WHERE " + line, { rs => (rs.getInt("aoid"), rs.getString("name")) })
			matches foreach (x => log.debug("Deleting item " + x))
			db.update("DELETE FROM entries WHERE " + line)
		}
		//db.commitTransaction()
	}

	def processStaticList(db: DB, staticList: Seq[String]) {
		val list = staticList map (_.split(','))

		//db.startTransaction()
		list foreach { parms =>
			log.debug("Processing static entry: " + parms.mkString(","))
			val result = db.querySingle(
				"SELECT * FROM entries WHERE aoid = ?",
				Seq(
					parms(0)
				),
				{ rs => new Entry(rs) }
			)

			if (result.isDefined) {
				val low = new Entry(parms(0).toInt, parms(2).toInt, if (parms.length >= 6) parms(5) else result.get.name, if (parms.length >= 5) parms(4).toInt else result.get.iconId, result.get.itemType)
				val high = new Entry(parms(1).toInt, parms(3).toInt, "", 0, "")
				log.debug("Static entry match: " + low)
				addItem(db, low, high)
			} else {
				log.debug("ERROR-Could not find item id '" + parms(0) + "' at ql '" + parms(2) + "'")
			}
		}

		// delete name separation entries so they are not processed again
		list foreach { parms =>
			db.update(
				"DELETE FROM entries WHERE aoid = ?",
				Seq(
					parms(0)
				)
			)
			db.update(
				"DELETE FROM entries WHERE aoid = ?",
				Seq(
					parms(1)
				)
			)
		}

		//db.commitTransaction()
	}

	def processRemaingingEntries(db: DB) {
		//db.startTransaction()
		val mapper = { rs: ResultSet =>
			Map("name" -> rs.getString("name"), "itemtype" -> rs.getString("itemtype"))
		}
		val distinctNames = db.query("SELECT DISTINCT name, itemtype FROM entries ORDER BY name", mapper)

		distinctNames foreach { ht =>
			val entries = db.query(
				"SELECT * FROM entries WHERE name = ? AND itemtype = ? ORDER BY ql ASC",
				ht("name") :: ht("itemtype") :: Nil,
				{ rs => new Entry(rs) }
			)

			val sequential = !entries.zip(entries.drop(1)).exists{ x => x._1.ql >= x._2.ql }

			if (sequential) {
				log.debug("Sequential ql handling for: '" + ht("name") + "'")
				pairEntries(db, entries)
			} else {
				log.debug("AOID handling for: '" + ht("name") + "'")
				val entries2 = db.query(
					"SELECT * FROM entries WHERE name = ? AND itemtype = ? ORDER BY aoid ASC",
					ht("name") :: ht("itemtype") :: Nil,
					{ rs => new Entry(rs) }
				)

				val result = entries2.foldLeft(0, List[Entry]()) { (params, entry) =>
					val (currentQl, tempEntries) = params
					val newEntries =
						if (currentQl >= entry.ql) {
							log.debug("Processing temp entries")
							pairEntries(db, tempEntries.reverse)
							
							Nil
						} else {
							tempEntries
						}

					log.debug("Adding to temp entries: " + entry)
					(entry.ql, entry :: newEntries)
				}
				log.debug("Processing temp entries")
				pairEntries(db, result._2.reverse)
			}
		}
		//db.commitTransaction()
	}

	def pairEntries(db: DB, entries: Seq[Entry]) {
		if (entries.size == 0) {
			return
		}

		log.debug("Matching for: '" + entries(0).name + "' count: " + entries.size)
		// if there is only one item, match it to itself
		if (entries.size == 1) {
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

	def addEntryItems(db: DB, entries: Seq[Entry]) {
		entries.grouped(2).foreach { pair =>
			if (pair.size == 1) {
				// last one could be single
				addItem(db, pair(0), pair(0))
			} else if (pair(0).ql == pair(1).ql - 1) {
				// if pair entries have sequential qls, add them as individual items
				addItem(db, pair(0), pair(0))
				addItem(db, pair(1), pair(1))
			} else {
				// add pair entries as item
				addItem(db, pair(0), pair(1))
			}
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

	def writeEntriesToDb(db: DB, entries: Seq[Entry]) {
		//db.startTransaction()
		log.debug("writing %d entries".format(entries.size))
		entries foreach { entry =>
			db.update("INSERT INTO entries (aoid, ql, name, icon, itemtype) VALUES (?, ?, ?, ?, ?)",
				Seq(
					entry.id,
					entry.ql,
					entry.name.trim,
					entry.iconId,
					entry.itemType
				)
			)
		}
		log.debug("finished writing entries")
		//db.commitTransaction()
	}

	def outputSqlFile(db: DB, file: String) {
		log.debug("writing results to file: '%s'".format(file))
		
		using(new PrintWriter(file)) { writer =>
			writer.println("DROP TABLE IF EXISTS aodb;")
			writer.println("CREATE TABLE aodb (lowid INT, highid INT, lowql INT, highql INT, name VARCHAR(150), icon INT);")

			val items = db.query("SELECT * FROM aodb ORDER BY name, lowql, lowid", { rs => new Item(rs) })

			items foreach { item =>
				writer.println("INSERT INTO aodb VALUES (%d, %d, %d, %d, '%s', %d);".format(item.lowId, item.highId, item.lowQl, item.highQl, item.name.replace("'", "''"), item.icon))
			}
		}
	}
	
	def outputWeaponAttributes(rdbItems: Seq[RDBItem], db: DB, file: String) {
		val itemMap = rdbItems  map { x =>
			(x.id, x)
		} toMap
		
		val weapons = db.query("SELECT * FROM aodb ORDER BY name, lowql, lowid", { rs => new Item(rs) }).foldLeft(List[RDBItem]()) { (list, item) =>
			if (item.highId != item.lowId) {
				itemMap(item.lowId) :: itemMap(item.highId) :: list
			} else {
				itemMap(item.highId) :: list
			}
		} filter { x => 
			x.attributes.get(Attribute.ItemDelay).isDefined &&
			x.attributes.get(Attribute.RechargeDelay).isDefined
		}
		
		using(new PrintWriter(file)) { writer =>
			writer.println("DROP TABLE IF EXISTS weapon_attributes;")
			writer.println("CREATE TABLE weapon_attributes (id INT, attack_time INT, recharge_time INT, full_auto INT, burst INT, fling_shot TINYINT NOT NULL, fast_attack TINYINT NOT NULL, aimed_shot TINYINT NOT NULL);")
			
			weapons foreach { item =>
				val flags = item.attributes(Attribute.Can)
				
				writer.println("INSERT INTO weapon_attributes VALUES (%s, %s, %s, %s, %s, %s, %s, %s);".
						format(item.id,
								item.attributes(Attribute.ItemDelay),
								item.attributes(Attribute.RechargeDelay),
								item.attributes.getOrElse(Attribute.FullAutoRecharge, "null"),
								item.attributes.getOrElse(Attribute.BurstRecharge, "null"),
								(if (getFlag(CanFlag.FlingShot, flags)) "1" else "0"),
								(if (getFlag(CanFlag.FastAttack, flags)) "1" else "0"),
								(if (getFlag(CanFlag.AimedShot, flags)) "1" else "0")))
			}
		}
	}

	def readEntriesFromFile(file: String): List[String] = {
		log.debug("reading entries from file: " + file)
		using(Source.fromFile(file)) { source =>
			source.getLines.toList map (_.trim) filter (_ != "")
		}
	}
	
	def getVersion(aoPath: String): String = {
		val source = Source.fromFile(aoPath + "version.id")
		val pieces = source.mkString.trim.replace("_EP1", "").split("\\.").map(_.toInt)
		source.close()
		"%02d.%02d.%02d.%02d".format(pieces(0), pieces(1), pieces(2), 0)
	}
	
	def getFlag(flagId: Long, input: Long): Boolean = {
		return (flagId & input) == flagId
	}
}
