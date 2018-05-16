package util.form

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

import play.api.data.FormError
import play.api.data.Forms.of
import play.api.data.Mapping
import play.api.data.format.Formats
import play.api.data.format.Formatter

object FormOffsetDateTime {

  def offsetDateTime: Mapping[OffsetDateTime] = of(offsetDateTimeFormat)

  private def offsetDateTimeFormat: Formatter[OffsetDateTime] = new Formatter[OffsetDateTime] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], OffsetDateTime] = {
      Formats.stringFormat.bind(key, data).right flatMap { s =>
        scala.util.control.Exception.allCatch[OffsetDateTime]
          .either(OffsetDateTime.parse(s))
          .left.map(e => Seq(FormError(key, s"Offset date time error: ${e.getMessage}", Nil)))
      }
    }
    override def unbind(key: String, value: OffsetDateTime): Map[String, String] =
      Map(key -> value.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
  }
}
