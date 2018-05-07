package util

import play.api.Logger

trait Logging {
  @transient
  protected lazy val logger = Logger(this.getClass.getName)
}
