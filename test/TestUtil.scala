package test

import play.api.http.HeaderNames._
import play.api.http.Status._
import play.api.libs.json.Json._
import play.api.libs.ws.WSClient
import play.api.mvc._
import play.api.mvc.Results._
import play.api.routing.sird._
import play.api.test._
import play.core.server.Server
import test.SaveUrlStatus._

object TestUtil {

  val TestFolder = "test/folder"
  val TestToken = "testToken"
  val ValidImageName = "image.png"
  val InvalidImageName = "NotAnImage"
  val ValidUrl = s"http://test.com/$ValidImageName"
  val InvalidUrl = "NotAUrl"
  val PendingJob = "PEiuxsfaISEAAAAAAADw7g"
  val DownloadingJob = "PEiuxsfaISEAAAAAAADw7h"
  val CompleteJob = "PEiuxsfaISEAAAAAAADw7i"
  val FailedJob = "PEiuxsfaISEAAAAAAADw7j"
  val InvalidJob = "NotAJob"
  val FailedJobMessage = "job failed"

  /** Executes a test block with a mock service implementation which mimics
    * the Dropbox API, and a Dropbox client instance that is configured to
    * issue requests to it.
    */
  def withDropbox[T](block: WSClient => Dropbox => T): T = {
    Server.withRouter() {
      case POST(p"/save_url/auto/$path*" ? q"url=$url") => Action {
        // Does not work currently because of error when using POST from SIRD with a bound request value.
        //withAuthorization(_) {
          path match {
            case ex"$TestFolder/$ValidImageName" => Ok(obj("status" -> PENDING, "job" -> PendingJob))
            case path => NotFound(s"incorrect path: $path")
          }
        //}
      }

      case GET(p"/save_url_job/$job*") => Action {
        withAuthorization(_) {
          job match {
            case PendingJob => Ok(obj("status" -> PENDING))
            case DownloadingJob => Ok(obj("status" -> DOWNLOADING))
            case CompleteJob => Ok(obj("status" -> COMPLETE))
            case FailedJob => Ok(obj("status" -> FAILED, "error" -> FailedJobMessage))
            case InvalidJob => NotFound(s"incorrect job: $InvalidJob")
          }
        }
      }

      case GET(p"/metadata/auto/$path*") => Action {
        withAuthorization(_) {
          path match {
            case ex"$TestFolder/$ValidImageName" => Ok(obj())
            case path => NotFound(s"incorrect path: $path")
          }
        }
      }
    } { implicit port =>

      val db = new Dropbox(TestFolder, TestToken) {
        override val DropboxUrl = s"http://localhost:$port"
      }

      WsTestClient.withClient { client =>
        block(client)(db)
      }
    }
  }
 
  // Validates that the request has the correct authorization before processing.
  def withAuthorization(request: RequestHeader)(block: => Result): Result = {
    val authorization = request.headers.toMap.find {
      case (AUTHORIZATION, Seq(ex"Bearer $TestToken")) => true
      case _ => false
    }
    if (authorization.isEmpty) Unauthorized("missing authorization")
    else block
  }
 
  // Enrich the `StringContext` with an extractor that applies interpolation, used in pattern matching.
  implicit class RichStringContext(val sc: StringContext) {
    object ex {
      def apply(args: Any*): String = sc.s(args: _*)
      def unapplySeq(s: String): Option[Seq[String]] = sc.parts.mkString ("(.+)").r.unapplySeq (s)
    }
  }
}
