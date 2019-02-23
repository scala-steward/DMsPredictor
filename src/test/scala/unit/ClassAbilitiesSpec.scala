package unit

import base.UnitSpecBase
import cats.syntax.option._
import io.github.tjheslin1.dmspredictor.classes.ClassAbilities.nextAbilityToUseInConjunction
import io.github.tjheslin1.dmspredictor.classes.CoreAbilities.extraAttack
import io.github.tjheslin1.dmspredictor.classes.fighter.Fighter
import io.github.tjheslin1.dmspredictor.model._
import io.github.tjheslin1.dmspredictor.model.ability.{Ability, AbilityAction, WholeAction}
import org.scalatest.OptionValues
import util.TestData._

class ClassAbilitiesSpec extends UnitSpecBase with OptionValues {

  "nextAbilityToUseInConjunction" should {
    "find the next ability lower in order" in {
      forAll { fighter: Fighter =>
        new TestContext {
          val combatant = fighter
            .withAbilities(List(dummyAbility(1), extraAttack(2), dummyAbility(3)))
            .withCombatIndex(1)

          val expected = dummyAbility(3)(combatant)
          val actual = nextAbilityToUseInConjunction(combatant,
                                                     none[Combatant],
                                                     2,
                                                     AbilityAction.Any).value.apply(combatant)

          actual.name shouldBe expected.name
          actual.order shouldBe expected.order
        }
      }
    }
  }

  private class TestContext {
    implicit val roll: RollStrategy = Dice.defaultRandomiser

    def dummyAbility(currentOrder: Int)(combatant: Combatant): Ability = new Ability(combatant) {
      val name: String     = s"test-ability-$currentOrder"
      val order            = currentOrder
      val levelRequirement = LevelOne
      val abilityAction    = WholeAction

      def triggerMet(others: List[Combatant]) = true
      val conditionMet: Boolean                 = true

      def useAbility[_: RS](others: List[Combatant], focus: Focus): (Combatant, List[Combatant]) =
        (combatant, target)

      def update: Creature = combatant.creature

    }
  }
}
