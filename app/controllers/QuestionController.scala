package controllers

import javax.inject.{Inject, Singleton}

import models.{Question, QuestionForm, Questions}
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class QuestionController @Inject()(cc: ControllerComponents) extends AbstractController(cc) {

  def index = Action.async { implicit request =>
    Questions.listAll.map(questions => Ok(views.html.questions(QuestionForm.form, questions)))
  }

  def add() = Action.async { implicit request =>
    QuestionForm.form.bindFromRequest.fold(
      errorForm => Future.successful(BadRequest("Your question form was borked: " + {
        errorForm.errors map(e => s"${e.key}: ${e.message}") mkString "; "
      })),
      data => {
        val newQuestion = Question(0, data.content)
        Questions.add(newQuestion).map(res =>
          Redirect(routes.QuestionController.index())
        )
      }
    )
  }

  def delete(id: Long) = Action.async { implicit request =>
    Questions.delete(id) map { res =>
      Redirect(routes.QuestionController.index());
    }
  }

}
