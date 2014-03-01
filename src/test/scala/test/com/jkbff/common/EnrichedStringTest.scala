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

	test("addEndingIfNeeded()") {
		assert(new EnrichedString.EnrichedString("TEST").addEndingIfNeeded("/").equals("TEST/"))
		assert(new EnrichedString.EnrichedString("TEST/").addEndingIfNeeded("/").equals("TEST/"))
	}
}