package util

import java.time.Instant
import java.time.LocalDateTime

object Time {

  def toJava(date: org.joda.time.LocalDateTime): LocalDateTime =
    LocalDateTime.of(
      date.getYear,
      date.getMonthOfYear,
      date.getDayOfMonth,
      date.getHourOfDay,
      date.getMinuteOfHour,
      date.getSecondOfMinute
    )

  def toJava(instant: org.joda.time.Instant): Instant = Instant.ofEpochMilli(instant.getMillis)
}
