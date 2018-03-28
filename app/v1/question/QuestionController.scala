package v1.question

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import auth.DefaultEnv
import auth.roles.ForEditors
import com.mohiva.play.silhouette.api.Silhouette
import javax.inject.Inject
import models.Question
import models.QuestionForm
import models.QuestionsRepository
import play.api.libs.json.Json
import util.JsonError
import util.JsonSuccess
import v1.RestBaseController
import v1.RestControllerComponents

class QuestionController @Inject()(
  rcc: RestControllerComponents,
  silhouette: Silhouette[DefaultEnv],
  questionsRepo: QuestionsRepository
)(implicit ec: ExecutionContext)
  extends RestBaseController(rcc) {

  def index = silhouette.SecuredAction.async { implicit request =>
    questionsRepo.listAll.map(questions => Ok(Json.toJson(questions)))
  }

  def get(id: Long) = silhouette.SecuredAction.async { implicit request =>
    questionsRepo.get(id) map {
      case None => BadRequest("Question not found")
      case Some(question) => Ok(Json.toJson(question))
    } recover {
      case _: Exception => BadRequest("Cannot serve that question") // XXX: More info? JSON?
    }
  }

  def add = silhouette.SecuredAction(ForEditors).async { implicit request =>
    QuestionForm.form.bindFromRequest().fold(
      badForm => Future.successful(BadRequest(badForm.errorsAsJson)),
      formData => {
        // XXX: Should question instantiation really be done here?
        questionsRepo.add(Question(0, formData.content, formData.answerType, formData.questionnaireId)) map { newQuestion =>
          Created(Json.toJson(newQuestion)) // XXX: And location header?
        } recover {
          case _: Exception => BadRequest("Could not create question.") // XXX: More info? JSON?
        }
      }
    )
  }

  def update(id: Long) = silhouette.SecuredAction(ForEditors).async { implicit request =>
    QuestionForm.form.bindFromRequest().fold(
      badForm => Future.successful(BadRequest(badForm.errorsAsJson)),
      formData => questionsRepo.update(Question(id, formData.content, formData.answerType, formData.questionnaireId)).map { q =>
        Ok(Json.toJson(q))
      }
    )
  }

  def delete(id: Long) = silhouette.SecuredAction(ForEditors).async { implicit request =>
    questionsRepo.delete(id)
      .map(num => Ok(JsonSuccess(s"Deleted $num ${if (num == 1) "entry" else "entries"}")))
      .recover {
        case e: IllegalArgumentException => BadRequest(JsonError(e.getMessage))
      }
  }
}
