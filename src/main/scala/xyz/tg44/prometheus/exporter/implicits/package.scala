package xyz.tg44.prometheus.exporter

import cats.Id
import cats.effect.Clock

import scala.concurrent.duration.{MILLISECONDS, NANOSECONDS, TimeUnit}

package object implicits extends
  Counter.implicits with
  Gauge.implicits with
  Histogram.implicits with
  Summary.implicits {

  implicit val idClock = new Clock[Id] {
    override def realTime(unit: TimeUnit): Id[Long] = unit.convert(System.currentTimeMillis(), MILLISECONDS)
    override def monotonic(unit: TimeUnit): Id[Long] = unit.convert(System.nanoTime(), NANOSECONDS)
  }
}
