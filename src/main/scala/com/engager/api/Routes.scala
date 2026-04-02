package com.engager.api

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.syntax._
import io.circe.generic.auto._
import com.engager.campaign.SegmentEvaluator
import com.engager.kafka.EventProducer
import com.engager.metrics.MetricsRegistry
import com.engager.models.ApiResponse
import com.engager.storage.{ElasticsearchService, PostgresRepository}

import scala.concurrent.ExecutionContext

object Routes {

  private def healthRoute: Route =
    path("health") {
      get {
        complete(StatusCodes.OK -> ApiResponse.ok(Map(
          "service" -> "engager",
          "status"  -> "healthy",
          "version" -> "0.1.0"
        )))
      }
    }

  private def metricsRoute: Route =
    path("metrics") {
      get {
        complete(StatusCodes.OK -> ApiResponse.ok(MetricsRegistry.snapshot()))
      }
    }

  def all(
    producer:  EventProducer,
    esService: ElasticsearchService,
    pgRepo:    PostgresRepository,
    segEval: SegmentEvaluator
  )(implicit ec: ExecutionContext): Route =
    concat(
      healthRoute,
      metricsRoute,
      pathPrefix("api" / "v1") {
        concat(
          new EventRoutes(producer, esService).routes,
          new UserRoutes(esService, segEval).routes,
          new SegmentRoutes(pgRepo, esService).routes,
          new CampaignRoutes(pgRepo).routes
        )
      }
    )
}
