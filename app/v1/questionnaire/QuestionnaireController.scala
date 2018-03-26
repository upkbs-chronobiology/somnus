package v1.questionnaire

import javax.inject.Inject

import scala.concurrent.ExecutionContext

import auth.DefaultEnv
import auth.roles.ForEditors
import com.mohiva.play.silhouette.api.Silhouette
import models.Questionnaire
import models.QuestionnaireRepository
import models.QuestionsRepository
import play.api.libs.json.Json
import util.JsonError
import v1.RestBaseController
import v1.RestControllerComponents

class QuestionnaireController @Inject()(
  rcc: RestControllerComponents,
  silhouette: Silhouette[DefaultEnv],
  questionnaires: QuestionnaireRepository,
  questionsRepo: QuestionsRepository
)(implicit ec: ExecutionContext) extends RestBaseController(rcc) {

  def add(questionnaire: Questionnaire) = silhouette.SecuredAction(ForEditors).async { implicit request =>
    questionnaires.create(questionnaire).map(q => Ok(Json.toJson(q)))
      .recover {
        case e: IllegalArgumentException => BadRequest(JsonError(e.getMessage))
      }
  }

  def getQuestions(questionnaireId: Long) = silhouette.SecuredAction(ForEditors).async { implicit request =>
    questionsRepo.listByQuestionnaire(questionnaireId)
      .map(questions => Ok(Json.toJson(questions)))
      .recover {
        case e: IllegalArgumentException => NotFound(JsonError(e.getMessage))
      }
  }
}
