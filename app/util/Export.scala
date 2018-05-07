package util

import java.io.ByteArrayOutputStream
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.sql.Timestamp
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

import scala.collection.JavaConverters

import com.opencsv.CSVWriter

object Export {

  val CHARSET = StandardCharsets.UTF_8

  @SuppressWarnings(Array("org.wartremover.warts.Product"))
  def asCsv(headers: Seq[String], tuples: Seq[Product]): String = {
    if (tuples.headOption.map(_.productIterator.length).getOrElse(headers.length) != headers.length)
      throw new IllegalArgumentException("Headers shape doesn't match data")

    val stringWriter = new StringWriter()
    val writer = new CSVWriter(stringWriter)

    writer.writeNext(headers.toArray)

    @SuppressWarnings(Array("org.wartremover.warts.Any"))
    val data = tuples.map(_.productIterator.toSeq.map(serialize).toArray)
    writer.writeAll(JavaConverters.seqAsJavaList(data))

    stringWriter.toString
  }

  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  // scalastyle:off null
  private def serialize(obj: Any): String = {
    obj match {
      case timestamp: Timestamp => timestamp.toInstant.toString
      case option: Option[Any] => serialize(option.orNull)
      case null => null
      case o => o.toString
    }
  }

  def zip(files: Map[String, String]): Array[Byte] = {
    val out = new ByteArrayOutputStream()
    val zip = new ZipOutputStream(out, CHARSET)

    for ((name, content) <- files) {
      zip.putNextEntry(new ZipEntry(name))
      zip.write(content.getBytes(CHARSET))
      zip.closeEntry()
    }
    zip.close()

    out.toByteArray
  }
}
