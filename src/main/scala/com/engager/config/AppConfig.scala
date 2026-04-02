package com.engager.config

import com.typesafe.config.{Config, ConfigFactory}

object AppConfig {
  private val config: Config = ConfigFactory.load()

  object Http {
    val host: String = config.getString("engager.http.host")
    val port: Int    = config.getInt("engager.http.port")
  }

  object Kafka {
    val bootstrapServers: String = config.getString("engager.kafka.bootstrap-servers")
    val eventsTopic: String      = config.getString("engager.kafka.events-topic")
    val groupId: String          = config.getString("engager.kafka.group-id")
  }

  object Elasticsearch {
    val host: String           = config.getString("engager.elasticsearch.host")
    val port: Int              = config.getInt("engager.elasticsearch.port")
    val eventsIndex: String    = config.getString("engager.elasticsearch.events-index")
    val profilesIndex: String  = config.getString("engager.elasticsearch.profiles-index")
  }

  object Postgres {
    val url: String      = config.getString("engager.postgres.url")
    val user: String     = config.getString("engager.postgres.user")
    val password: String = config.getString("engager.postgres.password")
    val driver: String   = config.getString("engager.postgres.driver")
  }
}
