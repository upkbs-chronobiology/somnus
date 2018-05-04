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

  def asCsv(tuples: Seq[Product]): String = {
    val stringWriter = new StringWriter()
    val writer = new CSVWriter(stringWriter)

    // XXX: Are nulls (or Options) handled correctly here?
    @SuppressWarnings(Array("org.wartremover.warts.Any"))
    @SuppressWarnings(Array("org.wartremover.warts.Product"))
    val data = tuples.map(_.productIterator.toSeq.map(serialize).toArray)
    writer.writeAll(JavaConverters.seqAsJavaList(data))

    stringWriter.toString
  }

  private def serialize(obj: Any): String = {
    obj match {
      case o: Timestamp => o.toInstant.toString
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
