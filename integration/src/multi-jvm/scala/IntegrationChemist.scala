package funnel
package integration

import scalaz.Scalaz
import scalaz.stream.async
import scalaz.stream.async.boundedQueue
import scalaz.concurrent.{Task,Strategy}
import chemist.{Chemist,PlatformEvent,Pipeline,sinks}

class IntegrationChemist extends Chemist[IntegrationPlatform]{
  import Scalaz._, PlatformEvent._, Pipeline.contextualise

  private[this] val log = journal.Logger[IntegrationChemist]

  private[this] val lifecycle =
    async.signalOf[PlatformEvent](NoOp)(Strategy.Executor(Chemist.serverPool))

  private[this] val queue =
    boundedQueue[PlatformEvent](100)(Chemist.defaultExecutor)

  def init: ChemistK[Unit] =
    for {
      cfg <- config
      _    = log.info("Initilizing Chemist....")
      _   <- Pipeline.task(
            lifecycle.discrete.map(contextualise),
            cfg.rediscoveryInterval
          )(cfg.discovery,
            queue,
            cfg.sharder,
            cfg.http,
            sinks.caching(cfg.state),
            sinks.unsafeNetworkIO(cfg.remoteFlask, queue)
          ).liftKleisli
    } yield ()
}
