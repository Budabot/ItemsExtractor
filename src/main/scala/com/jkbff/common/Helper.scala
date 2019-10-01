package com.jkbff.common

import java.lang.reflect.Method
import javax.sql.DataSource

import org.apache.commons.dbcp.BasicDataSource

import scala.Array.canBuildFrom

object Helper {
  def using[T <: { def close(): Any }, U](resource: T, message: String)(op: T => U): U = {
    try {
      using(resource)(op)
    } catch {
      case e: Exception =>
        throw new RuntimeException(message, e)
    }
  }

  def using[T <: { def close(): Any }, U](resource: T)(op: T => U): U = {
    try {
      op(resource)
    } finally {
      resource.close()
    }
  }

  def init[T](resource: T)(op: T => Any): T = {
    op(resource)
    resource
  }

  def sqlSearchBuilder(column: String, searchTerms: Array[String]): (String, Seq[String]) = {
    searchTerms.foldLeft(("", Seq[String]())) { case ((acc, params), searchTerm) =>
      (acc + " AND " + column + " LIKE ?", params :+ ("%" + searchTerm + "%"))
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
    println(s"${label}: ${elapsed}ms")
  }

  /**
   * get all methods on obj that have the specified annotation
   */
  def getMethods[T <: java.lang.annotation.Annotation](obj: Any, clazz: Class[T]): Array[(T, Method)] = {
    obj.getClass().getMethods().
      map{ method =>
        (method.getAnnotation(clazz), method)
      }.
      filter{ case (annotation, _) =>
        annotation != null
      }
  }

  def hasAnnotation[T <: java.lang.annotation.Annotation](method: Method, clazz: Class[T]): Boolean = {
    method.getAnnotation(clazz) != null
  }

  implicit def anyWithPipe[A](a: A) = new {
    def |>[B](f: A => B): B = f(a)
  }

  def getDataSource(driver: String, username: String, password: String, connectionString: String): DataSource = {
    init(new BasicDataSource()) { ds =>
      ds.setDriverClassName(driver)
      ds.setUrl(connectionString)
      ds.setUsername(username)
      ds.setPassword(password)
    }
  }
}