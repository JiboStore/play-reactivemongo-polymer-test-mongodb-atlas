package controllers

import backend.PostMongoRepo
import controllers.PostFields.{Avatar, Favorite, Id, Text, Username}
import org.specs2.matcher.{Expectable, Matcher}
import org.specs2.mock.Mockito
import play.api.libs.json._
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test._
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.commands.LastError
import reactivemongo.bson.BSONDocument

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global

object PostsSpec extends org.specs2.mutable.Specification with Results with Mockito {

  val mockPostRepo = mock[PostMongoRepo]
  val reactiveMongoRepo = mock[ReactiveMongoApi]

  val FirstPostId = "5559e224cdecd3b535e8b681"
  val SecondPostId = "5559e224cdecd3b535e8b682"

  val wojciechPost = Json.obj(
    Id -> FirstPostId,
    Text -> "Have you heard about the Javeo?",
    Username -> "Wojciech",
    Avatar -> "../images/avatar-01.svg",
    Favorite -> true
  )
  val arunPost = Json.obj(
    Id -> SecondPostId,
    Text -> "Microservices: the good, the bad, and the ugly",
    Username -> "Arun",
    Avatar -> "../images/avatar-03.svg",
    Favorite -> false
  )
  val controller = new TestController()
  val nothingHappenedLastError = new LastError(true, None, None, None, 0, None, false, None, None, false, None, None)

  case class PostBSONDocumentMatcher(expected: BSONDocument) extends Matcher[BSONDocument] {
    override def apply[S <: BSONDocument](t: Expectable[S]) = {
      result(evaluate(t),
        t.description + " is valid",
        t.description + " is not valid",
        t)
    }

    def evaluate[S <: BSONDocument](t: Expectable[S]): Boolean = {
      t.value.get(Text) === expected.get(Text) &&
        t.value.get(Username) === expected.get(Username) &&
        t.value.get(Avatar) === expected.get(Avatar)
    }
  }

  class TestController() extends Posts(reactiveMongoRepo) {
    override def postRepo: PostMongoRepo = mockPostRepo
  }

  "Posts Page#list" should {
    "list posts" in {
      mockPostRepo.find()(any[ExecutionContext]) returns Future(List(wojciechPost, arunPost))

      val result: Future[Result] = controller.list().apply(FakeRequest())

      contentAsJson(result) must be equalTo JsArray(List(arunPost, wojciechPost))
    }
  }

  "Posts Page#delete" should {
    "remove post" in {
      mockPostRepo.remove(any[BSONDocument])(any[ExecutionContext]) returns Future(nothingHappenedLastError)

      val result: Future[Result] = controller.delete(FirstPostId).apply(FakeRequest())

      status(result) must be equalTo SEE_OTHER
      redirectLocation(result) must beSome(routes.Posts.list().url)
      there was one(mockPostRepo).remove(any[BSONDocument])(any[ExecutionContext])
    }
  }


  "Posts Page#add" should {
    "create post" in {
      mockPostRepo.save(any[BSONDocument])(any[ExecutionContext]) returns Future(nothingHappenedLastError)
      val post = Json.obj(
        Text -> "Loving basketball",
        Username -> "Martin",
        Avatar -> "avatar.svg"
      )

      val request = FakeRequest().withBody(post)
      val result: Future[Result] = controller.add()(request)

      status(result) must be equalTo SEE_OTHER
      redirectLocation(result) must beSome(routes.Posts.list().url)

      val document = BSONDocument(Text -> "Loving basketball", Username -> "Martin", Avatar -> "avatar.svg")
      there was one(mockPostRepo).save(argThat(PostBSONDocumentMatcher(document)))(any[ExecutionContext])
    }
  }

  "Posts Page#like" should {
    "like post" in {
      mockPostRepo.update(any[BSONDocument], any[BSONDocument])(any[ExecutionContext]) returns Future(nothingHappenedLastError)

      val request = FakeRequest().withBody(Json.obj("favorite" -> true))
      val result: Future[Result] = controller.like(SecondPostId)(request)

      status(result) must be equalTo OK
      contentAsJson(result) must be equalTo Json.obj("success" -> true)
      there was one(mockPostRepo).update(any[BSONDocument], any[BSONDocument])(any[ExecutionContext])
    }
  }
}
