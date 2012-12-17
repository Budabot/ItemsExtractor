package com.jkbff.ao.itemsextractor

import java.sql.PreparedStatement
import java.sql.ResultSet

import org.springframework.jdbc.core.RowMapper

import com.jkbff.ao.tyrbot.jdbc.Helper.using

import javax.sql.DataSource

class DB(ds: DataSource) {
	val connection = ds.getConnection()

	def query[T](sql: String, params: Seq[Any], rowMapper: RowMapper[T]): List[T] = {
		using(connection.prepareStatement(sql)) { stmt =>
			using(executeQuery(stmt, params)) { rs =>
				var results = List[T]()
				var j = 0
				while (rs.next()) {
					results = rowMapper.mapRow(rs, j) :: results
					j += 1
				}
				results.reverse
			}
		}
	}
	
	private def executeQuery(stmt: PreparedStatement, params: Seq[Any]): ResultSet = {
		var i = 1
		params foreach { param =>
			stmt.setObject(i, param)
			i += 1
		}
		
		stmt.executeQuery()
	}
	
	def queryForObject[T](sql: String, params: Seq[Any], rowMapper: RowMapper[T]) = {
		using(connection.prepareStatement(sql)) { stmt =>
			using(executeQuery(stmt, params)) { rs =>
				if (rs.next()) {
					Some(rowMapper.mapRow(rs, 1))
				} else {
					None
				}
			}
		}
	}
	
	def query[T](sql: String, rowMapper: RowMapper[T]): List[T] = {
		query(sql, Seq(), rowMapper)
	}
	
	def update(sql: String, params: Seq[Any]): Int = {
		using(connection.prepareStatement(sql)) { stmt =>
			var i = 1
			params foreach { param =>
				stmt.setObject(i, param)
				i += 1
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
}