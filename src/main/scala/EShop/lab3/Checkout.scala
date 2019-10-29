package EShop.lab3

import akka.actor.{Actor, ActorRef, Cancellable, Props}
import akka.event.Logging

import scala.concurrent.duration._
import scala.language.postfixOps

object Checkout {

  sealed trait Data
  case object Uninitialized                               extends Data
  case class SelectingDeliveryStarted(timer: Cancellable) extends Data
  case class ProcessingPaymentStarted(timer: Cancellable) extends Data

  sealed trait Command
  case object StartCheckout                       extends Command
  case class SelectDeliveryMethod(method: String) extends Command
  case object CancelCheckout                      extends Command
  case object ExpireCheckout                      extends Command
  case class SelectPayment(payment: String)       extends Command
  case object ExpirePayment                       extends Command
  case object ReceivePayment                      extends Command

  sealed trait Event
  case object CheckOutClosed                   extends Event
  case class PaymentStarted(payment: ActorRef) extends Event

  def props(cart: ActorRef) = Props(new Checkout())
}

class Checkout extends Actor {
  import Checkout._

  private val scheduler = context.system.scheduler
  private val log       = Logging(context.system, this)

  val checkoutTimerDuration = 10 seconds
  val paymentTimerDuration  = 10 seconds

  private def checkoutTimer: Cancellable =
    scheduler.scheduleOnce(checkoutTimerDuration, self, ExpireCheckout)(context.dispatcher)
  private def paymentTimer: Cancellable =
    scheduler.scheduleOnce(checkoutTimerDuration, self, ExpirePayment)(context.dispatcher)

  def receive: Receive = {
    case StartCheckout => context become selectingDelivery(checkoutTimer)
    case _             => context.system.terminate
  }

  def selectingDelivery(timer: Cancellable): Receive = {
    case SelectDeliveryMethod(method) =>
      timer.cancel
      context become selectingPaymentMethod(checkoutTimer)
    case CancelCheckout | ExpireCheckout =>
      timer.cancel
      context become cancelled
      self ! CancelCheckout
    case _ => context.system.terminate
  }

  def selectingPaymentMethod(timer: Cancellable): Receive = {
    case SelectPayment(method) =>
      timer.cancel
      log.debug("Hello")
      val paymentRef = context.actorOf(Payment.props(method, sender, self))
      context become processingPayment(paymentTimer)
      sender ! PaymentStarted(paymentRef)
    case CancelCheckout | ExpireCheckout =>
      timer.cancel
      context become cancelled
      self ! CancelCheckout
    case _ => context.system.terminate
  }

  def processingPayment(timer: Cancellable): Receive = {
    case ReceivePayment =>
      timer.cancel
      context become closed
      self ! CheckOutClosed
    case CancelCheckout | ExpirePayment =>
      timer.cancel
      context become cancelled
      self ! CancelCheckout
    case _ => context.system.terminate
  }

  def cancelled: Receive = {
    case _ =>
      context.parent ! CartActor.CancelCheckout
      context stop self
  }

  def closed: Receive = {
    case _ =>
      context.parent ! CartActor.CloseCheckout
      context stop self
  }

}
