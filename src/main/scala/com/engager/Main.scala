package com.engager

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import com.engager.api.Routes
import com.engager.campaign.{CampaignTrigger, SegmentEvaluator}
import com.engager.config.AppConfig
import com.engager.kafka.EventProducer
import com.engager.storage.{ElasticsearchService, PostgresRepository}
import com.engager.stream.EventPipeline
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}

object Main extends App {
  private val log = LoggerFactory.getLogger(getClass)

  implicit val system: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "engager")
  implicit val ec: ExecutionContextExecutor = system.executionContext

  val producer         = new EventProducer()
  val esService        = new ElasticsearchService()
  val pgRepo           = new PostgresRepository()
  val segmentEvaluator = new SegmentEvaluator(esService, pgRepo)
  val campaignTrigger  = new CampaignTrigger(pgRepo)
  val pipeline         = new EventPipeline(esService, pgRepo, segmentEvaluator, campaignTrigger)

  pgRepo.initialize().onComplete {
    case Success(_)  => log.info("PostgreSQL ready")
    case Failure(ex) => log.error("PostgreSQL init failed", ex)
  }

  val routes        = Routes.all(producer, esService, pgRepo, segmentEvaluator)
  val bindingFuture = Http().newServerAt(AppConfig.Http.host, AppConfig.Http.port).bind(routes)

  bindingFuture.onComplete {
    case Success(binding) =>
      val addr = binding.localAddress
      log.info(s"Engager running at http://${addr.getHostString}:${addr.getPort}")
    case Failure(ex) =>
      log.error("Failed to bind HTTP server", ex)
      system.terminate()
  }

  val pipelineControl = pipeline.start()

  sys.addShutdownHook {
    log.info("Shutting down...")
    pipelineControl.shutdown()
    producer.close()
    esService.close()
    pgRepo.close()
    system.terminate()
  }

  scala.concurrent.Await.result(system.whenTerminated, Duration.Inf)
}
