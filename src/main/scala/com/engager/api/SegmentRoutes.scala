package com.engager.api

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import com.engager.models.{ApiResponse, Segment => SegmentModel}
import com.engager.storage.{ElasticsearchService, PostgresRepository}

import scala.concurrent.ExecutionContext

class SegmentRoutes(pgRepo: PostgresRepository, esService: ElasticsearchService)(implicit ec: ExecutionContext) {

  val routes: Route = pathPrefix("segments") {
    concat(
      pathEndOrSingleSlash {
        concat(
          // GET /api/v1/segments
          get {
            onSuccess(pgRepo.getAllSegments()) { segments =>
              complete(StatusCodes.OK -> ApiResponse.ok(segments))
            }
          },
          // POST /api/v1/segments
          post {
            entity(as[SegmentModel]) { segment =>
              onSuccess(pgRepo.saveSegment(segment)) {
                complete(StatusCodes.Created -> ApiResponse.ok(segment))
              }
            }
          }
        )
      },

      path(Segment) { segmentId =>
        concat(
          // GET /api/v1/segments/:id
          get {
            onSuccess(pgRepo.getSegment(segmentId)) {
              case Some(segment) =>
                complete(StatusCodes.OK -> ApiResponse.ok(segment))
              case None =>
                complete(StatusCodes.NotFound -> ApiResponse.fail[String](s"Segment '$segmentId' not found"))
            }
          },
          // DELETE /api/v1/segments/:id
          delete {
            onSuccess(pgRepo.deleteSegment(segmentId)) {
              complete(StatusCodes.OK -> ApiResponse.ok(Map("deleted" -> segmentId)))
            }
          }
        )
      },

      // GET /api/v1/segments/:id/users
      path(Segment / "users") { segmentId =>
        get {
          onSuccess(pgRepo.getSegment(segmentId)) {
            case None =>
              complete(StatusCodes.NotFound -> ApiResponse.fail[String](s"Segment '$segmentId' not found"))
            case Some(segment) =>
              onSuccess(esService.getUsersInSegment(segment.query)) { userIds =>
                complete(StatusCodes.OK -> ApiResponse.ok(Map(
                  "segmentId" -> segmentId,
                  "count"     -> userIds.size.toString,
                  "userIds"   -> userIds.mkString(",")
                )))
              }
          }
        }
      }
    )
  }
}
