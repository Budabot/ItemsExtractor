package test.com.jkbff.common

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import com.jkbff.common.EnrichedString
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class EnrichedStringTest extends FunSuite {
	test("EnrichedString.ucfirst should capitalize the first letter and lower-case all the rest") {
		assert(new EnrichedString.EnrichedString("tEst").ucfirst.equals("Test"))
		assert(new EnrichedString.EnrichedString("test").ucfirst.equals("Test"))
		assert(new EnrichedString.EnrichedString("TEST").ucfirst.equals("Test"))
	}
	
	test("EnrichedString.explode should correctly split strings when separator does not exist in string") {
		assert(new EnrichedString.EnrichedString("hi").explode(" ") == "hi" :: Nil)
	}
	
	test("EnrichedString.explode should correctly split strings when separator exists in string") {
		assert(new EnrichedString.EnrichedString("hi how are you").explode(" ") == "hi" :: "how" :: "are" :: "you" :: Nil)
	}
	
	test("EnrichedString.explode should correctly split strings with imposed limit") {
		assert(new EnrichedString.EnrichedString("hi how are you").explode(" ", 1) == "hi how are you" :: Nil)
		assert(new EnrichedString.EnrichedString("hi how are you").explode(" ", 2) == "hi" :: "how are you" :: Nil)
		assert(new EnrichedString.EnrichedString("hi how are you").explode(" ", 4) == "hi" :: "how" :: "are" :: "you" :: Nil)
		assert(new EnrichedString.EnrichedString("hi how are you").explode(" ", 6) == "hi" :: "how" :: "are" :: "you" :: Nil)
	}
}