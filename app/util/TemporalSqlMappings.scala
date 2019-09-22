package util

import java.sql.Date
import java.sql.Time
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

import slick.jdbc.H2Profile.api._


trait TemporalSqlMappings {

  // TODO: Find a better solution for this.
  protected val H2_INCLUDING_ISO_8601 = DateTimeFormatter.ofPattern("yyyy-MM-dd[ ]['T']HH:mm:ss" +
    "[.SSSSSSSSS]" +
    "[.SSSSSSSS]" +
    "[.SSSSSSS]" +
    "[.SSSSSS]" +
    "[.SSSSS]" +
    "[.SSSS]" +
    "[.SSS]" +
    "[.SS]" +
    "[.S]" +
    "[XXX][X]")

  implicit def localDate = MappedColumnType.base[LocalDate, Date](
    localDate => Date.valueOf(localDate),
    date => date.toLocalDate
  )

  implicit def localTime = MappedColumnType.base[LocalTime, Time](
    localTime => Time.valueOf(localTime),
    time => time.toLocalTime
  )

  implicit def offsetDateTime = MappedColumnType.base[OffsetDateTime, String](
    _.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
    OffsetDateTime.parse(_, this.H2_INCLUDING_ISO_8601)
  )
  // XXX: This might be cleaner/saver than String mapping (but doesn't work)
  //  implicit def offsetDateTime = MappedJdbcType.base[OffsetDateTime, TimestampWithTimeZone](
  //    o => {
  //      val date = DateTimeUtils.dateValue(o.getYear, o.getMonthValue, o.getDayOfMonth)
  //      val time = (o.getHour * 3600 + o.getMinute * 60 + o.getSecond) * 1000 * 1000 * 1000 + o.getNano
  //      val offset = o.getOffset.getTotalSeconds / 60
  //      new TimestampWithTimeZone(date, time, offset.asInstanceOf[Short])
  //    },
  //    t => {
  //      val localDate = LocalDate.of(t.getYear, t.getMonth, t.getDay)
  //      val localTime = DateTimeUtils.convertNanoToTime(t.getNanosSinceMidnight).toLocalTime
  //      val zoneOffset = ZoneOffset.ofTotalSeconds(t.getTimeZoneOffsetMins * 60)
  //      OffsetDateTime.of(LocalDateTime.of(localDate, localTime), zoneOffset)
  //    }
  //  )
}
