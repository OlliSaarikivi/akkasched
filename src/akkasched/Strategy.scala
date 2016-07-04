package akkasched

import akka.dispatch.Transition
import scala.collection.immutable.Queue
import akka.dispatch.TracingDispatcher
import scala.concurrent.Promise
import akka.actor.ActorSystem

object Context {
  val strategy: Strategy = new ExecutionTree
  var dispatcher: TracingDispatcher = null
}

class ActorId(val path: String) {

  override def equals(that: Any): Boolean =
    that match {
      case that: ActorId => this.path == that.path
      case _             => false
    }

  override def hashCode: Int = {
    return path.hashCode()
  }
}

trait Strategy {
  def init() // Called at the start of each test
  def end() // Called at the end of each test
  def isFinished: Boolean // True if no more tests to run
  def hasNext: Boolean // True if the current test not yet finished
  def next: Transition // Returns the next transition to execute
  def push(from: ActorId, to: ActorId, transition: Transition) // Called for each new transition
}

class Dummy() extends Strategy {
  var cloud = Map[(ActorId, ActorId), Queue[Transition]]().withDefaultValue(Queue())

  def init() {
    // NOP
  }

  def end() {
    // NOP
  }

  def isFinished = false

  def hasNext = !cloud.keys.isEmpty

  def next() = {
    val someKey = cloud.keys.head
    val (next, newQueue) = cloud(someKey).dequeue
    if (newQueue.isEmpty)
      cloud -= someKey
    else
      cloud += (someKey -> newQueue)
    next
  }

  def push(from: ActorId, to: ActorId, transition: Transition) {
    val key = (from, to)
    cloud += (key -> (cloud(key).enqueue(transition)))
  }
}

/*
 * Explores the full execution tree of scheduling choices (no reduction)
 */

class Node(
  val parent: Node,
  var enabled: Set[(ActorId, ActorId)],
  var children: Map[(ActorId, ActorId), Node] = Map(),
  var isClosed: Boolean = false) {}

class ExecutionTree() extends Strategy {
  var root: Node = null
  var cloud: Map[(ActorId, ActorId), Queue[Transition]] = null
  var current: Node = null
  var replay: Queue[(ActorId, ActorId)] = null

  def init() {
    cloud = Map().withDefaultValue(Queue())
    replay = Queue()
    if (root == null) {
      root = new Node(null, Set())
      current = root
    } else {
      current = root
      var node = root
      while (true) {
        node.children.find { x => !x._2.isClosed } match {
          case Some(x) => {
            replay = replay.enqueue(x._1)
            node = x._2
          }
          case None => {
            node.enabled.find { y => !node.children.contains(y) } match {
              case Some(y) => {
                replay = replay.enqueue(y)
                return
              }
              case None => throw new RuntimeException
            }
          }
        }
      }
    }
  }

  def end() {
    val shouldClose = (n: Node) => n.enabled.size == n.children.size &&
      n.children.values.forall { x => x.isClosed }
    while (current != null && shouldClose(current)) {
      current.children = null
      current.isClosed = true
      current = current.parent
    }
  }

  def isFinished = root != null && root.isClosed

  def hasNext = !cloud.keys.isEmpty

  def next() = {
    val someKey = if (!replay.isEmpty) {
      val (key, newReplay) = replay.dequeue
      replay = newReplay
      key
    } else cloud.keys.head
    val (next, newQueue) = cloud(someKey).dequeue
    if (newQueue.isEmpty)
      cloud -= someKey
    else
      cloud += (someKey -> newQueue)
    if (current.children.contains(someKey))
      current = current.children(someKey)
    else {
      val newNode = new Node(current, cloud.keySet, Map())
      current.children += (someKey -> newNode)
      current = newNode
    }
    next
  }

  def push(from: ActorId, to: ActorId, transition: Transition) {
    val key = (from, to)
    cloud += (key -> (cloud(key).enqueue(transition)))
    current.enabled += key
  }
}