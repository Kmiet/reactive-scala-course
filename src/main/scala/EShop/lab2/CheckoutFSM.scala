package EShop.lab2

import EShop.lab2.CheckoutFSM.Status
import EShop.lab2.Checkout._
import EShop.lab3.PaymentFSM
import akka.actor.{ActorRef, LoggingFSM, Props}

import scala.concurrent.duration._
import scala.language.postfixOps

object CheckoutFSM {

  object Status extends Enumeration {
    type Status = Value
    val NotStarted, SelectingDelivery, SelectingPaymentMethod, Cancelled, ProcessingPayment, Closed = Value
  }

  def props(cartActor: ActorRef) = Props(new CheckoutFSM)
}

class CheckoutFSM extends LoggingFSM[Status.Value, Data] {
  import CheckoutFSM.Status._
  // useful for debugging, see: https://doc.akka.io/docs/akka/current/fsm.html#rolling-event-log
  override def logDepth = 12

  val checkoutTimerDuration: FiniteDuration = 3 seconds
  val paymentTimerDuration: FiniteDuration  = 3 seconds

  startWith(NotStarted, Uninitialized)

  when(NotStarted) {
    case Event(StartCheckout, Uninitialized) => {
      goto(SelectingDelivery)
    }
  }

  when(SelectingDelivery, stateTimeout = checkoutTimerDuration) {
    case Event(SelectDeliveryMethod(method), Uninitialized) => {
      goto(SelectingPaymentMethod)
    }
    case Event(StateTimeout | CancelCheckout, _) => {
      context.parent ! CartActor.CancelCheckout
      goto(Cancelled)
    }
  }

  when(SelectingPaymentMethod, stateTimeout = checkoutTimerDuration) {
    case Event(SelectPayment(method), Uninitialized) => {
      val paymentRef = context.actorOf(PaymentFSM.props(method, sender, self))
      sender ! PaymentStarted(paymentRef)
      goto(ProcessingPayment)
    }
    case Event(StateTimeout | CancelCheckout, _) => {
      context.parent ! CartActor.CancelCheckout
      goto(Cancelled)
    }
  }

  when(ProcessingPayment, stateTimeout = paymentTimerDuration) {
    case Event(ReceivePayment, _) => {
      context.parent ! CartActor.CloseCheckout
      goto(Closed)
    }
    case Event(StateTimeout | CancelCheckout, _) => {
      context.parent ! CartActor.CancelCheckout
      goto(Cancelled)
    }
  }

  when(Cancelled) {
    case _ => stay
  }

  when(Closed) {
    case _ => stay
  }

  whenUnhandled {
    case Event(e, s) => {
      log.warning("received unhandled request {} in state {}/{}", e, stateName, s)
      stay
    }
  }

}
