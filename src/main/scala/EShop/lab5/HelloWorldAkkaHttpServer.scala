package EShop.lab5

import java.net.URI

import EShop.lab5.ProductCatalog.{GetItems, Items}
import akka.actor.ActorSystem
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.{HttpApp, Route}
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat}
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Success

object HelloWorldAkkaHttpServer {
  case class Product(id: String, name: String, brand: String, price: BigDecimal, count: Int)
  case class ProductList(products: List[Product])
}

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val productFormat     = jsonFormat5(HelloWorldAkkaHttpServer.Product)
  implicit def productListFormat = jsonFormat1(HelloWorldAkkaHttpServer.ProductList)

  //custom formatter just for example
  implicit val uriFormat = new JsonFormat[java.net.URI] {
    override def write(obj: java.net.URI): spray.json.JsValue = JsString(obj.toString)
    override def read(json: JsValue): URI = json match {
      case JsString(url) => new URI(url)
      case _             => throw new RuntimeException("Parsing exception")
    }
  }

}

object HelloWorldAkkaHttpServerApp extends App {
  new HelloWorldAkkaHttpServer().startServer("localhost", 9000)
}

/** Just to demonstrate how one can build akka-http based server with JsonSupport */
class HelloWorldAkkaHttpServer extends HttpApp with JsonSupport {

  private val config = ConfigFactory.load()
  val actorSystem    = ActorSystem("eshop", config.getConfig("eshop").withFallback(config))

  implicit val timeout: Timeout = 10.second

  override protected def routes: Route =
    pathPrefix("brands" / Segment / "products") { brandName =>
      concat(
        pathEnd {
          concat(
            get {
              parameter('q.as[String]) { query =>
                val catalog =
                  actorSystem.actorSelection("akka.tcp://ProductCatalog@127.0.0.1:2553/user/productcatalog")
                val fut = catalog ? GetItems(brandName, query.split(" ").toList)
                onComplete(fut) {
                  case Success(items) =>
                    complete(
                      HelloWorldAkkaHttpServer.ProductList(items.asInstanceOf[List[HelloWorldAkkaHttpServer.Product]])
                    )
                  case _ =>
                    complete("My dear 500")
                }
              }
            }
          )
        }
      )
    }

}
