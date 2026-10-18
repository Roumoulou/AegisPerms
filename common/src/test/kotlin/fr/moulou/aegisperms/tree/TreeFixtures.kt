// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.tree

import com.mojang.brigadier.Command
import com.mojang.brigadier.context.CommandContext
import fr.moulou.aegisperms.files.CommandRequirement
import java.util.function.Predicate

/*
Ce que partagent les tests du package sur des arbres synthétiques. La source d'un arbre est son niveau d'op, un entier de 0 à 4 : une exigence se teste
ainsi sans le jeu.
*/

/** L'exigence d'une commande de vanilla réservée aux ops de niveau 2. */
internal val GAMEMASTERS: Predicate<Any> = Predicate { (it as Int) >= 2 }

/** L'exigence d'une commande de vanilla réservée aux ops de niveau 3. */
internal val ADMINS: Predicate<Any> = Predicate { (it as Int) >= 3 }

/** Un prédicat écrit par un mod, que le mod ne sait pas lire. */
internal val MOD_OPTION: Predicate<Any> = Predicate { true }

/** Un loader simulé : chaque mod y a la classe qui définit ses commandes, et la classe d'[OrphanCommand] n'est dans aucun. */
internal class FakeMods(private val loaded: Set<String> = setOf("minecraft", "homes", "warps", "aegisperms")) : ModLocator {

    companion object {
        private val MOD_ID_BY_CLASS_NAME = mapOf(
            VanillaCommand::class.java.name to "minecraft",
            HomeCommand::class.java.name to "homes",
            WarpsMod::class.java.name to "warps",
            AegisCommand::class.java.name to "aegisperms",
        )
    }

    override fun modIdOf(className: String): String? = MOD_ID_BY_CLASS_NAME[className]

    override fun isLoaded(modId: String): Boolean = modId in loaded

    override fun nameOf(modId: String): String? = when (modId) {
        "minecraft" -> "Minecraft"
        "homes" -> "Homes"
        else -> null
    }

    override fun versionOf(modId: String): String? = when (modId) {
        "minecraft" -> "26.3"
        "homes" -> "1.2.0"
        else -> null
    }
}

/** Une lecture d'exigence simulée : les deux niveaux connus, le prédicat par défaut de Brigadier, et tout le reste en prédicat de mod. */
internal object FakeRequirements : RequirementReader {
    override fun read(requirement: Predicate<*>): CommandRequirement = when {
        Requirements.isDefault(requirement) -> CommandRequirement.NONE
        requirement === GAMEMASTERS -> CommandRequirement.GAMEMASTERS
        requirement === ADMINS -> CommandRequirement.ADMINS
        else -> CommandRequirement.MOD_PREDICATE
    }
}

/** Une commande de vanilla, qui rend [result] pour qu'un test sache laquelle a tourné. */
internal class VanillaCommand(private val result: Int = 1) : Command<Any> {
    override fun run(context: CommandContext<Any>): Int = result
}

internal class HomeCommand(private val result: Int = 1) : Command<Any> {
    override fun run(context: CommandContext<Any>): Int = result
}

internal class AegisCommand : Command<Any> {
    override fun run(context: CommandContext<Any>): Int = 1
}

internal class OrphanCommand : Command<Any> {
    override fun run(context: CommandContext<Any>): Int = 1
}

/** Un mod qui définit sa commande par une lambda : la classe de la commande est une classe cachée, dont cet objet est l'hôte. */
internal object WarpsMod {
    fun command(): Command<Any> = Command { 1 }
}
