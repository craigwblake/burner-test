package test

import java.util.concurrent.atomic._
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock._
import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner._
import play.api.libs.json._
import play.api.libs.json.Json._
import scala.concurrent._
import test.EventType._
import test.SaveUrlStatus._

@RunWith(classOf[JUnitRunner])
class ImplicitsSpec extends Specification {
  import Implicits._

  "Json formats" should {

    "deserialize event types" in {
      fromJson[EventType.Value](JsString("inboundMedia")) must equalTo (JsSuccess(InboundMedia))
      fromJson[EventType.Value](JsString("inboundText")) must equalTo (JsSuccess(InboundText))
      fromJson[EventType.Value](JsString("voiceMail")) must equalTo (JsSuccess(VoiceMail))
    }

    "deserialize valid event objects" in {
      val event = """{
        "type": "inboundMedia",
        "payload": "payloadString",
        "fromNumber": "fromNumberString",
        "toNumber": "toNumberString",
        "userId": "userIdString",
        "burnerId": "burnerIdString"
      }"""

      val deserialized = fromJson[Event](parse(event)).get
      deserialized.eventType must equalTo (InboundMedia)
      deserialized.payload must equalTo ("payloadString")
      deserialized.fromNumber must equalTo ("fromNumberString")
      deserialized.toNumber must equalTo ("toNumberString")
      deserialized.userId must equalTo ("userIdString")
      deserialized.burnerId must equalTo ("burnerIdString")
    }

    "fail to deserialize event with invalid type" in {
      val event = """{
        "type": "invalidType",
        "payload": "payloadString",
        "fromNumber": "fromNumberString",
        "toNumber": "toNumberString",
        "userId": "userIdString",
        "burnerId": "burnerIdString"
      }"""

      fromJson[Event](parse(event)) must haveClass[JsError]
    }

    "fail to deserialize event with a missing value" in {
      val event = """{
        "type": "inboundMedia",
        "payload": "payloadString",
        "fromNumber": "fromNumberString",
        "toNumber": "toNumberString",
        "userId": "userIdString"
      }"""

      fromJson[Event](parse(event)) must haveClass[JsError]
    }

    "deserialize URL status" in {
      fromJson[SaveUrlStatus.Value](JsString("PENDING")) must equalTo (JsSuccess(PENDING))
      fromJson[SaveUrlStatus.Value](JsString("DOWNLOADING")) must equalTo (JsSuccess(DOWNLOADING))
      fromJson[SaveUrlStatus.Value](JsString("COMPLETE")) must equalTo (JsSuccess(COMPLETE))
      fromJson[SaveUrlStatus.Value](JsString("FAILED")) must equalTo (JsSuccess(FAILED))
    }

    "serialize and deserialize a save URL response" in {
      val response = SaveUrlResponse(PENDING, "jobString", None)

      val json = toJson(response).toString
      json must equalTo ("""{"status":"PENDING","job":"jobString"}""")

      fromJson[SaveUrlResponse](parse(json)) must equalTo (JsSuccess(response))
    }

    "serialize and deserialize a save URL response with a name field" in {
      val response = SaveUrlResponse(PENDING, "jobString", Some("nameString"))

      val json = toJson(response).toString
      json must equalTo ("""{"status":"PENDING","job":"jobString","name":"nameString"}""")

      fromJson[SaveUrlResponse](parse(json)) must equalTo (JsSuccess(response))
    }

    "serialize and deserialize a save URL job response" in {
      val response = SaveUrlJobResponse(PENDING, None)

      val json = toJson(response).toString
      json must equalTo ("""{"status":"PENDING"}""")

      fromJson[SaveUrlJobResponse](parse(json)) must equalTo (JsSuccess(response))
    }

    "serialize and deserialize a save URL job response with error" in {
      val response = SaveUrlJobResponse(FAILED, Some("failure"))

      val json = toJson(response).toString
      json must equalTo ("""{"status":"FAILED","error":"failure"}""")

      fromJson[SaveUrlJobResponse](parse(json)) must equalTo (JsSuccess(response))
    }

    "serialize atomic longs" in {
      toJson(new AtomicLong(15234L)) must equalTo (JsNumber(15234L))
    }
  }

  "RichOption" should {

    "convert to a future from both states" in { implicit ee: ExecutionEnv =>
      Some("testing").toFuture("failure") must equalTo("testing").await
      None.toFuture("failure") must throwAn[Exception].await
    }

    "convert to a thrown exception when empty" in {
      val option: Option[String] = None
      option.orThrow("failure") must throwAn[Exception]
    }

    "convert to the value when some" in {
      Some("testing").orThrow("failure") must equalTo("testing")
    }
  }

  "RichJSResult" should {

    "convert to a future from both states" in { implicit ee: ExecutionEnv =>
      JsSuccess("testing").toFuture must equalTo("testing").await
      JsError("failure").toFuture must throwAn[Exception].await
    }
  }
}
