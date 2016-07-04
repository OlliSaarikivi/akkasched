package akkasched

import akka.actor.ActorSystem
import akka.actor.Props
import com.typesafe.config.ConfigFactory
import akka.dispatch.TracingDispatcher
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.Promise

object ScalaMain {
  def main(args: Array[String]) {
    val config = ConfigFactory.parseString("""
      akka {
        actor {
          default-dispatcher {
            type = "akka.dispatch.TracingDispatcherConfigurator"
          }
        }
      }
      """).withFallback(ConfigFactory.load())
    while (!Context.strategy.isFinished) {
      println("================================ NEW TEST ================================")
      Context.strategy.init()
      val system = ActorSystem("MySystem", config)
      Context.dispatcher.schedule = true
      val app = system.actorOf(Props[Application], "app")
      Thread.sleep(100) // TODO: get rid of this wait somehow
      Context.dispatcher.dispatchAll()
      val terminated = system.terminate()
      Await.result(terminated, Duration.Inf)
      Context.strategy.end()
    }
  }
}