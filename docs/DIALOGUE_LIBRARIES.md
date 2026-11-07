# Bibliotecas de diálogo

O fork lê árvores reutilizáveis em `config/easy_npc/dialogues/**/*.dialog.snbt`. Um arquivo pode
ser aplicado a um NPC ou a centenas deles de uma vez. A ligação com a biblioteca fica gravada no
NPC, portanto a árvore pode ser atualizada depois sem recriar os diálogos pela interface.

## Fluxo recomendado

1. Copie `examples/dialogue-libraries/paravexia/crossroads_intro.dialog.snbt` para
   `config/easy_npc/dialogues/paravexia/crossroads_intro.dialog.snbt` na instância ou servidor.
2. Execute `/easy_npc dialog library reload`.
3. Aplique a biblioteca com
   `/easy_npc dialog library apply <seletor_de_npcs> paravexia:crossroads/intro`.
   IDs entre aspas também são aceitos.
4. Edite o arquivo e execute
   `/easy_npc dialog library reload <seletor_de_npcs>` para recarregar e sincronizar em uma etapa.
   Para vincular uma biblioteca nova durante a recarga, use
   `/easy_npc dialog library reload <seletor_de_npcs> paravexia:crossroads/intro`.

Também existem os comandos `list`, `sync` e `unlink`. `sync` mantém cada NPC na biblioteca à qual
ele já está ligado. `unlink` conserva uma cópia independente da conversa no NPC e interrompe futuras
sincronizações.

## Organização em escala

Use um namespace por projeto e caminhos por região ou função, por exemplo:

```text
config/easy_npc/dialogues/
  paravexia/
    crossroads/merchant.dialog.snbt
    crossroads/guard.dialog.snbt
    act_01/oracle.dialog.snbt
```

Quando `Id` não é declarado no arquivo, o caminho vira o identificador. O último exemplo acima seria
`paravexia:act_01/oracle`. Declare `Id` quando quiser manter um nome estável após mover o arquivo.

Os campos `Text` e `Name` aceitam chaves de tradução. Isso permite que a mesma árvore atenda todos
os idiomas e que textos sejam revisados sem alterar ações, condições ou estrutura. Os metadados
existentes (`Conditions`, `Priority`, `Options` e `Actions`) continuam usando o formato SNBT do Easy
NPC. Cada arquivo aceita até 1 MiB e até oito níveis de pastas.

## Retratos cinematográficos

Retratos opcionais continuam configurados no resource pack em
`assets/easy_npc/hades/portraits.json`, por UUID, nome visível ou identificador da biblioteca. A
ordem de preferência é UUID, nome e biblioteca, permitindo um retrato específico ou compartilhado:

```json
{
  "paravexia:crossroads/intro": {
    "texture": "paravexia:textures/gui/portraits/hecate.png",
    "width": 512,
    "height": 768,
    "subtitle": "Bruxa da Encruzilhada"
  }
}
```

Sem retrato, a tela renderiza o modelo 3D do NPC automaticamente.

## Diálogo de comerciante

O exemplo `examples/dialogue-libraries/paravexia/crossroads_merchant.dialog.snbt` reproduz o fluxo
de um comerciante cinematográfico: saudação, três respostas numeradas, conversa secundária e uma
ação `OPEN_TRADING_SCREEN` que entrega o controle à tela de trocas segura do servidor.

Copie o arquivo para `config/easy_npc/dialogues/paravexia/crossroads_merchant.dialog.snbt`, recarregue
e aplique `paravexia:crossroads/merchant` ao NPC. O NPC também precisa ter o tipo de comércio e as
ofertas configurados na seção **Trading**; a biblioteca controla a conversa e quando a loja abre,
mas não inventa ou substitui as ofertas do comerciante.

## Interface flutuante no mundo (NeoForge)

No cliente NeoForge, aproximar-se até seis blocos de um NPC que tenha um diálogo disponível abre a
conversa automaticamente, sem pausar o jogo nem soltar o mouse. O balão acompanha o ponto de cabeça
do modelo e as respostas ficam ao lado dele. Mire em uma resposta e use ataque ou interação para
selecioná-la; quando houver mais de três respostas, use a roda do mouse.

Condições, limites de execução, transições e recompensas continuam validados pelo servidor. Ao
afastar-se, perder a linha de visão ou abrir outra tela, a sessão flutuante é encerrada. Para evitar
que ela reabra imediatamente, o mesmo NPC só volta a iniciar o diálogo depois que o jogador sair da
área e se aproximar novamente.

## Bibliotecas embutidas no jar

O jar também traz bibliotecas prontas em `easy_npc_dialogues/` (listadas em
`easy_npc_dialogues/index.txt`), como as do vilarejo inicial (`paravexia:ancoradouro/<morador>`).
Elas carregam sozinhas, sem copiar arquivos. Para editar uma, crie em
`config/easy_npc/dialogues/` um arquivo com o mesmo `Id`: o arquivo da pasta substitui o embutido
depois de `/easy_npc dialog library reload`.
