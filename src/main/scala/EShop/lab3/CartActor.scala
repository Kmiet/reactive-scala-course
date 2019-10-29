package EShop.lab3

import akka.actor.{Actor, ActorRef, Cancellable, Props}
import akka.event.Logging

import scala.concurrent.duration._
import scala.language.postfixOps

object CartActor {

  sealed trait Command
  case class AddItem(item: Any)    extends Command
  case class RemoveItem(item: Any) extends Command
  case object ExpireCart           extends Command
  case object StartCheckout        extends Command
  case object CancelCheckout       extends Command
  case object CloseCheckout        extends Command
  case object GetItems             extends Command // command made to make testing easier

  sealed trait Event
  case class CheckoutStarted(checkoutRef: ActorRef) extends Event

  def props = Props(new CartActor())
}

class CartActor extends Actor {
  import CartActor._

  private val log       = Logging(context.system, this)
  val cartTimerDuration = 5 seconds

  private def scheduleTimer: Cancellable =
    context.system.scheduler.scheduleOnce(cartTimerDuration, self, ExpireCart)(context.dispatcher)

  def receive: Receive = empty

  def empty: Receive = {
    case AddItem(item) =>
      val cart = Cart().addItem(item)
      context become nonEmpty(cart, scheduleTimer)
    case GetItems =>
      sender ! Cart.empty.items
    case _ => context stop self
  }

  def nonEmpty(cart: Cart, timer: Cancellable): Receive = {
    case AddItem(item) =>
      timer.cancel
      val newCart = cart.addItem(item)
      context become nonEmpty(newCart, scheduleTimer)
    case RemoveItem(item) if cart.contains(item) && cart.size > 1 =>
      timer.cancel
      val newCart = cart.removeItem(item)
      context become nonEmpty(newCart, scheduleTimer)
    case RemoveItem(item) if cart.contains(item) =>
      timer.cancel
      context become empty
    case RemoveItem =>
      timer.cancel
      context become nonEmpty(cart, scheduleTimer)
    case GetItems =>
      sender ! cart.items
    case ExpireCart =>
      timer.cancel
      context become empty
    case StartCheckout =>
      timer.cancel
      val checkoutRef = context.actorOf(Checkout.props(self))
      checkoutRef ! Checkout.StartCheckout
      context become inCheckout(cart)
      sender ! CheckoutStarted(checkoutRef)
    case _ => context stop self
  }

  def inCheckout(cart: Cart): Receive = {
    case CancelCheckout =>
      context become nonEmpty(cart, scheduleTimer)
    case CloseCheckout =>
      context stop self
    case _ => context stop self
  }

}
