package v1.questionnaire

import scala.concurrent.ExecutionContext

import auth.DefaultEnv
import auth.roles.ForEditors
import com.mohiva.play.silhouette.api.Silhouette
import javax.inject.Inject
import models.Questionnaire
import models.QuestionnaireForm
import models.QuestionnaireFormData
import models.QuestionnaireRepository
import models.QuestionsRepository
import play.api.libs.json.Json
import util.JsonError
import util.JsonSuccess
import v1.RestBaseController
import v1.RestControllerComponents

class QuestionnaireController @Inject()(
  rcc: RestControllerComponents,
  silhouette: Silhouette[DefaultEnv],
  questionnaires: QuestionnaireRepository,
  questionsRepo: QuestionsRepository
)(implicit ec: ExecutionContext) extends RestBaseController(rcc) {

  def index() = silhouette.SecuredAction(ForEditors).async { implicit request =>
    questionnaires.listAll().map(q => Ok(Json.toJson(q)))
  }

  def add() = silhouette.SecuredAction(ForEditors).async { implicit request =>
    digestForm[QuestionnaireFormData](QuestionnaireForm.form, formData => {
      questionnaires.create(Questionnaire(0, formData.name, formData.studyId)).map(q => Ok(Json.toJson(q)))
    })
  }

  def update(id: Long) = silhouette.SecuredAction(ForEditors).async { implicit request =>
    digestForm[QuestionnaireFormData](QuestionnaireForm.form, formData => {
      questionnaires.update(Questionnaire(id, formData.name, formData.studyId)).map(q => Ok(Json.toJson(q)))
    })
  }

  def delete(id: Long) = silhouette.SecuredAction(ForEditors).async { implicit request =>
    questionnaires.delete(id).map {
      case 0 => NotFound(JsonError(s"Failed to delete questionnaire with id $id"))
      case 1 => Ok(JsonSuccess(s"Successfully deleted questionnaire with id $id"))
      case 2 => throw new IllegalStateException("Deleted more than one questionaire by id, but ids should be unique")
    }.recover {
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
