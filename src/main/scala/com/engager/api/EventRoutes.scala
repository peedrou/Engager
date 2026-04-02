package com.engager.api

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import com.engager.kafka.EventProducer
import com.engager.metrics.MetricsRegistry
import com.engager.models.{ApiResponse, UserEvent}
import com.engager.storage.ElasticsearchService
import io.circe.Json
import io.circe.syntax._

import scala.concurrent.ExecutionContext

class EventRoutes(producer: EventProducer, esService: ElasticsearchService)(implicit
  ec: ExecutionContext
) {

  val routes: Route = pathPrefix("events") {
    concat(
      // POST /api/v1/events
      pathEndOrSingleSlash {
        post {
          entity(as[UserEvent]) { event =>
            onSuccess(producer.publish(event)) {
              MetricsRegistry.eventsIngested.incrementAndGet()
              complete(
                StatusCodes.Accepted -> ApiResponse.ok(
                  Map(
                    "eventId" -> event.eventId,
                    "status"  -> "accepted"
                  )
                )
              )
            }
          }
        }
      },

      // POST /api/v1/events/batch
      path("batch") {
        post {
          entity(as[List[UserEvent]]) { events =>
            onSuccess(producer.publishBatch(events)) {
              MetricsRegistry.eventsIngested.addAndGet(events.size.toLong)
              complete(
                StatusCodes.Accepted -> ApiResponse.ok(
                  Map(
                    "accepted" -> events.size.toString,
                    "status"   -> "accepted"
                  )
                )
              )
            }
          }
        }
      },

      // GET /api/v1/events/:userId
      path(Segment) { userId =>
        get {
          parameters("limit".as[Int].withDefault(50), "offset".as[Int].withDefault(0)) {
            (limit, offset) =>
              val result = for {
                events <- esService.searchEvents(userId, limit, offset)
                total  <- esService.countEvents(userId)
              } yield (events, total)
              onSuccess(result) { case (events, total) =>
                complete(
                  StatusCodes.OK -> ApiResponse.ok(
                    Json.obj(
                      "total"  -> total.asJson,
                      "limit"  -> limit.asJson,
                      "offset" -> offset.asJson,
                      "events" -> events.asJson
                    )
                  )
                )
              }
          }
        }
      }
    )
  }

}
