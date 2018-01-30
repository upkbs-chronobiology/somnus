package v1.question

import javax.inject.Inject

import auth.DefaultEnv
import com.mohiva.play.silhouette.api.Silhouette
import models.{Question, QuestionForm, Questions}
import play.api.libs.json.Json
import v1.{RestBaseController, RestControllerComponents}

import scala.concurrent.{ExecutionContext, Future}

class QuestionController @Inject()(
  rcc: RestControllerComponents,
  silhouette: Silhouette[DefaultEnv]
)(implicit ec: ExecutionContext)
  extends RestBaseController(rcc) {

  def index = silhouette.SecuredAction.async { implicit request =>
    Questions.listAll.map(questions => Ok(Json.toJson(questions)))
  }

  def get(id: Long) = silhouette.SecuredAction.async { implicit request =>
    Questions.get(id) map (_.get) map (q => Ok(Json.toJson(q))) recover {
      case _: Exception => BadRequest("Cannot serve that question") // XXX: More info? JSON?
    }
  }

  def add = silhouette.SecuredAction.async { implicit request =>
    QuestionForm.form.bindFromRequest().fold(
      badForm => Future.successful(BadRequest(badForm.errorsAsJson)),
      formData => {
        // XXX: Should question instantiation really be done here?
        Questions.add(Question(0, formData.content)) map { newQuestion =>
          Created(Json.toJson(newQuestion)) // XXX: And location header?
        } recover {
          case _: Exception => BadRequest("Could not create question.") // XXX: More info? JSON?
        }
      }
    )
  }

  def delete(id: Long) = silhouette.SecuredAction.async { implicit request =>
    Questions.delete(id) map (num => Ok(s"Deleted $num ${if (num == 1) "entry" else "entries"}")) // XXX: Respond with more information (JSON)?
  }
}
