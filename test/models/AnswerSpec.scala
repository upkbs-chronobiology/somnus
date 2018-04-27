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

  val questionnairesRepo = inject[QuestionnaireRepository]
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
      val question = doSync(questionsRepo.add(Question(0, "My question X", AnswerType.RangeDiscrete, answerRange = Some("1,3"))))

      an[IllegalArgumentException] shouldBe thrownBy {
        doSync(answersRepo.add(Answer(0, question.id, "This should not be text", this.baseUser.id, null)))
      }
    }

    "list by user and questionnaire" in {
      val questionnaire1 = doSync(questionnairesRepo.create(Questionnaire(0, "Questionnnaire 1", None)))
      val questionnaire2 = doSync(questionnairesRepo.create(Questionnaire(0, "Questionnnaire 2", None)))

      val question1 = doSync(questionsRepo.add(Question(0, "Question 1", AnswerType.Text, questionnaireId = Some(questionnaire1.id))))
      val question2 = doSync(questionsRepo.add(Question(0, "Question 2", AnswerType.Text, questionnaireId = Some(questionnaire2.id))))
      val dummyQuestion = doSync(questionsRepo.add(Question(0, "Dummy", AnswerType.Text)))

      doSync(answersRepo.add(Answer(0, question1.id, "Answer 1 base", baseUser.id, null)))
      doSync(answersRepo.add(Answer(0, question2.id, "Answer 2 base", baseUser.id, null)))
      doSync(answersRepo.add(Answer(0, question1.id, "Answer 1 researcher", researchUser.id, null)))
      doSync(answersRepo.add(Answer(0, question2.id, "Answer 2 researcher", researchUser.id, null)))
      doSync(answersRepo.add(Answer(0, question1.id, "Dummy", adminUser.id, null)))
      doSync(answersRepo.add(Answer(0, dummyQuestion.id, "Dummy", baseUser.id, null)))

      val answersBase1 = doSync(answersRepo.listByUserAndQuestionnaire(baseUser.id, questionnaire1.id)).map(_.content)
      answersBase1.length must equal(1)
      answersBase1.head must equal("Answer 1 base")

      val answersBase2 = doSync(answersRepo.listByUserAndQuestionnaire(baseUser.id, questionnaire2.id)).map(_.content)
      answersBase2.length must equal(1)
      answersBase2.head must equal("Answer 2 base")

      val answersResearch1 = doSync(answersRepo.listByUserAndQuestionnaire(researchUser.id, questionnaire1.id)).map(_.content)
      answersResearch1.length must equal(1)
      answersResearch1.head must equal("Answer 1 researcher")

      val answersResearch2 = doSync(answersRepo.listByUserAndQuestionnaire(researchUser.id, questionnaire2.id)).map(_.content)
      answersResearch2.length must equal(1)
      answersResearch2.head must equal("Answer 2 researcher")
    }
  }
}
