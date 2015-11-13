package io.scalac.seed.service

import akka.actor._
import io.scalac.seed.domain._
import java.util.UUID

object ChildrenAggregateManager {

  import AggregateManager._

  case class RegisterChild(name: String) extends Command

  case object GetAllChildren extends Command

  def props: Props = Props(new ChildrenAggregateManager)
}

class ChildrenAggregateManager extends AggregateManager {

  import AggregateRoot._
  import ChildrenAggregateManager._
  import ChildrenAggregate._
  def processCommand = {
    case RegisterChild(name) =>
      val id = UUID.randomUUID().toString()
      processAggregateCommand(id, Initialize(name))
    case GetAllChildren =>
      sender() ! processAggregateCollectionCommand(GetState)
  }

  override def aggregateProps(id: String) = ChildrenAggregate.props(id)
}
