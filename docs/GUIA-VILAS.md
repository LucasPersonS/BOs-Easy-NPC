# Guia das Vilas — passo a passo (Easy NPC Paravexia)

Como criar uma vila com moradores pelo **painel do site** ou pelos **comandos no jogo**. Vale para o jar
`easy_npc-neoforge-1.21.1-7.18.0-hades-villages.jar` (servidor **e** cliente) + ponte `paravexiaadmin-0.2.19.jar` (servidor).

---

## 1. Em 1 minuto

- **Vila**: uma área redonda (centro + raio) com nome e uma chave (`aldeia_norte`).
- **Ponto**: um lugar marcado dentro da vila — Casa, Trabalho, Praça, Banco ou Outro.
- **Morador**: um Easy NPC que pertence à vila. Cada um tem **um perfil**:

| Perfil | Para quê | O que faz |
|---|---|---|
| **Figurante** | gente de fundo | fica parado, só olha para jogadores. O mais leve. |
| **Morador** | quem tem rotina | anda entre os pontos da rotina (casa → trabalho → praça…) |
| **Funcional** | mercador, quem dá missões, mestre | fica parado no ponto, com conversa e comércio |
| **Guarda** | defesa | mantém o combate configurado. Use pouco. |

- **Rotina**: só para Morador. Lista de "a partir de tal hora do dia, vá para tal ponto".
- **Dormir/acordar**: sem jogador por perto, a vila **dorme** sozinha (moradores param, quase sem custo). Chegou alguém, ela **acorda**.
- **Limites**: até **40 moradores por vila** e **600 no servidor todo**. O painel não deixa publicar acima disso.

Horas do dia do Minecraft: `0` amanhecer · `1000` manhã · `6000` meio-dia · `12000` pôr do sol · `13000` noite · `18000` meia-noite.

---

## 2. Criar pelo site (painel)

1. **Abra o painel.** Produção: dê dois cliques em `Abrir painel (producao).cmd` e deixe a janela aberta; o painel abre em `http://localhost:3000/admin/`. Cole a **Chave de administrador** e clique em **Conectar**.
2. Na barra lateral, entre em **NPCs e diálogos** e escolha a aba **Vilas**.
3. Clique em **Nova vila**. Na aba **Vila** preencha:
   - **Nome** (o que aparece) e **Chave** (minúsculas, sem espaço: `aldeia_norte`).
   - **Dimensão** (normalmente o Overworld).
   - **Centro**: digite X/Y/Z ou clique no nome de um jogador online para usar a posição dele (fique no meio da vila no jogo e clique no seu nome).
   - **Raio**: tamanho da vila (até 48 blocos).
   - **Despertar**: deixe em `raio + 32` (padrão) ou escolha **próprio** para definir a distância em que a vila acorda.
4. **Pontos.** Clique em **+ ponto** e depois clique na planta onde fica o lugar. Na aba **Pontos** escolha o **Tipo** (Casa, Trabalho, Praça, Banco, Outro) e confira a **Posição**. Pontos fora do círculo da vila ficam marcados como **Fora do raio** e bloqueiam a publicação.
5. **Moradores.** Na aba **Moradores**, clique em **Novo morador** e preencha:
   - **Nome**, **Tipo** (o modelo do Easy NPC, ex.: humanoide) e **Perfil** (Figurante, Morador, Funcional, Guarda).
   - **Casa**: o ponto onde ele nasce e para onde volta.
   - **Rotina** (só Morador): **Novo horário** → hora do dia + ponto. Ex.: `1000 → Trabalho`, `9000 → Praça`, `13000 → Casa`.
   - **Conversa**: escolha a biblioteca de diálogo (as mesmas da aba de diálogos).
   - **História**: texto livre sobre o personagem (guardado para o site/lore).
6. **Confira a rotina.** Arraste **Hora do dia** na planta: os moradores se movem para onde estariam naquela hora. Cores = perfil.
7. **Custo.** Abra a aba **Custo**: mostra quanto a vila pesa acordada em relação ao orçamento (4 ms por tick) e os limites. Se houver problema (acima do orçamento, acima de 40, ponto fora do raio, horário repetido…), ele aparece listado e o **Publicar** fica bloqueado.
8. Clique em **Publicar**. O estado muda: **Enviando → Servidor aplicando → Aplicada**. Depois a vila aparece como **Acordada** ou **Dormindo**.
   - Os moradores **nascem aos poucos** (2 por tick) e **só quando a área estiver carregada** — se ninguém estiver lá, eles aparecem quando o primeiro jogador chegar.
9. **Editar depois**: mude o que quiser e publique de novo. Publicar a mesma coisa duas vezes não duplica nada.
   - Remover um morador da lista **apaga esse NPC do mundo** quando publicar.
   - Não troque a chave de um morador que já existe: o painel trataria como outro morador.
   - Se aparecer **Dados mudaram**, alguém alterou a vila no servidor; clique em **Atualizar** e refaça a mudança.
10. **Remover a vila**: aba **Vila** → **Remover vila** → **Confirmar remoção**. Marque **Apagar moradores do mundo** se os NPCs também devem sumir; senão eles ficam no mundo, só deixam de ser controlados pela vila.

---

## 3. Criar no jogo (comandos)

Precisa ser operador (nível 2). Use **Tab** para completar nomes de vilas, pontos e NPCs.

1. Fique no **meio** da vila:
   `/easy_npc village create aldeia_norte 30`
2. Vá até cada lugar e marque um ponto (o ponto fica onde você está):
   `/easy_npc village poi add aldeia_norte casa_ana home`
   `/easy_npc village poi add aldeia_norte forja work`
   `/easy_npc village poi add aldeia_norte praca square`
   Tipos: `home`, `work`, `square`, `bench`, `custom`.
