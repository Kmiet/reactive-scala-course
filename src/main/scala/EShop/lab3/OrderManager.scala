package EShop.lab3

import EShop.lab2.CartActor
import EShop.lab2.Checkout
import EShop.lab3.OrderManager._
import akka.actor.{Actor, ActorRef}
import akka.event.{Logging, LoggingReceive}

object OrderManager {
  sealed trait State
  case object Uninitialized extends State
  case object Open          extends State
  case object InCheckout    extends State
  case object InPayment     extends State
  case object Finished      extends State

  sealed trait Command
  case class AddItem(id: String)                                               extends Command
  case class RemoveItem(id: String)                                            extends Command
  case class SelectDeliveryAndPaymentMethod(delivery: String, payment: String) extends Command
  case object Buy                                                              extends Command
  case object Pay                                                              extends Command

  sealed trait Ack
  case object Done extends Ack //trivial ACK

  sealed trait Data
  case object Empty                                                            extends Data
  case class CartData(cartRef: ActorRef)                                       extends Data
  case class CartDataWithSender(cartRef: ActorRef, sender: ActorRef)           extends Data
  case class InCheckoutData(checkoutRef: ActorRef)                             extends Data
  case class InCheckoutDataWithSender(checkoutRef: ActorRef, sender: ActorRef) extends Data
  case class InPaymentData(paymentRef: ActorRef)                               extends Data
  case class InPaymentDataWithSender(paymentRef: ActorRef, sender: ActorRef)   extends Data
}

class OrderManager extends Actor {

  private val log = Logging(context.system, this)

  override def receive = uninitialized

  def uninitialized: Receive = {
    case AddItem(id) =>
      val cartRef = context actorOf CartActor.props
      cartRef ! CartActor.AddItem(id)
      context become open(cartRef)
      sender ! Done
    case _ => context stop self
  }

  def open(cartActor: ActorRef): Receive = {
    case AddItem(id) =>
      cartActor ! CartActor.AddItem(id)
      sender ! Done
    case RemoveItem(id) =>
      cartActor ! CartActor.RemoveItem(id)
      sender ! Done
    case Buy =>
      cartActor ! CartActor.StartCheckout
      context become inCheckout(cartActor, sender)
    case _ => context stop self
  }

  def inCheckout(cartActorRef: ActorRef, senderRef: ActorRef): Receive = {
    case CartActor.CheckoutStarted(checkoutRef, cart) =>
      context become inCheckout(checkoutRef)
      senderRef ! Done
  }

  def inCheckout(checkoutActorRef: ActorRef): Receive = {
    case SelectDeliveryAndPaymentMethod(delivery, payment) =>
      checkoutActorRef ! Checkout.SelectDeliveryMethod(delivery)
      checkoutActorRef ! Checkout.SelectPayment(payment)
      context become inPayment(sender)
  }

  def inPayment(senderRef: ActorRef): Receive = {
    case Checkout.PaymentStarted(paymentRef) =>
      context become inPayment(paymentRef, self)
      senderRef ! Done
  }

  def inPayment(paymentActorRef: ActorRef, senderRef: ActorRef): Receive = {
    case Pay =>
      paymentActorRef ! Payment.DoPayment
      context become inPayment(paymentActorRef, sender)
    case Payment.PaymentConfirmed =>
      context become finished
      senderRef ! Done
  }

  def finished: Receive = {
    case _ =>
      sender ! "order manager finished job"
      context become uninitialized
  }
}
