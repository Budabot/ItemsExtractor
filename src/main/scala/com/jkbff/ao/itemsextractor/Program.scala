package com.jkbff.ao.itemsextractor

import java.io.{File, RandomAccessFile}

import org.apache.log4j.Logger
import com.beust.jcommander.JCommander
import com.beust.jcommander.ParameterException
import com.jkbff.ao.itemsextractor.rdb.MultiRandomAccessFile
import com.jkbff.common.EnrichedString.stringToEnrichedString
import com.jkbff.common.Helper.using

object Program extends App {
	val log = Logger.getLogger(this.getClass())

	System.setProperty("line.separator", "\n")
	
	val commandLineArguments = new CommandLineArguments
	try {
		new JCommander(commandLineArguments, args.toArray: _*)
	} catch {
		case _: ParameterException =>
			val jcommander = new JCommander()
			jcommander.getObjects().add(new CommandLineArguments)
			jcommander.usage()
			sys.exit(-2)
	}
	
	try {
		val path = commandLineArguments.aoPath.replaceAll("\"", "").replaceAll("\\\\", "/").addEndingIfNeeded("/")
		
		using(new MultiRandomAccessFile(getDatabaseFiles(path))) { db =>
			val indexFile = new RandomAccessFile(path + "cd_image/data/db/ResourceDatabase.idx", "r")
			//new JsonDumpModule().execute(db, indexFile, path)
			new BudabotSqlDumpModule().execute(db, indexFile, path)
		}
	} catch {
		case e: Exception => log.error("", e)
	}

	def getDatabaseFiles(aoPath: String): Seq[String] = {
		new File(aoPath + "cd_image/data/db/").listFiles().collect {
			case file: File if file.isFile() && file.getName().startsWith("ResourceDatabase.dat") => file.getCanonicalPath()
		}
	}
}