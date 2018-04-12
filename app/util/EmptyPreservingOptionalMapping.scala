package util

import play.api.data.FormError
import play.api.data.Mapping
import play.api.data.validation.Constraint

// XXX: Adopted and adjusted from OptionalMapping, since not modular enough to allow extension/modification
case class EmptyPreservingOptionalMapping[T](val wrapped: Mapping[T], val constraints: Seq[Constraint[Option[T]]] = Nil) extends Mapping[Option[T]] {
  def bind(data: Map[String, String]): Either[Seq[FormError], Option[T]] = {
    data.keys.filter(p => p == key || p.startsWith(key + ".") || p.startsWith(key + "["))
      .map(k => data.get(k))
      .collect { case Some(v) => v }.headOption.map { _ =>
      wrapped.bind(data).right.map(Some(_))
    }.getOrElse {
      Right(None)
    }.right.flatMap(applyConstraints)
  }

  // XXX: Everything below this line is untouched (coming from OptionalMapping)

  override val format: Option[(String, Seq[Any])] = wrapped.format

  val key = wrapped.key

  def verifying(addConstraints: Constraint[Option[T]]*): Mapping[Option[T]] = {
    this.copy(constraints = constraints ++ addConstraints.toSeq)
  }

  def unbind(value: Option[T]): Map[String, String] = {
    value.map(wrapped.unbind).getOrElse(Map.empty)
  }

  def unbindAndValidate(value: Option[T]): (Map[String, String], Seq[FormError]) = {
    val errors = collectErrors(value)
    value.map(wrapped.unbindAndValidate).map(r => r._1 -> (r._2 ++ errors)).getOrElse(Map.empty -> errors)
  }

  def withPrefix(prefix: String): Mapping[Option[T]] = {
    copy(wrapped = wrapped.withPrefix(prefix))
  }

  val mappings: Seq[Mapping[_]] = wrapped.mappings
}

object CustomForms {
  def emptyPreservingOptional[A](mapping: Mapping[A]) = new EmptyPreservingOptionalMapping(mapping)
}
