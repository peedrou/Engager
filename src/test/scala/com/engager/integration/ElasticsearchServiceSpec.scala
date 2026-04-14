package com.engager.integration

import com.dimafeng.testcontainers.ElasticsearchContainer
import com.dimafeng.testcontainers.scalatest.TestContainerForAll
import com.engager.models._
import com.engager.storage.ElasticsearchService
import io.circe.Json
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import org.testcontainers.utility.DockerImageName

import scala.concurrent.ExecutionContext.Implicits.global

class ElasticsearchServiceSpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with Eventually
    with TestContainerForAll {

  override val containerDef = ElasticsearchContainer.Def(
    DockerImageName
      .parse("docker.elastic.co/elasticsearch/elasticsearch:8.11.4")
      .asCompatibleSubstituteFor("docker.elastic.co/elasticsearch/elasticsearch")
  )

  implicit val pc: PatienceConfig = PatienceConfig(timeout = Span(30, Seconds))

  implicit val ep: Eventually.PatienceConfig =
    Eventually.PatienceConfig(timeout = Span(15, Seconds), interval = Span(1, Seconds))

  private def makeService(es: ElasticsearchContainer): ElasticsearchService = {
    val hostPort = es.httpHostAddress.split(":")
    new ElasticsearchService(hostPort(0), hostPort(1).toInt)
  }

  private def sampleEvent(userId: String = "user-test") = UserEvent(
    userId = userId,
    eventType = EventType.Purchase
  )

  "ElasticsearchService" should {

    "index an event without error" in {
      withContainers { es =>
        val service = makeService(es)
        val event   = sampleEvent()
        service.indexEvent(event).futureValue
      }
    }

    "retrieve an indexed event by userId" in {
      withContainers { es =>
        val service = makeService(es)
        val event   = sampleEvent("user-search")

        service.indexEvent(event).futureValue

        // ES indexing is near-real-time; wait for the shard to refresh
        eventually {
          val results = service.searchEvents("user-search").futureValue
          results.map(_.eventId) should contain(event.eventId)
        }
      }
    }

    "upsert a user profile and read it back" in {
      withContainers { es =>
        val service = makeService(es)
        val event   = sampleEvent("user-profile")

        service.upsertProfile(event).futureValue

        eventually {
          val profile = service.getProfile("user-profile").futureValue
          profile shouldBe defined
          profile.get.userId shouldBe "user-profile"
          profile.get.totalPurchases shouldBe 1
        }
      }
    }

    "increment purchase counter on repeated upserts" in {
      withContainers { es =>
        val service = makeService(es)
        val userId  = "user-counter"

        service.upsertProfile(sampleEvent(userId)).futureValue
        service.upsertProfile(sampleEvent(userId)).futureValue
        service.upsertProfile(sampleEvent(userId)).futureValue

        eventually {
          val profile = service.getProfile(userId).futureValue
          profile.get.totalPurchases shouldBe 3
          profile.get.eventCount shouldBe 3
        }
      }
    }

    "return None for a profile that does not exist" in {
      withContainers { es =>
        val service = makeService(es)
        service.getProfile("does-not-exist").futureValue shouldBe None
      }
    }

    "find users matching a segment query" in {
      withContainers { es =>
        val service = makeService(es)
        val userId  = "user-segment"

        // Build up 4 purchases
        (1 to 4).foreach(_ => service.upsertProfile(sampleEvent(userId)).futureValue)

        val query = SegmentQuery("totalPurchases", QueryOperator.Gte, Json.fromInt(3))

        eventually {
          val userIds = service.getUsersInSegment(query).futureValue
          userIds should contain(userId)
        }
      }
    }
  }

}
