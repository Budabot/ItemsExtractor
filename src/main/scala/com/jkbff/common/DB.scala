package com.jkbff.common

import java.sql.PreparedStatement
import java.sql.ResultSet
import com.jkbff.common.Helper.using
import javax.sql.DataSource

class DB(ds: DataSource) {
	val connection = ds.getConnection()

	def query[T](sql: String, params: Seq[Any], rowMapper: ResultSet => T): List[T] = {
		using(connection.prepareStatement(sql)) { stmt =>
			using(executeQuery(stmt, params)) { rs =>
				new ResultSetIterator(rs).map(rowMapper).toList
			}
		}
	}
	
	private def executeQuery(stmt: PreparedStatement, params: Seq[Any]): ResultSet = {
		params.foldLeft(1) { (index, param) =>
			stmt.setObject(index, param)
			index + 1
		}
		
		stmt.executeQuery()
	}
	
	def queryForObject[T](sql: String, params: Seq[Any], rowMapper: ResultSet => T): Option[T] = {
		using(connection.prepareStatement(sql)) { stmt =>
			using(executeQuery(stmt, params)) { rs =>
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
	
	def update(sql: String, params: Seq[Any]): Int = {
		using(connection.prepareStatement(sql)) { stmt =>
			params.foldLeft(1) { (index, param) =>
				stmt.setObject(index, param)
				index + 1
			}
			
			stmt.executeUpdate()
		}
	}
	
	def update(sql: String): Int = {
		update(sql, Seq())
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
}