package EShop.lab2

import EShop.lab2.Checkout._
import EShop.lab2.CheckoutFSM.Status._
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestFSMRef, TestKit}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

class CheckoutFSMTest
  extends TestKit(ActorSystem("CheckoutTest"))
  with Matchers
  with FlatSpecLike
  with ImplicitSender
  with BeforeAndAfterAll {

  val deliveryMethod = "post"
  val paymentMethod  = "paypal"

  override def afterAll: Unit =
    TestKit.shutdownActorSystem(system)
  import CheckoutFSMTest._

  it should "be in selectingDelivery state after checkout start" in {
    val checkoutActor = checkoutActorWithResponseOnStateChange(system)

    checkoutActor ! StartCheckout
    expectMsg(selectingDeliveryMsg)
  }

  it should "be in cancelled state after cancel message received in selectingDelivery State" in {
    val checkoutActor = checkoutActorWithResponseOnStateChange(system)

    checkoutActor ! StartCheckout
    expectMsg(selectingDeliveryMsg)
    checkoutActor ! CancelCheckout
    expectMsg(cancelledMsg)
  }

  it should "be in cancelled state after expire checkout timeout in selectingDelivery state" in {
    val checkoutActor = TestFSMRef[Status, Data, CheckoutFSM](new CheckoutFSM())

    checkoutActor ! StartCheckout
    Thread.sleep(4000)
    checkoutActor ! SelectDeliveryMethod(deliveryMethod)
    checkoutActor.stateName shouldBe Cancelled
  }

  it should "be in selectingPayment state after delivery method selected" in {
    val checkoutActor = checkoutActorWithResponseOnStateChange(system)

    checkoutActor ! StartCheckout
    expectMsg(selectingDeliveryMsg)
    checkoutActor ! SelectDeliveryMethod(deliveryMethod)
    expectMsg(selectingPaymentMethodMsg)
  }

  it should "be in cancelled state after cancel message received in selectingPayment State" in {
    val checkoutActor = checkoutActorWithResponseOnStateChange(system)

    checkoutActor ! StartCheckout
    expectMsg(selectingDeliveryMsg)
    checkoutActor ! SelectDeliveryMethod(deliveryMethod)
    expectMsg(selectingPaymentMethodMsg)
    checkoutActor ! CancelCheckout
    expectMsg(cancelledMsg)
  }

  it should "be in cancelled state after expire checkout timeout in selectingPayment state" in {
    val checkoutActor = checkoutActorWithResponseOnStateChange(system)

    checkoutActor ! StartCheckout
    expectMsg(selectingDeliveryMsg)
    checkoutActor ! SelectDeliveryMethod(deliveryMethod)
    expectMsg(selectingPaymentMethodMsg)
    Thread.sleep(4000)
    checkoutActor ! SelectPayment(paymentMethod)
    expectNoMessage()
  }

  it should "be in processingPayment state after payment selected" in {
    val checkoutActor = checkoutActorWithResponseOnStateChange(system)

    checkoutActor ! StartCheckout
    expectMsg(selectingDeliveryMsg)
    checkoutActor ! SelectDeliveryMethod(deliveryMethod)
    expectMsg(selectingPaymentMethodMsg)
    checkoutActor ! SelectPayment(paymentMethod)
    expectMsgPF() {
      case PaymentStarted(_) => ()
    }
    expectMsg(processingPaymentMsg)
  }

  it should "be in cancelled state after cancel message received in processingPayment State" in {
    val checkoutActor = checkoutActorWithResponseOnStateChange(system)

    checkoutActor ! StartCheckout
    expectMsg(selectingDeliveryMsg)
    checkoutActor ! SelectDeliveryMethod(deliveryMethod)
    expectMsg(selectingPaymentMethodMsg)
    checkoutActor ! SelectPayment(paymentMethod)
    expectMsgPF() {
      case PaymentStarted(_) => ()
    }
    expectMsg(processingPaymentMsg)
    checkoutActor ! CancelCheckout
    expectMsg(cancelledMsg)
  }

  it should "be in cancelled state after expire checkout timeout in processingPayment state" in {
    val checkoutActor = TestFSMRef[Status, Data, CheckoutFSM](new CheckoutFSM())

    checkoutActor ! StartCheckout
    checkoutActor ! SelectDeliveryMethod(deliveryMethod)
    checkoutActor ! SelectPayment(paymentMethod)
    expectMsgPF() {
      case PaymentStarted(_) => ()
    }
    Thread.sleep(6000)
    checkoutActor ! ReceivePayment
    checkoutActor.stateName shouldBe Cancelled
  }

  it should "be in closed state after payment completed" in {
    val checkoutActor = checkoutActorWithResponseOnStateChange(system)

    checkoutActor ! StartCheckout
    expectMsg(selectingDeliveryMsg)
    checkoutActor ! SelectDeliveryMethod(deliveryMethod)
    expectMsg(selectingPaymentMethodMsg)
    checkoutActor ! SelectPayment(paymentMethod)
    expectMsgPF() {
      case PaymentStarted(_) => ()
    }
    expectMsg(processingPaymentMsg)
    checkoutActor ! ReceivePayment
    expectMsg(closedMsg)
  }

  it should "not change state after cancel msg in completed state" in {
    val checkoutActor = checkoutActorWithResponseOnStateChange(system)

    checkoutActor ! StartCheckout
    expectMsg(selectingDeliveryMsg)
    checkoutActor ! SelectDeliveryMethod(deliveryMethod)
    expectMsg(selectingPaymentMethodMsg)
    checkoutActor ! SelectPayment(paymentMethod)
    expectMsgPF() {
      case PaymentStarted(_) => ()
    }
    expectMsg(processingPaymentMsg)
    checkoutActor ! ReceivePayment
    expectMsg(closedMsg)
    checkoutActor ! CancelCheckout
    expectNoMessage()
  }

}

object CheckoutFSMTest {

  val emptyMsg                  = "empty"
  val selectingDeliveryMsg      = "selectingDelivery"
  val selectingPaymentMethodMsg = "selectingPaymentMethod"
  val processingPaymentMsg      = "processingPayment"
  val cancelledMsg              = "cancelled"
  val closedMsg                 = "closed"

  def checkoutActorWithResponseOnStateChange(system: ActorSystem): ActorRef =
    system.actorOf(Props(new CheckoutFSM {

      onTransition {
        case NotStarted -> SelectingDelivery =>
          sender ! selectingDeliveryMsg

        case SelectingDelivery -> SelectingPaymentMethod =>
          sender ! selectingPaymentMethodMsg

        case SelectingPaymentMethod -> ProcessingPayment =>
          sender ! processingPaymentMsg

        case ProcessingPayment -> Closed =>
          sender ! closedMsg

        case ProcessingPayment -> Cancelled =>
          sender ! cancelledMsg

        case SelectingPaymentMethod -> Cancelled =>
          sender ! cancelledMsg

        case SelectingDelivery -> Cancelled =>
          sender ! cancelledMsg

      }
    }))
}
