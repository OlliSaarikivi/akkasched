
package akkasched

import akka.actor.Actor
import akka.actor.Props

class Application extends Actor {
  var first = true
  
  override def preStart = {
    context.actorOf(Props[Child], "A") ! "report"
    context.actorOf(Props[Child], "B") ! "report"
  }
  def receive = {
    case "reporting" => {
      println(sender.path.name + " is " + (if (first) "first" else "second"))
      first = false
    }
  }
}