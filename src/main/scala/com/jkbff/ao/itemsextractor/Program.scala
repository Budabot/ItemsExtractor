package com.jkbff.ao.itemsextractor

import org.apache.log4j.Logger

import com.beust.jcommander.JCommander
import com.beust.jcommander.ParameterException
import com.jkbff.common.EnrichedString.stringToEnrichedString

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
		
		new Runner(path).run()
	} catch {
		case e: Exception => log.error("", e)
	}
}