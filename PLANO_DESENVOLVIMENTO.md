# Plano de desenvolvimento — Quadrado Runner

## Visão do jogo
Um jogo 2D de plataforma com fases rejogáveis, argolas douradas coletáveis e habilidades permanentes compradas entre fases. Algumas rotas e fases exigem habilidades específicas (por exemplo, dash e planar).

## Regras de trabalho para o Codex
1. Leia `AGENTS.md` e este plano antes de alterar código.
2. Execute **somente a primeira etapa `PENDENTE`** por sessão/solicitação, salvo autorização explícita.
3. Antes de implementar, examine a arquitetura existente; preserve funcionalidades e saves.
4. Mantenha lógica de jogo independente da renderização; não crie frameworks nem dependências sem necessidade.
5. Crie/atualize testes apropriados e execute `mvn test` (ou o comando de testes real do projeto).
6. Marque `OK` **apenas depois** de implementar, testar e confirmar os critérios de aceite. Se houver falha, mantenha `PENDENTE` e registre o bloqueio.
7. Ao concluir cada etapa, atualize a tabela de progresso, o registro de execução e informe arquivos alterados, testes executados e resultado.
8. Não modifique etapas já marcadas `OK` sem explicar a necessidade e pedir aprovação para mudança de escopo.
9. Não use a passagem de fase ou uma derrota para restaurar argolas já coletadas; persistência de coletáveis e habilidades deve sobreviver ao fechamento do jogo.
10. Caso uma etapa demande decisões ainda não definidas, escolha uma solução simples, documente-a no registro e evite implementar etapas futuras.

## Decisões de design (iniciais; revisáveis)
- Uma argola coletada pela primeira vez aumenta o saldo permanente em 1, identificado pelo par **ID da fase + ID da argola**.
- A argola desaparece definitivamente daquele save, inclusive ao reiniciar o jogo ou rejogar a fase.
- O saldo é ganho no momento da coleta e permanece disponível para compras **entre fases**; não é concedido novamente ao concluir a fase.
- Gastar argolas reduz o saldo, não altera a lista de argolas já coletadas. Evitar contagem dupla.
- Habilidades compradas são permanentes no save. Double-jump existente deve ser preservado; qualquer mudança no seu desbloqueio será uma decisão explícita posterior.
- A morte e o reinício da fase não removem progresso salvo; a definição exata de checkpoint pode ser refinada posteriormente.
- Começar com um único slot de save local e arquivo versionado por esquema. Não colocar arquivos pessoais de save no Git.

## Progresso
| Etapa | Status | Entrega / critério de aceite |
|---|---|---|
| 01 | OK | Inventário técnico: localizar classes de jogo, fases, colisão, coleta, power-ups e testes. Registrar arquitetura atual abaixo, **sem mudar comportamento**. |
| 02 | OK | Modelo de progresso: `GameProgress` (ou equivalente) com saldo, IDs de argolas coletadas, habilidades desbloqueadas e identificadores de fases. Testes sem interface gráfica. |
| 03 | OK | Persistência local: salvar/carregar progresso com tratamento de arquivo ausente/corrompido, gravação segura e teste de reinício. Versionar formato. |
| 04 | PENDENTE | Argolas nas fases: entidades com IDs estáveis e únicos por fase, renderização e colisão; argolas já coletadas não aparecem. Testar coleta única. |
| 05 | PENDENTE | Economia: atualizar saldo **uma vez por argola**, mantê-lo entre fases e reinícios; testar replay e múltiplas visitas. |
| 06 | PENDENTE | Final de fase: tela/resumo com argolas novas coletadas nessa tentativa e saldo total; permitir rejogar ou avançar, sem duplicar recompensa. |
| 07 | PENDENTE | Loja entre fases: listar poderes/preços, validar saldo, debitar corretamente e impedir compra duplicada. Testes de compra válida/inválida. |
| 08 | PENDENTE | Dash: movimento com duração/recarga e colisão apropriadas, disponível apenas após compra; testar estados e limites. |
| 09 | PENDENTE | Planar: queda desacelerada enquanto comando estiver ativo, disponível apenas após compra; testar física e limites. |
| 10 | PENDENTE | Fases e bloqueios: criar uma rota que exige dash e outra que exige planar; nunca criar progressão impossível de desbloquear. Testar acesso e condição de conclusão. |
| 11 | PENDENTE | Seleção/replay de fases: escolher fases concluídas para rejogar; preservar saldo, compras e argolas ausentes. Teste de persistência entre sessões. |
| 12 | PENDENTE | Refinamento: HUD de saldo/habilidades, feedback audiovisual, instruções e documentação. Rodar testes completos e fazer validação manual do fluxo. |

