package util

object Serialization {

  def parseList(serializedList: String): Seq[String] = {
    serializedList.replaceAllLiterally("_", "\\_").replaceAllLiterally("\\,", "__")
      .split(",", -1).map(_.replaceAll("([^\\\\])__", "$1,").replaceAllLiterally("\\_", "_").replaceAllLiterally("\\\\", "\\"))
  }

  def serialize(list: Seq[String]): String = {
    if (list.isEmpty)
      null
    else
      list.map(_.replaceAllLiterally("\\", "\\\\").replaceAllLiterally(",", "\\,")).mkString(",")
  }
}
