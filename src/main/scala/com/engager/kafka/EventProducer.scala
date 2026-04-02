package com.engager.kafka

import com.engager.config.AppConfig
import com.engager.models.UserEvent
import io.circe.syntax._
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig, ProducerRecord}
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory

import java.util.Properties
import scala.concurrent.{ExecutionContext, Future}

class EventProducer {
  private val log = LoggerFactory.getLogger(getClass)

  private val props = {
    val p = new Properties()
    p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, AppConfig.Kafka.bootstrapServers)
    p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, classOf[StringSerializer].getName)
    p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, classOf[StringSerializer].getName)
    p.put(ProducerConfig.ACKS_CONFIG, "all")
    p.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true")
    p.put(ProducerConfig.RETRIES_CONFIG, "3")
    p
  }

  private val producer = new KafkaProducer[String, String](props)

  def publish(event: UserEvent)(implicit ec: ExecutionContext): Future[Unit] = Future {
    val json   = event.asJson.noSpaces
    val record = new ProducerRecord[String, String](AppConfig.Kafka.eventsTopic, event.userId, json)
    producer.send(record).get()
    log.debug(s"Published event ${event.eventId} (${event.eventType}) for user ${event.userId}")
  }

  def publishBatch(events: List[UserEvent])(implicit ec: ExecutionContext): Future[Unit] =
    Future.traverse(events)(publish(_)).map(_ => ())

  def close(): Unit = {
    producer.flush()
    producer.close()
  }
}
