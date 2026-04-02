package com.engager.campaign

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest}
import com.engager.metrics.MetricsRegistry
import com.engager.models.{ActionType, Segment}
import com.engager.storage.PostgresRepository
import io.circe.syntax._
import io.circe.generic.auto._
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class CampaignTrigger(
  pgRepo: PostgresRepository
)(implicit system: ActorSystem[_], ec: ExecutionContext) {
  private val log = LoggerFactory.getLogger(getClass)

  def triggerForSegments(userId: String, segments: List[Segment]): Future[Unit] =
    Future
      .traverse(segments) { segment =>
        pgRepo.getCampaignsBySegment(segment.segmentId).flatMap { campaigns =>
          Future.traverse(campaigns) { campaign =>
            log.info(s"Triggering campaign '${campaign.name}' for user $userId (segment '${segment.name}')")
            MetricsRegistry.campaignsFired.incrementAndGet()
            executeAction(userId, campaign.action.actionType, campaign.action.webhookUrl, campaign.action.payload)
          }
        }
      }
      .map(_ => ())

  private def executeAction(
    userId: String,
    actionType: ActionType,
    webhookUrl: Option[String],
    payload: Map[String, String]
  ): Future[Unit] = actionType match {
    case ActionType.Log =>
      log.info(s"[CAMPAIGN LOG] userId=$userId payload=$payload")
      Future.successful(())

    case ActionType.Webhook =>
      webhookUrl match {
        case Some(url) => fireWebhook(userId, url, payload)
        case None =>
          log.warn(s"Webhook campaign missing URL for user $userId")
          Future.successful(())
      }
  }

  private def fireWebhook(userId: String, url: String, payload: Map[String, String]): Future[Unit] = {
    val body    = (payload + ("userId" -> userId)).asJson.noSpaces
    val request = HttpRequest(
      method = HttpMethods.POST,
      uri    = url,
      entity = HttpEntity(ContentTypes.`application/json`, body)
    )
    Http().singleRequest(request).transform {
      case Success(resp) =>
        resp.discardEntityBytes()
        log.info(s"Webhook fired for user $userId → $url (${resp.status})")
        Success(())
      case Failure(ex) =>
        log.error(s"Webhook failed for user $userId → $url: ${ex.getMessage}")
        Success(()) // absorb so it doesn't kill the pipeline
    }
  }
}
