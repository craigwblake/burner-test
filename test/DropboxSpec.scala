package test

import akka.stream.scaladsl.Source
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner.RunWith
import play.api.http.HeaderNames._
import play.api.http.Status._
import play.api.libs.json._
import play.api.libs.json.Json._
import play.api.mvc.Results._
import play.api.test._
import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import test.SaveUrlStatus._
import test.TestUtil._

@RunWith(classOf[JUnitRunner])
class DropboxSpec extends Specification {
  import Implicits._

  "Dropbox" should {

    "issue a valid REST request for saveUrl and return response" in { implicit ee: ExecutionEnv =>
      withDropbox { implicit client => db => 
        db.saveUrl(ValidUrl) must equalTo (SaveUrlResponse(PENDING, PendingJob, Some(ValidImageName))).await
      }
    }

    "issue an invalid REST request for saveUrl and return failure" in { implicit ee: ExecutionEnv =>
      withDropbox { implicit client => db => 
        db.saveUrl(InvalidUrl) must throwAn[Exception].await
      }
    }

    "issue a valid REST request for saveUrlStatus and return response" in { implicit ee: ExecutionEnv =>
      withDropbox { implicit client => db => 
        db.saveUrlStatus(PendingJob) must equalTo (SaveUrlJobResponse(PENDING, None)).await
        db.saveUrlStatus(DownloadingJob) must equalTo (SaveUrlJobResponse(DOWNLOADING, None)).await
        db.saveUrlStatus(CompleteJob) must equalTo (SaveUrlJobResponse(COMPLETE, None)).await
        db.saveUrlStatus(FailedJob) must equalTo (SaveUrlJobResponse(FAILED, Some(FailedJobMessage))).await
      }
    }

    "issue an invalid REST request for saveUrlStatus and return failure" in { implicit ee: ExecutionEnv =>
      withDropbox { implicit client => db => 
        db.saveUrl(InvalidJob) must throwAn[Exception].await
      }
    }
  
    "issue a valid REST request for fileExists and return response" in { implicit ee: ExecutionEnv =>
      withDropbox { implicit client => db => 
        db.fileExists(ValidImageName) must equalTo (true).await
      }
    }

    "issue an invalid REST request for fileExists and return failure" in { implicit ee: ExecutionEnv =>
      withDropbox { implicit client => db => 
        db.fileExists(InvalidImageName) must equalTo (false).await
      }
    }
  }
}
