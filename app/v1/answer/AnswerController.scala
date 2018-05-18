package v1.answer

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import auth.DefaultEnv
import auth.roles.ForEditors
import com.mohiva.play.silhouette.api.Silhouette
import javax.inject.Inject
import models.Answer
import models.AnswerForm
import models.AnswersRepository
import play.api.libs.json.JsArray
import play.api.libs.json.Json
import util.JsonError
import util.JsonSuccess
import util.Logging
import v1.RestBaseController
import v1.RestControllerComponents

class AnswerController @Inject()(
  rcc: RestControllerComponents,
  silhouette: Silhouette[DefaultEnv],
  answersRepo: AnswersRepository
)(implicit ec: ExecutionContext)
  extends RestBaseController(rcc) with Logging {

  def index = silhouette.SecuredAction(ForEditors).async { implicit request =>
    answersRepo.listAll().map(answers => Ok(Json.toJson(answers)))
  }

  def listMineByQuestionnaire(questionnaireId: Long) = silhouette.SecuredAction.async { implicit request =>
    answersRepo.listByUserAndQuestionnaire(request.identity.id, questionnaireId).map(answers => Ok(Json.toJson(answers)))
  }

  def get(id: Long) = silhouette.SecuredAction(ForEditors).async { implicit request =>
    answersRepo.get(id).map(answer => Ok(Json.toJson(answer))) recover {
      case _: Exception => BadRequest("Cannot serve that answer")
    }
  }

  def add = silhouette.SecuredAction.async { implicit request =>
    request.body.asJson match {
      case Some(array: JsArray) =>
        try {
          val newAnswers = array.value.map(item =>
            AnswerForm.form.bind(item).fold(
              badForm => throw new IllegalArgumentException(badForm.errorsAsJson.toString()),
              formData => {
                val userId = formData.userId.getOrElse(request.identity.id)
                Answer(0, formData.questionId, formData.content, userId, null, formData.createdLocal) // scalastyle:ignore null
              }
            )
          )
          answersRepo.addAll(newAnswers).map(answers => Created(Json.toJson(answers))) recover {
            case e: IllegalArgumentException => BadRequest(JsonError(e.getMessage))
          }
        } catch {
          case e: IllegalArgumentException => Future.successful(BadRequest(JsonError(e.getMessage)))
          case e: Exception =>
            logger.error("Failed to create answer", e)
            Future.successful(InternalServerError(JsonError("Could not create answer")))
        }
      case _ => Future.successful(BadRequest(JsonError("Expected array of answers")))
    }
  }

  def delete(id: Long) = silhouette.SecuredAction(ForEditors).async { implicit request =>
    answersRepo.delete(id).map {
      num => Ok(JsonSuccess(s"Deleted $num answer${if (num != 1) "s"}"))
    }
  }
}
