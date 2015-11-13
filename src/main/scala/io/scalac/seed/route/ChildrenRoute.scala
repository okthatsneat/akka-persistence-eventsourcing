package io.scalac.seed.route

import akka.actor._
import io.scalac.seed.domain.ChildrenAggregate
import io.scalac.seed.service._
import spray.httpx.Json4sSupport
import spray.routing._
import spray.routing.authentication.BasicAuth

object ChildrenRoute {

}

trait ChildrenRoute extends HttpService with Json4sSupport with RequestHandlerCreator with UserAuthenticator {

  import ChildrenRoute._

  import ChildrenAggregateManager._

  val childrenAggregateManager: ActorRef

  val childrenRoute =
    path("children") {
      get {
        serveGetAll(GetAllChildren)
      } ~
      post {
        entity(as[RegisterChild]) { cmd =>
          serveRegister(cmd)
        }
      }
    }

  private def serveRegister(message: AggregateManager.Command): Route =
    ctx => handleRegister[ChildrenAggregate.Child](ctx, childrenAggregateManager, message)

  private def serveGetAll(message: AggregateManager.Command): Route =
    ctx => handleGetAll[List[ChildrenAggregate.Child]](ctx, childrenAggregateManager, message)

}