3. Crie o NPC normalmente (ovo/preset do Easy NPC, nome, skin, diálogo) e coloque-o na vila com um perfil e uma casa:
   `/easy_npc village npc add aldeia_norte <npc> resident casa_ana`
   Perfis: `extra` (Figurante), `resident` (Morador), `functional` (Funcional), `guard` (Guarda).
4. Rotina (só `resident`):
   `/easy_npc village schedule set aldeia_norte <npc> 1000 forja`
   `/easy_npc village schedule set aldeia_norte <npc> 9000 praca`
   `/easy_npc village schedule set aldeia_norte <npc> 13000 casa_ana`
5. Conferir: `/easy_npc village status aldeia_norte` — mostra se está acordada, quantos moradores por perfil, quantos andando e o nível do orçamento.

Outros comandos:
- `/easy_npc village npc remove aldeia_norte <npc>` — tira da vila (o NPC continua no mundo).
- `/easy_npc village remove aldeia_norte` — apaga a vila (NPCs ficam). Com `delete_npcs` no fim, apaga os NPCs criados por ela.
- `/easy_npc village wake aldeia_norte` / `sleep` / `auto` — força acordar/dormir para testes; `auto` volta ao normal.
- `/easy_npc village import aldeia_norte` — carrega `config/easy_npc/villages/aldeia_norte.json` (formato em `docs/VILLAGES.md`). Aperte Tab para ver os arquivos.
- `/easy_npc village import aldeia_norte here` — carrega o preset **centrado onde você está** (não precisa do `mover-vila.js`).
- `/easy_npc village poi list aldeia_norte` — lista os pontos: verde = ok (mostra onde os NPCs ficam de fato), vermelho = sem lugar livre perto, cinza = área não carregada.
- `/easy_npc village poi set aldeia_norte forja` — move o ponto para onde você está e reposiciona quem usa esse ponto.
- `/easy_npc village respawn aldeia_norte all` — devolve os NPCs carregados para os seus lugares.

Os NPCs nunca ficam no bloco exato do ponto: cada um ganha um lugar livre perto dele (chão firme, sem bloco na cabeça, sem água), a 2 blocos dos outros. Ponto dentro de parede, embaixo do chão ou no ar é corrigido sozinho; NPC preso dentro de bloco ou travado no caminho é movido para o lugar certo.

---

## 4. Ajustes do servidor (`config/easy_npc/village.cfg`)

Os padrões já são seguros. Os que mais importam:

| Ajuste | Padrão | Para quê |
|---|---|---|
| `enabled` | true | liga/desliga o sistema de vilas |
| `npcBudgetMicros` | 4000 | orçamento dos Easy NPCs por tick (4 ms). Acima disso o servidor se protege sozinho |
| `maxNpcsPerVillage` / `maxVillageNpcs` | 40 / 600 | limites por vila e no servidor todo |
| `maxWalkersPerVillage` | 4 | quantos moradores andam ao mesmo tempo por vila |
| `spawnPerTick` | 2 | quantos NPCs nascem por tick ao publicar |
| `sleepDelayTicks` | 200 | quanto tempo sem jogador até a vila dormir (200 = 10 s) |
| `residentIdleStroll` | false | se `true`, moradores passeiam em volta da casa quando parados (mais bonito, mais pacotes de rede) |

**Proteção automática**: se os Easy NPCs passarem do orçamento, o servidor reduz em etapas — 1) só 1 morador anda por vila, 2) vilas sem jogador bem perto dormem, 3) figurantes das vilas acordadas dormem também. Volta ao normal sozinho. Cada mudança aparece **uma vez** no log.

---

## 5. O que mudou nas conversas

- Chegar perto de um NPC mostra a **prévia** da fala; isso **não conta** como conversa (não gasta limites nem dispara missões). Conta quando o jogador abre a conversa ou clica.
- Clicar no NPC volta a executar as ações dele (comércio, comandos, gatilhos de missão). Mercador com botão de comércio na conversa cumprimenta primeiro.
- Conversa aberta de longe (mais de 8 blocos, por comando ou gatilho de distância) usa a tela clássica em vez de fechar sozinha.
- NPC escondido para um jogador (condição de visibilidade) não conversa com ele.

## 6. Também novo (Easy NPC 7.13–7.18)

Visibilidade por jogador (aba "Display", ex.: por placar), comando `/easy_npc attribute`, projéteis próprios e objetivo "Projectile Attack", barra de chefe com "Hide after", e salvamento dos NPCs em segundo plano (`config/easy_npc/storage.cfg`) — foi isso que tirou a maior parte do travamento do autosave.

## 7. Problemas comuns

| Sintoma | Causa provável | O que fazer |
|---|---|---|
| Publiquei e não apareceu ninguém | área não carregada | vá até a vila; eles nascem quando o primeiro jogador chega |
| Vila sempre "Dormindo" | ninguém dentro do raio de despertar | aproxime-se ou aumente **Despertar** |
| Morador não sai de casa | não é **Morador**, sem rotina, ou limite de andantes cheio | confira perfil/rotina; espere a vez (até 4 andam juntos) |
| Morador para no meio do caminho | caminho bloqueado ou longe demais (> ~48 blocos) | aproxime os pontos ou abra o caminho |
| "Fora do raio" | ponto fora do círculo ou com altura muito diferente do centro (±32) | mova o ponto ou aumente o raio |
| Publicar bloqueado | problema listado na aba **Custo** | resolva o item listado |
| Jogador desconecta ao falar com NPC | cliente com jar antigo | atualize o jar do Easy NPC no modpack (cliente e servidor iguais) |
