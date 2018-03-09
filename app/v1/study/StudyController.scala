package v1.study

import javax.inject.Inject

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import auth.DefaultEnv
import auth.roles.ForEditors
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import models.Study
import models.StudyForm
import models.StudyFormData
import models.StudyRepository
import play.api.libs.json.Json
import play.api.mvc.AnyContent
import play.api.mvc.Result
import util.JsonError
import util.JsonSuccess
import v1.RestBaseController
import v1.RestControllerComponents

class StudyController @Inject()(
  rcc: RestControllerComponents,
  silhouette: Silhouette[DefaultEnv],
  studyRepository: StudyRepository
)(implicit ec: ExecutionContext) extends RestBaseController(rcc) {

  def index = silhouette.SecuredAction.async { implicit request =>
    studyRepository.listAll().map(studies => Ok(Json.toJson(studies)))
  }

  def get(id: Long) = silhouette.SecuredAction.async { implicit request =>
    studyRepository.read(id).map {
      case None => BadRequest(JsonError(s"Study with id $id not found"))
      case Some(study) => Ok(Json.toJson(study))
    }
  }

  def add = silhouette.SecuredAction(ForEditors).async { implicit request =>
    digestForm(formData => studyRepository.create(Study(0, formData.name))
      .map(study => Created(Json.toJson(study))))
  }

  def update(id: Long) = silhouette.SecuredAction(ForEditors).async { implicit request =>
    digestForm(formData => studyRepository.update(Study(id, formData.name))
      .map(study => Ok(Json.toJson(study))))
  }

  def delete(id: Long) = silhouette.SecuredAction(ForEditors).async { implicit request =>
    studyRepository.delete(id)
      .map(num => Ok(JsonSuccess(s"Deleted $num stud${if (num == 1) "y" else "ies"}")))
      .recover {
        case e: IllegalArgumentException => BadRequest(JsonError(e.getMessage))
      }
  }

  def getParticipants(studyId: Long) = silhouette.SecuredAction(ForEditors).async { implicit request =>
    studyRepository.listParticipants(studyId)
      .map(participants => Ok(Json.toJson(participants)))
      .recover {
        case e: IllegalArgumentException => BadRequest(JsonError(e.getMessage))
      }
  }

  def addParticipant(studyId: Long, userId: Long) = silhouette.SecuredAction(ForEditors).async {
    implicit request =>
      studyRepository.addParticipant(studyId, userId)
        .map(num => Created(JsonSuccess(s"Updated $num entr${if (num == 1) "y" else "ies"}")))
  }

  def removeParticipant(studyId: Long, userId: Long) = silhouette.SecuredAction(ForEditors).async {
    implicit request =>
      studyRepository.removeParticipant(studyId, userId)
        .map(num => Ok(JsonSuccess(s"Removed $num participant${if (num != 1) "s"}")))
  }

  private def digestForm(validCallback: StudyFormData => Future[Result])(
    implicit request: SecuredRequest[DefaultEnv, AnyContent]
  ): Future[Result] = {
    StudyForm.form.bindFromRequest.fold(
      badForm => Future.successful(BadRequest(badForm.errorsAsJson)),
      formData => validCallback(formData).recover {
        case e: IllegalArgumentException => BadRequest(JsonError(e.getMessage))
      }
    )
  }
}
