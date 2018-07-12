package io.github.tjheslin1.model

import cats.data.State

import scala.annotation.tailrec
import scala.collection.immutable.Queue

class Turn(initiatives: Map[String, Initiative])(implicit rollStrategy: RollStrategy) {

  val initiativeOrder: Queue[Creature] =
    Queue[Creature](
      initiatives.toSeq
        .map {
          case (_, initiative) => initiative
        }
        .sortBy(_.score)
        .reverse
        .map(_.creature): _*)

  def run: Queue[Creature] = {

    @tailrec
    def nextCreature(queue: Queue[Creature], creaturesMovesLeft: Int): Queue[Creature] = {

      if (creaturesMovesLeft < 1) queue
      else {
        val nextTurnQueue = Move.takeMove(queue)
        nextCreature(nextTurnQueue, creaturesMovesLeft - 1)
      }
    }

    nextCreature(initiativeOrder, initiatives.size)
  }
}

object Turn {

  case class TurnTracker(movesLeft: Int, creatureQueue: Queue[Creature])

  type TurnState = State[Int, Queue[Creature]]

  def nextTurn(turnState: TurnState): TurnState = {
    State { movesLeft =>
      if (movesLeft == 0) ???
      else ???
    }
  }

  def apply(initiatives: Map[String, Initiative])(implicit rollStrategy: RollStrategy): Turn = new Turn(initiatives)
}
