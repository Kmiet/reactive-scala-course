package EShop.lab3

import EShop.lab3.CartActor.{
  AddItem,
  CancelCheckout,
  CheckoutStarted,
  CloseCheckout,
  GetItems,
  RemoveItem,
  StartCheckout
}
import EShop.lab3.CartFSM.Status
import akka.actor.{LoggingFSM, Props}

import scala.concurrent.duration._
import scala.language.postfixOps

object CartFSM {

  object Status extends Enumeration {
    type Status = Value
    val Empty, NonEmpty, InCheckout = Value
  }

  def props() = Props(new CartFSM())
}

class CartFSM extends LoggingFSM[Status.Value, Cart] {
  import EShop.lab3.CartFSM.Status._

  // useful for debugging, see: https://doc.akka.io/docs/akka/current/fsm.html#rolling-event-log
  override def logDepth = 12

  val cartTimerDuration: FiniteDuration = 3 seconds

  startWith(Empty, Cart.empty)

  when(Empty) {
    case Event(AddItem(item), cart) => {
      goto(NonEmpty).using(cart.addItem(item))
    }
    case Event(GetItems, cart) => {
      sender ! cart.items
      stay()
    }
  }

  when(NonEmpty, stateTimeout = cartTimerDuration) {
    case Event(AddItem(item), cart: Cart) => {
      stay().using(cart.addItem(item))
    }
    case Event(RemoveItem(item), cart: Cart) if cart.contains(item) && cart.size > 1 => {
      stay().using(cart.removeItem(item))
    }
    case Event(RemoveItem(item), cart: Cart) if !cart.contains(item) => {
      stay().using(cart)
    }
    case Event(RemoveItem(item), cart) => {
      goto(Empty).using(cart.removeItem(item))
    }
    case Event(GetItems, cart) => {
      sender ! cart.items
      stay()
    }
    case Event(StartCheckout, cart) => {
      val checkoutRef = context.actorOf(CheckoutFSM.props(self))
      checkoutRef ! Checkout.StartCheckout
      sender ! CheckoutStarted(checkoutRef)
      goto(InCheckout).using(cart)
    }
    case Event(StateTimeout, _) => {
      goto(Empty).using(Cart.empty)
    }
  }

  when(InCheckout) {
    case Event(CloseCheckout, _) => {
      goto(Empty).using(Cart.empty)
    }
    case Event(CancelCheckout, cart) => {
      goto(NonEmpty).using(cart)
    }
  }

}
