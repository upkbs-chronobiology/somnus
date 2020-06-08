package v1.user

import javax.inject.Inject

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.matching.Regex

import auth.AuthService
import auth.DefaultEnv
import auth.acl.AccessRules
import auth.acl.Acls
import auth.roles.ForAnyEditorOrUser
import auth.roles.ForEditors
import auth.roles.Role
import com.mohiva.play.silhouette.api.Silhouette
import models.AccessLevel
import models.Study
import models.StudyAccess
import models.StudyAccessRepository
import models.StudyRepository
import models.UserRepository
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.Constraints.pattern
import play.api.libs.json.Json
import util.Futures
import util.Futures.IterableFutureExtensions
import util.JsonError
import util.JsonSuccess
import v1.RestBaseController
import v1.RestControllerComponents

case class UserCreationFormData(name: String)

object UserCreationForm {
  val form = Form(mapping("name" -> nonEmptyText)(UserCreationFormData.apply)(UserCreationFormData.unapply))
}

case class UserUpdateFormData(role: Option[String])

object UserUpdateForm {
  private val RolePattern: Regex = new Regex(s"^(${Role.values.mkString("|")})$$")

  val form = Form(
    mapping("role" -> optional(nonEmptyText.verifying(pattern(RolePattern))))(UserUpdateFormData.apply)(
      UserUpdateFormData.unapply
    )
  )
}

class UserController @Inject() (
  rcc: RestControllerComponents,
  silhouette: Silhouette[DefaultEnv],
  userRepository: UserRepository,
  studyRepository: StudyRepository,
  authService: AuthService,
  studyAccessRepo: StudyAccessRepository,
  acls: Acls,
  accessRules: AccessRules
)(implicit ec: ExecutionContext)
    extends RestBaseController(rcc) {

  def index = silhouette.SecuredAction(ForEditors).async { implicit request =>
    userRepository
      .listAll()
      .filterIterableAsync(user => accessRules.mayAccessUser(request.identity, user.id, AccessLevel.Read))
      .map(users => Ok(Json.toJson(users)))
  }

  def create = silhouette.SecuredAction(ForEditors).async { implicit request =>
    UserCreationForm.form
      .bindFromRequest()
      .fold(
        badForm => Future.successful(BadRequest(badForm.errorsAsJson)),
        formData =>
          authService.register(formData.name, None).map(u => Created(Json.toJson(u))) recover {
            case e: IllegalArgumentException => BadRequest(JsonError(s"Failed to create user: ${e.getMessage}"))
          }
      )
  }

  def getStudies(userId: Long) = silhouette.SecuredAction(ForAnyEditorOrUser(userId)).async { implicit request =>
    // TODO: Use accessRules instead
    def studyFilter(study: Study, acls: Seq[StudyAccess]) =
      userId == request.identity.id ||
        request.identity.hasRole(Role.Admin) ||
        acls.find(_.studyId == study.id).exists(_.level >= AccessLevel.Read)

    Futures
      .parallel(studyAccessRepo.listByUser(request.identity.id), studyRepository.listForParticipant(userId))
      .map(aclsAndStudies => {
        val acls = aclsAndStudies._1
        val studies = aclsAndStudies._2
        studies.filter(studyFilter(_, acls))
      })
      .map(studies => Ok(Json.toJson(studies)))
      .recover {
        case e: IllegalArgumentException => BadRequest(JsonError(e.getMessage))
      }
  }

  def update(id: Long) = silhouette.SecuredAction(acls.withUserAccess(id, AccessLevel.Write)).async {
    implicit request =>
      UserUpdateForm.form
        .bindFromRequest()
        .fold(
          badForm => Future.successful(BadRequest(badForm.errorsAsJson)),
          formData => {
            // prevent admins from un-admining themselves (don't shoot yourself in the foot)
            if (request.identity.id == id && formData.role != request.identity.role)
              Future
                .successful(BadRequest(JsonError("Refusing to reduce current user's own rights (by altering role)")))
            else
              userRepository
                .setRole(id, formData.role.map(r => Role.withName(r)))
                .map(num => Ok(JsonSuccess(s"Updated $num user(s)")))
          }
        )
  }
}
