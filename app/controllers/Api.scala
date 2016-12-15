package controllers

import akka.actor.ActorSystem
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import play.api.{Configuration, Logger}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json.toJson
import play.api.libs.ws.WSClient
import play.api.mvc.{Action, Controller, Result}
import play.api.mvc.BodyParsers.parse.json
import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.Future.{successful}
import scala.concurrent.duration._
import test._
import test.EventType._
import test.SaveUrlStatus._

/** REST API service controller definition. This controller implements the `event` and `report` REST endpoints,
  * which allow for submitting/voting on images and retrieving vote results respectively.
  */
class Api @Inject() (implicit config: Configuration, ws: WSClient, as: ActorSystem) extends Controller {
  import Implicits._

  // For now we just fail fast if the required config values are missing
  val folder = config.getString("dropboxFolder").orThrow("""missing "dropboxFolder" configuration value""")
  val token = config.getString("dropboxToken").orThrow("""missing "dropboxToken" configuration value""")
  val db = Dropbox(folder, token)

  /** Votes are tallied in a thread-safe map which contains atomic counters. This is so we can easily
    * (and efficiently) deal with concurrent mutation without adding explicit locking.
    */
  val votes = new ConcurrentHashMap[String, AtomicLong]()

  // Processes an incoming event.
  def event = Action.async(json) { request =>
    for {
      event <- request.body.validate[Event].toFuture
      result <- handleEvent(db, event)
    } yield result
  }

  // Returns a report of images and votes.
  def report = Action { request =>
    Ok(toJson(votes.asScala.toMap))
  }

  /** Add a counter to the votes store and mutate it's value if one does not exist, in a thread-safe manner. If a
    * counter does exist (which would typically only be the case under concurrent writes) we'll mutate it's value
    * instead.
    *
    * @param name The name of the key in the voter store.
    * @param count The count we want to set for (or increment to, if it exists) the counter.
    */
  private def setIfAbsent(name: String, count: Long) = Option(votes.putIfAbsent(name, new AtomicLong(count))) match {
    case None => // The key did not exist, so we are done.
    case Some(counter) =>
      // The key already existed, so we increment it.
      counter.addAndGet(count)
  }

  /** Handles an incoming Burner event by initiating a URL save request, or assigning a vote.
    *
    * @param db The Dropbox client instance to use.
    * @param event The Burner event to handle.
    */
  private def handleEvent(db: Dropbox, event: Event): Future[Result] = event match {

    // For an inbound media event, kick of an asynchronous URL save request.
    case Event(InboundMedia, url, _, _, _, _) =>
      Logger.info(s"initiating asynchronous retrieval of file data into Dropbox")
      db.saveUrl(url).map {
        case SaveUrlResponse(_, job, Some(name)) =>
          monitorStatus(db, job, name)
          setIfAbsent(name, 0L)
          Ok
      }

    // For an inbound media event, kick of an asynchronous URL save request.
    case Event(InboundText, name, _, _, _, _) =>

      Option(votes.get(name)) match {

        // If we have a count for this name already, start using it
        case Some(count) =>
          Logger.info(s"registering vote for image $name")
          count.incrementAndGet
          successful(Ok)

        // Otherwise, fall back to checking Dropbox for an image we are missing.
        case None =>
          Logger.info(s"name is not in local store, falling back to check Dropbox")
          db.fileExists(name).map {
            // If the named file exists in Dropbox, it's valid.
            case true =>
              setIfAbsent(name, 1L)
              Ok
            // Otherwise we ignore the message.
            case false =>
              Logger.info(s"ignoring text with invalid name")
              Ok
          }
      }

    // Ignore any other events.
    case event =>
      Logger.info(s"skipping handling of non-media message: $event")
      successful(Ok)
  }

  /** Monitors the progress of an asynchronous URL fetch and cleans up hashmap
    * entries in the case of an eventual failure.
    *
    * @param db The Dropbox client instance to use.
    * @param job The id of the job we want to check.
    * @param name The name of the file that the job represents.
    */
  private def monitorStatus(db: Dropbox, job: String, name: String): Future[Unit] =
    db
      .saveUrlStatus(job)
      .map {
        // When the job in complete, simply log and end the task.
        case SaveUrlJobResponse(COMPLETE, _) =>
          Logger.info(s"asynchronous URL fetch to Dropbox has completed")

        // When a job fails, clean up the temporary entry from the votes store and log.
        case SaveUrlJobResponse(FAILED, errorOpt) =>
          val error = errorOpt.getOrElse("")
          Logger.warn(s"asynchronous URL fetch to Dropbox failed: $error")
          votes.remove(name)

        // While a job is in progress, just reattempt after a short timeout
        case SaveUrlJobResponse(_, _) =>
          Logger.warn(s"asynchronous URL save job $job is still in progress")
          as.scheduler.scheduleOnce(10 seconds) { monitorStatus(db, job, name) }
      }
}
