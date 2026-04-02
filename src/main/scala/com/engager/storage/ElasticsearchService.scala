package com.engager.storage

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch.core.{
  GetRequest,
  IndexRequest,
  SearchRequest,
  UpdateRequest
}
import co.elastic.clients.json.jackson.JacksonJsonpMapper
import co.elastic.clients.transport.rest_client.RestClientTransport
import co.elastic.clients.elasticsearch.core.CountRequest
import com.engager.config.AppConfig
import com.engager.models._
import com.fasterxml.jackson.databind.ObjectMapper
import io.circe.parser._
import io.circe.syntax._
import org.apache.http.HttpHost
import org.elasticsearch.client.RestClient
import org.slf4j.LoggerFactory

import java.io.StringReader
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

class ElasticsearchService(
  esHost: String = AppConfig.Elasticsearch.host,
  esPort: Int = AppConfig.Elasticsearch.port
)(implicit ec: ExecutionContext) {

  private val log    = LoggerFactory.getLogger(getClass)
  private val mapper = new ObjectMapper()

  private val restClient = RestClient.builder(new HttpHost(esHost, esPort)).build()
  private val transport  = new RestClientTransport(restClient, new JacksonJsonpMapper())
  private val client     = new ElasticsearchClient(transport)

  def indexEvent(event: UserEvent): Future[Unit] = Future {
    val req = new IndexRequest.Builder[java.util.Map[String, Object]]()
      .index(AppConfig.Elasticsearch.eventsIndex)
      .id(event.eventId)
      .withJson(new StringReader(event.asJson.noSpaces))
      .build()
    client.index(req)
    log.debug(s"Indexed event ${event.eventId}")
  }.recover { case ex => log.error(s"Failed to index event ${event.eventId}", ex) }

  def searchEvents(userId: String, limit: Int = 50, offset: Int = 0): Future[List[UserEvent]] =
    Future {
      val query =
        s"""{"query":{"term":{"userId":"$userId"}},"sort":[{"timestamp":{"order":"desc"}}],"from":$offset,"size":$limit}"""
      val searchReq = new SearchRequest.Builder()
        .index(AppConfig.Elasticsearch.eventsIndex)
        .withJson(new StringReader(query))
        .build()
      val resp = client.search(searchReq, classOf[java.util.Map[_, _]])
      resp.hits().hits().asScala.toList.flatMap { hit =>
        decode[UserEvent](mapper.writeValueAsString(hit.source())).toOption
      }
    }.recover { case ex =>
      log.error(s"Failed to search events for user $userId", ex)
      List.empty
    }

  def countEvents(userId: String): Future[Long] = {
    Future {
      val query = s"""{"query": {"term": {"userId": "$userId"}}}"""
      val countRequest = new CountRequest.Builder()
        .withJson(new StringReader(query))
        .build()
      val resp = client.count(countRequest)
      resp.count()
    }
  }

  def upsertProfile(event: UserEvent): Future[Unit] = Future {
    val eventTypeStr = event.eventType match {
      case EventType.Purchase => "PURCHASE"
      case EventType.PageView => "PAGE_VIEW"
      case EventType.Signup   => "SIGNUP"
      case EventType.Custom   => "CUSTOM"
    }
    val (initPurchases, initPageViews) = event.eventType match {
      case EventType.Purchase => (1, 0)
      case EventType.PageView => (0, 1)
      case _                  => (0, 0)
    }
    val script =
      "ctx._source.eventCount = (ctx._source.eventCount != null ? ctx._source.eventCount : 0) + 1;" +
        "ctx._source.lastEventAt = params.lastEventAt;" +
        "if (params.eventType == 'PURCHASE') {" +
        "  ctx._source.totalPurchases = (ctx._source.totalPurchases != null ? ctx._source.totalPurchases : 0) + 1;" +
        "} else if (params.eventType == 'PAGE_VIEW') {" +
        "  ctx._source.totalPageViews = (ctx._source.totalPageViews != null ? ctx._source.totalPageViews : 0) + 1;" +
        "}"
    val body =
      s"""{
         |  "script": {
         |    "lang": "painless",
         |    "source": ${io.circe.Json.fromString(script).noSpaces},
         |    "params": {"eventType":"$eventTypeStr","lastEventAt":"${event.timestamp}"}
         |  },
         |  "upsert": {
         |    "userId":"${event.userId}",
         |    "totalPurchases":$initPurchases,
         |    "totalPageViews":$initPageViews,
         |    "eventCount":1,
         |    "lastEventAt":"${event.timestamp}",
         |    "createdAt":"${event.timestamp}"
         |  }
         |}""".stripMargin
    val updateReq =
      new UpdateRequest.Builder[java.util.Map[String, Object], java.util.Map[String, Object]]()
        .index(AppConfig.Elasticsearch.profilesIndex)
        .id(event.userId)
        .withJson(new StringReader(body))
        .build()
    client.update(updateReq, classOf[java.util.Map[String, Object]])
    log.debug(s"Upserted profile for user ${event.userId}")
  }.recover { case ex => log.error(s"Failed to upsert profile for user ${event.userId}", ex) }

  def getProfile(userId: String): Future[Option[UserProfile]] = Future {
    val getReq = new GetRequest.Builder()
      .index(AppConfig.Elasticsearch.profilesIndex)
      .id(userId)
      .build()
    val resp = client.get(getReq, classOf[java.util.Map[_, _]])
    if (resp.found())
      decode[UserProfile](mapper.writeValueAsString(resp.source())).toOption
    else None
  }.recover { case ex =>
    log.error(s"Failed to get profile for user $userId", ex)
    None
  }

  def getUsersInSegment(query: SegmentQuery, limit: Int = 100): Future[List[String]] = Future {
    val esFilter = buildFilter(query)
    val body     = s"""{"query":$esFilter,"_source":["userId"],"size":$limit}"""
    val segReq = new SearchRequest.Builder()
      .index(AppConfig.Elasticsearch.profilesIndex)
      .withJson(new StringReader(body))
      .build()
    val resp = client.search(segReq, classOf[java.util.Map[_, _]])
    resp.hits().hits().asScala.toList.flatMap { hit =>
      Option(hit.source())
        .flatMap(s => Option(s.asInstanceOf[java.util.Map[String, Object]].get("userId")))
        .map(_.toString)
    }
  }.recover { case ex =>
    log.error(s"Failed to query users in segment", ex)
    List.empty
  }

  private def buildFilter(query: SegmentQuery): String = query.operator match {
    case QueryOperator.Eq => s"""{"term":{"${query.field}":${query.value.noSpaces}}}"""
    case QueryOperator.Neq =>
      s"""{"bool":{"must_not":[{"term":{"${query.field}":${query.value.noSpaces}}}]}}"""
    case QueryOperator.Gt  => s"""{"range":{"${query.field}":{"gt":${query.value.noSpaces}}}}"""
    case QueryOperator.Gte => s"""{"range":{"${query.field}":{"gte":${query.value.noSpaces}}}}"""
    case QueryOperator.Lt  => s"""{"range":{"${query.field}":{"lt":${query.value.noSpaces}}}}"""
    case QueryOperator.Lte => s"""{"range":{"${query.field}":{"lte":${query.value.noSpaces}}}}"""
  }

  def close(): Unit = {
    transport.close()
    restClient.close()
  }
}