## Registro de execução
_Ao terminar ou encontrar bloqueio, adicione uma linha:_

| Data | Etapa | Resultado | Evidência (testes/arquivos) | Observações |
|---|---|---|---|---|
| 2026-10-31 | 01 | OK — inventário técnico concluído; arquitetura e testes registrados abaixo. | Inspeção de `src/main/java/br/com/quadradorunner`, `src/test/java/br/com/quadradorunner`, `pom.xml`, `README.md` e `.gitignore`; execução de `mvn test` pelo Maven local: 21 testes, 0 falhas, 0 erros, 0 ignorados, BUILD SUCCESS. Relatórios em `target/surefire-reports`. | Apenas documentação; nenhum código, teste ou comportamento alterado. Argolas, progresso permanente e saves ainda não existem. Avisos de áudio durante os testes são falhas de dispositivo simuladas intencionalmente. Etapas 02–12 mantidas PENDENTE. `AGENTS.md` incluído no versionamento conforme instrução do plano, sem alteração de conteúdo. |
| 2026-10-31 | 02 | OK — modelo de progresso imutável implementado e validado sem interface gráfica. | Novos `src/main/java/br/com/quadradorunner/GameProgress.java` e `src/test/java/br/com/quadradorunner/GameProgressTest.java`; execução do mesmo comando Maven registrado na etapa 01: 30 testes aprovados (9 novos), 0 falhas, 0 erros, 0 ignorados, BUILD SUCCESS. Relatórios em `target/surefire-reports`. | Saldo não negativo, identidade de argola pelo par de IDs, habilidades e IDs de fases concluídas; conjuntos defensivamente copiados e imutáveis, IDs não nulos/não brancos. Modelo isolado, sem alteração da partida ou do salto duplo. Persistência, economia e integração de argolas não implementadas; etapas 03–12 mantidas PENDENTE. |
| 2026-10-31 | 03 | OK — persistência local versionada implementada e validada. | Novos `src/main/java/br/com/quadradorunner/GameProgressStore.java` e `src/test/java/br/com/quadradorunner/GameProgressStoreTest.java`; `.gitignore` atualizado; execução do mesmo comando Maven registrado na etapa 01: 39 testes aprovados (9 novos), 0 falhas, 0 erros, 0 ignorados, BUILD SUCCESS. Relatórios em `target/surefire-reports`. | Slot local, formato Properties UTF-8 versão 1, escrita temporária sincronizada e substituição atômica. Save ausente retorna progresso inicial; corrupção/versão incompatível geram erro e bloqueiam sobrescrita. Reinício simulado por nova instância do repositório lendo o arquivo. Partida e etapas concluídas preservadas; etapas 04–12 mantidas PENDENTE. |

## Persistência local (etapa 03)

