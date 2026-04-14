package com.engager.unit

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.server.Directives._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._
import com.engager.models.ApiResponse
import com.engager.metrics.MetricsRegistry
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class HealthRouteSpec extends AnyWordSpec with Matchers with ScalatestRouteTest {

  private val healthRoute =
    path("health") {
      get {
        complete(
          StatusCodes.OK -> ApiResponse.ok(
            Map(
              "service" -> "engager",
              "status"  -> "healthy",
              "version" -> "0.1.0"
            )
          )
        )
      }
    }

  private val metricsRoute =
    path("metrics") {
      get {
        complete(StatusCodes.OK -> ApiResponse.ok(MetricsRegistry.snapshot()))
      }
    }

  "GET /health" should {
    "return 200 OK" in {
      Get("/health") ~> healthRoute ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    "return service name and healthy status" in {
      Get("/health") ~> healthRoute ~> check {
        val body = responseAs[ApiResponse[Map[String, String]]]
        body.success shouldBe true
        body.data.get("service") shouldBe "engager"
        body.data.get("status") shouldBe "healthy"
      }
    }
  }

  "GET /metrics" should {
    "return 200 OK" in {
      Get("/metrics") ~> metricsRoute ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    "return all expected counter keys" in {
      Get("/metrics") ~> metricsRoute ~> check {
        val body = responseAs[ApiResponse[Map[String, Long]]]
        body.success shouldBe true
        val data = body.data.get
        data.keys should contain allOf (
          "events_ingested", "events_processed",
          "pipeline_errors", "segment_matches", "campaigns_fired"
        )
      }
    }

    "reflect incremented counters" in {
      MetricsRegistry.eventsIngested.set(42L)
      Get("/metrics") ~> metricsRoute ~> check {
        val body = responseAs[ApiResponse[Map[String, Long]]]
        body.data.get("events_ingested") shouldBe 42L
      }
      MetricsRegistry.eventsIngested.set(0L) // reset
    }
  }

}
