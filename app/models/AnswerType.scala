package models

import slick.jdbc.H2Profile.api._

object AnswerType extends Enumeration {
  type AnswerType = Value
  val Text = Value("text")
  // [0, 1] over ℝ
  val RangeContinuous = Value("range-continuous")
  // [1, 5] over ℕ
  val RangeDiscrete5 = Value("range-discrete-5")
  val MultipleChoice = Value("multiple-choice")

  implicit val answerTypeMapper = MappedColumnType.base[AnswerType, String](
    answerType => answerType.toString,
    answerTypeString => AnswerType.withName(answerTypeString)
  )
}
