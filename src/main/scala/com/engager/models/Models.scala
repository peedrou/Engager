package com.engager.models

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto._
import java.time.Instant
import java.util.UUID

case class UserEvent(
  eventId: String = UUID.randomUUID().toString,
  userId: String,
  eventType: EventType,
  properties: Map[String, io.circe.Json] = Map.empty,
  timestamp: Instant = Instant.now()
)

object UserEvent {
  implicit val encoder: Encoder[UserEvent] = deriveEncoder[UserEvent]
  implicit val decoder: Decoder[UserEvent] = Decoder.instance { c =>
    for {
      eventId    <- c.getOrElse[String]("eventId")(UUID.randomUUID().toString)
      userId     <- c.get[String]("userId")
      eventType  <- c.get[EventType]("eventType")
      properties <- c.getOrElse[Map[String, Json]]("properties")(Map.empty)
      timestamp  <- c.getOrElse[Instant]("timestamp")(Instant.now())
    } yield UserEvent(eventId, userId, eventType, properties, timestamp)
  }
}

sealed trait EventType

object EventType {
  case object Purchase  extends EventType
  case object PageView  extends EventType
  case object Signup    extends EventType
  case object Custom    extends EventType

  implicit val encoder: Encoder[EventType] = Encoder.encodeString.contramap {
    case Purchase => "PURCHASE"
    case PageView => "PAGE_VIEW"
    case Signup   => "SIGNUP"
    case Custom   => "CUSTOM"
  }

  implicit val decoder: Decoder[EventType] = Decoder.decodeString.emap {
    case "PURCHASE"  => Right(Purchase)
    case "PAGE_VIEW" => Right(PageView)
    case "SIGNUP"    => Right(Signup)
    case "CUSTOM"    => Right(Custom)
    case other       => Left(s"Unknown event type: $other")
  }
}

case class UserProfile(
  userId: String,
  email: Option[String] = None,
  attributes: Map[String, String] = Map.empty,
  totalPurchases: Int = 0,
  totalPageViews: Int = 0,
  eventCount: Int = 0,
  lastEventAt: Option[Instant] = None,
  createdAt: Instant = Instant.now()
)

object UserProfile {
  implicit val encoder: Encoder[UserProfile] = deriveEncoder[UserProfile]
  implicit val decoder: Decoder[UserProfile] = deriveDecoder[UserProfile]
}

case class Segment(
  segmentId: String = UUID.randomUUID().toString,
  name: String,
  description: Option[String] = None,
  query: SegmentQuery,
  createdAt: Instant = Instant.now()
)

object Segment {
  implicit val encoder: Encoder[Segment] = deriveEncoder[Segment]
  implicit val decoder: Decoder[Segment] = Decoder.instance { c =>
    for {
      segmentId   <- c.getOrElse[String]("segmentId")(UUID.randomUUID().toString)
      name        <- c.get[String]("name")
      description <- c.get[Option[String]]("description")
      query       <- c.get[SegmentQuery]("query")
      createdAt   <- c.getOrElse[Instant]("createdAt")(Instant.now())
    } yield Segment(segmentId, name, description, query, createdAt)
  }
}

case class SegmentQuery(
  field: String,
  operator: QueryOperator,
  value: io.circe.Json,
  timeWindow: Option[String] = None
)

object SegmentQuery {
  implicit val encoder: Encoder[SegmentQuery] = deriveEncoder[SegmentQuery]
  implicit val decoder: Decoder[SegmentQuery] = deriveDecoder[SegmentQuery]
}

sealed trait QueryOperator

object QueryOperator {
  case object Eq    extends QueryOperator
  case object Neq   extends QueryOperator
  case object Gt    extends QueryOperator
  case object Gte   extends QueryOperator
  case object Lt    extends QueryOperator
  case object Lte   extends QueryOperator

  implicit val encoder: Encoder[QueryOperator] = Encoder.encodeString.contramap {
    case Eq  => "eq"
    case Neq => "neq"
    case Gt  => "gt"
    case Gte => "gte"
    case Lt  => "lt"
    case Lte => "lte"
  }

  implicit val decoder: Decoder[QueryOperator] = Decoder.decodeString.emap {
    case "eq"  => Right(Eq)
    case "neq" => Right(Neq)
    case "gt"  => Right(Gt)
    case "gte" => Right(Gte)
    case "lt"  => Right(Lt)
    case "lte" => Right(Lte)
    case other => Left(s"Unknown operator: $other")
  }
}

case class Campaign(
  campaignId: String = UUID.randomUUID().toString,
  name: String,
  segmentId: String,
  action: CampaignAction,
  active: Boolean = true,
  createdAt: Instant = Instant.now()
)

object Campaign {
  implicit val encoder: Encoder[Campaign] = deriveEncoder[Campaign]
  implicit val decoder: Decoder[Campaign] = Decoder.instance { c =>
    for {
      campaignId <- c.getOrElse[String]("campaignId")(UUID.randomUUID().toString)
      name       <- c.get[String]("name")
      segmentId  <- c.get[String]("segmentId")
      action     <- c.get[CampaignAction]("action")
      active     <- c.getOrElse[Boolean]("active")(true)
      createdAt  <- c.getOrElse[Instant]("createdAt")(Instant.now())
    } yield Campaign(campaignId, name, segmentId, action, active, createdAt)
  }
}

case class CampaignAction(
  actionType: ActionType,
  webhookUrl: Option[String] = None,
  payload: Map[String, String] = Map.empty
)

object CampaignAction {
  implicit val encoder: Encoder[CampaignAction] = deriveEncoder[CampaignAction]
  implicit val decoder: Decoder[CampaignAction] = deriveDecoder[CampaignAction]
}

sealed trait ActionType

object ActionType {
  case object Webhook extends ActionType
  case object Log     extends ActionType

  implicit val encoder: Encoder[ActionType] = Encoder.encodeString.contramap {
    case Webhook => "WEBHOOK"
    case Log     => "LOG"
  }

  implicit val decoder: Decoder[ActionType] = Decoder.decodeString.emap {
    case "WEBHOOK" => Right(Webhook)
    case "LOG"     => Right(Log)
    case other     => Left(s"Unknown action type: $other")
  }
}

case class ApiResponse[T](
  success: Boolean,
  data: Option[T] = None,
  error: Option[String] = None
)

object ApiResponse {
  implicit def encoder[T: Encoder]: Encoder[ApiResponse[T]] = deriveEncoder[ApiResponse[T]]

  def ok[T](data: T): ApiResponse[T] =
    ApiResponse(success = true, data = Some(data))

  def fail[T](message: String): ApiResponse[T] =
    ApiResponse(success = false, error = Some(message))
}
