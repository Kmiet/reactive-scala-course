package EShop.lab3

import EShop.lab2.{Cart, CartActor}
import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

class CartTest
  extends TestKit(ActorSystem("CartTest"))
  with FlatSpecLike
  with ImplicitSender
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {

  override def afterAll: Unit =
    TestKit.shutdownActorSystem(system)

  //use GetItems command which was added to make test easier
  it should "add item properly" in {
    val cartActor = TestActorRef(new CartActor())

    cartActor ! CartActor.GetItems
    expectMsg(Seq.empty)
    cartActor ! CartActor.AddItem("item")
    expectNoMessage()
    cartActor ! CartActor.GetItems
    expectMsg(Cart().addItem("item").items)

    val cartActor2 = system.actorOf(CartActor.props)

    cartActor2 ! CartActor.GetItems
    expectMsg(Seq.empty)
    cartActor2 ! CartActor.AddItem("item")
    expectNoMessage()
    cartActor2 ! CartActor.GetItems
    expectMsg(Cart().addItem("item").items)
  }

  it should "be empty after adding and removing the same item" in {
    val cartActor = TestActorRef(new CartActor())

    cartActor ! CartActor.AddItem("item")
    expectNoMessage()
    cartActor ! CartActor.GetItems
    expectMsg(Cart().addItem("item").items)
    cartActor ! CartActor.RemoveItem("item")
    expectNoMessage()
    cartActor ! CartActor.GetItems
    expectMsg(Seq.empty)

    val cartActor2 = system.actorOf(CartActor.props)

    cartActor2 ! CartActor.AddItem("item")
    expectNoMessage()
    cartActor2 ! CartActor.GetItems
    expectMsg(Cart().addItem("item").items)
    cartActor2 ! CartActor.RemoveItem("item")
    expectNoMessage()
    cartActor2 ! CartActor.GetItems
    expectMsg(Seq.empty)
  }

  it should "start checkout" in {
    val cartActor = TestActorRef(new CartActor())

    cartActor ! CartActor.AddItem("item")
    expectNoMessage()
    cartActor ! CartActor.GetItems
    expectMsg(Cart().addItem("item").items)
    cartActor ! CartActor.StartCheckout
    expectMsgPF() {
      case CartActor.CheckoutStarted(_) => ()
    }

    val cartActor2 = system.actorOf(CartActor.props)

    cartActor2 ! CartActor.AddItem("item")
    expectNoMessage()
    cartActor2 ! CartActor.GetItems
    expectMsg(Cart().addItem("item").items)
    cartActor2 ! CartActor.StartCheckout
    expectMsgPF() {
      case CartActor.CheckoutStarted(_) => ()
    }
  }
}
