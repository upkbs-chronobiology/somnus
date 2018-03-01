package v1.question

import javax.inject.Inject

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import auth.DefaultEnv
import auth.roles.ForEditors
import com.mohiva.play.silhouette.api.Silhouette
import models.Question
import models.QuestionForm
import models.Questions
import play.api.libs.json.Json
import util.JsonError
import util.JsonSuccess
import v1.RestBaseController
import v1.RestControllerComponents

class QuestionController @Inject()(
  rcc: RestControllerComponents,
  silhouette: Silhouette[DefaultEnv]
)(implicit ec: ExecutionContext)
  extends RestBaseController(rcc) {

  def index = silhouette.SecuredAction.async { implicit request =>
    Questions.listAll.map(questions => Ok(Json.toJson(questions)))
  }

  def get(id: Long) = silhouette.SecuredAction.async { implicit request =>
    Questions.get(id) map {
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
        Questions.add(Question(0, formData.content, formData.answerType)) map { newQuestion =>
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
      formData => Questions.update(Question(id, formData.content, formData.answerType)).map { q =>
        Ok(Json.toJson(q))
      }
    )
  }

  def delete(id: Long) = silhouette.SecuredAction(ForEditors).async { implicit request =>
    Questions.delete(id)
      .map(num => Ok(JsonSuccess(s"Deleted $num ${if (num == 1) "entry" else "entries"}")))
      .recover {
        case e: IllegalArgumentException => BadRequest(JsonError(e.getMessage))
      }
  }
}
