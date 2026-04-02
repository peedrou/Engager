package com.engager.api

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import com.engager.models.{ApiResponse, Campaign}
import com.engager.storage.PostgresRepository

import scala.concurrent.ExecutionContext

class CampaignRoutes(pgRepo: PostgresRepository)(implicit ec: ExecutionContext) {

  val routes: Route = pathPrefix("campaigns") {
    concat(
      pathEndOrSingleSlash {
        concat(
          // GET /api/v1/campaigns
          get {
            onSuccess(pgRepo.getAllCampaigns()) { campaigns =>
              complete(StatusCodes.OK -> ApiResponse.ok(campaigns))
            }
          },
          // POST /api/v1/campaigns
          post {
            entity(as[Campaign]) { campaign =>
              onSuccess(pgRepo.saveCampaign(campaign)) {
                complete(StatusCodes.Created -> ApiResponse.ok(campaign))
              }
            }
          }
        )
      },

      path(Segment) { campaignId =>
        concat(
          // GET /api/v1/campaigns/:id
          get {
            onSuccess(pgRepo.getCampaign(campaignId)) {
              case Some(campaign) =>
                complete(StatusCodes.OK -> ApiResponse.ok(campaign))
              case None =>
                complete(StatusCodes.NotFound -> ApiResponse.fail[String](s"Campaign '$campaignId' not found"))
            }
          },
          // DELETE /api/v1/campaigns/:id
          delete {
            onSuccess(pgRepo.deleteCampaign(campaignId)) {
              complete(StatusCodes.OK -> ApiResponse.ok(Map("deleted" -> campaignId)))
            }
          }
        )
      },

      // PUT /api/v1/campaigns/:id/activate
      path(Segment / "activate") { campaignId =>
        put {
          onSuccess(pgRepo.setCampaignActive(campaignId, active = true)) {
            complete(StatusCodes.OK -> ApiResponse.ok(Map("campaignId" -> campaignId, "active" -> "true")))
          }
        }
      },

      // PUT /api/v1/campaigns/:id/deactivate
      path(Segment / "deactivate") { campaignId =>
        put {
          onSuccess(pgRepo.setCampaignActive(campaignId, active = false)) {
            complete(StatusCodes.OK -> ApiResponse.ok(Map("campaignId" -> campaignId, "active" -> "false")))
          }
        }
      }
    )
  }
}
