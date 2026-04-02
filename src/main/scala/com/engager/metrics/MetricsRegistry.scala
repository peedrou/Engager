package com.engager.metrics

import java.util.concurrent.atomic.AtomicLong

object MetricsRegistry {
  val eventsIngested  = new AtomicLong(0)
  val eventsProcessed = new AtomicLong(0)
  val pipelineErrors  = new AtomicLong(0)
  val segmentMatches  = new AtomicLong(0)
  val campaignsFired  = new AtomicLong(0)

  def snapshot(): Map[String, Long] = Map(
    "events_ingested"  -> eventsIngested.get(),
    "events_processed" -> eventsProcessed.get(),
    "pipeline_errors"  -> pipelineErrors.get(),
    "segment_matches"  -> segmentMatches.get(),
    "campaigns_fired"  -> campaignsFired.get()
  )
}