- `GameProgressStore` salva/carrega snapshots de `GameProgress` sem dependência de interface gráfica. O construtor padrão aponta para `${user.home}/.quadrado-runner/progress.properties`, um único slot fora do projeto. O construtor com `Path` permite testes isolados em `@TempDir`. Nenhum save pessoal foi criado durante a implementação. `/.quadrado-runner/` foi adicionado ao `.gitignore` para proteger também um diretório local de mesmo nome.
- O formato usa `java.util.Properties` com leitor/escritor UTF-8 e `schema.version=1`. Campos obrigatórios: `balance`, `rings.count`, `abilities.count`, `levels.count`; entradas `ring.N.level` e `ring.N.id`, `ability.N` e `level.N`. Habilidades usam seus nomes de enum (`DASH`, `GLIDE`). A ordem dos conjuntos não é significativa; índices são apenas a representação no arquivo, não a identidade das argolas.
- `load()` retorna progresso vazio apenas quando o arquivo não existe, sem criar diretórios ou arquivos. Campos ausentes/inesperados, números inválidos, contagens inconsistentes, IDs inválidos, entradas duplicadas nos conjuntos, habilidades desconhecidas e versões incompatíveis geram `SaveFormatException`, derivada de `IOException`. Outros erros de I/O são propagados, sem transformar falhas de acesso em progresso vazio. O arquivo original permanece intocado.
- `save()` valida o arquivo existente antes de substituí-lo: dados corrompidos ou de versão incompatível não são sobrescritos silenciosamente. Recuperação desses arquivos exige uma decisão explícita futura; não há reset automático nem migração nesta versão.
- Escrita segura: cria o diretório de destino quando necessário; grava um temporário no mesmo diretório; fecha o escritor; executa `FileChannel.force(true)`; substitui o slot com `ATOMIC_MOVE` e `REPLACE_EXISTING`; remove o temporário no encerramento normal ou em exceção. Se o sistema de arquivos não suportar substituição atômica, a operação falha sem fallback não atômico. A garantia de durabilidade final depende do sistema de arquivos e do dispositivo; não há coordenação entre múltiplas instâncias gravando simultaneamente.
- Um temporário deixado por encerramento abrupto não é carregado como save e não substitui o último snapshot confirmado. Não há exclusão automática de temporários antigos, backups ou salvamento de preferências de áudio nesta etapa.
- Os nove testes cobrem arquivo ausente, round-trip completo após criar nova instância do repositório (reinício lógico), versão gravada, atualização de save existente e limpeza dos temporários, IDs com Unicode/caracteres especiais, corrupção sem sobrescrita, versão futura preservada, campos/contagens/duplicatas inválidos, erros de I/O e um temporário incompleto coexistindo com o save válido. Todos usam arquivos temporários e nenhuma janela; a suíte completa passou com 39 testes. Não foi realizado reinício manual da aplicação nem simulação de queda real de energia.
- Esta entrega fornece a persistência do modelo, ainda sem ligação com `Main`, `GameModel` ou `GamePanel`: o jogo atual não possui argolas permanentes a salvar. A integração com coleta/economia fica para as etapas correspondentes. As operações são bloqueantes e devem ser chamadas fora da EDT quando essa integração for implementada.

## Modelo de progresso (etapa 02)

- `GameProgress` é um `record` com acesso de pacote, seguindo a visibilidade dos modelos existentes. Representa um snapshot em memória, sem dependência de Swing, AWT, áudio ou armazenamento. O construtor sem argumentos cria progresso com saldo zero e todos os conjuntos vazios; o construtor completo permite representar um snapshot existente.
- Campos: `balance` (`int` não negativo), `collectedRings` (`Set<RingId>`), `unlockedAbilities` (`Set<Ability>`) e `completedLevelIds` (`Set<String>`). O saldo é armazenado separadamente do histórico de coleta, pois futuramente compras reduzirão o saldo sem restaurar argolas.
- `RingId` é um record aninhado com `levelId` e `ringId`. A igualdade considera os dois campos, evitando colisões por concatenação e distinguindo argolas com o mesmo ID local em fases diferentes. IDs são sensíveis a maiúsculas/minúsculas, preservados literalmente e rejeitam `null`, vazio ou apenas espaços em branco. Nenhum ID foi atribuído ao mapa atual nesta etapa.
- `Ability` é um enum aninhado com `DASH` e `GLIDE`, apenas identificadores de habilidades permanentes; não implementa movimento, desbloqueio ou compra. O salto duplo temporário dos cristais permanece independente e sem alterações.
- Os identificadores de fases desta etapa representam fases concluídas e o escopo das argolas. Catálogo, fase atual, seleção e regras de acesso ficam para as etapas correspondentes; não é inferido desbloqueio por ordem ou nome do ID.
- `Set.copyOf()` rejeita coleções/elementos nulos, isola o modelo das coleções recebidas e impede modificações pelos accessors. A ordem dos elementos não possui significado. Não há setters nem operações de coleta, crédito, débito ou compra nesta etapa; essas regras e sua integração pertencem às etapas futuras.
- Os nove testes de `GameProgressTest` verificam estado inicial, representação completa, saldo separado do histórico, identidade composta, cópias defensivas, coleções imutáveis, valores inválidos e igualdade de snapshots. Nenhuma janela ou arquivo de save é criado. A suíte completa passou com 30 testes; os avisos de áudio são esperados nos testes que simulam falhas de dispositivo.

## Arquitetura atual (inventário da etapa 01)

