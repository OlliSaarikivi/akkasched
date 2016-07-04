package akka.dispatch

import akka.actor.Cell
import akka.actor.ActorCell
import akka.dispatch.sysmsg.SystemMessage
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.Duration
import akka.actor.ActorRef
import scala.collection.immutable.Queue
import akkasched.WaitableExecutorServiceWrapper
import akkasched.ExecutionTree
import akkasched.ActorId
import akkasched.Context

class Transition(val receiver: ActorCell, val invocation: Envelope) {}

/*
 * This class implements a dispatcher, which asks a Strategy instance
 * for the execution order.
 */

class TracingDispatcher(
  _configurator: MessageDispatcherConfigurator,
  val _id: String,
  val _throughput: Int,
  val _throughputDeadlineTime: Duration,
  _executorServiceFactoryProvider: ExecutorServiceFactoryProvider,
  val _shutdownTimeout: FiniteDuration)
    extends Dispatcher(_configurator,
      _id,
      _throughput,
      _throughputDeadlineTime,
      _executorServiceFactoryProvider,
      _shutdownTimeout) {

  var strategy = Context.strategy
  Context.dispatcher = this
  @volatile var schedule: Boolean = false

  var cellIds = Map[ActorCell, ActorId]()
  var refIds = Map[ActorRef, ActorId]()

  /* Currently there is a separate ActorId for receiving and sending */
  def idOf(cell: ActorCell): ActorId = {
    if (cellIds.contains(cell))
      cellIds(cell)
    else {
      val id = new ActorId(cell.self.path.toString())
      cellIds += (cell -> id)
      id
    }
  }
  def idOf(ref: ActorRef): ActorId = {
    if (refIds.contains(ref))
      refIds(ref)
    else {
      val id = new ActorId(ref.path.toString())
      refIds += (ref -> id)
      id
    }
  }

  def dispatchAll() = {
    val executor = executorService.executor match {
      case d: WaitableExecutorServiceWrapper => d
      case _                                 => throw new ClassCastException
    }

    while (strategy.hasNext) {
      val next = strategy.next
      println("Dispatch " + next.invocation.message + " from " +
          next.invocation.sender.path + " to " + next.receiver.self.path)
      super.dispatch(next.receiver, next.invocation)
      executor.awaitTasksExecuted()
      Thread.sleep(100) // TODO: fix scheduling so that this is not needed
    }
    schedule = false
  }

  protected[akka] override def dispatch(receiver: ActorCell, invocation: Envelope): Unit = {
    if (schedule && invocation.sender.path.elements.toList(0) == "user") {
      val senderId = idOf(invocation.sender)
      val receiverId = idOf(receiver)
      strategy.push(senderId, receiverId, new Transition(receiver, invocation))
    } else super.dispatch(receiver, invocation)
  }

  protected[akka] override def systemDispatch(receiver: ActorCell, message: SystemMessage): Unit = {
    super.systemDispatch(receiver, message)
  }

  protected[akka] override def register(actor: ActorCell) = {
    super.register(actor)
  }

  protected[akka] override def unregister(actor: ActorCell) = {
    super.unregister(actor)
  }
}

