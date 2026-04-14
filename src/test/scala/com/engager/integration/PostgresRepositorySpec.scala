package com.engager.integration

import com.dimafeng.testcontainers.PostgreSQLContainer
import com.dimafeng.testcontainers.scalatest.TestContainerForAll
import com.engager.models._
import com.engager.storage.PostgresRepository
import io.circe.Json
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.ExecutionContext.Implicits.global

class PostgresRepositorySpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with TestContainerForAll {

  override val containerDef = PostgreSQLContainer.Def()

  implicit val pc: PatienceConfig = PatienceConfig(timeout = Span(10, Seconds))

  private def makeRepo(pg: PostgreSQLContainer): PostgresRepository =
    new PostgresRepository(pg.jdbcUrl, pg.username, pg.password)

  "PostgresRepository" should {

    "initialize schema without error" in {
      withContainers { pg =>
        val repo = makeRepo(pg)
        repo.initialize().futureValue
      }
    }

    "save and retrieve a segment" in {
      withContainers { pg =>
        val repo = makeRepo(pg)
        repo.initialize().futureValue

        val query   = SegmentQuery("totalPurchases", QueryOperator.Gte, Json.fromInt(3))
        val segment = Segment(name = "High Value", query = query)

        repo.saveSegment(segment).futureValue

        val retrieved = repo.getSegment(segment.segmentId).futureValue
        retrieved shouldBe defined
        retrieved.get.name shouldBe "High Value"
        retrieved.get.query.field shouldBe "totalPurchases"
      }
    }

    "list all segments" in {
      withContainers { pg =>
        val repo = makeRepo(pg)
        repo.initialize().futureValue

        val q = SegmentQuery("eventCount", QueryOperator.Gt, Json.fromInt(0))
        repo.saveSegment(Segment(name = "Active Users", query = q)).futureValue
        repo.saveSegment(Segment(name = "Power Users", query = q)).futureValue

        val all = repo.getAllSegments().futureValue
        all.map(_.name) should contain allOf ("Active Users", "Power Users")
      }
    }

    "delete a segment" in {
      withContainers { pg =>
        val repo = makeRepo(pg)
        repo.initialize().futureValue

        val segment = Segment(
          name = "Temp",
          query = SegmentQuery("eventCount", QueryOperator.Gt, Json.fromInt(0))
        )
        repo.saveSegment(segment).futureValue
        repo.deleteSegment(segment.segmentId).futureValue

        repo.getSegment(segment.segmentId).futureValue shouldBe None
      }
    }

    "save and retrieve a campaign" in {
      withContainers { pg =>
        val repo = makeRepo(pg)
        repo.initialize().futureValue

        val segment = Segment(
          name = "Seg",
          query = SegmentQuery("eventCount", QueryOperator.Gt, Json.fromInt(0))
        )
        val action   = CampaignAction(ActionType.Log)
        val campaign = Campaign(name = "Welcome", segmentId = segment.segmentId, action = action)

        repo.saveSegment(segment).futureValue
        repo.saveCampaign(campaign).futureValue

        val retrieved = repo.getCampaign(campaign.campaignId).futureValue
        retrieved shouldBe defined
        retrieved.get.name shouldBe "Welcome"
        retrieved.get.active shouldBe true
      }
    }

    "activate and deactivate a campaign" in {
      withContainers { pg =>
        val repo = makeRepo(pg)
        repo.initialize().futureValue

        val segment = Segment(
          name = "Seg",
          query = SegmentQuery("eventCount", QueryOperator.Gt, Json.fromInt(0))
        )
        val campaign = Campaign(
          name = "Toggle",
          segmentId = segment.segmentId,
          action = CampaignAction(ActionType.Log)
        )

        repo.saveSegment(segment).futureValue
        repo.saveCampaign(campaign).futureValue

        repo.setCampaignActive(campaign.campaignId, active = false).futureValue
        repo.getCampaign(campaign.campaignId).futureValue.get.active shouldBe false

        repo.setCampaignActive(campaign.campaignId, active = true).futureValue
        repo.getCampaign(campaign.campaignId).futureValue.get.active shouldBe true
      }
    }

    "return only active campaigns for a segment" in {
      withContainers { pg =>
        val repo = makeRepo(pg)
        repo.initialize().futureValue

        val segment = Segment(
          name = "Seg",
          query = SegmentQuery("eventCount", QueryOperator.Gt, Json.fromInt(0))
        )
        repo.saveSegment(segment).futureValue

        val active = Campaign(
          name = "Active",
          segmentId = segment.segmentId,
          action = CampaignAction(ActionType.Log),
          active = true
        )
        val inactive = Campaign(
          name = "Inactive",
          segmentId = segment.segmentId,
          action = CampaignAction(ActionType.Log),
          active = false
        )

        repo.saveCampaign(active).futureValue
        repo.saveCampaign(inactive).futureValue

        val results = repo.getCampaignsBySegment(segment.segmentId).futureValue
        results.map(_.name) shouldBe List("Active")
      }
    }
  }

}
