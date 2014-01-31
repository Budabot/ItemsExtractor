package com.jkbff.common

import java.lang.reflect.Method
import java.sql.Connection
import java.sql.Statement
import scala.Array.canBuildFrom

object Helper {
	def using[T <: { def close(): Unit }, U](resource: T)(op: T => U): U = {
		try {
			op(resource)
		} finally {
			resource.close()
		}
	}

	def transaction(connection: Connection)(op: Connection => Unit) {
		using(connection) { connection =>
			try {
				connection.setAutoCommit(false)
				op(connection)
				connection.commit()
			} catch {
				case e: Exception =>
					connection.rollback()
					throw e
			} finally {
				connection.setAutoCommit(true)
			}
		}
	}

	def execute(connection: Connection)(op: Statement => Unit) {
		using(connection) { connection =>
			using(connection.createStatement) { stmt =>
				op(stmt)
			}
		}
	}

	def sqlSearchBuilder(column: String, searchTerms: Array[String]): (String, Map[String, String], Int) = {
		searchTerms.foldLeft(("", Map[String, String](), 0)) { (result, searchTerm) =>
			val name = column + result._3
			(result._1 + " AND " + column + " LIKE :" + name, result._2 + (name -> ("%" + searchTerm + "%")), result._3 + 1)
		}
	}

	def stopwatch[T](op: => T): Long = {
		val startTime = System.currentTimeMillis
		op
		val endTime = System.currentTimeMillis
		(endTime - startTime)
	}

	/**
	 * get all methods on obj that have the HandleCommand annotation
	 */
	def getMethods[T <: java.lang.annotation.Annotation](obj: Any, clazz: Class[T]): Array[(T, Method)] = {
		obj.getClass().getMethods().map{ method => (method.getAnnotation(clazz), method) }.filter(x => x._1 != null)
	}

	def hasAnnotation[T <: java.lang.annotation.Annotation](method: Method, clazz: Class[T]): Boolean = {
		method.getAnnotation(clazz) != null
	}
}