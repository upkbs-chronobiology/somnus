package v1

import javax.inject.Inject

class GeneralController @Inject() (rcc: RestControllerComponents) extends RestBaseController(rcc) {

  /**
    * Simple empty-body response, useful for e.g. time or connectivity checks.
    */
  def poke = Action(Ok(""))
}
