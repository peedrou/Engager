package com.engager.api

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import com.engager.models.ApiResponse
import com.engager.campaign.SegmentEvaluator
import com.engager.storage.ElasticsearchService

import scala.concurrent.ExecutionContext

class UserRoutes(esService: ElasticsearchService, segEval: SegmentEvaluator)(implicit
  ec: ExecutionContext
) {

  val routes: Route = pathPrefix("users") {
    concat(
      // GET /api/v1/users/:userId
      path(Segment) { userId =>
        get {
          onSuccess(esService.getProfile(userId)) {
            case Some(profile) =>
              complete(StatusCodes.OK -> ApiResponse.ok(profile))
            case None =>
              complete(
                StatusCodes.NotFound -> ApiResponse.fail[String](s"User '$userId' not found")
              )
          }
        }
      },

      // GET /api/v1/users/:userId/segments
      path(Segment / "segments") { userId =>
        get {
          onSuccess(segEval.matchingSegments(userId)) { segments =>
            complete(StatusCodes.OK -> ApiResponse.ok(segments))

          }
        }
      }
    )
  }

}
