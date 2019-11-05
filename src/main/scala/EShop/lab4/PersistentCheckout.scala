package EShop.lab4

import EShop.lab2.CartActor
import EShop.lab3.Payment
import akka.actor.{ActorRef, Cancellable, Props}
import akka.event.{Logging, LoggingReceive}
import akka.persistence.PersistentActor

import scala.util.Random
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

object PersistentCheckout {

  def props(cartActor: ActorRef, persistenceId: String) =
    Props(new PersistentCheckout(cartActor, persistenceId))
}

class PersistentCheckout(
  cartActor: ActorRef,
  val persistenceId: String
) extends PersistentActor {

  import EShop.lab2.Checkout._
  private val scheduler = context.system.scheduler
  private val log       = Logging(context.system, this)
  val timerDuration     = 1.seconds

  def cancelTimer = scheduler.scheduleOnce(timerDuration, self, ExpireCheckout)(context.dispatcher)

  def paymentTimer = scheduler.scheduleOnce(timerDuration, self, ExpirePayment)(context.dispatcher)

  private def updateState(event: Event, maybeTimer: Option[Cancellable] = None): Unit = {
    event match {
      case CheckoutStarted                => context become selectingDelivery(maybeTimer.get)
      case DeliveryMethodSelected(method) => context become selectingPaymentMethod(maybeTimer.get)
      case CheckOutClosed                 => context become closed
      case CheckoutCancelled              => context become cancelled
      case PaymentStarted(payment)        => context become processingPayment(maybeTimer.get)
    }
  }

  def receiveCommand: Receive = {
    case StartCheckout =>
      persist(CheckoutStarted) { event =>
        updateState(event, Some(cancelTimer))
      }
  }

  def selectingDelivery(timer: Cancellable): Receive = {
    case SelectDeliveryMethod(method) =>
      timer.cancel
      persist(DeliveryMethodSelected(method)) { event =>
        updateState(event, Some(cancelTimer))
      }
    case CancelCheckout | ExpireCheckout =>
      timer.cancel
      persist(CheckoutCancelled) { event =>
        updateState(event)
      }
      self ! CancelCheckout
  }

  def selectingPaymentMethod(timer: Cancellable): Receive = {
    case SelectPayment(method) =>
      timer.cancel
      val paymentRef = context.actorOf(Payment.props(method, sender, self))
      persist(PaymentStarted(paymentRef)) { event =>
        updateState(event, Some(paymentTimer))
      }
      sender ! PaymentStarted(paymentRef)
    case CancelCheckout | ExpireCheckout =>
      timer.cancel
      persist(CheckoutCancelled) { event =>
        updateState(event)
      }
      self ! CancelCheckout
  }

  def processingPayment(timer: Cancellable): Receive = {
    case ReceivePayment =>
      timer.cancel
      persist(CheckOutClosed) { event =>
        updateState(event)
      }
      self ! CheckOutClosed
    case CancelCheckout | ExpirePayment =>
      timer.cancel
      persist(CheckoutCancelled) { event =>
        updateState(event)
      }
      self ! CancelCheckout
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

  override def receiveRecover: Receive = {
    case CheckoutStarted                => updateState(CheckoutStarted, Some(cancelTimer))
    case DeliveryMethodSelected(method) => updateState(DeliveryMethodSelected(method), Some(cancelTimer))
    case PaymentStarted(r)              => updateState(PaymentStarted(r), Some(paymentTimer))
    case event: Event                   => updateState(event)
  }
}
