package controllers

import java.util.concurrent.atomic._
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock._
import org.specs2.mutable._
import org.specs2.runner._
import org.specs2.specification.AfterAll
import org.junit.runner._
import play.api.Configuration
import play.api.libs.json._
import play.api.libs.json.Json._
import play.api.test._
import play.api.test.Helpers._
import test._
import test.EventType._
import test.SaveUrlStatus._
import test.TestUtil._

@RunWith(classOf[JUnitRunner])
class ApiSpec extends Specification with AfterAll {
  import ConsoleSupport._
  import Implicits._

  def afterAll = ConsoleSupport.close

  val config = Configuration("dropboxFolder" -> "test/folder", "dropboxToken" -> "testToken")

  "Api controller" should {

    "output a valid empty report" in {
      val api = new Api()(config, null, null)
      val result = api.report().apply(FakeRequest()) 
      contentAsString(result) must equalTo ("{}")
    }

    "output a valid populated report" in {
      val api = new Api()(config, null, null)
      api.votes.put("test1", new AtomicLong(5L))
      api.votes.put("test2", new AtomicLong(15L))
      val result = api.report().apply(FakeRequest()) 
      contentAsString(result) must equalTo ("""{"test2":15,"test1":5}""")
    }

    "handle an incoming media event" in { implicit ee: ExecutionEnv =>
      withDropbox { implicit client => dropbox => 
        val api = new Api()(config, client, null) {
          override val db = dropbox
        }

        val request = FakeRequest("POST", "/event")
          .withHeaders(CONTENT_TYPE -> "application/json")
          .withJsonBody(parse(s"""{
    "type": "inboundMedia",
    "payload": "$ValidUrl",
    "fromNumber": "+13237463190",
    "toNumber": "+14432304720",
    "burnerId": "79f8c5be-5721-4b99-ab45-bd96114fd78e",
    "userId": "c0ca0fbf-8151-40db-afc8-7eab4cbe9ff5"
}"""))

        val result = Helpers.call(api.event, request)
        status(result) must equalTo (OK)
      }
    }

    "handle an invalid vote event" in { implicit ee: ExecutionEnv =>
      withDropbox { implicit client => dropbox => 

        val api = new Api()(config, client, null) {
          override val db = dropbox
        }

        val request = FakeRequest("POST", "/event")
          .withHeaders(CONTENT_TYPE -> "application/json")
          .withBody(s"""{
    "type": "inboundText",
    "payload": "$InvalidImageName",
    "fromNumber": "+13237463190",
    "toNumber": "+14432304720",
    "burnerId": "79f8c5be-5721-4b99-ab45-bd96114fd78e",
    "userId": "c0ca0fbf-8151-40db-afc8-7eab4cbe9ff5"
}""")

        val result1 = Helpers.call(api.event, request)
        status(result1) must equalTo (OK)

        val result2 = api.report().apply(FakeRequest()) 
        contentAsString(result2) must equalTo ("""{}""")
      }
    }

    "handle an valid vote event" in { implicit ee: ExecutionEnv =>
      withDropbox { implicit client => dropbox => 

        val api = new Api()(config, client, null) {
          override val db = dropbox
        }

        val request = FakeRequest("POST", "/event")
          .withHeaders(CONTENT_TYPE -> "application/json")
          .withBody(s"""{
    "type": "inboundText",
    "payload": "$ValidImageName",
    "fromNumber": "+13237463190",
    "toNumber": "+14432304720",
    "burnerId": "79f8c5be-5721-4b99-ab45-bd96114fd78e",
    "userId": "c0ca0fbf-8151-40db-afc8-7eab4cbe9ff5"
}""")

        val result1 = Helpers.call(api.event, request)
        status(result1) must equalTo (OK)

        val result2 = api.report().apply(FakeRequest()) 
        contentAsString(result2) must equalTo ("""{"image.png":1}""")
      }
    }

    "handle two valid vote events" in { implicit ee: ExecutionEnv =>
      withDropbox { implicit client => dropbox => 

        val api = new Api()(config, client, null) {
          override val db = dropbox
        }

        val request = FakeRequest("POST", "/event")
          .withHeaders(CONTENT_TYPE -> "application/json")
          .withBody(s"""{
    "type": "inboundText",
    "payload": "$ValidImageName",
    "fromNumber": "+13237463190",
    "toNumber": "+14432304720",
    "burnerId": "79f8c5be-5721-4b99-ab45-bd96114fd78e",
    "userId": "c0ca0fbf-8151-40db-afc8-7eab4cbe9ff5"
}""")

        val result1 = Helpers.call(api.event, request)
        status(result1) must equalTo (OK)
        val result2 = Helpers.call(api.event, request)
        status(result2) must equalTo (OK)

        val result3 = api.report().apply(FakeRequest()) 
        contentAsString(result3) must equalTo ("""{"image.png":2}""")
      }
    }
  
    "handle an ignored event" in { implicit ee: ExecutionEnv =>
      withDropbox { implicit client => dropbox => 

        val api = new Api()(config, client, null) {
          override val db = dropbox
        }

        val request = FakeRequest("POST", "/event")
          .withHeaders(CONTENT_TYPE -> "application/json")
          .withBody("""{
    "type": "voiceMail",
    "payload": "anything",
    "fromNumber": "+13237463190",
    "toNumber": "+14432304720",
    "burnerId": "79f8c5be-5721-4b99-ab45-bd96114fd78e",
    "userId": "c0ca0fbf-8151-40db-afc8-7eab4cbe9ff5"
}""")

        val result1 = Helpers.call(api.event, request)
        status(result1) must equalTo (OK)
      }
    }
  }
}
