package io.scalac.seed.service

import akka.actor._
import io.scalac.seed.domain.AggregateRoot

import scala.concurrent.{Await, Future, ExecutionContext}
import scala.reflect.ClassTag

object AggregateManager {

  trait Command

  val maxChildren = 40
  val childrenToKillAtOnce = 20

  case class PendingCommand(sender: ActorRef, targetProcessorId: String, command: AggregateRoot.Command)

}

/**
 * Base aggregate manager.
 * Handles communication between client and aggregate.
 * It is also capable of aggregates creation and removal.
 *
 */
trait AggregateManager extends Actor with ActorLogging {

  import AggregateRoot._
  import AggregateManager._
  import akka.pattern.ask
  import scala.collection.immutable._

  implicit val ec: ExecutionContext = context.dispatcher

  private var childrenBeingTerminated: Set[ActorRef] = Set.empty
  private var pendingCommands: Seq[PendingCommand] = Nil

  /**
   * Processes command.
   * In most cases it should transform message to appropriate aggregate command (and apply some additional logic if needed) and call [[AggregateManager.processAggregateCommand]]
   *
   */
  def processCommand: Receive

  override def receive = processCommand orElse defaultProcessCommand

  private def defaultProcessCommand: Receive = {
    case Terminated(actor) =>
      childrenBeingTerminated = childrenBeingTerminated filterNot (_ == actor)
      val (commandsForChild, remainingCommands) = pendingCommands partition (_.targetProcessorId == actor.path.name)
      pendingCommands = remainingCommands
      log.debug("Child termination finished. Applying {} cached commands.", commandsForChild.size)
      for (PendingCommand(commandSender, targetProcessorId, command) <- commandsForChild) {
        val child = findOrCreate(targetProcessorId)
        child.tell(command, commandSender)
      }
  }

  /**
   * Processes aggregate command.
   * Creates an aggregate (if not already created) and handles commands caching while aggregate is being killed.
   *
   * @param aggregateId Aggregate id
   * @param command Command that should be passed to aggregate
   */
  def processAggregateCommand(aggregateId: String, command: AggregateRoot.Command) = {
    val maybeChild = context child aggregateId
    maybeChild match {
      case Some(child) if childrenBeingTerminated contains child =>
        log.debug("Received command for aggregate currently being killed. Adding command to cache.")
        pendingCommands :+= PendingCommand(sender(), aggregateId, command)
      case Some(child) =>
        child forward command
      case None =>
        val child = create(aggregateId)
        child forward command
    }
  }

  def processAggregateCollectionCommand[S <: AggregateRoot.State](command: AggregateRoot.Command)(implicit tag: ClassTag[S]): List[S] = {
    import scala.concurrent.duration._
    import akka.util.Timeout

    implicit val timeout = Timeout(3.seconds)
    val futures = context.children.map(_.ask(command)).toList

    Await.result(Future.sequence(futures), timeout.duration).asInstanceOf[List[S]]
  }

  protected def findOrCreate(id: String): ActorRef =
    context.child(id) getOrElse create(id)

  protected def create(id: String): ActorRef = {
    killChildrenIfNecessary()
    val agg = context.actorOf(aggregateProps(id), id)
    context watch agg
    agg
  }

  /**
   * Returns Props used to create an aggregate with specified id
   *
   * @param id Aggregate id
   * @return Props to create aggregate
   */
  def aggregateProps(id: String): Props

  private def killChildrenIfNecessary() = {
    val childrenCount = context.children.size - childrenBeingTerminated.size
    if (childrenCount >= maxChildren) {
      log.debug(s"Max manager children exceeded. Killing ${childrenToKillAtOnce} children.")
      val childrenNotBeingTerminated = context.children.filterNot(childrenBeingTerminated.toSet)
      val childrenToKill = childrenNotBeingTerminated take childrenToKillAtOnce
      childrenToKill foreach (_ ! KillAggregate)
      childrenBeingTerminated ++= childrenToKill
    }
  }

}
