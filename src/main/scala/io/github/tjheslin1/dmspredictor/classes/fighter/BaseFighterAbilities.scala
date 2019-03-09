package io.github.tjheslin1.dmspredictor.classes.fighter

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
    val abilityAction    = BonusAction

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

      (updatedCombatant, others)
    }

    def update: Creature = {
      val secondWindUsedFighter = (BaseFighter.abilityUsagesLens composeLens secondWindUsedLens)
        .set(true)(baseFighter)

      Player.playerBonusActionUsedLens.set(true)(secondWindUsedFighter)
    }
  }

  def twoWeaponFighting(currentOrder: Int)(combatant: Combatant): Ability = new Ability(combatant) {
    val baseFighter = combatant.creature.asInstanceOf[BaseFighter]

    val name             = "Two Weapon Fighting"
    val order            = currentOrder
    val levelRequirement = LevelOne
    val abilityAction    = SingleAttack

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
        case None => (combatant, others)
        case Some(attackTarget) =>
          val mainHandAttack = attack(combatant, combatant.creature.weapon, attackTarget)

          val (updatedAttacker, attackTarget1) =
            if (mainHandAttack.result > 0)
              resolveDamageMainHand(combatant, attackTarget, mainHandAttack)
            else
              (combatant, attackTarget)

          val updatedEnemies = enemies.replace(attackTarget1)

          nextToFocus(updatedEnemies, focus) match {
            case None => (combatant, others.replace(updatedEnemies))
            case Some(nextTarget) =>
              val offHandWeapon = combatant.creature.offHand.get.asInstanceOf[Weapon]
              val offHandAttack = attack(updatedAttacker, offHandWeapon, nextTarget)

              val (attacker2, attackTarget2) =
                if (offHandAttack.result > 0)
                  resolveDamage(updatedAttacker, nextTarget, offHandWeapon, offHandAttack)
                else
                  (updatedAttacker, nextTarget)

              (attacker2, others.replace(updatedEnemies).replace(attackTarget2))
          }
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

      def useAbility[_: RS](others: List[Combatant], focus: Focus): (Combatant, List[Combatant]) = {
        logger.debug(s"${combatant.creature.name} used Action Surge")

        val enemies = monsters(others)
        val target  = nextToFocus(enemies, focus)

        target match {
          case None => (combatant, others)
          case Some(_) =>
            nextAbilityToUseInConjunction(combatant, enemies, order, AbilityAction.Any)
              .fold(useAttackActionTwice(combatant, enemies, focus)) { nextAbility =>
                val (updatedAttacker, updatedTargets) =
                  useAdditionalAbility(nextAbility, combatant, enemies, focus)

                val updatedEnemies = enemies.replace(updatedTargets)

                nextAbilityToUseInConjunction(updatedAttacker,
                                              updatedEnemies,
                                              order,
                                              AbilityAction.Any)
                  .fold {
                    nextToFocus(updatedEnemies, focus).fold((updatedAttacker, updatedEnemies)) {
                      nextTarget =>
                        val (updatedAttacker2, updatedTarget2) =
                          attackAndDamage(updatedAttacker, nextTarget)
                        (updatedAttacker2, others.replace(updatedEnemies).replace(updatedTarget2))
                    }
                  } { nextAbility2 =>
                    val (updatedAttacker2, updatedEnemies2) =
                      useAdditionalAbility(nextAbility2, updatedAttacker, updatedEnemies, focus)
                    (updatedAttacker2, others.replace(updatedEnemies2))
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
