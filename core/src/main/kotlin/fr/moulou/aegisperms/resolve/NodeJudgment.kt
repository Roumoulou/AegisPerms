// SPDX-FileCopyrightText: 2026 roumoulou
// SPDX-License-Identifier: LGPL-3.0-only

package fr.moulou.aegisperms.resolve

/**
 * Le jugement d'un nœud de commande pour un sujet (cahier des charges, 2.7) : ce que les règles en disent, toutes les clés de son chemin lues. L'exigence
 * vanilla, que le jugement ne connaît pas, décide du reste (2.8) : un nœud [ALLOWED] ou [ALLOWED_UNDER_REFUSAL] est vu quand les conditions de son exigence
 * qui ne portent pas sur les droits passent, un nœud [UNSET] quand son exigence passe.
 *
 * Aucun nœud n'est sans règle sous un refus : la règle qui refuse une clé ancêtre couvre aussi ses descendantes.
 */
enum class NodeJudgment {

    /** Un refus masqué sur l'une des clés du chemin : le nœud n'est pas vu. */
    HIDDEN,

    /** Un refus visible sur la clé du nœud : il est vu, et refusé. */
    REFUSED,

    /** Une règle `allow` répond de la clé du nœud, sans refus sur son chemin. */
    ALLOWED,

    /** Une règle `allow` répond de la clé du nœud, sous le refus visible d'une clé ancêtre : elle est sans effet. */
    ALLOWED_UNDER_REFUSAL,

    /** Aucune règle ne répond : vanilla décide. */
    UNSET;

    /** Vrai sans refus sur le chemin : le nœud est permis dès qu'il est vu. */
    val hasNoRefusal: Boolean get() = this == ALLOWED || this == UNSET

    /** Vrai pour un refus visible sur le chemin, que la porte d'exécution dit par un message. */
    val hasVisibleRefusal: Boolean get() = this == REFUSED || this == ALLOWED_UNDER_REFUSAL

    /** Vrai quand une règle `allow` répond de la clé du nœud : son exigence vanilla ne garde que les conditions qui ne portent pas sur les droits. */
    val isAllowed: Boolean get() = this == ALLOWED || this == ALLOWED_UNDER_REFUSAL
}
