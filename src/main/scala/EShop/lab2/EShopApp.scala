package EShop.lab2

import akka.actor.ActorSystem

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object EShopApp extends App {
  val system        = ActorSystem("Reactive2")
  val cartActor     = system.actorOf(CartActor.props, "cartActor")
  val checkoutActor = system.actorOf(Checkout.props(cartActor), "checkoutActor")
  val cartFsm       = system.actorOf(CartActor.props, "cartFSM")
  val checkoutFsm   = system.actorOf(CartActor.props, "checkoutFSM")

  // Test CartActor

  //  cartActor ! CartActor.AddItem("item")
  //  cartActor ! CartActor.RemoveItem("item")
  //  cartActor ! CartActor.AddItem("new-item")
  //  Thread.sleep(7000)
  //  cartActor ! CartActor.AddItem("new-item2")
  //
  //  cartActor ! CartActor.StartCheckout
  //  cartActor ! CartActor.CancelCheckout
  //
  //  cartActor ! CartActor.StartCheckout
  //  cartActor ! CartActor.CloseCheckout
  //  cartActor ! CartActor.AddItem("another-item")

  // Test Checkout

  //  checkoutActor ! Checkout.StartCheckout
  //  checkoutActor ! Checkout.SelectDeliveryMethod("mail")
  //  checkoutActor ! Checkout.SelectPayment("card")
  //  checkoutActor ! Checkout.ReceivePayment

  // Test CartFSM

  // Test CheckoutFSM

  Await.result(system.whenTerminated, Duration.Inf)
}
