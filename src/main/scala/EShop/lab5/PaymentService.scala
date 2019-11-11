package EShop.lab5

import EShop.lab5.PaymentService.{ClientError, PaymentClientError, PaymentServerError, PaymentSucceeded, ServerError}
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCode, StatusCodes}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}

import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object PaymentService {

  case object ClientError
  case object ServerError
  case object PaymentSucceeded // http status 200
  class PaymentClientError extends Exception // http statuses 400, 404
  class PaymentServerError extends Exception // http statuses 500, 408, 418

  def props(method: String, payment: ActorRef) = Props(new PaymentService(method, payment))

}

class PaymentService(method: String, payment: ActorRef) extends Actor with ActorLogging {

  final implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))

  private val logger = Logging(context.system, this)
  private val http   = Http(context.system)
  private val URI    = getURI

  override def preStart(): Unit = {
    val resF = http.singleRequest(HttpRequest(uri = getURI))
    resF.onComplete {
      case Success(res) =>
        res.status match {
          case StatusCodes.OK =>
            self ! PaymentSucceeded
          case StatusCodes.RequestTimeout =>
            self ! ServerError
          case StatusCodes.NotFound =>
            self ! ClientError
          case _ =>
            self ! ServerError
        }
//      case _ =>
//        self ! ServerError
    }
  } //create http request (use http and uri)

  override def receive: Receive = {
    case ClientError =>
      throw new PaymentClientError()
    case ServerError =>
      throw new PaymentServerError()
    case PaymentSucceeded =>
      payment ! PaymentSucceeded
      context stop self
  }

  private def getURI: String = method match {
    case "payu"   => "http://127.0.0.1:8080"
    case "paypal" => "http://httpbin.org/status/408"
    case "visa"   => "http://httpbin.org/status/200"
    case _        => "http://httpbin.org/status/404"
  }

}
