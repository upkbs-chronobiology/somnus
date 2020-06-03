package models

import slick.jdbc.MySQLProfile.api._

object AnswerType extends Enumeration {
  type AnswerType = Value
  val Text = Value("text")
  // [min, max] over ℝ
  val RangeContinuous = Value("range-continuous")
  // [min, max] over ℕ
  val RangeDiscrete = Value("range-discrete")
  val MultipleChoiceSingle = Value("multiple-choice-single")
  val MultipleChoiceMany = Value("multiple-choice-many")
  val TimeOfDay = Value("time-of-day")
  val Date = Value("date")

  implicit val answerTypeMapper = MappedColumnType.base[AnswerType, String](
    answerType => answerType.toString,
    answerTypeString => AnswerType.withName(answerTypeString)
  )
}
