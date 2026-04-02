package com.engager.storage

import com.engager.config.AppConfig
import com.engager.models._
import io.circe.parser._
import io.circe.syntax._
import slick.jdbc.PostgresProfile.api._
import org.slf4j.LoggerFactory

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

class PostgresRepository(
  dbUrl:      String = AppConfig.Postgres.url,
  dbUser:     String = AppConfig.Postgres.user,
  dbPassword: String = AppConfig.Postgres.password,
  dbDriver:   String = AppConfig.Postgres.driver
)(implicit ec: ExecutionContext) {

  private val log = LoggerFactory.getLogger(getClass)

  private val db = Database.forURL(
    url      = dbUrl,
    user     = dbUser,
    password = dbPassword,
    driver   = dbDriver
  )

  private class SegmentsTable(tag: Tag)
    extends Table[(String, String, Option[String], String, String)](tag, "segments") {
    def segmentId   = column[String]("segment_id", O.PrimaryKey)
    def name        = column[String]("name")
    def description = column[Option[String]]("description")
    def queryJson   = column[String]("query_json")
    def createdAt   = column[String]("created_at")
    def *           = (segmentId, name, description, queryJson, createdAt)
  }

  private class CampaignsTable(tag: Tag)
    extends Table[(String, String, String, String, Boolean, String)](tag, "campaigns") {
    def campaignId = column[String]("campaign_id", O.PrimaryKey)
    def name       = column[String]("name")
    def segmentId  = column[String]("segment_id")
    def actionJson = column[String]("action_json")
    def active     = column[Boolean]("active")
    def createdAt  = column[String]("created_at")
    def *          = (campaignId, name, segmentId, actionJson, active, createdAt)
  }

  private val segments  = TableQuery[SegmentsTable]
  private val campaigns = TableQuery[CampaignsTable]

  def initialize(): Future[Unit] =
    db.run((segments.schema ++ campaigns.schema).createIfNotExists)
      .map(_ => log.info("PostgreSQL schema initialized"))

  def saveSegment(segment: Segment): Future[Unit] = {
    val row = (segment.segmentId, segment.name, segment.description,
               segment.query.asJson.noSpaces, segment.createdAt.toString)
    db.run(segments.insertOrUpdate(row)).map(_ => ())
  }

  def getSegment(segmentId: String): Future[Option[Segment]] =
    db.run(segments.filter(_.segmentId === segmentId).result.headOption)
      .map(_.flatMap(rowToSegment))

  def getAllSegments(): Future[List[Segment]] =
    db.run(segments.result).map(_.toList.flatMap(rowToSegment))

  def deleteSegment(segmentId: String): Future[Unit] =
    db.run(segments.filter(_.segmentId === segmentId).delete).map(_ => ())

  private def rowToSegment(row: (String, String, Option[String], String, String)): Option[Segment] = {
    val (id, name, desc, queryJson, createdAt) = row
    decode[SegmentQuery](queryJson).toOption.map(q => Segment(id, name, desc, q, Instant.parse(createdAt)))
  }

  def saveCampaign(campaign: Campaign): Future[Unit] = {
    val row = (campaign.campaignId, campaign.name, campaign.segmentId,
               campaign.action.asJson.noSpaces, campaign.active, campaign.createdAt.toString)
    db.run(campaigns.insertOrUpdate(row)).map(_ => ())
  }

  def getCampaign(campaignId: String): Future[Option[Campaign]] =
    db.run(campaigns.filter(_.campaignId === campaignId).result.headOption)
      .map(_.flatMap(rowToCampaign))

  def getCampaignsBySegment(segmentId: String): Future[List[Campaign]] =
    db.run(campaigns.filter(c => c.segmentId === segmentId && c.active).result)
      .map(_.toList.flatMap(rowToCampaign))

  def getAllCampaigns(): Future[List[Campaign]] =
    db.run(campaigns.result).map(_.toList.flatMap(rowToCampaign))

  def setCampaignActive(campaignId: String, active: Boolean): Future[Unit] =
    db.run(campaigns.filter(_.campaignId === campaignId).map(_.active).update(active)).map(_ => ())

  def deleteCampaign(campaignId: String): Future[Unit] =
    db.run(campaigns.filter(_.campaignId === campaignId).delete).map(_ => ())

  private def rowToCampaign(row: (String, String, String, String, Boolean, String)): Option[Campaign] = {
    val (id, name, segId, actionJson, active, createdAt) = row
    decode[CampaignAction](actionJson).toOption.map(a => Campaign(id, name, segId, a, active, Instant.parse(createdAt)))
  }

  def close(): Unit = db.close()
}
