package v1.answer

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import auth.DefaultEnv
import com.mohiva.play.silhouette.api.Silhouette
import javax.inject.Inject
import models.Answer
import models.AnswerForm
import models.AnswersRepository
import play.api.libs.json.JsArray
import play.api.libs.json.Json
import util.JsonError
import v1.RestBaseController
import v1.RestControllerComponents

class AnswerController @Inject()(
  rcc: RestControllerComponents,
  silhouette: Silhouette[DefaultEnv],
  answersRepo: AnswersRepository
)(implicit ec: ExecutionContext)
  extends RestBaseController(rcc) {

  def index = silhouette.SecuredAction.async { implicit request =>
    answersRepo.listAll().map(answers => Ok(Json.toJson(answers)))
  }

  def get(id: Long) = silhouette.SecuredAction.async { implicit request =>
    answersRepo.get(id).map(answer => Ok(Json.toJson(answer))) recover {
      case _: Exception => BadRequest("Cannot serve that answer")
    }
  }

  def add = silhouette.SecuredAction.async { implicit request =>
    request.body.asJson match {
      case Some(array: JsArray) =>
        val newAnswers = array.value.map(item =>
          AnswerForm.form.bind(item).fold(
            badForm => throw new IllegalArgumentException(badForm.errorsAsJson.toString()),
            formData => {
              val userId = formData.userId.getOrElse(request.identity.id)
              Answer(0, formData.questionId, formData.content, userId, null)
            }
          )
        )
        answersRepo.addAll(newAnswers).map(answers => Created(Json.toJson(answers))).recover {
          case e: IllegalArgumentException => BadRequest(JsonError(e.getMessage))
          case _: Exception => BadRequest(JsonError("Could not create answer"))
        }
      case _ => Future.successful(BadRequest(JsonError("Expected array of answers")))
    }
  }

  def delete(id: Long) = silhouette.SecuredAction.async { implicit request =>
    answersRepo.delete(id).map {
      num => Ok(s"Deleted $num answer${if (num != 1) "s"}")
    }
  }
}
