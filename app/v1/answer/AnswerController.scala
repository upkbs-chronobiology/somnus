package v1.answer

import javax.inject.Inject

import models.{Answer, AnswerForm, Answers}
import play.api.libs.json.Json
import v1.{RestBaseController, RestControllerComponents}

import scala.concurrent.{ExecutionContext, Future}

class AnswerController @Inject()(rcc: RestControllerComponents)(implicit ec: ExecutionContext)
  extends RestBaseController(rcc) {

  def index = Action.async { implicit request =>
    Answers.listAll().map(answers => Ok(Json.toJson(answers)))
  }

  def get(id: Long) = Action.async { implicit request =>
    Answers.get(id).map(answer => Ok(Json.toJson(answer))) recover {
      case _: Exception => BadRequest("Cannot serve that answer")
    }
  }

  def add = Action.async { implicit request =>
    AnswerForm.form.bindFromRequest().fold(
      badForm => Future.successful(BadRequest(badForm.errorsAsJson)),
      formData => {
        val answer = Answer(0, formData.questionId, formData.content)
        Answers.add(answer).map(newAnswer => Created(Json.toJson(newAnswer)))
          .recover {
            case _: Exception => BadRequest("Could not create answer") // XXX: More info?
          }
      }
    )
  }

  def delete(id: Long) = Action.async { implicit request =>
    Answers.delete(id).map {
      num => Ok(s"Deleted $num answer${if (num != 1) "s"}")
    }
  }
}
