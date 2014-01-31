package com.jkbff.ao.itemsextractor

import com.beust.jcommander.JCommander
import com.beust.jcommander.ParameterException

import com.jkbff.common.EnrichedString._

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
	
	new Runner(commandLineArguments.aoPath.replaceAll("\\\\", "/").addEndingIfNeeded("/")).run()
}