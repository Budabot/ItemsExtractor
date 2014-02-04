package com.jkbff.common

import java.lang.reflect.Method
import java.sql.Connection

import scala.Array.canBuildFrom

import org.apache.commons.dbcp.BasicDataSource

import javax.sql.DataSource

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

	def sqlSearchBuilder(column: String, searchTerms: Array[String]): (String, Seq[String]) = {
		searchTerms.foldLeft(("", Seq[String]())) { (result, searchTerm) =>
			(result._1 + " AND " + column + " LIKE ?", result._2 :+ ("%" + searchTerm + "%"))
		}
	}

	def stopwatch(op: => Any): Long = {
		val startTime = System.currentTimeMillis
		op
		val endTime = System.currentTimeMillis
		(endTime - startTime)
	}
	
	def stopwatch(label: String)(op: => Any): Unit = {
		val elapsed = stopwatch(op)
		println(label, elapsed)
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
	
	implicit def anyWithPipe[A](a: A) = new {
		def |>[B](f: A => B): B = f(a)
	}
	
	def getDataSource(driver: String, username: String, password: String, connectionString: String): DataSource = {
		val ds = new BasicDataSource()
		ds.setDriverClassName(driver)
		ds.setUrl(connectionString)
		ds.setUsername(username)
		ds.setPassword(password)
		ds
	}
}