# Identidade visual v2

O pacote gráfico enviado foi usado como referência de layout e paleta:
- Fundo azul-noite: #070918
- Painéis: #111631
- Ciano: #00EFFF
- Magenta: #FF2DD6
- Roxo: #8F5BFF
- Amarelo: #FFDE4D
- Verde: #4AFFA3
- Texto: #EBF4FF

As fontes anexadas foram integradas ao build:
- Stranger back in the Night (subset somente para o logotipo RETROBOY).
- Stranger back in the Night aplicado também aos títulos e chamadas de destaque para manter a identidade tipográfica consistente.
- NightWork permanece no pacote de fontes recebido, mas não foi incorporada ao APK nesta etapa.

Os PNGs do pacote gráfico continuam como referência para a integração dos avatares, medalhas, molduras, chips, botões e telas completas nas próximas iterações. Esta versão já aplica a tipografia e a paleta; não declara todos os PNGs como integrados.


## Recursos integrados ao perfil nesta versão

O build extrai `artwork/retroboy-profile-assets.zip` para `drawable-nodpi`:
- Avatares: alien, fox, ninja e robot.
- Moldura neon do jogador.
- Insígnia inicial bronze.

O perfil já permite editar o nome e escolher avatar, e apresenta ID do jogador, nível/XP inicial, biblioteca, partidas, conquistas e medalhas bloqueadas. Estatísticas reais e social dependem do runtime e do backend.
