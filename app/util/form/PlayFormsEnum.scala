package util.form

import play.api.data.FormError
import play.api.data.Forms.of
import play.api.data.Mapping
import play.api.data.format.Formats
import play.api.data.format.Formatter

object PlayFormsEnum {

  def enum[E <: Enumeration](enum: E): Mapping[E#Value] = of(enumFormat(enum))

  def enumFormat[E <: Enumeration](enum: E): Formatter[E#Value] = new Formatter[E#Value] {

    def bind(key: String, data: Map[String, String]): Either[Seq[FormError], E#Value] = {
      Formats.stringFormat
        .bind(key, data)
        .right
        .flatMap(
          s =>
            scala.util.control.Exception
              .allCatch[E#Value]
              .either(enum.withName(s))
              .left
              .map(_ => Seq(FormError(key, "error.enum", Nil)))
        )
    }

    override def unbind(key: String, value: E#Value): Map[String, String] = Map(key -> value.toString)
  }
}
