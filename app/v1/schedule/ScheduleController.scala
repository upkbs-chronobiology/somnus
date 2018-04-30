package v1.schedule

import scala.concurrent.ExecutionContext

import auth.DefaultEnv
import auth.roles.ForEditors
import com.mohiva.play.silhouette.api.Silhouette
import javax.inject.Inject
import models.Schedule
import models.ScheduleForm
import models.ScheduleFormData
import models.SchedulesRepository
import play.api.libs.json.Json
import util.JsonError
import util.JsonSuccess
import v1.RestBaseController
import v1.RestControllerComponents

class ScheduleController @Inject()(
  rcc: RestControllerComponents,
  silhouette: Silhouette[DefaultEnv],
  schedulesRepo: SchedulesRepository
)(implicit ec: ExecutionContext) extends RestBaseController(rcc) {

  def listByQuestionnaire(questionnaireId: Long) = silhouette.SecuredAction(ForEditors).async { implicit request =>
    schedulesRepo.getByQuestionnaire(questionnaireId).map(schedules => Ok(Json.toJson(schedules)))
      .recover {
        case e: IllegalArgumentException => NotFound(JsonError(e.getMessage))
      }
  }

  def listByUser(userId: Long) = silhouette.SecuredAction(ForEditors).async { implicit request =>
    listUserSchedules(userId)
  }

  def listForCurrentUser() = silhouette.SecuredAction.async { implicit request =>
    listUserSchedules(request.identity.id)
  }

  private def listUserSchedules(userId: Long) = {
    schedulesRepo.getByUser(userId).map(schedules => Ok(Json.toJson(schedules)))
      .recover {
        case e: IllegalArgumentException => NotFound(JsonError(e.getMessage))
      }
  }

  def add() = silhouette.SecuredAction(ForEditors).async { implicit request =>
    digestForm[ScheduleFormData](ScheduleForm.form, formData => {
      val schedule = Schedule(0, formData.questionnaireId, formData.userId,
        formData.startDate, formData.endDate, formData.startTime, formData.endTime, formData.frequency)
      schedulesRepo.create(schedule).map(s => Created(Json.toJson(s)))
    })
  }

  def update(id: Long) = silhouette.SecuredAction(ForEditors).async { implicit request =>
    digestForm[ScheduleFormData](ScheduleForm.form, formData => {
      val schedule = Schedule(id, formData.questionnaireId, formData.userId,
        formData.startDate, formData.endDate, formData.startTime, formData.endTime, formData.frequency)
      schedulesRepo.update(schedule).map(s => Ok(Json.toJson(s)))
    })
  }

  def delete(id: Long) = silhouette.SecuredAction(ForEditors).async { implicit request =>
    schedulesRepo.delete(id).map(num => Ok(JsonSuccess(s"Deleted $num schedule(s)")))
  }
}
