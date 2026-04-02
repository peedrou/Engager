package com.engager.campaign

import com.engager.metrics.MetricsRegistry
import com.engager.models._
import com.engager.storage.{ElasticsearchService, PostgresRepository}
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

class SegmentEvaluator(
  esService: ElasticsearchService,
  pgRepo: PostgresRepository
)(implicit ec: ExecutionContext) {
  private val log = LoggerFactory.getLogger(getClass)

  def matchingSegments(userId: String): Future[List[Segment]] =
    for {
      allSegments <- pgRepo.getAllSegments()
      profileOpt  <- esService.getProfile(userId)
    } yield profileOpt match {
      case None =>
        log.debug(s"No profile found for user $userId, no segments matched")
        List.empty
      case Some(profile) =>
        val matched = allSegments.filter(s => evaluate(profile, s.query))
        matched.foreach { _ => MetricsRegistry.segmentMatches.incrementAndGet() }
        if (matched.nonEmpty)
          log.info(s"User $userId matched segments: ${matched.map(_.name).mkString(", ")}")
        matched
    }

  private def evaluate(profile: UserProfile, query: SegmentQuery): Boolean = {
    val fieldValue: Option[Double] = query.field match {
      case "totalPurchases" => Some(profile.totalPurchases.toDouble)
      case "totalPageViews" => Some(profile.totalPageViews.toDouble)
      case "eventCount"     => Some(profile.eventCount.toDouble)
      case other =>
        log.debug(s"Unknown segment field '$other', skipping")
        None
    }
    val queryValue: Option[Double] =
      query.value.asNumber.flatMap(_.toBigDecimal).map(_.toDouble)

    (fieldValue, queryValue) match {
      case (Some(fv), Some(qv)) =>
        query.operator match {
          case QueryOperator.Eq  => fv == qv
          case QueryOperator.Neq => fv != qv
          case QueryOperator.Gt  => fv > qv
          case QueryOperator.Gte => fv >= qv
          case QueryOperator.Lt  => fv < qv
          case QueryOperator.Lte => fv <= qv
        }
      case _ => false
    }
  }
}
