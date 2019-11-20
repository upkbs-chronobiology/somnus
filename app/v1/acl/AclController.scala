package v1.acl

import scala.concurrent.ExecutionContext

import auth.DefaultEnv
import auth.acl.WithStudyAccess
import auth.acl.WithUserAccess
import com.mohiva.play.silhouette.api.Silhouette
import javax.inject.Inject
import models.AccessLevel
import models.AccessLevel.AccessLevel
import models.StudyAccess
import models.StudyAccessRepository
import play.api.libs.json.Json
import util.JsonSuccess
import v1.RestBaseController
import v1.RestControllerComponents

class AclController @Inject()(
  rcc: RestControllerComponents,
  silhouette: Silhouette[DefaultEnv],
  studyAccessRepo: StudyAccessRepository
)(implicit ec: ExecutionContext) extends RestBaseController(rcc) {

  // XXX: Hacky
  implicit val implicitSAR: StudyAccessRepository = studyAccessRepo

  private def studyOwnerAction(studyId: Long) = silhouette.SecuredAction(WithStudyAccess(studyId, AccessLevel.Own))

  def listStudyAccessByUser(userId: Long) = silhouette.SecuredAction(WithUserAccess(userId, AccessLevel.Read)).async {
    implicit request =>
      studyAccessRepo.listByUser(userId).map(seq => Ok(Json.toJson(seq)))
  }

  def listAccessByStudy(studyId: Long) = silhouette.SecuredAction(WithStudyAccess(studyId, AccessLevel.Read)).async {
    implicit request =>
      studyAccessRepo.listByStudy(studyId).map(seq => Ok(Json.toJson(seq)))
  }

  def put(userId: Long, studyId: Long) = studyOwnerAction(studyId).async { implicit request =>
    request.body.asJson.flatMap(json => json.apply(StudyAccess.LevelJsonKey).asOpt[AccessLevel]) match {
      case None =>
        throw new IllegalArgumentException("Failed to parse body JSON")
      case Some(level) =>
        studyAccessRepo.upsert(StudyAccess(userId, studyId, level))
          .map(num => Ok(JsonSuccess(s"Updated $num entries")))
    }
  }

  def delete(userId: Long, studyId: Long) = studyOwnerAction(studyId).async { implicit request =>
    studyAccessRepo.delete(userId, studyId).map(num => Ok(JsonSuccess(s"Deleted $num entries")))
  }
}
