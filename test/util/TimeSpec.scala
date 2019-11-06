package util

import java.time.ZoneId
import java.time.format.DateTimeFormatter

import org.joda.time.DateTimeZone
import org.scalatestplus.play.PlaySpec

class TimeSpec extends PlaySpec {
  val ISO_DATE_TIME_PATTERN = "yyyy-MM-dd'T'HH:mm:ss"

  "Time" should {
    "convert LocalDateTimes from Joda to Java" in {
      val before = new org.joda.time.LocalDateTime(2019, 11, 2, 18, 1, 2)

      val after = Time.toJava(before)
      after.getYear mustEqual before.getYear
      after.getMonthValue mustEqual before.getMonthOfYear
      after.getDayOfMonth mustEqual before.getDayOfMonth
      after.getHour mustEqual before.getHourOfDay
      after.getMinute mustEqual before.getMinuteOfHour
      after.getSecond mustEqual before.getSecondOfMinute

      after.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) mustEqual before.toString(ISO_DATE_TIME_PATTERN)
    }

    "convert Instants from Joda to Java" in {
      val before = new org.joda.time.Instant(123456789000L)
      val after = Time.toJava(before)

      val zoneId = "Africa/Windhoek"
      val zone = ZoneId.of(zoneId)
      val beforeZoned = before.toDateTime(DateTimeZone.forID(zoneId))
      val afterZoned = after.atZone(zone)

      after.toEpochMilli mustEqual beforeZoned.getMillis

      afterZoned.getYear mustEqual beforeZoned.toDateTime.getYear
      afterZoned.getMonthValue mustEqual beforeZoned.toDateTime.getMonthOfYear
      afterZoned.getDayOfMonth mustEqual beforeZoned.toDateTime.getDayOfMonth
      afterZoned.getHour mustEqual beforeZoned.toDateTime.getHourOfDay
      afterZoned.getMinute mustEqual beforeZoned.toDateTime.getMinuteOfHour
      afterZoned.getSecond mustEqual beforeZoned.toDateTime.getSecondOfMinute

      afterZoned.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) mustEqual beforeZoned.toString(ISO_DATE_TIME_PATTERN)
    }
  }
}
