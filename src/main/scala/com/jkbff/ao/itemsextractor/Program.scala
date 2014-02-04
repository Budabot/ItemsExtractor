package com.jkbff.ao.itemsextractor

import com.beust.jcommander.JCommander
import com.beust.jcommander.ParameterException
import com.jkbff.common.EnrichedString._
import org.apache.log4j.Logger

object Program extends App {
	val log = Logger.getLogger(this.getClass())
	
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
	
	try {
		new Runner(commandLineArguments.aoPath.replaceAll("\"", "").replaceAll("\\\\", "/").addEndingIfNeeded("/")).run()
	} catch {
		case e: Exception => log.error("", e)
	}
}