package com.jkbff.ao.itemsextractor

import com.beust.jcommander.Parameter

class CommandLineArguments {
	@Parameter(names = Array("-d", "--dir"), description = "The AO directory", required = true)
	var aoPath: String = _
}