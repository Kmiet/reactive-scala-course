package EShop.lab2

case class Cart(items: Seq[Any] = Seq.empty) {
  def contains(item: Any): Boolean = items.contains(item)
  def addItem(item: Any): Cart     = Cart(items :+ item)
  def removeItem(item: Any): Cart  = Cart(items.filter(elem => elem != item))
  def size: Int                    = items.length
}

object Cart {
  def empty: Cart = Cart()
}