Base inspecionada: commit `3203b28`. Java 17, Maven, Swing, Java2D e Java Sound; sem dependências externas em execução. Todas as classes de produção estão em `src/main/java/br/com/quadradorunner/`; testes no pacote correspondente em `src/test/java/`. O `pom.xml` configura compilação com `release 17`, JAR executável, JUnit Jupiter 5.11.4 e Surefire 3.5.2.

- **Classe principal:** `Main.main()` cria a janela e o `GamePanel` por `SwingUtilities.invokeLater()`. Encaminha perda/ganho de foco e fechamento para o painel, incluindo encerramento do áudio.
- **Estado da partida:** `GameModel` mantém posição, velocidade vertical, contato com o chão, saltos usados, tempo do poder e cristais coletados. `State` contém `READY`, `RUNNING`, `PAUSED`, `LOST` e `WON`. As regras não dependem de Swing, mas usam geometria AWT/Java2D. Na aplicação, o modelo é acessado pela EDT; campos possuem acesso de pacote e são lidos diretamente pelo painel.
- **Atualização/renderização:** `GamePanel.tick()` mede tempo com `System.nanoTime()` e encaminha a simulação via `FixedStepLoop.advance()` para `GamePanel.updateGame()` e `GameModel.update(dt)`. O timer Swing tem intervalo nominal de 16 ms e funciona durante a corrida. O acumulador executa passos de 1/120 s, com limite de 30 passos por callback; tempo excedente a 250 ms é descartado. Pausa/retomada reinicializa o acumulador. `paintComponent()` calcula a câmera e chama `drawBackground()`, `drawWorld()`, `drawHud()` e `overlay()`, sem avançar a física. A viewport é fixa em 960 × 540; o mundo recebe uma transformação horizontal e o HUD fica em coordenadas de tela. Obstáculos fora da câmera não são desenhados. `addNotify()`/`removeNotify()` gerenciam timer e áudio.
- **Movimentação/colisão:** `GameModel.update()` aplica corrida automática de 260 px/s, gravidade de 1.700 px/s², integração da velocidade e posição verticais e correção do pouso. `jump()` aplica velocidade vertical de -710 px/s quando permitido. O jogador usa uma hitbox retangular de 34 × 34; o chão está em y = 428. Obstáculos são `Shape` (retângulos, triângulos, círculos e losangos), criados por `Level.obstacle()`, `triangle()`, `circle()` e `diamond()`. A colisão usa `Shape.intersects()` a cada passo, sem varredura contínua. Primeiro são resolvidos movimento/pouso e colisões; depois, se ainda estiver correndo, coleta e vitória. Derrota tem precedência sobre coleta e chegada no mesmo passo. Vitória ocorre quando a posição x do jogador alcança 8.400.
- **Fases/mapas:** `Level.standard()` cria uma única fase fixa com 17 obstáculos e três cristais. `Level` guarda listas de geometrias copiadas com `List.copyOf()`; os objetos geométricos continuam mutáveis. O construtor também permite mapas menores nos testes. Não existem IDs de fase/coletável, seleção, transição entre fases, checkpoints ou carregamento de mapas externos.
- **Coleta e double-jump/power-ups:** os cristais estão em `Level.powerUps`, como retângulos de 28 × 28 nos x = 1.200, 3.900 e 6.750; são desenhados como losangos azuis. Em `GameModel.update()`, a interseção com o jogador marca `collected[i]` e define `powerRemaining = 10` segundos de simulação. O índice da lista identifica apenas o cristal daquela tentativa. Um novo cristal renova a duração; não acumula tempo. `jump()` permite até dois saltos antes do próximo pouso enquanto o poder está ativo. O pouso zera `jumpsUsed`, a pausa congela o tempo e `restart()` restaura cristais e remove o efeito. A derrota não chama `restart()` automaticamente. Esses cristais temporários não são as argolas permanentes previstas no plano; seu comportamento atual foi preservado.
- **Entrada/eventos:** `GamePanel.bindKey()` usa key bindings `WHEN_IN_FOCUSED_WINDOW`. `GameInput.press()`/`release()` mantêm teclas em `EnumSet`, rejeitam repetição e compartilham o bloqueio da ação de salto entre Espaço/seta para cima. P/Esc pausam, R reinicia e M alterna mute. `pauseForFocusLoss()` limpa teclas, pausa a corrida e interrompe áudio; recuperar foco não retoma automaticamente a partida pausada.
- **Áudio:** `GamePanel.jump()` e `updateGame()` encaminham efeitos de salto aceito, nova coleta, derrota e vitória. `GameAudio` serializa comandos em um executor dedicado e continua sem áudio se o dispositivo falhar. `AudioSamples` sintetiza efeitos e uma música original de 16 segundos em PCM; `SynthAudioOutput` reproduz com `Clip` e libera recursos em `close()`. A música toca também nas telas inicial e finais, sendo interrompida por pausa, perda de foco ou mute. A saída é substituível nos testes por `GameAudio.Output`.
- **Persistência existente:** nenhuma. Não há leitura/gravação de saves, saldo, argolas, habilidades permanentes, compras nem fases desbloqueadas. Estado dos cristais, poder e preferência de mute ficam apenas em memória; o mute permanece ao reiniciar a fase, mas não após fechar o jogo. Não há formato ou caminho de save a preservar. `.gitignore` ignora `target/`, diretórios de IDE e arquivos `.iml`; ainda não há regra específica para saves, pois a persistência não foi implementada.
- **Testes atuais:** 21 métodos `@Test` em sete classes JUnit 5, mais o auxiliar `TestAudioOutput`. Os testes de regras não abrem janelas; os testes do painel executam na EDT e usam saída de áudio substituta. Não existe ferramenta de medição de cobertura configurada; a execução da suíte não equivale à validação manual de jogabilidade ou áudio real.

