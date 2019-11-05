package EShop.lab4

import EShop.lab2.{Cart, CartActor, Checkout}
import akka.actor.{Cancellable, Props}
import akka.event.Logging
import akka.persistence.PersistentActor

import scala.concurrent.duration._
import scala.util.Random

object PersistentCartActor {

  def props(persistenceId: String) = Props(new PersistentCartActor(persistenceId))
}

class PersistentCartActor(
  val persistenceId: String
) extends PersistentActor {

  import EShop.lab2.CartActor._

  private val log       = Logging(context.system, this)
  val cartTimerDuration = 5.seconds

  private def scheduleTimer: Cancellable =
    context.system.scheduler.scheduleOnce(cartTimerDuration, self, ExpireCart)(context.dispatcher)

  override def receiveCommand: Receive = empty

  private def updateState(event: Event, timer: Option[Cancellable] = None): Unit = {
    event match {
      case CartExpired | CheckoutClosed       => context become empty
      case CheckoutCancelled(cart)            => context become nonEmpty(cart, timer.get)
      case ItemAdded(item, cart)              => context become nonEmpty(cart.addItem(item), timer.get)
      case CartEmptied                        => context become empty
      case ItemRemoved(item, cart)            => context become nonEmpty(cart.removeItem(item), timer.get)
      case CheckoutStarted(checkoutRef, cart) => context become inCheckout(cart)
    }
  }

  def empty: Receive = {
    case AddItem(item) =>
      persist(ItemAdded(item, Cart())) { _ =>
        updateState(ItemAdded(item, Cart()), Some(scheduleTimer))
      }
  }

  def nonEmpty(cart: Cart, timer: Cancellable): Receive = {
    case AddItem(item) =>
      persist(ItemAdded(item, cart)) { _ =>
        timer.cancel
        updateState(ItemAdded(item, cart), Some(scheduleTimer))
      }
    case RemoveItem(item) if cart.contains(item) && cart.size > 1 =>
      persist(ItemRemoved(item, cart)) { _ =>
        timer.cancel
        updateState(ItemRemoved(item, cart), Some(scheduleTimer))
      }
    case RemoveItem(item) if !cart.contains(item) => null
    case RemoveItem(item) =>
      persist(ItemRemoved(item, cart)) { _ =>
        timer.cancel
        updateState(CartEmptied)
      }
    case StartCheckout =>
      val checkoutRef = context.actorOf(PersistentCheckout.props(self, Random.alphanumeric.take(256).mkString))
      persist(CheckoutStarted(checkoutRef, cart)) { _ =>
        timer.cancel
        updateState(CheckoutStarted(checkoutRef, cart))
      }
    case ExpireCart =>
      persist(CartExpired) { _ =>
        timer.cancel
        updateState(CartExpired)
      }
  }

  def inCheckout(cart: Cart): Receive = {
    case CartActor.CancelCheckout =>
      persist(CheckoutCancelled(cart)) { _ =>
        updateState(CheckoutCancelled(cart), Some(scheduleTimer))
      }
    case CartActor.CloseCheckout =>
      persist(CheckoutClosed) { _ =>
        updateState(CheckoutClosed)
      }
  }

  override def receiveRecover: Receive = {
    case ItemAdded(item, cart) => updateState(ItemAdded(item, cart), Some(scheduleTimer))
    case ItemRemoved(item, cart) =>
      if (cart.contains(item) && cart.size > 1) updateState(ItemRemoved(item, cart), Some(scheduleTimer))
      else if (!cart.contains(item)) updateState(ItemRemoved(item, cart), Some(scheduleTimer))
      else updateState(CartEmptied)
    case CheckoutStarted(_, cart) =>
      val checkoutRef = context.actorOf(Checkout.props(self))
      updateState(CheckoutStarted(checkoutRef, cart))
    case CartExpired             => updateState(CartExpired)
    case CheckoutCancelled(cart) => updateState(CheckoutCancelled(cart))
    case CheckoutClosed          => updateState(CheckoutClosed)
  }
}
