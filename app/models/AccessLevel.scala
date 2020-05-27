package models

import play.api.libs.json.Reads
import slick.jdbc.H2Profile.api._

object AccessLevel extends Enumeration {
  type AccessLevel = Value
  val Read = Value("read")
  val Write = Value("write")
  val Own = Value("own")

  implicit class AccessLevelValue(level: Value) {

    def >=(other: AccessLevel): Boolean = // scalastyle:ignore method.name
      this.level == Own || other == Read || this.level == other

    def <=(other: AccessLevel): Boolean = // scalastyle:ignore method.name
      this.level == Read || other == Own || this.level == other
  }

  implicit val accessLevelMapper = MappedColumnType
    .base[AccessLevel, String](level => level.toString, levelString => AccessLevel.withName(levelString))

  implicit val implicitReads = Reads.enumNameReads(AccessLevel)
}
