package EShop.lab3

import EShop.lab3.CartActor.CheckoutStarted
import EShop.lab3.Checkout.PaymentStarted
import EShop.lab3.OrderManager._
import akka.actor.FSM

class OrderManagerFSM extends FSM[State, Data] {

  startWith(Uninitialized, Empty)

  when(Uninitialized) {
    case Event(AddItem(item), Empty) => {
      val cartActor = context.actorOf(CartFSM.props)
      cartActor ! CartActor.AddItem(item)
      sender ! Done
      goto(Open).using(CartData(cartActor))
    }
  }

  when(Open) {
    case Event(AddItem(item), CartData(cartActor)) => {
      cartActor ! CartActor.AddItem(item)
      sender ! Done
      goto(Open).using(CartData(cartActor))
    }
    case Event(RemoveItem(item), CartData(cartActor)) => {
      cartActor ! CartActor.RemoveItem(item)
      sender ! Done
      goto(Open).using(CartData(cartActor))
    }
    case Event(Buy, CartData(cartActor)) => {
      cartActor ! CartActor.StartCheckout
      goto(InCheckout).using(CartDataWithSender(cartActor, sender))
    }
  }

  when(InCheckout) {
    case Event(CheckoutStarted(checkoutRef), CartDataWithSender(cartActor, senderRef)) => {
      senderRef ! Done
      goto(InCheckout).using(InCheckoutData(checkoutRef))
    }
    case Event(SelectDeliveryAndPaymentMethod(delivery, payment), InCheckoutData(checkoutRef)) =>
      checkoutRef ! Checkout.SelectDeliveryMethod(delivery)
      checkoutRef ! Checkout.SelectPayment(payment)
      goto(InPayment).using(InCheckoutDataWithSender(checkoutRef, sender))
  }

  when(InPayment) {
    case Event(PaymentStarted(paymentRef), InCheckoutDataWithSender(checkoutRef, senderRef)) => {
      senderRef ! Done
      goto(InPayment).using(InPaymentData(paymentRef))
    }
    case Event(Pay, InPaymentData(paymentRef)) => {
      paymentRef ! Payment.DoPayment
      goto(InPayment).using(InPaymentDataWithSender(paymentRef, sender))
    }
    case Event(Payment.PaymentConfirmed, InPaymentDataWithSender(paymentRef, senderRef)) =>
      senderRef ! Done
      goto(Finished).using(Empty)
  }

  when(Finished) {
    case _ =>
      sender ! "order manager finished job"
      stay()
  }

}
