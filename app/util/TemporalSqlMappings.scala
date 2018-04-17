package util

import java.sql.Date
import java.sql.Time
import java.time.LocalDate
import java.time.LocalTime

import slick.jdbc.H2Profile.api._

trait TemporalSqlMappings {

  implicit def localDate = MappedColumnType.base[LocalDate, Date](
    localDate => Date.valueOf(localDate),
    date => date.toLocalDate
  )

  implicit def localTime = MappedColumnType.base[LocalTime, Time](
    localTime => Time.valueOf(localTime),
    time => time.toLocalTime
  )
}
