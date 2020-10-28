package xyz.tg44.prometheus.exporter


import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Source}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.util.Timeout

import concurrent.duration._

import scala.concurrent.ExecutionContext

object CheckpointStage {

  lazy val defaultBuckets: Seq[Double] = Seq(
    100.nanoseconds, 500.nanoseconds,
    5.microseconds, 100.microseconds, 0.5.milliseconds,
    1.millisecond, 10.milliseconds, 100.milliseconds, 500.milliseconds,
    1.second, 2.seconds, 5.seconds)
    .map(_.toNanos.toDouble)

  final implicit class FlowMetricsOps[In, Out, Mat](val flow: Flow[In, Out, Mat]) extends AnyVal {
    def checkpoint(name: String)(
      implicit registry: Registry,
      actorSystem: ActorSystem,
      ec: ExecutionContext
    ): Flow[In, Out, Mat] =
      flow.via(CheckpointStage(name))

    def checkpointForSlowStream(name: String, buckets: Seq[Double] = defaultBuckets)(
      implicit registry: Registry,
      actorSystem: ActorSystem,
      ec: ExecutionContext
    ): Flow[In, Out, Mat] =
      flow.via(CheckpointStageWithHistogram(name, buckets))
  }

  final implicit class SourceMetricsOps[Out, Mat](val source: Source[Out, Mat]) extends AnyVal {
    def checkpoint(name: String)(
      implicit registry: Registry,
      actorSystem: ActorSystem,
      ec: ExecutionContext
    ): Source[Out, Mat] =
      source.via(CheckpointStage(name))

    def checkpointForSlowStream(name: String, buckets: Seq[Double] = defaultBuckets)(
      implicit registry: Registry,
      actorSystem: ActorSystem,
      ec: ExecutionContext
    ): Source[Out, Mat] =
      source.via(CheckpointStageWithHistogram(name, buckets))
  }

  private final case class CheckpointStage[T](
    name: String
  )(
    implicit registry: Registry,
    actorSystem: ActorSystem,
    ec: ExecutionContext
  ) extends GraphStage[FlowShape[T, T]] {
    implicit val to: Timeout = Timeout(500.millis)
    private val backpressureRatioSum = Counter(
      name + "_backpressure_ratio_sum",
      "bpratio rate(sum)/rate(count) is the avg percentage of time that the upstream waits for downstream the bigger this num the slower the downstream"
    )
    private val backpressureRatioCount = Counter(name + "_backpressure_ratio_count", "")
    private val throughput = Counter(name + "_throughput", s"Throughput for $name")

    private def markPush(ratio: Long): Unit = {
      backpressureRatioSum.inc(ratio)
      backpressureRatioCount.inc()
      throughput.inc()
    }

    val in = Inlet[T]("Checkpoint.in")
    val out = Outlet[T]("Checkpoint.out")
    override val shape = FlowShape(in, out)

    override def initialAttributes: Attributes = Attributes.name("checkpoint")

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) with InHandler with OutHandler {
        var lastPulled: Long = 0L
        var lastPushed: Long = 0L

        override def preStart(): Unit = {
          lastPulled = System.nanoTime()
          lastPushed = lastPulled
        }

        override def onPush(): Unit = {
          push(out, grab(in))

          val now = System.nanoTime()
          val divider = now - lastPushed
          if (divider > 0) {
            markPush((lastPulled - lastPushed) * 100 / divider)
            lastPushed = now
          }
        }

        override def onPull(): Unit = {
          pull(in)
          lastPulled = System.nanoTime()
        }

        setHandlers(in, out, this)
      }
  }

  private final case class CheckpointStageWithHistogram[T](
    name: String,
    buckets: Seq[Double]
  )(
    implicit registry: Registry,
    actorSystem: ActorSystem,
    ec: ExecutionContext
  ) extends GraphStage[FlowShape[T, T]] {

    import xyz.tg44.prometheus.exporter.implicits._

    implicit val to: Timeout = Timeout(500.millis)
    private val pullLatency       = Histogram(name + "_pull_latency_ns", "", buckets)
    private val pushLatency       = Histogram(name + "_push_latency_ns", "", buckets)
    private val backpressureRatio = Histogram(name + "_backpressure_ratio", "", 1 to 100)
    private val backpressureRatioSum = Counter(
      name + "_backpressure_ratio_sum",
      "bpratio rate(sum)/rate(count) is the avg percentage of time that the upstream waits for downstream the bigger this num the slower the downstream"
    )
    private val backpressureRatioCount = Counter(name + "_backpressure_ratio_count", "")
    private val throughput = Counter(name + "_throughput", s"Throughput for $name")
    private val backpressured     = Gauge(name + "_backpressured", s"Backpressure state for $name")

    private def markPull(nanos: Long): Unit = {
      pullLatency.observe(nanos)
      backpressured.set(0)
    }

    private def markPush(nanos: Long, ratio: Long): Unit = {
      pushLatency.observe(nanos)
      backpressureRatio.observe(ratio)
      backpressureRatioSum.inc(ratio)
      backpressureRatioCount.inc()
      throughput.inc()
      backpressured.set(1)
    }

    val in = Inlet[T]("Checkpoint.in")
    val out = Outlet[T]("Checkpoint.out")
    override val shape = FlowShape(in, out)

    override def initialAttributes: Attributes = Attributes.name("checkpoint")

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) with InHandler with OutHandler {
        var lastPulled: Long = 0L
        var lastPushed: Long = 0L

        override def preStart(): Unit = {
          lastPulled = System.nanoTime()
          lastPushed = lastPulled
          backpressured.inc()
        }

        override def onPush(): Unit = {
          push(out, grab(in))

          val now = System.nanoTime()
          val divider = now - lastPushed
          if (divider > 0) {
            markPush(now - lastPulled, (lastPulled - lastPushed) * 100 / divider)
            lastPushed = now
          }
        }

        override def onPull(): Unit = {
          pull(in)

          lastPulled = System.nanoTime()
          val nanos = lastPulled - lastPushed
          if(nanos > 0) {
            markPull(nanos)
          }
        }

        setHandlers(in, out, this)
      }
  }
}

