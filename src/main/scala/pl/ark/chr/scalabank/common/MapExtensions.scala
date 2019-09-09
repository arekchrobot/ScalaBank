package pl.ark.chr.scalabank.common

object MapExtensions {

  implicit class MapExt[K, V](val self: Map[K, V]) extends AnyVal {
    def getOrElseUpdated(key: K, op: => V): Map[K, V] =
      self.get(key) match {
        case Some(_) => self
        case None =>
          val newVal = op
          self.updated(key, newVal)
      }
  }
}
