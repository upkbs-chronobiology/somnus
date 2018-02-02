package controllers

import javax.inject.Inject
import javax.inject.Singleton

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import auth.DefaultEnv
import com.mohiva.play.silhouette.api.Silhouette
import models.Question
import models.QuestionForm
import models.Questions
import play.api.mvc._

@Singleton
class QuestionHtmlController @Inject()(
  cc: ControllerComponents, silhouette: Silhouette[DefaultEnv]
) extends AbstractController(cc) {

  def index = silhouette.SecuredAction.async { implicit request =>
    Questions.listAll.map(questions => Ok(views.html.questions(QuestionForm.form, questions)))
  }

  def add() = silhouette.SecuredAction.async { implicit request =>
    QuestionForm.form.bindFromRequest.fold(
      errorForm => Future.successful(BadRequest("Your question form was borked: " + {
        errorForm.errors map (e => s"${e.key}: ${e.message}") mkString "; "
      })),
      data => {
        val newQuestion = Question(0, data.content)
        Questions.add(newQuestion).map(_ =>
          Redirect(routes.QuestionHtmlController.index())
        )
      }
    )
  }

  def delete(id: Long) = silhouette.SecuredAction.async { implicit request =>
    Questions.delete(id) map { res =>
      Redirect(routes.QuestionHtmlController.index());
    }
  }

}