| Classe de teste | Quantidade | Comportamentos verificados |
|---|---:|---|
| `GameModelTest` | 8 | Início, corrida, salto/pouso, bloqueio de salto sem poder, pausa, coleta única por tentativa, renovação, reinício, salto duplo/expiração, quatro formas de colisão, precedência de derrota, vitória e conclusão da fase sem cristais. |
| `FixedStepLoopTest` | 2 | Mesma trajetória com intervalos diferentes de callbacks, recuperação de atraso, limite de passos, descarte do resíduo ao reiniciar e rejeição de tempo inválido. |
| `GameInputTest` | 2 | Teclas alternativas de salto, rejeição de repetição, soltura e limpeza da entrada. |
| `GamePanelTest` | 2 | Bindings, foco, ciclo de vida sem janela e renderização dos cinco estados, incluindo HUD fora da transformação da câmera. Gera PNGs em `target/render-review/`. |
| `GamePanelAudioTest` | 2 | Efeitos apenas em eventos válidos, sem duplicação, mute sem repetição, foco e encerramento da saída. |
| `GameAudioTest` | 3 | Execução fora da thread chamadora, ordenação de mute/pausa/fechamento, dispositivo indisponível e liberação após falha de reprodução. |
| `AudioSamplesTest` | 2 | Duração da música, amplitude das amostras PCM, efeitos curtos e extremidades suaves. Não avalia qualidade auditiva em dispositivo real. |

**Validação da etapa 01:** executado em 2026-10-31 com `& 'C:/Users/DEV02/.m2/wrapper/dists/apache-maven-3.9.11/d6d3cbd4012d4c1d840e93277aca316c/bin/mvn.cmd' -B -o '-Dmaven.repo.local=D:/felipe/git/Felipe/test7/target/maven-repository' test`. O executável foi usado diretamente porque `mvn` não está no PATH; modo offline com dependências já disponíveis no repositório local do workspace. Resultado: **21 testes aprovados, sem falhas, erros ou testes ignorados**. Nenhum teste foi adicionado ou modificado porque esta etapa é exclusivamente documental. Não foi realizada partida manual nem avaliação auditiva nesta etapa.

**Limites para a continuação:** o array `collected` atual pertence aos cristais temporários; não deve ser reutilizado como save de argolas. O modelo de progresso permanente, seus IDs e sua persistência serão tratados pelas etapas correspondentes, sem implementação antecipada neste inventário.

## Como retomar em uma nova sessão
> Leia `AGENTS.md` e `PLANO_DESENVOLVIMENTO.md`. Identifique a primeira etapa `PENDENTE`, verifique o estado real do repositório e implemente **somente essa etapa**, seguindo seus critérios de aceite. Rode os testes, atualize o status para `OK` somente se estiver tudo validado e registre o resultado. Não avance automaticamente para a etapa seguinte.

## IMPORTANTE
> Você tem autonomia para usar sua criatividade para criar elementos divertidos. E não esqueça de dar commits periódicos. Inclusive os arquivos: AGENTS.md e PLANO_DESENVOLVIMENTO.md precisam ser commitados.
