package v1.answer

import javax.inject.Inject

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import auth.DefaultEnv
import auth.acl.AccessRules
import auth.acl.Acls
import auth.acl.ForbiddenAccessException
import auth.roles.ForEditors
import com.mohiva.play.silhouette.api.Silhouette
import models.AccessLevel
import models.Answer
import models.AnswerForm
import models.AnswersRepository
import play.api.libs.json.JsArray
import play.api.libs.json.Json
import util.Futures.IterableFutureExtensions
import util.JsonError
import util.JsonSuccess
import util.Logging
import v1.RestBaseController
import v1.RestControllerComponents

class AnswerController @Inject() (
  rcc: RestControllerComponents,
  silhouette: Silhouette[DefaultEnv],
  answersRepo: AnswersRepository,
  accessRules: AccessRules,
  acls: Acls
)(implicit ec: ExecutionContext)
    extends RestBaseController(rcc)
    with Logging {

  def index = silhouette.SecuredAction(ForEditors).async { implicit request =>
    answersRepo
      .listAll()
      .filterIterableAsync(a => accessRules.mayAccessAnswer(request.identity, a, AccessLevel.Read))
      .map(answers => Ok(Json.toJson(answers)))
  }

  def listByQuestionnaire(questionnaireId: Long) =
    silhouette.SecuredAction(ForEditors && acls.withQuestionnaireAccess(questionnaireId, AccessLevel.Read)).async {
      implicit request =>
        answersRepo
          .listByQuestionnaire(questionnaireId)
          .map(answers => Ok(Json.toJson(answers)))
    }

  def listMineByQuestionnaire(questionnaireId: Long) = silhouette.SecuredAction.async { implicit request =>
    answersRepo
      .listByUserAndQuestionnaire(request.identity.id, questionnaireId)
      .map(answers => Ok(Json.toJson(answers)))
  }

  def get(id: Long) = silhouette.SecuredAction(ForEditors && acls.withAnswerAccess(id, AccessLevel.Read)).async {
    implicit request =>
      answersRepo.get(id).map(answer => Ok(Json.toJson(answer))) recover {
        case _: Exception => BadRequest("Cannot serve that answer")
      }
  }

  def add = silhouette.SecuredAction.async { implicit request =>
    // TODO: Consider using "for" pattern (see StudyRepository) to disentangle this mess
    request.body.asJson match {
      case Some(array: JsArray) =>
        try {
          val newAnswers = array.value.map(
            item =>
              AnswerForm.form
                .bind(item)
                .fold(
                  badForm => throw new IllegalArgumentException(badForm.errorsAsJson.toString()),
                  formData => {
                    val userId = request.identity.id // ignore form data userId (to prevent shenanigans)
                    Answer(
                      0,
                      formData.questionId,
                      formData.content,
                      userId,
                      null,
                      formData.createdLocal
                    ) // scalastyle:ignore null
                  }
                )
          )
          Future
            .sequence(newAnswers.map(answer => {
              // anyone with read access to question may submit answers (for now)
              accessRules.mayAccessQuestion(request.identity, answer.questionId, AccessLevel.Read) map {
                case false =>
                  throw new ForbiddenAccessException(s"Not allowed to answer question ${answer.questionId}")
                case true =>
                  answer
              }
            }))
            .flatMap(
              validatedAnswers =>
                answersRepo.addAll(validatedAnswers).map(answers => Created(Json.toJson(answers))) recover {
                  case e: IllegalArgumentException => BadRequest(JsonError(e.getMessage))
                }
            ) recover {
            case e: ForbiddenAccessException => Forbidden(JsonError(e.getMessage))
          }
        } catch {
          case e: IllegalArgumentException => Future.successful(BadRequest(JsonError(e.getMessage)))
          case e: Exception =>
            logger.error("Failed to create answer", e)
            Future.successful(InternalServerError(JsonError("Could not create answer")))
        }
      case _ => Future.successful(BadRequest(JsonError("Expected array of answers")))
    }
  }

  def delete(id: Long) = silhouette.SecuredAction(ForEditors && acls.withAnswerAccess(id, AccessLevel.Write)).async {
    implicit request =>
      answersRepo.delete(id).map { num => Ok(JsonSuccess(s"Deleted $num answer${if (num != 1) "s"}")) }
  }
}
