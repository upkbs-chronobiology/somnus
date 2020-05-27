package auth.acl

import javax.inject.Inject
import javax.inject.Singleton

import models.AccessLevel.AccessLevel

@Singleton
class Acls @Inject()(implicit accessRules: AccessRules) {

  def withUserAccess(userId: Long, level: AccessLevel) = WithUserAccess(userId, level)

  def withStudyAccess(studyId: Long, level: AccessLevel) = WithStudyAccess(studyId, level)

  def withQuestionnaireAccess(questionnaireId: Long, level: AccessLevel) =
    WithQuestionnaireAccess(questionnaireId, level)

  def withQuestionAccess(questionId: Long, level: AccessLevel) =
    WithQuestionAccess(questionId, level)

  def withAnswerAccess(answerId: Long, level: AccessLevel) =
    WithAnswerAccess(answerId, level)

  def withScheduleAccess(scheduleId: Long, level: AccessLevel) =
    WithScheduleAccess(scheduleId, level)
}
