package EShop.lab3

import EShop.lab2.{CartActor, Checkout, CheckoutFSM}
import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

class CheckoutTest
  extends TestKit(ActorSystem("CheckoutTest"))
  with FlatSpecLike
  with ImplicitSender
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {

  override def afterAll: Unit =
    TestKit.shutdownActorSystem(system)

  it should "Send close confirmation to cart" in {
    val testCart      = TestProbe()
    val checkoutActor = testCart.childActorOf(Checkout.props(testCart.ref))

    checkoutActor ! Checkout.StartCheckout
    expectNoMessage()

    checkoutActor ! Checkout.SelectDeliveryMethod("post")
    expectNoMessage()

    checkoutActor ! Checkout.SelectPayment("card")
    expectMsgPF() {
      case Checkout.PaymentStarted(_) => ()
    }

    checkoutActor ! Checkout.ReceivePayment
    testCart.expectMsg(CartActor.CloseCheckout)
  }
}
