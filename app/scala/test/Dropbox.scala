package test

import akka.stream.scaladsl.Source
import java.net.URL
import play.api.http.HeaderNames.AUTHORIZATION
import play.api.libs.json.Json.fromJson
import play.api.libs.ws.{WSClient, WSRequest, WSRequestExecutor, WSRequestFilter, WSResponse}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.concurrent.Future.{failed, successful}
import scala.language.postfixOps

// Set of possible status values for an asynchronous `save_url` call.
object SaveUrlStatus extends Enumeration {
  type SaveUrlStatus = Value
  val PENDING, DOWNLOADING, COMPLETE, FAILED = Value
}

// Structure of the response JSON for a `save_url` call, plus an extra field to communicate file name.
case class SaveUrlResponse(status: SaveUrlStatus.Value, job: String, name: Option[String])

// Structure of the response JSON for a `save_url_job` call.
case class SaveUrlJobResponse(status: SaveUrlStatus.Value, error: Option[String])

/** Class containing the state necessary to interact with the Dropbox REST API.
  *
  * @param folder The target folder in the Dropboc account where files will be stored.
  * @param token The App authentication token issued by Dropbox, necessary to authenticate against the Dropbox REST API.
  */
case class Dropbox(folder: String, token: String) {
  import test.Implicits._

  val Bearer = "Bearer"
  val Url = "url"
  val DropboxUrl = "https://api.dropboxapi.com/1"
  val Timeout = 5 seconds

  /** Initiates an asynchronous process to store the data from a URL in a Dropbox folder.
    *
    *  @param targetUrl The URL which points at the data to be stored in Dropbox.
    */
  def saveUrl(targetUrl: String)(implicit ws: WSClient, ec: ExecutionContext): Future[SaveUrlResponse] = {
    // Execute the URL parsing in a future to simplify error handling
    Future { new URL(targetUrl).getFile.split("/").last } flatMap { name =>
      ws
        .url(s"$DropboxUrl/save_url/auto/$folder/$name")
        .withQueryString(Url -> targetUrl)
        .withRequestFilter(PrepareRequest)
        .post(Source.empty)
        .flatMap {
          case response if response.status >= 200 && response.status < 300 =>
            fromJson[SaveUrlResponse](response.json)
              .map(_.copy(name = Some(name)))
              .toFuture
          case response => fail(response)
        }
    }
  }

  /** Retreives the status of the asynchronous URL retrieval and storage operation.
    *
    *  @param job The identifier of the job for which to retrieve status.
    */

  def saveUrlStatus(job: String)(implicit ws: WSClient, ec: ExecutionContext): Future[SaveUrlJobResponse] = {
    ws
      .url(s"$DropboxUrl/save_url_job/$job")
      .withRequestFilter(PrepareRequest)
      .get()
      .flatMap {
        case response if response.status >= 200 && response.status < 300 => fromJson[SaveUrlJobResponse](response.json).toFuture
        case response => fail(response)
      }
  }

  /** Checks to see if a file exists in Dropbox within the configured folder. Returns `true` if a file exists at that path,
    * and `false` otherwise.
    *
    *  @param name The name of the file to check for in the target folder.
    */
  def fileExists(name: String)(implicit ws: WSClient, ec: ExecutionContext): Future[Boolean] = {
    ws
      .url(s"$DropboxUrl/metadata/auto/$folder/$name")
      .withRequestFilter(PrepareRequest)
      .get()
      .flatMap {
        case response if response.status >= 200 && response.status < 300 => successful(true)
        case response if response.status == 404 => successful(false)
        case response => fail(response)
      }
  }

  /** Returns a failed `Future` with a relevant message.
    *
    * @param response The failed response.
    */
  private def fail[T](response: WSResponse): Future[T] = failed(new Exception(s"request failed with status ${response.status}: ${response.body}"))

  /** Prepares a web service request with some common settings. We enable following redirects,
    * set a short request timeout, and set the required authorization header.
    */
  object PrepareRequest extends WSRequestFilter {
    
    def apply(executor: WSRequestExecutor): WSRequestExecutor = new WSRequestExecutor {
      def execute(request: WSRequest): Future[WSResponse] =
        executor execute {
          request
            .withHeaders(AUTHORIZATION -> s"$Bearer $token")
            .withRequestTimeout(Timeout)
            .withFollowRedirects(true)
        }
    }
  }
}
