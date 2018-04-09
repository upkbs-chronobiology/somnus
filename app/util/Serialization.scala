package util

object Serialization {

  def parseList(serializedList: String): Seq[String] = {
    serializedList.replaceAllLiterally("_", "\\_").replaceAllLiterally("\\,", "__")
      .split(",", -1).map(_.replaceAll("([^\\\\])__", "$1,").replaceAllLiterally("\\_", "_").replaceAllLiterally("\\\\", "\\"))
  }

  def serialize(list: Seq[String]): String = {
    if (list.isEmpty)
      null // scalastyle:ignore null
    else
      list.map(_.replaceAllLiterally("\\", "\\\\").replaceAllLiterally(",", "\\,")).mkString(",")
  }

  def parseIntRange(serializedRange: String): InclusiveRange[Int] = {
    parseRange(serializedRange, _.toInt)
  }

  def parseFloatRange(serializedRange: String): InclusiveRange[Float] = {
    parseRange(serializedRange, _.toFloat)
  }

  def parseRange[T](serializedRange: String, numberParser: String => T): InclusiveRange[T] = {
    val parts = serializedRange.split(",")
    if (parts.size != 2) throw new IllegalArgumentException("Serialized range has bad format (not exactly 2 items)")

    try {
      InclusiveRange(numberParser(parts(0)), numberParser(parts(1)))
    } catch {
      case e: NumberFormatException =>
        throw new IllegalArgumentException("Bad format of min or max value in serialized range", e)
    }
  }

  def serialize[T](range: InclusiveRange[T]): String = {
    s"${range.min},${range.max}"
  }
}
