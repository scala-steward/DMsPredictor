package unit.spellcasting

import base.{Tracking, UnitSpecBase}
import cats.syntax.option._
import eu.timepit.refined.auto._
import io.github.tjheslin1.dmspredictor.classes.SpellCaster
import io.github.tjheslin1.dmspredictor.classes.cleric.Cleric
import io.github.tjheslin1.dmspredictor.model._
import io.github.tjheslin1.dmspredictor.model.spellcasting.Concentration._
import util.TestData._

class ConcentrationSpec extends UnitSpecBase {

  "concentrationDifficultyClass" should {
    "set the DC to 10 if half the damage taken is less than 10" in {
      concentrationDifficultyClass(5) shouldBe 10
    }

    "set the DC to half the damage taken if more than 10" in {
      concentrationDifficultyClass(22) shouldBe 11
    }
  }

  "handleConcentration" should {
    "break concentration if check failed" in {
      forAll { cleric: Cleric =>
        new TestContext {
          implicit val roll: RollStrategy = _ => RollResult(8)

          val lowConstitutionCleric = cleric
            .withConcentrating(trackedConditionSpell(1).some)
            .withConstitution(5)
            .asInstanceOf[SpellCaster]

          val updatedCleric = handleConcentration(lowConstitutionCleric, damageTaken = 20)

          updatedCleric.isConcentrating shouldBe false
        }
      }
    }

    "maintain concentration if check passed" in {
      forAll { cleric: Cleric =>
        new TestContext {
          implicit val roll: RollStrategy = _ => RollResult(8)

          val highConstitutionCleric = cleric
            .withConcentrating(trackedConditionSpell(1).some)
            .withConstitution(18)
            .asInstanceOf[SpellCaster]

          val updatedCleric = handleConcentration(highConstitutionCleric, damageTaken = 10)

          updatedCleric.isConcentrating shouldBe true
        }
      }
    }
  }

  abstract private class TestContext extends Tracking {
    implicit val roll: RollStrategy
  }
}