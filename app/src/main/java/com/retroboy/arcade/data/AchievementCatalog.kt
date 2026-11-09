package com.retroboy.arcade.data

import com.retroboy.arcade.model.Achievement

object AchievementCatalog {
    val all = listOf(
        Achievement("first-coin", "Primeira ficha", "Abra seu primeiro jogo no Retroboy Arcade.", 1, 10, "Iniciante"),
        Achievement("arcade-veteran", "Frequência no fliperama", "Jogue 10 sessões de Arcade ou Neo Geo.", 10, 30, "Arcade"),
        Achievement("fighter", "Lutador de respeito", "Complete 5 partidas de luta.", 5, 25, "Arcade"),
        Achievement("marathon", "Virou a noite", "Acumule 5 horas de jogo.", 300, 40, "Veterano"),
        Achievement("new-friend", "Novo parceiro", "Adicione seu primeiro amigo.", 1, 15, "Social"),
        Achievement("duo-session", "Juntos no fliperama", "Jogue 3 sessões com um amigo.", 3, 30, "Social"),
        Achievement("first-trade", "Mestre das trocas", "Faça sua primeira troca em um jogo compatível.", 1, 35, "Pokémon"),
        Achievement("first-battle", "Desafio aceito", "Conclua sua primeira batalha Pokémon online.", 1, 35, "Pokémon"),
        Achievement("collector", "Colecionador", "Importe 10 jogos compatíveis.", 10, 40, "Coleção"),
        Achievement("completionist", "Lenda do Retroboy", "Desbloqueie 8 outras conquistas.", 8, 100, "Especial")
    )
}
