package test

import java.util.concurrent.atomic.AtomicLong
import play.api.libs.functional.syntax._
import play.api.libs.json.Json.format
import play.api.libs.json.{JsNumber, JsPath, JsResult, Reads, Writes}
import play.api.libs.json.Reads.enumNameReads
import scala.compat.Platform.EOL
import scala.concurrent.Future
import scala.concurrent.Future.{failed, successful}

// Contains implicit conversions and formats.
object Implicits {

  implicit val eventTypeReads = enumNameReads(EventType)
  implicit val saveUrlStatusReads = enumNameReads(SaveUrlStatus)
  implicit val saveUrlResponseFormat = format[SaveUrlResponse]
  implicit val saveUrlJobResponseFormat = format[SaveUrlJobResponse]

  // We need an explicit `Reads` definition so we can map the `type` field to something valid in Scala.
  implicit val eventReads: Reads[Event] = (
    (JsPath \ "type").read[EventType.Value] and
    (JsPath \ "payload").read[String] and
    (JsPath \ "fromNumber").read[String] and
    (JsPath \ "toNumber").read[String] and
    (JsPath \ "userId").read[String] and
    (JsPath \ "burnerId").read[String]
  )(Event.apply _)

  implicit val atomicLongWrites = new Writes[AtomicLong] {
    def writes(value: AtomicLong) = JsNumber(value.get)
  }

  // Enriches the `Option` type to enable conversion to a `Future` so we can compose them.
  implicit class RichOption[T](val option: Option[T]) extends AnyVal {
    def toFuture(message: String): Future[T] = option.map(successful(_)).getOrElse(failed(new Exception(message)))
    def orThrow(message: String): T = option.getOrElse(throw new Exception(message))
  }

  // Enriches the `JsResult` type to enable conversion to a `Future` so we can compose them.
  implicit class RichJsResult[T](val result: JsResult[T]) extends AnyVal {

    def toFuture: Future[T] =
      result
        .map(successful(_))
        .recoverTotal { error =>
          val message = error.errors map { case (path, errors) => s"$path => ${errors.mkString(", ")}" } mkString(EOL)
          failed(new Exception(message))
        }
  }
}
