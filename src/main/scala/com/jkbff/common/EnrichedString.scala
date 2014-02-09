package com.jkbff.common

object EnrichedString {
    implicit def stringToEnrichedString(f: String) = new EnrichedString(f)
    
    class EnrichedString (f: String) {
        def ucfirst(): String = f.toLowerCase().capitalize
        
        def explode(separator: String = " ", limit: Int = -1, input: String = f): List[String] = {
        	input.indexOf(separator) match {
        		case index: Int if index == -1 || limit == 1 => List(input)
        		case index: Int => input.take(index) :: explode(separator, limit - 1, input.takeRight(input.length - index - separator.length()))
        	}
        }
        
        def addEndingIfNeeded(ending: String): String = {
        	if (f.endsWith(ending)) {
				f
			} else {
				f + ending
			}
        }
    }
}
