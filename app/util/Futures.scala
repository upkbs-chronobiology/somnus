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
}
