package com.engager.stream

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter._
import akka.kafka.scaladsl.{Committer, Consumer}
import akka.kafka.{CommitterSettings, ConsumerSettings, Subscriptions}
import akka.stream.scaladsl.Keep
import com.engager.campaign.{CampaignTrigger, SegmentEvaluator}
import com.engager.config.AppConfig
import com.engager.metrics.MetricsRegistry
import com.engager.models.UserEvent
import com.engager.storage.{ElasticsearchService, PostgresRepository}
import io.circe.parser._
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

class EventPipeline(
  esService: ElasticsearchService,
  pgRepo: PostgresRepository,
  segmentEvaluator: SegmentEvaluator,
  campaignTrigger: CampaignTrigger
)(implicit system: ActorSystem[_], ec: ExecutionContext) {
  private val log = LoggerFactory.getLogger(getClass)

  private val consumerSettings =
    ConsumerSettings(system.toClassic, new StringDeserializer, new StringDeserializer)
      .withBootstrapServers(AppConfig.Kafka.bootstrapServers)
      .withGroupId(AppConfig.Kafka.groupId)
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
      .withProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")

  private val committerSettings = CommitterSettings(system.toClassic)

  def start(): Consumer.Control = {
    val (control, done) = Consumer
      .committableSource(consumerSettings, Subscriptions.topics(AppConfig.Kafka.eventsTopic))
      .mapAsync(parallelism = 4) { msg =>
        decode[UserEvent](msg.record.value()) match {
          case Left(err) =>
            MetricsRegistry.pipelineErrors.incrementAndGet()
            log.warn(s"Skipping unparseable message: $err")
            scala.concurrent.Future.successful(msg.committableOffset)

          case Right(event) =>
            log.info(s"Processing event ${event.eventId} (${event.eventType}) for user ${event.userId}")
            for {
              _        <- esService.indexEvent(event)
              _        <- esService.upsertProfile(event)
              segments <- segmentEvaluator.matchingSegments(event.userId)
              _        <- campaignTrigger.triggerForSegments(event.userId, segments)
            } yield {
              MetricsRegistry.eventsProcessed.incrementAndGet()
              msg.committableOffset
            }
        }
      }
      .toMat(Committer.sink(committerSettings))(Keep.both)
      .run()

    done.onComplete {
      case Success(_)  => log.info("Event pipeline shut down cleanly")
      case Failure(ex) => log.error("Event pipeline terminated with error", ex)
    }

    log.info(s"Event pipeline started — consuming from '${AppConfig.Kafka.eventsTopic}'")
    control
  }
}
