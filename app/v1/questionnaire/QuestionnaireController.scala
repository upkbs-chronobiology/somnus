package v1.questionnaire

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import auth.DefaultEnv
import auth.acl.AccessRules
import auth.acl.Acls
import auth.roles.ForEditors
import com.mohiva.play.silhouette.api.Silhouette
import javax.inject.Inject
import models.AccessLevel
import models.Questionnaire
import models.QuestionnaireForm
import models.QuestionnaireFormData
import models.QuestionnairesRepository
import models.QuestionsRepository
import models.User
import play.api.libs.json.Json
import util.Futures.FutureOptionExtensions
import util.Futures.TraversableFutureExtensions
import util.JsonError
import util.JsonSuccess
import v1.RestBaseController
import v1.RestControllerComponents

class QuestionnaireController @Inject()(
  rcc: RestControllerComponents,
  silhouette: Silhouette[DefaultEnv],
  questionnaires: QuestionnairesRepository,
  questionsRepo: QuestionsRepository,
  accessRules: AccessRules,
  acls: Acls
)(implicit ec: ExecutionContext) extends RestBaseController(rcc) {

  def index() = silhouette.SecuredAction(ForEditors).async { implicit request =>
    questionnaires.listAll()
      .filterTraversableAsync(accessRules.mayAccessQuestionnaire(request.identity, _, AccessLevel.Read))
      .map(q => Ok(Json.toJson(q)))
  }

  def add() = silhouette.SecuredAction(ForEditors).async { implicit request =>
    digestForm[QuestionnaireFormData](QuestionnaireForm.form, formData => {
      permittedToWrite(request.identity, formData) flatMap {
        case false =>
          Future.successful(Forbidden(JsonError(s"No write permission on linked study")))
        case true =>
          questionnaires.create(Questionnaire(0, formData.name, formData.studyId)).map(q => Created(Json.toJson(q)))
      }
    })
  }

  def update(id: Long) = silhouette.SecuredAction(ForEditors && acls.withQuestionnaireAccess(id, AccessLevel.Write)).async { implicit request =>
    digestForm[QuestionnaireFormData](QuestionnaireForm.form, formData => {
      permittedToWrite(request.identity, formData) flatMap {
        case false =>
          Future.successful(Forbidden(JsonError(s"No write permission on linked study")))
        case true =>
          questionnaires.update(Questionnaire(id, formData.name, formData.studyId)).map(q => Ok(Json.toJson(q)))
      }
    })
  }

  def delete(id: Long) = silhouette.SecuredAction(ForEditors && acls.withQuestionnaireAccess(id, AccessLevel.Write)).async { implicit request =>
    questionnaires.delete(id).map {
      case 0 => NotFound(JsonError(s"Failed to delete questionnaire with id $id"))
      case 1 => Ok(JsonSuccess(s"Successfully deleted questionnaire with id $id"))
      case 2 => throw new IllegalStateException("Deleted more than one questionaire by id, but ids should be unique")
    }.recover {
      case e: IllegalArgumentException => BadRequest(JsonError(e.getMessage))
    }
  }

  def duplicate(id: Long) = silhouette.SecuredAction(ForEditors && acls.withQuestionnaireAccess(id, AccessLevel.Read)).async { implicit request =>
    questionnaires.duplicate(id).map(q => Created(Json.toJson(q)))
      .recover {
        case e: IllegalArgumentException => BadRequest(JsonError(e.getMessage))
      }
  }

  def getQuestions(questionnaireId: Long) = silhouette.SecuredAction(acls.withQuestionnaireAccess(questionnaireId, AccessLevel.Read)).async {
    implicit request =>
      questionsRepo.listByQuestionnaire(questionnaireId)
        .map(questions => Ok(Json.toJson(questions)))
        .recover {
          case e: IllegalArgumentException => NotFound(JsonError(e.getMessage))
        }
  }

  private def permittedToWrite(user: User, formData: QuestionnaireFormData) = {
    formData.studyId.map(studyId => accessRules.mayAccessStudy(user, studyId, AccessLevel.Write))
      .toOptionFuture.map(_.getOrElse(accessRules.DisconnectedEntitiesPublic))
  }
}
