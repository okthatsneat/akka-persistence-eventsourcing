package io.scalac.seed.route

import scala.concurrent.duration._
import scala.reflect.ClassTag

import akka.actor._
import io.scalac.seed.common.Error
import io.scalac.seed.domain.AggregateRoot
import io.scalac.seed.domain.AggregateRoot.{Removed, Uninitialized}
import io.scalac.seed.service.AggregateManager
import org.json4s.DefaultFormats
import spray.http.StatusCode
import spray.http.StatusCodes._
import spray.httpx.Json4sSupport
import spray.routing.{HttpService, RequestContext}

object RequestHandler {

  case class RegisterRequestActor[S <: AggregateRoot.State](
      r: RequestContext, 
      target: ActorRef, 
      message: AggregateManager.Command)(implicit tag: ClassTag[S]) extends RequestHandler {
    override def processResult: Receive = {
      case tag(res) => complete(Created, res)
    }
  }

  case class GetAllRequestActor[S <: List[AggregateRoot.State]](
      r: RequestContext, 
      target: ActorRef, 
      message: AggregateManager.Command)(implicit tag: ClassTag[S]) extends RequestHandler {
    override def processResult: Receive = {
      case tag(res) => complete(OK, res)
    }
  }

}

/**
 * RequestHandler is an actor that is created once per request.
 * Immediately after instantiation it sends a command to specific [[AggregateManager]].
 * It then awaits response from [[AggregateManager]] or handles exception or timeout if one of them occurred.
 * Once the result is known, it is written as http response.
 */
trait RequestHandler extends Actor with ActorLogging with Json4sSupport {

  import context._

  val json4sFormats = DefaultFormats

  def r: RequestContext
  def target: ActorRef
  def message: AggregateManager.Command

  setReceiveTimeout(2.seconds)
  target ! message

  /**
   * Completes the request using given status code and body and stops the actor.
   *
   * @param status Response status code
   * @param obj Response body
   * @tparam T Response body type
   */
  def complete[T <: AnyRef](status: StatusCode, obj: T) = {
    r.complete(status, obj)
    stop(self)
  }

  override def receive = processResult orElse defaultReceive

  /**
   * Processes result returned from aggregate and completes the request using [[RequestHandler.complete]] method.
   */
  def processResult: Receive

  private def defaultReceive: Receive = {
    case ReceiveTimeout =>
      log.debug("Received timeout. Sending GatewayTimeout response.")
      complete(GatewayTimeout, Error("Request timeout"))
    case res =>
      log.error("received unexpected message " + res)
      complete(InternalServerError, "Something unexpected happened. We're working on it.")
  }

}

/**
 * RequestHandlerCreator contains some helper methods to create RequestHandlers for specific request types.
 */
trait RequestHandlerCreator {
  self: HttpService =>

  import RequestHandler._

  def handleRegister[S <: AggregateRoot.State](
      r: RequestContext, 
      target: ActorRef, 
      message: AggregateManager.Command)(implicit tag: ClassTag[S]) =
    actorRefFactory.actorOf(Props(RegisterRequestActor[S](r, target, message)))

  def handleGetAll[S <: List[AggregateRoot.State]](
      r: RequestContext, 
      target: ActorRef, 
      message: AggregateManager.Command)(implicit tag: ClassTag[S]) =
    actorRefFactory.actorOf(Props(GetAllRequestActor[S](r, target, message)))

}
