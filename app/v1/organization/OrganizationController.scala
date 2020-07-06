package v1.organization

import javax.inject.Inject

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import auth.DefaultEnv
import auth.acl.AccessRules
import auth.roles.ForAdmins
import com.mohiva.play.silhouette.api.Silhouette
import models.AccessLevel
import models.Organization
import models.OrganizationForm
import models.OrganizationRepository
import play.api.libs.json.Json
import util.Futures.IterableFutureExtensions
import util.JsonError
import util.JsonSuccess
import v1.RestBaseController
import v1.RestControllerComponents

class OrganizationController @Inject() (
  rcc: RestControllerComponents,
  silhouette: Silhouette[DefaultEnv],
  organizationRepo: OrganizationRepository,
  accessRules: AccessRules
)(implicit ec: ExecutionContext)
    extends RestBaseController(rcc) {

  def index = silhouette.SecuredAction.async { implicit request =>
    organizationRepo
      .listAll()
      .filterIterable(o => accessRules.mayAccessOrganization(request.identity, o.id, AccessLevel.Read))
      .map(answers => Ok(Json.toJson(answers)))
  }

  def get(organizationId: Long) = silhouette.SecuredAction.async { implicit request =>
    if (accessRules.mayAccessOrganization(request.identity, organizationId, AccessLevel.Read)) {
      organizationRepo
        .get(organizationId)
        .map {
          case Some(organization) => Ok(Json.toJson(organization))
          case None => NotFound(JsonError("No such organization"))
        }
    } else {
      Future.successful(Forbidden(JsonError("Not allowed to read this organization")))
    }
  }

  def add = silhouette.SecuredAction(ForAdmins).async { implicit request =>
    OrganizationForm.form
      .bindFromRequest()
      .fold(
        badForm => Future.successful(BadRequest(badForm.errorsAsJson)),
        formData =>
          organizationRepo
            .create(Organization(0, formData.name))
            .map(o => Created(Json.toJson(o)))
      )
  }

  def update(organizationId: Long) = silhouette.SecuredAction(ForAdmins).async { implicit request =>
    OrganizationForm.form
      .bindFromRequest()
      .fold(
        badForm => Future.successful(BadRequest(badForm.errorsAsJson)),
        formData =>
          organizationRepo
            .update(Organization(organizationId, formData.name))
            .map {
              case Some(o) => Ok(Json.toJson(o))
              case None => NotFound(JsonError("No such organization"))
            }
      )
  }

  def delete(organizationId: Long) = silhouette.SecuredAction(ForAdmins).async { implicit request =>
    organizationRepo
      .delete(organizationId)
      .map {
        case 0 => NotFound(JsonError("No such organization"))
        case n => Ok(JsonSuccess(s"Deleted $n organization(s)"))
      }
  }
}
