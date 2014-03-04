package com.jkbff.common

import java.sql.PreparedStatement
import java.sql.ResultSet
import com.jkbff.common.Helper.using
import javax.sql.DataSource

class DB(ds: DataSource) {
	val connection = ds.getConnection()

	def query[T](sql: String, params: Seq[Any], rowMapper: ResultSet => T): List[T] = {
		using(connection.prepareStatement(sql)) { stmt =>
			setParams(stmt, params)
			using(stmt.executeQuery()) { rs =>
				new ResultSetIterator(rs).map(rowMapper).toList
			}
		}
	}
	
	def querySingle[T](sql: String, params: Seq[Any], rowMapper: ResultSet => T): Option[T] = {
		using(connection.prepareStatement(sql)) { stmt =>
			setParams(stmt, params)
			using(stmt.executeQuery()) { rs =>
				if (rs.next()) {
					Some(rowMapper(rs))
				} else {
					None
				}
			}
		}
	}
	
	def query[T](sql: String, rowMapper: ResultSet => T): List[T] = {
		query(sql, Seq(), rowMapper)
	}
	
	def setParams(stmt: PreparedStatement, params: Seq[Any]): Unit = {
		params.foldLeft(1) { (index, param) =>
			stmt.setObject(index, param)
			index + 1
		}
	}
	
	def update(sql: String, params: Seq[Any]): Int = {
		using(connection.prepareStatement(sql)) { stmt =>
			setParams(stmt, params)
			stmt.executeUpdate()
		}
	}
	
	def update(sql: String): Int = {
		update(sql, Seq())
	}
	
	def transaction[T]()(op: => T): T = {
		try {
			connection.setAutoCommit(false)
			val result = op
			connection.commit()
			result
		} catch {
			case e: Exception =>
				connection.rollback()
				throw e
		} finally {
			connection.setAutoCommit(true)
		}
	}
	
	def startTransaction() {
		connection.setAutoCommit(false)
	}
	
	def commitTransaction() {
		connection.commit()
		connection.setAutoCommit(true)
	}
	
	def rollbackTransaction() {
		connection.rollback()
	}
	
	def close(): Unit = {
		connection.close()
	}
	
	def getColumnLabels(rs: ResultSet): List[String] = {
		val meta = rs.getMetaData()
		(1 to meta.getColumnCount()).foldLeft(List[String]()) { (params, i) =>
			meta.getColumnLabel(i) :: params
		}.reverse
	}
}