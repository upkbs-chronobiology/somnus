package v1.user

import javax.inject.Inject

import scala.concurrent.ExecutionContext

import auth.DefaultEnv
import auth.roles.ForAnyEditorOrUser
import auth.roles.ForEditors
import com.mohiva.play.silhouette.api.Silhouette
import models.StudyRepository
import models.UserRepository
import play.api.libs.json.Json
import util.JsonError
import v1.RestBaseController
import v1.RestControllerComponents

class UserController @Inject()(
  rcc: RestControllerComponents,
  silhouette: Silhouette[DefaultEnv],
  userRepository: UserRepository,
  studyRepository: StudyRepository
)(implicit ec: ExecutionContext) extends RestBaseController(rcc) {

  def index = silhouette.SecuredAction(ForEditors).async {
    userRepository.listAll().map(users => Ok(Json.toJson(users)))
  }

  def getStudies(userId: Long) = silhouette.SecuredAction(ForAnyEditorOrUser(userId)).async {
    studyRepository.listForParticipant(userId)
      .map(studies => Ok(Json.toJson(studies)))
      .recover {
        case e: IllegalArgumentException => BadRequest(JsonError(e.getMessage))
      }
  }
}
