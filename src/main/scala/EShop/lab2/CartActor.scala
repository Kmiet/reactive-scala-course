package EShop.lab2

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
      log.info("Cart: " + cart.toString)
      context become nonEmpty(cart, scheduleTimer)
    case _ => context stop self
  }

  def nonEmpty(cart: Cart, timer: Cancellable): Receive = {
    case AddItem(item) =>
      timer.cancel
      val newCart = cart.addItem(item)
      log.info(newCart.toString)
      context become nonEmpty(newCart, scheduleTimer)
    case RemoveItem(item) if cart.contains(item) && cart.size > 1 =>
      timer.cancel
      val newCart = cart.removeItem(item)
      log.info(newCart.toString)
      context become nonEmpty(newCart, scheduleTimer)
    case RemoveItem(item) if cart.contains(item) =>
      timer.cancel
      log.info("Removed last item from cart")
      context become empty
    case RemoveItem =>
      timer.cancel
      log.info("Cannot remove item. It's not included in cart:" + cart.toString)
      context become nonEmpty(cart, scheduleTimer)
    case ExpireCart =>
      timer.cancel
      log.info("Cart has expired")
      context become empty
    case StartCheckout =>
      timer.cancel
      log.info("Checkout begins")
      context.actorOf(Checkout.props(self)) ! Checkout.StartCheckout
      context become inCheckout(cart)
    case _ => context stop self
  }

  def inCheckout(cart: Cart): Receive = {
    case CancelCheckout =>
      log.info("Checkout canceled")
      context become nonEmpty(cart, scheduleTimer)
    case CloseCheckout =>
      log.info("Checkout completed")
      context become empty
    case _ => context stop self
  }

}
