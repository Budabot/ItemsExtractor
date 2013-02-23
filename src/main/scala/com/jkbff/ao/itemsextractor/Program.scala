package com.jkbff.ao.itemsextractor
import java.io.RandomAccessFile
import com.jkbff.ao.tyrbot.jdbc.Helper
import org.apache.commons.dbcp.BasicDataSource
import scala.io.Source
import com.beust.jcommander.JCommander
import com.beust.jcommander.ParameterException

object Program extends App {
	val commandLineArguments = new CommandLineArguments
	try {
		new JCommander(commandLineArguments, args.toArray: _*)
	} catch {
		case e: ParameterException => {
			val jcommander = new JCommander()
			jcommander.getObjects().add(new CommandLineArguments)
			jcommander.usage()
			sys.exit(-2)
		}
	}
	
	val aoPath = commandLineArguments.aoPath
	
	val db = new MultiRandomAccessFile(
		Seq(
			aoPath + "cd_image/data/db/ResourceDatabase.dat",
			aoPath + "cd_image/data/db/ResourceDatabase.dat.001",
			aoPath + "cd_image/data/db/ResourceDatabase.dat.002"
		)
	)
	val in = new RandomAccessFile(aoPath + "cd_image/data/db/ResourceDatabase.idx", "r")

	val AODB_ITEM_TYPE = 0x000f4254
	val AODB_TYP_NANO = 0x000fde85
	
	val itemExtractor = new ItemExtractor
	val indexReader = new RDBIndexReader(in)
	val idMatcher = new IdMatcher

	idMatcher.writeSqlFile(getEntries, "aodb" + getVersion(aoPath) + ".sql")
	
	def getEntries(): List[Entry] = {
		val nullAttribute = new RDBAttribute(0, 0)
		
		indexReader.resourceTypeMap(AODB_ITEM_TYPE) map { x =>
			val item = itemExtractor.readItem(db, x)
			val iconAttribute = item.attributes.find(_.id == 79)
			val qlAttribute = item.attributes.find(_.id == 54)
			val itemTypeAttribute = item.attributes.find(_.id == 76)
			
			new Entry(item.id.toInt, qlAttribute.getOrElse(nullAttribute).value.toInt, item.name, iconAttribute.getOrElse(nullAttribute).value.toInt, RDBFunctions.getItemType(itemTypeAttribute.getOrElse(nullAttribute).value))
		}
	}
	
	def getSingleItem(id: Long): RDBItem = {
		val record = indexReader.resourceTypeMap(AODB_ITEM_TYPE).find(_.resourceId == id).get
		itemExtractor.readItem(db, record)
	}
	
	def getVersion(aoPath: String): String = {
		val source = Source.fromFile(aoPath + "version.id")
		val pieces = source.mkString.trim.replace("_EP1", "").split("\\.").map(_.toInt)
		source.close()
		"%02d.%02d.%02d.%02d".format(pieces(0), pieces(1), pieces(2), 0)
	}
}