package com.jkbff.ao.itemsextractor.idmatcher

import java.io.PrintWriter
import java.sql.ResultSet

import scala.io.Source
import org.apache.log4j.Logger
import com.jkbff.ao.itemsextractor.rdb.RDBItem
import com.jkbff.ao.itemsextractor.rdb.constants._
import com.jkbff.common.{DB, Helper}
import com.jkbff.common.Helper._
import java.nio.file.{Files, Paths}

import javax.sql.DataSource

import scala.annotation.tailrec

class IdMatcher {

	val log: Logger = Logger.getLogger(this.getClass)

	lazy val h2Ds: DataSource = Helper.getDataSource("org.h2.Driver", "", "", "jdbc:h2:mem:db1")

	def writeSqlFile(rdbItems: Seq[RDBItem], rdbNanos: Seq[RDBItem], aoPath: String) {
		val entries = rdbItems.map { item =>
			val iconAttribute = item.attributes.getOrElse(Attribute.Icon, 0L)
			val qlAttribute = item.attributes.getOrElse(Attribute.Level, 0L)
			val itemTypeAttribute = item.attributes.getOrElse(Attribute.ItemClass, 0L)
				
			new Entry(item.id.toInt, qlAttribute.toInt, item.name, iconAttribute.toInt, ItemType.getType(itemTypeAttribute).get)
		}
		
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

			val itemsDbFilename = getNewFileName(aoPath, "aodb{version}.sql")
			outputSqlFile(outputdb, itemsDbFilename)

			val itemsMap: Map[Long, RDBItem] = rdbItems.map(x => (x.id, x)).toMap
			val nanosMap: Map[Long, RDBItem] = rdbNanos.map(x => (x.id, x)).toMap

			val weaponAttributesFilename = getNewFileName(aoPath, "weapon_attributes{version}.sql")
			outputWeaponAttributes(itemsMap, outputdb, weaponAttributesFilename)

			val buffsFilename = getNewFileName(aoPath, "item_buffs{version}.sql")
			val itemTypesFilename = getNewFileName(aoPath, "item_types{version}.sql")
			outputBuffInfo(itemsMap, nanosMap, outputdb, buffsFilename, itemTypesFilename)
		}
		log.info("Elapsed time: %ds".format(elapsed / 1000))
	}

	@tailrec
	private def getNewFileName(aoPath: String, filename: String, buildNumber: Int = 0): String = {
		val version = getVersion(aoPath, buildNumber)
		val newFilename = filename.replaceAll("\\{version\\}", version)
		if (Files.exists(Paths.get(newFilename))) {
			getNewFileName(aoPath, filename, buildNumber + 1)
		} else {
			newFilename
		}
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

				if (entries.nonEmpty) {
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
			val matches = db.query("SELECT aoid, name, ql FROM entries WHERE " + line, { rs => (rs.getInt("aoid"), rs.getString("name"), rs.getInt("ql")) })
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
		if (entries.isEmpty) {
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
				tempEntries = entries(i + 1) :: entries(i) :: tempEntries
				i += 2
			} else {
				log.debug("Splitting QLs for: '" + entries(i).ql + "'")
				val entry = new Entry(entries(i).id, entries(i).ql - 1, entries(i).name, entries(i).iconId, entries(i).itemType)
				tempEntries = entries(i) :: entry :: tempEntries
				i += 1
			}
		}
		if (i < entries.size) {
			tempEntries = entries.last :: tempEntries
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
			} else if (pair(0).name != pair(1).name) {
				// split pairs when name does not match
				addItem(db, pair(0), new Entry(pair(1).id, pair(1).ql - 1, pair(0).name, pair(0).iconId, pair(0).itemType))
				addItem(db, pair(1), pair(1))
			} else {
				// add pair entries as single item
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
			writer.println("CREATE INDEX idx_highid ON aodb(highid);")
		}
	}
	
	def outputWeaponAttributes(rdbMap: Map[Long, RDBItem], db: DB, file: String) {
		log.debug("writing weapon attributes to file: '%s'".format(file))

		val weapons = db.query("SELECT * FROM aodb ORDER BY name, lowql, lowid", { rs => new Item(rs) }).foldLeft(List[RDBItem]()) { (list, item) =>
			if (item.highId != item.lowId) {
				rdbMap(item.lowId) :: rdbMap(item.highId) :: list
			} else {
				rdbMap(item.highId) :: list
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

	def outputBuffInfo(itemsMap: Map[Long, RDBItem], nanosMap: Map[Long, RDBItem], db: DB, buffsFile: String, itemTypeFile: String): Unit = {
		log.debug("writing buff attributes to file: '%s'".format(buffsFile))
		log.debug("writing itemTypes to file: '%s'".format(itemTypeFile))

		val itemIds = db.query("SELECT lowid AS itemid FROM aodb UNION SELECT highid AS itemid FROM aodb ORDER BY itemid", { rs => rs.getInt("itemid") })

		val weaponGroups = Seq(
			(Seq(WeaponSlot.UTIL1, WeaponSlot.UTIL2, WeaponSlot.UTIL3), "Util"),
			(Seq(WeaponSlot.HUD1, WeaponSlot.HUD2, WeaponSlot.HUD3), "Hud"),
			(Seq(WeaponSlot.BELT, WeaponSlot.DECK1, WeaponSlot.DECK2, WeaponSlot.DECK3, WeaponSlot.DECK4, WeaponSlot.DECK5, WeaponSlot.DECK6), "Deck"),
			(Seq(WeaponSlot.LEFT_HAND, WeaponSlot.RIGHT_HAND), "Weapon"))

		val armorGroups = Seq(
			(Seq(ArmorSlot.NECK), "Neck"),
			(Seq(ArmorSlot.HEAD), "Head"),
			(Seq(ArmorSlot.BACK), "Back"),
			(Seq(ArmorSlot.CHEST), "Chest"),
			(Seq(ArmorSlot.HANDS), "Hands"),
			(Seq(ArmorSlot.LEGS), "Legs"),
			(Seq(ArmorSlot.FEET), "Feet"),
			(Seq(ArmorSlot.RIGHT_SHOULDER, ArmorSlot.LEFT_SHOULDER), "Shoulders"),
			(Seq(ArmorSlot.RIGHT_ARM, ArmorSlot.LEFT_ARM), "Arms"),
			(Seq(ArmorSlot.RIGHT_WRIST, ArmorSlot.LEFT_WRIST), "Wrists"),
			(Seq(ArmorSlot.RIGHT_FINGER, ArmorSlot.LEFT_FINGER), "Fingers"))

		using(new PrintWriter(itemTypeFile)) { itemTypesWriter =>
			itemTypesWriter.println("DROP TABLE IF EXISTS item_types;")
			itemTypesWriter.println("CREATE TABLE item_types (item_id INT, item_type VARCHAR(50));")

			using(new PrintWriter(buffsFile)) { buffsWriter =>
				buffsWriter.println("DROP TABLE IF EXISTS item_buffs;")
				buffsWriter.println("CREATE TABLE item_buffs (item_id INT, attribute_id INT, amount INT);")

				itemIds.foreach { itemId =>
					val rdbItem = itemsMap(itemId)
					val itemType = getItemType(rdbItem)
					if (itemType != "Spirit" && itemType != "Implant" && itemType != "Misc" && itemType != "Unknown") {
						if (writeBuffs(rdbItem, rdbItem.id, EventType.OnWear, buffsWriter)) {
							val subItemTypes = itemType match {
								case "Armor" =>
									getSlots(armorGroups, rdbItem)
								case "Weapon" =>
									getSlots(weaponGroups, rdbItem)
							}
							subItemTypes.foreach(subItemType => itemTypesWriter.println(s"INSERT INTO item_types (item_id, item_type) VALUES (${rdbItem.id}, '$subItemType');"))
						}
					}

					val nanoBuffs: Seq[RDBItem] = rdbItem.events
						.filter(_.eventType == EventType.OnUse)
						.flatMap(_.functions.filter(_.functionNum == 53019L))
						.map(_.params(0).toInt)
						.flatMap(nanoId => nanosMap.get(nanoId))

					val hasBuffs = nanoBuffs.map { nanoItem =>
						if (writeBuffs(nanoItem, rdbItem.id, EventType.OnUse, buffsWriter)) {
							log.debug(s"Item ${rdbItem.name} uploads nano ${nanoItem.name}")
							true
						} else {
							false
						}
					}.exists(p => p)

					if (hasBuffs) {
						itemTypesWriter.println(s"INSERT INTO item_types (item_id, item_type) VALUES (${rdbItem.id}, 'Nanoprogram');")
					}
				}
				buffsWriter.println("CREATE INDEX idx_item_id ON item_buffs(item_id);")
			}
			itemTypesWriter.println("CREATE INDEX idx_item_type ON item_types(item_type);")
		}
	}

	def writeBuffs(rdbItem: RDBItem, itemId: Long, eventType: Long, writer: PrintWriter): Boolean = {
		val buffs = getBuffs(rdbItem, eventType)
		buffs.foreach(buff => writer.println(s"INSERT INTO item_buffs (item_id, attribute_id, amount) VALUES ($itemId, ${buff._1}, ${buff._2});"))
		buffs.nonEmpty
	}

	def getItemType(item: RDBItem): String = {
		item.attributes.find(_._1 == Attribute.ItemClass).map(x => ItemType.getType(x._2).getOrElse("Armor")).getOrElse("Unknown")
	}

	def getSlots(groups: Seq[(Seq[Int], String)], item: RDBItem): Seq[String] = {
		val result = item.attributes.find(_._1 == Attribute.Placement).map { case (_, placement) =>
			groups.flatMap{ case (bitMasks, label) =>
				if (testBitMasks(bitMasks, placement.toInt)) {
					Some(label)
				} else {
					None
				}
			}
		}.getOrElse(Seq())

		if (result.isEmpty) {
			Seq("Unknown")
		} else {
			result
		}
	}

	def testBitMasks(bitMasks: Seq[Int], test: Int): Boolean = {
		bitMasks.exists(x => (x & test) == x)
	}

	def getBuffs(item: RDBItem, eventType: Long): Seq[(Int, Int)] = {
		item.events
			.filter(_.eventType == eventType)
			.flatMap(_.functions.filter(_.functionNum == 53045L))
			.map(_.params)
	  	.map( params => (params(0).toInt, params(1).toInt))
	}

	def readEntriesFromFile(file: String): List[String] = {
		log.debug("reading entries from file: " + file)
		using(Source.fromInputStream(getClass.getClassLoader.getResourceAsStream(file))) { source =>
			source.getLines().toList map (_.trim) filter (_ != "")
		}
	}
	
	def getVersion(aoPath: String, buildNumber: Int): String = {
		val source = Source.fromFile(aoPath + "version.id")
		val pieces = source.mkString.trim.replace("_EP1", "").split("\\.").map(_.toInt)
		source.close()
		"%02d.%02d.%02d.%02d".format(pieces(0), pieces(1), pieces(2), buildNumber)
	}
	
	def getFlag(flagId: Long, input: Long): Boolean = {
		(flagId & input) == flagId
	}
}
