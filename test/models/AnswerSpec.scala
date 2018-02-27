package models

import java.util.Calendar

import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import testutil.Authenticated
import testutil.FreshDatabase
import testutil.TestUtils
import testutil.FreshDatabase
import testutil.TestUtils

class AnswerSpec extends PlaySpec
  with GuiceOneAppPerSuite with FreshDatabase with TestUtils with Authenticated {

  val TimeDelta = 5000 // [ms]

  "Answer" should {
    "automatically receive a close enough timestamp after single creation" in {
      val dummyQuestion = doSync(Questions.add(Question(0, "Foobar?")))
      val answer = Answer(0, dummyQuestion.id, "Baz.", this.baseUser.id, null)
      val newAnswer = doSync(Answers.add(answer))

      val now = Calendar.getInstance().getTimeInMillis
      newAnswer.created.getTime must be >= now - TimeDelta
      newAnswer.created.getTime must be <= now + TimeDelta
    }

    "automatically receive a close enough timestamp after bulk creation" in {
      val dummyQuestionA = doSync(Questions.add(Question(0, "Foobar A?")))
      val dummyQuestionB = doSync(Questions.add(Question(0, "Foobar B?")))

      val answerA = Answer(0, dummyQuestionA.id, "Baz A.", this.baseUser.id, null)
      val answerB = Answer(0, dummyQuestionB.id, "Baz B.", this.baseUser.id, null)
      val newAnswers = doSync(Answers.addAll(Seq(answerA, answerB)))

      newAnswers.length must equal(2)
      val now = Calendar.getInstance().getTimeInMillis
      newAnswers.foreach(newAnswer => {
        newAnswer.created.getTime must be >= now - TimeDelta
        newAnswer.created.getTime must be <= now + TimeDelta
      })
    }
  }
}
