package models

import slick.jdbc.H2Profile.api._
import slick.lifted.Tag

case class StudyParticipant(userId: Long, studyId: Long)

class StudyParticipantsTable(tag: Tag) extends Table[StudyParticipant](tag, "study_participants") {
  def userId = column[Long]("user_id")
  def studyId = column[Long]("study_id")

  override def * = (userId, studyId) <> (StudyParticipant.tupled, StudyParticipant.unapply)
}
