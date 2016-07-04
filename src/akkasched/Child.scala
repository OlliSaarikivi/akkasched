
package akkasched

import akka.actor.Actor
import akka.actor.Props

class Child extends Actor {
  def receive = {
    case "report" => context.parent ! "reporting"
  }
}