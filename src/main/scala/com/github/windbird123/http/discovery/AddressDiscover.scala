package com.github.windbird123.http.discovery

import zio._

trait AddressDiscover {
  val periodSec: Long = 300L
  def fetch(): Task[Seq[String]]
}

object AddressDiscover {
  val sample: AddressDiscover = new AddressDiscover {
    override val periodSec: Long            = 300L
    override def fetch(): Task[Seq[String]] = UIO(Seq("https://jsonplaceholder.typicode.com"))
  }
}
