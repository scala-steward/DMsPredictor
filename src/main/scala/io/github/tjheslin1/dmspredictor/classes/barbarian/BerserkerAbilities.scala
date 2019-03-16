package io.github.tjheslin1.dmspredictor.classes.barbarian

import cats.data.NonEmptyList
import com.typesafe.scalalogging.LazyLogging
import io.github.tjheslin1.dmspredictor.classes.ClassAbilities._
import io.github.tjheslin1.dmspredictor.classes.Player.playerBonusActionUsedLens
import io.github.tjheslin1.dmspredictor.classes.barbarian.BaseBarbarian._
import io.github.tjheslin1.dmspredictor.model.Actions.attackAndDamage
import io.github.tjheslin1.dmspredictor.model.Creature.creatureResistancesLens
import io.github.tjheslin1.dmspredictor.model._
import io.github.tjheslin1.dmspredictor.model.ability._
import io.github.tjheslin1.dmspredictor.strategy.Focus
import io.github.tjheslin1.dmspredictor.strategy.Focus.nextToFocus
import io.github.tjheslin1.dmspredictor.strategy.Target.monsters
import io.github.tjheslin1.dmspredictor.util.ListOps._

object BerserkerAbilities extends LazyLogging {

  def frenzy(currentOrder: Int)(combatant: Combatant): Ability = new Ability(combatant) {
    val berserker = combatant.creature.asInstanceOf[Berserker]

    val name                         = "Frenzy"
    val order                        = currentOrder
    val abilityAction: AbilityAction = WholeAction
    val levelRequirement: Level      = LevelThree

    def triggerMet(others: List[Combatant]) =
      berserker.inRage == false && berserker.inFrenzy == false
    def conditionMet: Boolean = berserker.level >= levelRequirement && berserker.rageUsages > 0

    def useAbility[_: RS](others: List[Combatant], focus: Focus): (Combatant, List[Combatant]) = {
      logger.debug(s"${combatant.creature.name} used Frenzy")

      val ragingBarbarianCombatant =
        Combatant.creatureLens.set(updateFrenzyingBarbarian(berserker))(combatant)

      val enemies = monsters(others)
      val target  = nextToFocus(enemies, focus)

      target match {
        case None => (ragingBarbarianCombatant, List.empty[Combatant])
        case Some(targetOfAttack) =>
          nextAbilityToUseInConjunction(ragingBarbarianCombatant,
                                        enemies,
                                        order,
                                        AbilityAction.Action)
            .fold {
              val (updatedAttacker, updatedTarget) =
                attackAndDamage(ragingBarbarianCombatant, targetOfAttack)
              (updatedAttacker, others.replace(updatedTarget))
            } { nextAbility =>
              val (updatedAttacker, updatedEnemies) =
                useAdditionalAbility(nextAbility, ragingBarbarianCombatant, enemies, focus)
              (updatedAttacker, others.replace(updatedEnemies))
            }
      }
    }

    def update: Creature = berserker

    private def updateFrenzyingBarbarian(unfrenziedBerserker: Berserker): Creature = {
      val frenzyBerserkser  = Berserker._inFrenzy.set(true)(unfrenziedBerserker)
      val updatedRageUsages = frenzyBerserkser.rageUsages - 1

      val updatedBerserker       = rageUsagesLens.set(updatedRageUsages)(frenzyBerserkser)
      val rageTurnsLeftBerserker = rageTurnsLeftLens.set(10)(updatedBerserker)

      val resistantBerserker = creatureResistancesLens
        .set(berserker.resistances ++ List(Bludgeoning, Piercing, Slashing))(rageTurnsLeftBerserker)
        .asInstanceOf[BaseBarbarian]

      val bonusActionUsedBerserker =
        playerBonusActionUsedLens.set(true)(resistantBerserker).asInstanceOf[Berserker]

      Berserker._inFrenzy.set(true)(bonusActionUsedBerserker)
    }
  }

  def bonusFrenzyAttack(currentOrder: Int)(combatant: Combatant): Ability = new Ability(combatant) {
    val berserker = combatant.creature.asInstanceOf[Berserker]

    val name                         = "Bonus Frenzy Attack"
    val order                        = currentOrder
    val abilityAction: AbilityAction = BonusAction
    val levelRequirement: Level      = LevelThree

    def triggerMet(others: List[Combatant]): Boolean = berserker.inFrenzy == true
    def conditionMet: Boolean =
      berserker.level >= levelRequirement && berserker.bonusActionUsed == false

    def useAbility[_: RS](others: List[Combatant], focus: Focus): (Combatant, List[Combatant]) = {
      logger.debug(s"${combatant.creature.name} used $name")

      val enemies = monsters(others)
      val target  = nextToFocus(enemies, focus)

      target match {
        case None => (combatant, others)
        case Some(targetOfAttack) =>
          nextAbilityToUseInConjunction(combatant, enemies, order, NonEmptyList.of(SingleAttack))
            .fold {
              val (updatedAttacker, updatedTarget) = attackAndDamage(combatant, targetOfAttack)
              (updatedAttacker, others.replace(updatedTarget))
            } { singleAttackAbility =>
              val (updatedCombatant, updatedTargets) =
                useAdditionalAbility(singleAttackAbility, combatant, enemies, focus)

              val updatedEnemies = enemies.replace(updatedTargets)

              (updatedCombatant, updatedEnemies)
            }
      }
    }

    def update: Creature = Berserker._bonusActionUsed.set(true)(berserker)
  }
}
