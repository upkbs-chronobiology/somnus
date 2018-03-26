package models

import java.util.Calendar

import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.test.Injecting
import testutil.Authenticated
import testutil.FreshDatabase
import testutil.TestUtils

class AnswerSpec extends PlaySpec
  with GuiceOneAppPerSuite with Injecting with FreshDatabase with TestUtils with Authenticated {

  val questionsRepo = inject[QuestionsRepository]
  val answersRepo = inject[AnswersRepository]

  val TimeDelta = 5000 // [ms]

  "Answer" should {
    "automatically receive a close enough timestamp after single creation" in {
      val dummyQuestion = doSync(questionsRepo.add(Question(0, "Foobar?", AnswerType.Text)))
      val answer = Answer(0, dummyQuestion.id, "Baz.", this.baseUser.id, null)
      val newAnswer = doSync(answersRepo.add(answer))

      val now = Calendar.getInstance().getTimeInMillis
      newAnswer.created.getTime must be >= now - TimeDelta
      newAnswer.created.getTime must be <= now + TimeDelta
    }

    "automatically receive a close enough timestamp after bulk creation" in {
      val dummyQuestionA = doSync(questionsRepo.add(Question(0, "Foobar A?", AnswerType.Text)))
      val dummyQuestionB = doSync(questionsRepo.add(Question(0, "Foobar B?", AnswerType.Text)))

      val answerA = Answer(0, dummyQuestionA.id, "Baz A.", this.baseUser.id, null)
      val answerB = Answer(0, dummyQuestionB.id, "Baz B.", this.baseUser.id, null)
      val newAnswers = doSync(answersRepo.addAll(Seq(answerA, answerB)))

      newAnswers.length must equal(2)
      val now = Calendar.getInstance().getTimeInMillis
      newAnswers.foreach(newAnswer => {
        newAnswer.created.getTime must be >= now - TimeDelta
        newAnswer.created.getTime must be <= now + TimeDelta
      })
    }

    "reject text answers to continuous-number type questions" in {
      val question = doSync(questionsRepo.add(Question(0, "My question X", AnswerType.RangeContinuous)))

      an[IllegalArgumentException] shouldBe thrownBy {
        doSync(answersRepo.add(Answer(0, question.id, "This should not be text", this.baseUser.id, null)))
      }
    }

    "reject text answers to discrete-number type questions" in {
      val question = doSync(questionsRepo.add(Question(0, "My question X", AnswerType.RangeDiscrete5)))

      an[IllegalArgumentException] shouldBe thrownBy {
        doSync(answersRepo.add(Answer(0, question.id, "This should not be text", this.baseUser.id, null)))
      }
    }
  }
}
