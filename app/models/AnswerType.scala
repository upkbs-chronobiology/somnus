package models

import slick.jdbc.H2Profile.api._

object AnswerType extends Enumeration {
  type AnswerType = Value
  val Text = Value("text")
  // [min, max] over ℝ
  val RangeContinuous = Value("range-continuous")
  // [min, max] over ℕ
  val RangeDiscrete = Value("range-discrete")
  val MultipleChoice = Value("multiple-choice")

  implicit val answerTypeMapper = MappedColumnType.base[AnswerType, String](
    answerType => answerType.toString,
    answerTypeString => AnswerType.withName(answerTypeString)
  )
}
