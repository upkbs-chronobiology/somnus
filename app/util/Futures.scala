package util

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

object Futures {
  def swapOption[T](option: Option[Future[T]])(implicit ec: ExecutionContext): Future[Option[T]] = {
    option match {
      case Some(future) => future.map(Some(_))
      case None => Future.successful(None)
    }
  }

  /**
    * Type-safe version of Future#sequence for exactly 2 elements.
    */
  @SuppressWarnings(Array("org.wartremover.warts.Any", "org.wartremover.warts.AsInstanceOf"))
  def parallel[TA, TB](a: Future[TA], b: Future[TB])(implicit ec: ExecutionContext): Future[(TA, TB)] =
    Future.sequence(Seq(a, b)).map(seq => (seq(0).asInstanceOf[TA], seq(1).asInstanceOf[TB]))

  implicit class OptionFutureExtensions[T](optionFuture: Future[Option[T]])(implicit ec: ExecutionContext) {

    def mapOption[B](mapping: T => B): Future[Option[B]] = optionFuture.map(_.map(mapping))

    def mapOptionFlat[B](mapping: T => Option[B]): Future[Option[B]] = optionFuture.map(_.flatMap(mapping))

    def flatMapOption[B](mapping: T => Future[B]): Future[Option[B]] =
      optionFuture.flatMap(opt => swapOption(opt.map(mapping)))
  }

  implicit class FutureOptionExtensions[T](futureOption: Option[Future[T]])(implicit ec: ExecutionContext) {

    def toOptionFuture: Future[Option[T]] = futureOption.map(_.map(Some(_))).getOrElse(Future.successful(None))
  }

  implicit class TraversableFutureExtensions[T](traversableFuture: Future[Traversable[T]])
    (implicit ec: ExecutionContext) {

    def mapTraversable[B](mapping: T => B): Future[Traversable[B]] =
      traversableFuture.map(_.map(mapping))

    def mapTraversableAsync[B](mapping: T => Future[B]): Future[Traversable[B]] =
      traversableFuture.flatMap(trav => Future.sequence(trav.map(mapping)))

    def filterTraversable(filter: T => Boolean): Future[Traversable[T]] =
      traversableFuture.map(_.filter(filter))

    def filterTraversableAsync(filter: T => Future[Boolean]): Future[Traversable[T]] = {
      traversableFuture
        .map(seq => seq.map(t => filter(t).map {
          case true => Some(t)
          case false => None
        }))
        .flatMap(futures => Future.sequence(futures))
        .map(_.filter(_.isDefined))
        .map(_.map(_.getOrElse(
          throw new IllegalStateException("Undefined options should have been filtered out"))))
    }
  }

}
