package io.scalac.seed.domain

import akka.actor._
import akka.persistence._

object ChildrenAggregate {

  import AggregateRoot._

  case class Child(id: String, name: String = "") extends State

  case class Initialize(name: String) extends Command

  case class ChildInitialized(name: String) extends Event

  def props(id: String): Props = Props(new ChildrenAggregate(id))
}

class ChildrenAggregate(id: String) extends AggregateRoot {

  import AggregateRoot._
  import ChildrenAggregate._

  override def persistenceId = id

  override def updateState(evt: AggregateRoot.Event): Unit = evt match {
    case ChildInitialized(name) =>
      context.become(created)
      state = Child(id, name)
  }

  val initial: Receive = {
    case Initialize(name) =>
      persist(ChildInitialized(name))(afterEventPersisted)
    case GetState =>
      respond()
    case KillAggregate =>
      context.stop(self)
  }

  val created: Receive = {
    case GetState =>
      respond()
    case KillAggregate =>
      context.stop(self)
  }

  val receiveCommand: Receive = initial

  override def restoreFromSnapshot(metadata: SnapshotMetadata, state: State) = {
    this.state = state
    state match {
      case Uninitialized => context become initial
      case _: Child => context become created
    }
  }

}
