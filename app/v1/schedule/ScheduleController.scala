package v1.schedule

import javax.inject.Inject

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import auth.DefaultEnv
import auth.acl.AccessRules
import auth.acl.Acls
import auth.roles.ForEditors
import com.mohiva.play.silhouette.api.Silhouette
import models.AccessLevel
import models.Schedule
import models.ScheduleForm
import models.ScheduleFormData
import models.SchedulesRepository
import models.User
import play.api.libs.json.Json
import util.Futures.IterableFutureExtensions
import util.JsonError
import util.JsonSuccess
import v1.RestBaseController
import v1.RestControllerComponents

class ScheduleController @Inject() (
  rcc: RestControllerComponents,
  silhouette: Silhouette[DefaultEnv],
  schedulesRepo: SchedulesRepository,
  acls: Acls,
  accessRules: AccessRules
)(implicit ec: ExecutionContext)
    extends RestBaseController(rcc) {

  def listByQuestionnaire(questionnaireId: Long) =
    silhouette.SecuredAction(ForEditors && acls.withQuestionnaireAccess(questionnaireId, AccessLevel.Read)).async {
      implicit request =>
        schedulesRepo
          .getByQuestionnaire(questionnaireId)
          .map(schedules => Ok(Json.toJson(schedules)))
          .recover {
            case e: IllegalArgumentException => NotFound(JsonError(e.getMessage))
          }
    }

  def listByUser(userId: Long) =
    silhouette.SecuredAction(ForEditors).async { implicit request => listUserSchedules(userId, request.identity) }

  def listForCurrentUser() = silhouette.SecuredAction.async { implicit request =>
    listUserSchedules(request.identity.id, request.identity)
  }

  private def listUserSchedules(userId: Long, reader: User) = {
    schedulesRepo
      .getByUser(userId)
      .filterIterableAsync(schedule => accessRules.mayAccessSchedule(reader, schedule, AccessLevel.Read))
      .map(schedules => Ok(Json.toJson(schedules)))
      .recover {
        case e: IllegalArgumentException => NotFound(JsonError(e.getMessage))
      }
  }

  def add() = silhouette.SecuredAction(ForEditors).async { implicit request =>
    digestForm[ScheduleFormData](
      ScheduleForm.form,
      formData => {
        accessRules.mayAccessQuestionnaire(request.identity, formData.questionnaireId, AccessLevel.Write) flatMap {
          case false =>
            Future.successful(
              Forbidden(JsonError(s"No write access to (study of) questionnaire ${formData.questionnaireId}"))
            )
          case true =>
            val schedule = Schedule(
              0,
              formData.questionnaireId,
              formData.userId,
              formData.startDate,
              formData.endDate,
              formData.startTime,
              formData.endTime,
              formData.frequency
            )
            schedulesRepo.create(schedule).map(s => Created(Json.toJson(s)))
        }
      }
    )
  }

  def update(id: Long) = silhouette.SecuredAction(ForEditors && acls.withScheduleAccess(id, AccessLevel.Write)).async {
    implicit request =>
      digestForm[ScheduleFormData](
        ScheduleForm.form,
        formData => {
          val schedule = Schedule(
            id,
            formData.questionnaireId,
            formData.userId,
            formData.startDate,
            formData.endDate,
            formData.startTime,
            formData.endTime,
            formData.frequency
          )
          schedulesRepo.update(schedule).map(s => Ok(Json.toJson(s)))
        }
      )
  }

  def delete(id: Long) = silhouette.SecuredAction(ForEditors && acls.withScheduleAccess(id, AccessLevel.Write)).async {
    implicit request => schedulesRepo.delete(id).map(num => Ok(JsonSuccess(s"Deleted $num schedule(s)")))
  }
}
