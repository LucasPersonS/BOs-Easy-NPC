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
