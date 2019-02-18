package io.github.tjheslin1.dmspredictor.model

trait Condition extends Product with Serializable {
  val saveDc: Int
  val turnsLeft: Int
}

case class Turned(saveDc: Int, turnsLeft: Int) extends Condition
