package v1.question

import javax.inject.Inject

import models.{Question, QuestionForm, Questions}
import play.api.libs.json.Json
import v1.{RestBaseController, RestControllerComponents}

import scala.concurrent.{ExecutionContext, Future}

class QuestionController @Inject()(rcc: RestControllerComponents)(implicit ec: ExecutionContext)
  extends RestBaseController(rcc) {

  def index = Action.async { implicit request =>
    Questions.listAll.map(questions => Ok(Json.toJson(questions)))
  }

  def get(id: Long) = Action.async { implicit request =>
    Questions.get(id) map(_.get) map(q => Ok(Json.toJson(q))) recover {
      case _: Exception => BadRequest("Cannot serve that question") // XXX: More info? JSON?
    }
  }

  def add = Action.async { implicit request =>
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

  def delete(id: Long) = Action.async { implicit request =>
    Questions.delete(id) map (num => Ok(s"Deleted $num ${if (num == 1) "entry" else "entries"}")) // XXX: Respond with more information (JSON)?
  }
}
