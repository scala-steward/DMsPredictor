package io.github.tjheslin1.dmspredictor.classes.fighter

import cats.syntax.option._
import com.typesafe.scalalogging.LazyLogging
import io.github.tjheslin1.dmspredictor.classes.ClassAbilities._
import io.github.tjheslin1.dmspredictor.classes.Player
import io.github.tjheslin1.dmspredictor.model.Actions._
import io.github.tjheslin1.dmspredictor.model.Creature.creatureHealthLens
import io.github.tjheslin1.dmspredictor.model._
import io.github.tjheslin1.dmspredictor.model.ability._
import io.github.tjheslin1.dmspredictor.strategy.Focus
import io.github.tjheslin1.dmspredictor.strategy.Focus.nextToFocus
import io.github.tjheslin1.dmspredictor.strategy.Target.monsters
import io.github.tjheslin1.dmspredictor.util.IntOps._
import io.github.tjheslin1.dmspredictor.util.ListOps._
import monocle.Lens
import monocle.macros.GenLens

case class BaseFighterAbilities(secondWindUsed: Boolean, actionSurgeUsed: Boolean)

object BaseFighterAbilities extends LazyLogging {

  import Fighter._

  val secondWindUsedLens: Lens[BaseFighterAbilities, Boolean] =
    GenLens[BaseFighterAbilities](_.secondWindUsed)
  val actionSurgeUsedLens: Lens[BaseFighterAbilities, Boolean] =
    GenLens[BaseFighterAbilities](_.actionSurgeUsed)

  def allUsed(): BaseFighterAbilities   = BaseFighterAbilities(true, true)
  def allUnused(): BaseFighterAbilities = BaseFighterAbilities(false, false)

  def secondWind(currentOrder: Int)(combatant: Combatant): Ability = new Ability(combatant) {
    val baseFighter = combatant.creature.asInstanceOf[BaseFighter]

    val name             = "Second Wind"
    val order            = currentOrder
    val levelRequirement = LevelTwo
    val abilityAction    = WholeAction

    def triggerMet(others: List[Combatant]) =
      combatant.creature.health <= combatant.creature.maxHealth / 2

    def conditionMet =
      baseFighter.level.value >= levelRequirement && baseFighter.abilityUsages.secondWindUsed == false

    def useAbility[_: RS](others: List[Combatant], focus: Focus): (Combatant, List[Combatant]) = {
      logger.debug(s"${combatant.creature.name} used Second wind")

      val updatedHealth =
        Math.min(combatant.creature.maxHealth,
                 combatant.creature.health + (1 * HitDice) + baseFighter.level.value)
      val updatedCombatant =
        (Combatant.creatureLens composeLens creatureHealthLens).set(updatedHealth)(combatant)

      (updatedCombatant, List.empty[Combatant])
    }

    def update: Creature =
      (BaseFighter.abilityUsagesLens composeLens secondWindUsedLens)
        .set(true)(baseFighter)
  }

  def twoWeaponFighting(currentOrder: Int)(combatant: Combatant): Ability = new Ability(combatant) {
    val baseFighter = combatant.creature.asInstanceOf[BaseFighter]

    val name             = "Two Weapon Fighting"
    val order            = currentOrder
    val levelRequirement = LevelOne
    val abilityAction    = BonusAction

    def triggerMet(others: List[Combatant]) = true

    def conditionMet: Boolean = combatant.creature.offHand match {
      case Some(w: Weapon) =>
        baseFighter.bonusActionUsed == false &&
          w.twoHanded == false &&
          combatant.creature.baseWeapon.twoHanded == false &&
          baseFighter.fightingStyles.contains(TwoWeaponFighting)
      case _ => false
    }

    def useAbility[_: RS](others: List[Combatant], focus: Focus): (Combatant, List[Combatant]) = {
      logger.debug(s"${combatant.creature.name} used two weapon fighting")

      val enemies = monsters(others)
      val target  = nextToFocus(enemies, focus)

      target match {
        case None => (combatant, List.empty[Combatant])
        case Some(attackTarget: Combatant) =>
          val mainHandAttack = attack(combatant, combatant.creature.weapon, attackTarget)

          val (attacker1, attackTarget1) =
            if (mainHandAttack.result > 0)
              resolveDamageMainHand(combatant, attackTarget, mainHandAttack)
            else
              (combatant, attackTarget)

          val offHandWeapon = combatant.creature.offHand.get.asInstanceOf[Weapon]
          val offHandAttack = attack(attacker1, offHandWeapon, attackTarget1)

          val (attacker2, attackTarget2) =
            if (offHandAttack.result > 0)
              resolveDamage(attacker1, attackTarget1, offHandWeapon, offHandAttack)
            else
              (attacker1, attackTarget1)

          (attacker2, List(attackTarget2))
      }
    }

    def update: Creature = Player.playerBonusActionUsedLens.set(true)(baseFighter)
  }

  def actionSurge(currentOrder: Int)(combatant: Combatant): Ability =
    new Ability(combatant: Combatant) {
      val baseFighter = combatant.creature.asInstanceOf[BaseFighter]

      val name             = "Action Surge"
      val order            = currentOrder
      val levelRequirement = LevelTwo
      val abilityAction    = WholeAction

      def triggerMet(others: List[Combatant]) = true
      def conditionMet: Boolean               = baseFighter.abilityUsages.actionSurgeUsed == false

      def useAbility[_: RS](others: List[Combatant],
                            focus: Focus): (Combatant, List[Combatant]) = {
        logger.debug(s"${combatant.creature.name} used Action Surge")

        val enemies = monsters(others)
        val target  = nextToFocus(enemies, focus)

        target match {
          case None => (combatant, List.empty[Combatant])
          case Some(attackTarget: Combatant) =>
            nextAbilityToUseInConjunction(combatant, enemies, order, AbilityAction.Any)
              .fold(useAttackActionTwice(combatant, attackTarget)) { nextAbility =>
                val (updatedAttacker, updatedTargets) =
                  useAdditionalAbility(nextAbility, combatant, enemies, focus)

                val updatedEnemies = enemies.replace(updatedTargets)

                

//                optUpdatedTarget.fold((updatedAttacker, List.empty[Combatant])) { updatedTarget =>
//                  val updatedEnemies = enemies.replace(updatedTarget)
//
//                  nextAbilityToUseInConjunction(updatedAttacker,
//                                                updatedEnemies,
//                                                order,
//                                                AbilityAction.Any)
//                    .fold(useAttackActionTwice(updatedAttacker, updatedTarget)) { nextAbility2 =>
//                      useAdditionalAbility(nextAbility2, updatedAttacker, updatedEnemies, focus)
//                    }
                }
              }
        }
      }

      def update: Creature =
        (BaseFighter.abilityUsagesLens composeLens actionSurgeUsedLens)
          .set(true)(baseFighter)
          .asInstanceOf[Creature]
    }
}
