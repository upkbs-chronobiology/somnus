package v1.question

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import auth.DefaultEnv
import auth.acl.AccessRules
import auth.acl.Acls
import auth.roles.ForEditors
import com.mohiva.play.silhouette.api.Silhouette
import javax.inject.Inject
import models.AccessLevel
import models.AccessLevel.AccessLevel
import models.Question
import models.QuestionForm
import models.QuestionsRepository
import models.User
import play.api.libs.json.Json
import play.api.mvc.Result
import util.EmptyPreservingReads
import util.Futures.TraversableFutureExtensions
import util.InclusiveRange
import util.JsonError
import util.JsonSuccess
import util.Serialization
import v1.RestBaseController
import v1.RestControllerComponents

class QuestionController @Inject()(
  rcc: RestControllerComponents,
  silhouette: Silhouette[DefaultEnv],
  questionsRepo: QuestionsRepository,
  accessRules: AccessRules,
  acls: Acls
)(implicit ec: ExecutionContext)
  extends RestBaseController(rcc) {

  implicit val _ = EmptyPreservingReads.readsStringSeq

  def index = silhouette.SecuredAction(ForEditors).async { implicit request =>
    // XXX: This could be done more efficiently if listing by readable questionnaires/studies rather than all and then filtering
    questionsRepo.listAll
      .filterTraversableAsync(q => accessRules.mayAccessQuestion(request.identity, q.id, AccessLevel.Read))
      .map(questions => Ok(Json.toJson(questions)))
  }

  def get(id: Long) = silhouette.SecuredAction(ForEditors && acls.withQuestionAccess(id, AccessLevel.Read)).async { implicit request =>
    questionsRepo.get(id) map {
      case None => BadRequest("Question not found")
      case Some(question) => Ok(Json.toJson(question))
    } recover {
      case _: IllegalArgumentException => NotFound(JsonError(s"No question with id: $id"))
    }
  }

  def add = silhouette.SecuredAction(ForEditors).async { implicit request =>
    QuestionForm.form.bindFromRequest().fold(
      badForm => Future.successful(BadRequest(badForm.errorsAsJson)),
      formData => checkAccess(request.identity, AccessLevel.Write, formData.questionnaireId, {
        val answerLabels = formData.answerLabels.map(Serialization.serialize)
        val answerRange = formData.answerRange.map(range => Serialization.serialize(InclusiveRange(range.min, range.max)))
        questionsRepo.add(Question(0, formData.content, formData.answerType, answerLabels, answerRange, formData.questionnaireId)) map { newQuestion =>
          Created(Json.toJson(newQuestion)) // XXX: And location header?
        } recover {
          case e: IllegalArgumentException => BadRequest(JsonError(s"Could not create question: ${e.getMessage}"))
        }
      })
    )
  }

  def update(id: Long) = silhouette.SecuredAction(ForEditors && acls.withQuestionAccess(id, AccessLevel.Write)).async { implicit request =>
    QuestionForm.form.bindFromRequest().fold(
      badForm => Future.successful(BadRequest(badForm.errorsAsJson)),
      formData => {
        val answerLabels = formData.answerLabels.map(Serialization.serialize)
        val answerRange = formData.answerRange.map(range => Serialization.serialize(InclusiveRange(range.min, range.max)))
        questionsRepo.update(Question(id, formData.content, formData.answerType, answerLabels, answerRange, formData.questionnaireId)).map { q =>
          Ok(Json.toJson(q))
        } recover {
          case e: IllegalArgumentException => BadRequest(JsonError(s"Could not update question: ${e.getMessage}"))
        }
      }
    )
  }

  def delete(id: Long) = silhouette.SecuredAction(ForEditors && acls.withQuestionAccess(id, AccessLevel.Write)).async { implicit request =>
    questionsRepo.delete(id)
      .map(num => Ok(JsonSuccess(s"Deleted $num ${if (num == 1) "entry" else "entries"}")))
      .recover {
        case e: IllegalArgumentException => BadRequest(JsonError(e.getMessage))
      }
  }

  private def checkAccess(user: User, accessLevel: AccessLevel, questionnaireId: Option[Long], ifSo: => Future[Result]): Future[Result] = {
    questionnaireId match {
      case Some(id) =>
        accessRules.mayAccessQuestionnaire(user, id, accessLevel) flatMap {
          case false => Future.successful(Forbidden(JsonError(s"Permission denied for questionnaire $id")))
          case true => ifSo
        }
      case None => ifSo
    }
  }
}
