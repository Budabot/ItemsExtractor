package com.jkbff.common

import java.sql.ResultSet

class ResultSetIterator(rs: ResultSet) extends Iterator[ResultSet] {
  override def hasNext() = rs.next
  override def next() = rs
}