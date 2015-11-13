package io.scalac.seed

import akka.actor._
import io.scalac.seed.route._
import io.scalac.seed.service._
import org.json4s.DefaultFormats
import spray.http.MediaTypes._

class ServiceActor extends Actor with ActorLogging with ChildrenRoute with UserRoute {

  val json4sFormats = DefaultFormats

  implicit def actorRefFactory = context

  implicit val executionContext = context.dispatcher
  
  val childrenAggregateManager = context.actorOf(ChildrenAggregateManager.props)

  val userAggregateManager = context.actorOf(UserAggregateManager.props)

  def receive =
    runRoute(
      pathPrefix("api") {
        respondWithMediaType(`application/json`) {
          childrenRoute ~ userRoute
        }
      }
    )

}
