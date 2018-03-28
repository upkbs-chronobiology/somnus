package v1.user

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.matching.Regex

import auth.DefaultEnv
import auth.roles.ForAdmins
import auth.roles.ForAnyEditorOrUser
import auth.roles.ForEditors
import auth.roles.Role
import com.mohiva.play.silhouette.api.Silhouette
import javax.inject.Inject
import models.StudyRepository
import models.UserRepository
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.Constraints.pattern
import play.api.libs.json.Json
import util.JsonError
import util.JsonSuccess
import v1.RestBaseController
import v1.RestControllerComponents

case class UserUpdateFormData(role: Option[String])

object UserUpdateForm {
  private val RolePattern: Regex = new Regex(s"^(${Role.values.mkString("|")})$$")

  val form = Form(
    mapping(
      "role" -> optional(nonEmptyText.verifying(pattern(RolePattern)))
    )(UserUpdateFormData.apply)(UserUpdateFormData.unapply)
  )
}

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

  def update(id: Long) = silhouette.SecuredAction(ForAdmins).async { implicit request =>
    UserUpdateForm.form.bindFromRequest().fold(
      badForm => Future.successful(BadRequest(badForm.errorsAsJson)),
      formData => {
        // prevent admins from un-admining themselves (don't shoot yourself in the foot)
        if (request.identity.id == id && formData.role != request.identity.role)
          Future.successful(BadRequest(JsonError("Refusing to reduce current user's own rights (by altering role)")))
        else
          userRepository.setRole(id, formData.role.map(r => Role.withName(r)))
            .map(num => Ok(JsonSuccess(s"Updated $num user(s)")))
      }
    )
  }
}
