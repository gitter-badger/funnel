package funnel
package flask

import scala.concurrent.duration._
import scalaz.concurrent.Task
import scalaz.std.option._
import scalaz.syntax.applicative._
import elastic.ElasticCfg

object Main {
  import java.io.File
  import knobs.{ ClassPathResource, Config, FileResource, Required }

  val config: Task[Config] = for {
    a <- knobs.loadImmutable(List(Required(
      FileResource(new File("/usr/share/oncue/etc/flask.cfg")) or
        ClassPathResource("oncue/flask.cfg"))))
    b <- knobs.aws.config
  } yield a ++ b

  val (options, cfg) = config.flatMap { cfg =>
    val name             = cfg.lookup[String]("flask.name")
    val cluster          = cfg.lookup[String]("flask.cluster")
    val retriesDuration  = cfg.require[Duration]("flask.schedule.duration")
    val maxRetries       = cfg.require[Int]("flask.schedule.retries")
    val elasticURL       = cfg.lookup[String]("flask.elastic-search.url")
    val elasticIx        = cfg.lookup[String]("flask.elastic-search.index-name")
    val elasticTy        = cfg.lookup[String]("flask.elastic-search.type-name")
    val elasticDf        =
      cfg.lookup[String]("flask.elastic-search.partition-date-format").getOrElse("yyyy.MM.dd")
    val elasticTimeout   = cfg.lookup[Duration]("flask.elastic-search.connection-timeout").getOrElse(5.seconds)
    val esGroups         = cfg.lookup[List[String]]("flask.elastic-search.groups")
    val esTemplate       = cfg.lookup[String]("flask.elastic-search.template.name").getOrElse("flask")
    val esTemplateLoc    = cfg.lookup[String]("flask.elastic-search.template.location")
    val esPublishTimeout = cfg.lookup[Duration]("flask.elastic-search.minimum-publish-frequency").getOrElse(10.minutes)
    val riemannHost      = cfg.lookup[String]("flask.riemann.host")
    val riemannPort      = cfg.lookup[Int]("flask.riemann.port")
    val ttl              = cfg.lookup[Int]("flask.riemann.ttl-in-minutes").map(_.minutes)
    val riemann          = (riemannHost |@| riemannPort |@| ttl)(RiemannCfg)
    val elastic          = (elasticURL |@| elasticIx |@| elasticTy |@| esGroups)(
      ElasticCfg(_, _, _, elasticDf, esTemplate, esTemplateLoc, _, esPublishTimeout.toNanos.nanos, elasticTimeout))
    val httpPort         = cfg.lookup[Int]("flask.network.http-port").getOrElse(5775)
    val selfiePort       = cfg.lookup[Int]("flask.network.selfie-port").getOrElse(7557)
    val metricTTL        = cfg.lookup[Duration]("flask.metric-ttl")
    val telemetryPort    = cfg.require[Int]("flask.network.telemetry-port")
    val collectLocal     = cfg.lookup[Boolean]("flask.collect-local-metrics")
    val localFrequency   = cfg.lookup[Int]("flask.local-metric-frequency")

    Task((Options(name, cluster, retriesDuration, maxRetries, elastic, riemann, collectLocal, localFrequency, httpPort, selfiePort, metricTTL, telemetryPort), cfg))
  }.run

  val I = new Instruments(1.minute)

  val app = new Flask(options, I)

  def main(args: Array[String]) = app.run(args)
}
