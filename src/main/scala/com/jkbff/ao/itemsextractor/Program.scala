package com.jkbff.ao.itemsextractor

import java.io.{File, RandomAccessFile}

import org.slf4j.LoggerFactory
import com.beust.jcommander.JCommander
import com.beust.jcommander.ParameterException
import com.jkbff.ao.itemsextractor.rdb.MultiRandomAccessFile
import com.jkbff.common.EnrichedString.stringToEnrichedString
import com.jkbff.common.Helper.using

object Program extends App {
	val log = LoggerFactory.getLogger(this.getClass())

	System.setProperty("line.separator", "\n")
	
	val commandLineArguments = new CommandLineArguments
	try {
		new JCommander(commandLineArguments, args.toArray: _*)
	} catch {
		case e: ParameterException =>
			log.error("Failed to parse command line arguments", e)
			val jcommander = new JCommander()
			jcommander.getObjects().add(new CommandLineArguments)
			jcommander.usage()
			sys.exit(-2)
	}
	
	try {
		val path = commandLineArguments.aoPath.replaceAll("\"", "").replaceAll("\\\\", "/").addEndingIfNeeded("/")
		log.info(s"Starting ItemsExtractor with AO path: $path")
		val dbFiles = getDatabaseFiles(path)
		log.info(s"Found ${dbFiles.size} database file(s).")
		
		using(new MultiRandomAccessFile(dbFiles)) { db =>
			val indexFile = new RandomAccessFile(path + "cd_image/data/db/ResourceDatabase.idx", "r")
			//new JsonDumpModule().execute(db, indexFile, path)
			new BudabotSqlDumpModule().execute(db, indexFile, path)
		}
	} catch {
		case e: Exception => log.error("Error running ItemsExtractor", e)
	}

	def getDatabaseFiles(aoPath: String): Seq[String] = {
		val dbDir = new File(aoPath + "cd_image/data/db/")
		Option(dbDir.listFiles()).map(_.collect {
			case file: File if file.isFile() && file.getName().startsWith("ResourceDatabase.dat") => file.getCanonicalPath()
		}.sorted.toSeq).getOrElse(Seq.empty)
	}
}