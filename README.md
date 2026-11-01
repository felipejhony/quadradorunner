# Quadrado Runner

Este jogo foi criado com inteligência artificial (IA) para fins educacionais, como exemplo prático de desenvolvimento de jogos 2D em Java com Swing e Java2D.

Jogo 2D de corrida lateral feito com **Java 17, Maven, Swing e Java2D**, sem dependências externas em tempo de execução. Todos os elementos são desenhados por código.

O quadrado corre automaticamente da esquerda para a direita em quatro fases com obstáculos gerados antes da partida. Alcance a bandeira para ver o resumo e preparar o próximo desafio. As colisões acompanham o formato dos obstáculos e retiram uma vida, com breve proteção após o impacto. Tangência exata não é considerada colisão. O jogador usa uma caixa retangular, inclusive nos cantos arredondados do desenho.

## Novos desafios e loja

Blocos grandes podem aparecer em qualquer fase. Ao se aproximar, eles se dividem: continue correndo **sem pular**. A metade inferior verde é inofensiva; a superior vermelha causa dano. O vão diminui durante a passagem, mas continua seguro no chão, sem poderes.

A partir da terceira fase aparecem pilhas altas com passagem por baixo ou pelo meio. Também caem blocos do céu, com marcação visual do local do impacto. Os obstáculos não exibem dicas textuais de como atravessá-los; o jogador descobre as estratégias durante a partida. Buracos continuam independentes desses obstáculos.

A loja usa botões e rolagem no estilo da seleção de fases. Clique para comprar ou use setas e Enter/Espaço. Os atalhos 1–7 correspondem aos itens numerados. Itens adquiridos, sem saldo ou sem o poder base ficam desabilitados. B/Esc volta ao resumo.

## Requisitos

- JDK 17 ou superior (`java` e `javac` no PATH).
- Maven 3.6.3 ou superior para o build Maven.
- Ambiente gráfico disponível.

## Compilar e executar com Maven

```sh
mvn clean package
java -jar target/quadrado-runner-1.0.0.jar
```

O compilador Maven usa `release 17`, inclusive quando o build roda com um JDK mais recente. Os plugins do Maven precisam ser baixados na primeira compilação; o jogo não utiliza bibliotecas gráficas externas.

## Executar sem Maven (PowerShell)

```powershell
New-Item -ItemType Directory -Force target/classes
javac --release 17 -encoding UTF-8 -d target/classes src/main/java/br/com/quadradorunner/*.java
Copy-Item -Recurse -Force src/main/resources/* target/classes/
java -cp target/classes br.com.quadradorunner.Main
```

## Controles

| Tecla | Ação |
|---|---|
| Espaço ou seta para cima | Começar / pular |
| D | Dash, após comprar a habilidade |
| S | Durabilidade, após comprar a habilidade |
| W | Foguete guiado, após comprar a habilidade |
| Espaço (manter pressionada) | Planar durante a queda, após comprar a habilidade |
| P ou Esc | Pausar / continuar |
| R | Reiniciar / rejogar a fase atual |
| F | Abrir / fechar seleção de fases (Esc também fecha) |
| Enter | Avançar após a vitória, quando houver próxima fase |
| B | Abrir a loja após a vitória / voltar ao resumo |
| 1–7 ou setas + Enter/Espaço | Comprar o item correspondente na loja |
| M | Silenciar / reativar música e efeitos |

Solte todas as teclas de pulo antes de pressionar novamente para cada salto. Comandos ignoram a repetição automática do teclado. Ao perder o foco da janela, a partida pausa e limpa as teclas pressionadas; pressione P ou Esc para continuar. O pulo está um pouco mais alto para facilitar a corrida. Colete os cristais azuis com o número **2** para liberar um segundo salto no ar por **10 segundos**. Cada pouso renova os dois saltos enquanto o efeito estiver ativo. Um novo cristal renova a duração para 10 segundos; a pausa congela esse tempo. O indicador superior mostra a duração restante e os saltos disponíveis. Reiniciar restaura os cristais e remove o efeito.

A câmera acompanha o jogador e o indicador superior mostra o progresso na fase atual. A primeira fase pode ser concluída sem coletar cristais; as seguintes exigem comprar e usar a habilidade indicada.

## Argolas e progresso salvo

A primeira fase possui 12 argolas douradas; as fases 2 e 3 possuem 6 cada; Piscinas possui 40, totalizando 64 argolas únicas. O catálogo completo custa 61 argolas, independentemente da ordem das compras, respeitando os pré-requisitos. Encoste na argola para ganhar 1 no saldo permanente. Cada argola é identificada pela fase e por um ID fixo: depois de coletada, não reaparece ao reiniciar ou reabrir o jogo. Rejogar permite buscar as que ainda faltam.

O aplicativo carrega e salva automaticamente um slot em `${user.home}/.quadrado-runner/progress.properties`. As gravações acontecem fora da thread da interface, usando o formato versionado e a substituição atômica existentes. Fechar normalmente aguarda a gravação final; evite encerrar o processo à força enquanto salva. Se houver falha ao salvar, o jogo mantém o progresso em memória e permite repetir a tentativa ao fechar novamente. Saves inválidos ou de versão incompatível são preservados e informados ao carregar, sem começar silenciosamente do zero.

O HUD mostra o saldo e a contagem de argolas já coletadas na fase (por exemplo, **12/12**). Quando todas já foram coletadas no save, elas permanecem ausentes e a tela inicial informa esse motivo. Se a coleta estiver parcial, a tela inicial mostra quantas ainda restam. Esse contador representa o histórico permanente da fase; o resumo final mostra separadamente as argolas novas da tentativa.

## Loja entre fases

O botão vermelho **Zerar progresso**, disponível nos menus e na pausa, abre uma confirmação que explica o que será apagado. **Manter progresso**, Enter, Esc ou fechar esse diálogo cancela; **Sim, zerar tudo** confirma. O reset zera saldo, argolas coletadas, habilidades compradas e fases concluídas. Os cristais e a tentativa são restaurados, e apenas a primeira fase fica liberada. O jogo aguarda a gravação do save; se ela falhar, o progresso anterior continua na sessão. **R** continua reiniciando somente a tentativa, preservando o save.

Após concluir uma fase, pressione **B** para abrir a loja. Preços iniciais: **Dash 4**, **Planar 6**, **Pulo duplo 5**, **Durabilidade 5** e **Foguete 5** argolas. Cada poder base comprado aumenta em 3 argolas o preço dos outros poderes base. As melhorias **Dash II** e **Pulo duplo II** custam **3 argolas fixas** cada, exigem a compra do poder correspondente e não aumentam os preços dos poderes. A tela mostra preços atualizados e pré-requisitos. **B/Esc** retorna ao resumo; **R** rejoga mantendo as compras.

Cada compra é permanente, debita o preço uma única vez e é salva automaticamente. Compras repetidas ou sem saldo são recusadas, preservando o progresso; gastar não restaura argolas já coletadas. A loja também funciona depois da última fase disponível. Saia da loja para usar Enter e avançar quando houver uma próxima fase.

Após comprar Dash, use **D** durante a corrida. Após comprar Planar, mantenha **Espaço** durante a queda. O salto duplo temporário dos cristais continua independente das compras.

## Durabilidade, Foguete e melhorias

**Durabilidade (S):** invulnerabilidade contra obstáculos e barreiras por **5 segundos**. Recarga de **10 segundos contada desde a ativação**; a aura azul indica proteção. Não cria chão sobre buracos.

**Foguete (W):** lança um projétil guiado contra o obstáculo mais próximo até **700 px à frente**. O alvo é destruído quando o projétil chega; obstáculos especiais também podem ser destruídos. Recarga de **4 segundos**, sem consumo caso não exista alvo. A destruição dura só naquela tentativa: reiniciar restaura os obstáculos.

**Dash II:** reduz a recarga de **1,2 para 0,6 segundo**, preservando duração e velocidade. **Pulo duplo II:** reduz a recarga do segundo salto de **2 para 1 segundo**. Pousar renova os saltos, mas não elimina essa recarga. O limite continua sendo dois saltos antes do próximo pouso.

Pausar congela recargas, proteção e projétil. Reiniciar ou trocar de fase limpa esses estados; poderes e melhorias comprados permanecem no save existente.

## Dash

O dash acelera horizontalmente para **780 px/s durante 0,2 segundo**, no chão ou no ar. A recarga é de **1,2 segundo a partir da ativação**. Solte e pressione D novamente para cada impulso; manter a tecla pressionada não ativa outro dash ao terminar a recarga. A habilidade só pode ser usada durante a corrida e depois da compra permanente na loja.

O indicador mostra bloqueio, disponibilidade, ativação e recarga, e o jogador deixa um rastro roxo enquanto acelera. O dash mantém a gravidade e os limites de salto: obstáculos continuam causando derrota. A colisão e a coleta são verificadas em subpassos com deslocamento horizontal de até 2 px durante o impulso. A pausa e a perda de foco congelam seus temporizadores; reiniciar a fase ou carregar outra começa com dash pronto, preservando a compra. Apenas o desbloqueio é salvo, não a duração ou a recarga de uma tentativa.

## Planar

Depois de comprar Planar na loja por **6 argolas**, mantenha **Espaço** pressionada durante a corrida. A habilidade atua apenas no ar e na descida: a gravidade cai para **300 px/s²** e a velocidade de queda fica limitada a **120 px/s**. É possível segurar G antes do ponto mais alto do salto para começar a planar automaticamente na descida; a subida mantém a física normal.

Soltar G restaura a gravidade normal a partir da velocidade atual. A habilidade continua descendo até o chão, sem conceder saltos extras ou invulnerabilidade, e pode ser combinada com dash e com o salto duplo temporário. Pousar encerra o efeito; se G continuar pressionada, um novo salto volta a planar na descida. Pausar, perder foco, reiniciar, concluir a fase ou morrer cancela o comando. Depois de uma pausa, solte e pressione G novamente para reativar.

O indicador mostra bloqueio, instrução de uso ou estado ativo. Durante o efeito, o jogador aparece em azul-claro com uma pequena asa acima dele. A compra permanece no save, mas o comando pressionado e a velocidade da tentativa não são persistidos.

## Final de fase

Ao chegar à bandeira, o jogo mostra as **argolas novas coletadas naquela tentativa** e o **saldo total**. A conclusão é registrada no mesmo save, sem conceder recompensa adicional: as argolas já foram creditadas na coleta.

Use **R** para rejogar a fase atual. O contador da tentativa volta a zero, mantendo o saldo e as argolas permanentemente coletadas. **Enter** avança para a próxima fase após a conclusão, com a loja fechada. Depois da quarta fase, a campanha está concluída.

Pressione **F** ou clique em **Fases (F)** nos menus para escolher uma fase. A lista indica fases concluídas, liberadas e bloqueadas; os botões bloqueados ficam desabilitados. É possível rejogar fases concluídas ou retomar a próxima liberada após reabrir o jogo, respeitando conclusões anteriores e habilidades exigidas. Escolher inicia uma nova tentativa, preservando saldo, compras e argolas ausentes. Abrir a seleção durante a corrida pausa a tentativa; **F** ou **Esc** cancela a seleção e mantém a pausa. Use P ou Esc depois de fechar para continuar. A fase selecionada não é salva: ao reabrir, use o menu para escolher novamente.

## Fases e desbloqueios

| Fase | Comprimento | Acesso e desafio |
|---|---:|---|
| 1 — A pista | 8.400 px | Liberada desde o início; cenário noturno. |
| 2 — Amanhecer | 8.400 px | Concluir a fase 1; buracos e cenário ao amanhecer. |
| 3 — Corrente aérea | 8.400 px | Concluir as anteriores; tema espacial, pilhas e quedas. |
| 4 — Piscinas | 8.400 px | Concluir as anteriores; piscinas internas, azulejos, pilares e aquedutos, sem sol. |

As fases abrem em ordem e mantêm saldo, compras e argolas coletadas. Saves que já concluíram as três primeiras liberam Piscinas pelo menu F. O cenário de Piscinas usa duas fileiras de aquedutos, piscinas ao fundo e quatro vãos com água na pista; as regras dos buracos permanecem iguais. A trilha original combina acordes suaves, notas semelhantes a gotas e ecos. Não há dicas textuais de travessia.

A fase 2 tem seis argolas acessíveis usando apenas Dash, suficientes para comprar Planar mesmo se você chegar com saldo zero. O jogo não concede outra recompensa por concluir ou rejogar uma fase. Depois do reset completo, saldo e compras são zerados, todas as argolas voltam e somente a fase 1 fica liberada. Fora do reset confirmado, morte e replay preservam o progresso.

## Organização

O HUD separa saldo permanente, argolas coletadas na fase e progresso até a chegada. A segunda linha mostra salto duplo temporário, estado/recarga do Dash e estado do Planar. Coletar mostra um aviso curto junto ao jogador; menus e loja mantêm instruções visíveis, sem cobrir os indicadores superiores. A barra inferior separa botões de menu dos controles de movimento.

Títulos, HUD e botões usam **Oxanium**, dos [autores do projeto Oxanium](https://github.com/sevmeyer/oxanium), distribuída sob a [SIL Open Font License 1.1](src/main/resources/fonts/OFL-Oxanium.txt). Os arquivos originais, crédito e licença acompanham o JAR em `fonts/`; não é necessário instalar a fonte nem conectar à internet para jogar. Se o recurso estiver ausente ou inválido, a interface usa SansSerif e registra o motivo.

- `Main.java`: cria a janela na EDT e encaminha perda de foco.
- `GamePanel.java`: adapta entrada e timer do Swing; desenha mundo, câmera e HUD.
- `GameUi.java`, `GameButton.java`, `ResetConfirmation.java`: fontes locais, botões e confirmação de reset, sem alterar padrões globais do Swing.
- `GameModel.java`: estado, movimentação, saltos, colisões e power-ups, sem dependência de Swing.
- `GameSession.java`: sequência de fases, replay e avanço com preservação do progresso.
- `AbilityShop.java`: catálogo, preços e transação imutável de compra.
- `Ring.java`: identidade local, posição e colisão das argolas.
- `GameProgress.java`: snapshot imutável de saldo, argolas, habilidades e fases concluídas.
- `GameProgressStore.java`: persistência local versionada com gravação atômica.
- `ProgressAutosave.java`: fila de gravação fora da EDT.
- `Level.java`: geometria e configuração da fase.
- `AbilityGate.java`: barreiras e correntes que exigem um movimento ativo.
- `GameInput.java`: transições das teclas e bloqueio de repetição.
- `FixedStepLoop.java`: acumulador de tempo com passo fixo de 1/120 s.
- `GameAudio.java`: coordena áudio fora da EDT e trata indisponibilidade do dispositivo.
- `AudioSamples.java`: gera a composição original e os efeitos em PCM.
- `SynthAudioOutput.java`: reproduz os sons com clips do Java Sound e libera os recursos.
- `src/test`: testes JUnit 5 das regras, entrada, loop e integração Swing sem janela.

O timer Swing solicita atualizações aproximadamente a cada 16 ms apenas durante a corrida. Cada callback executa até 30 passos fixos (250 ms de simulação); atrasos maiores descartam o excedente para evitar bloquear a EDT. Assim, os 10 segundos do cristal são medidos em tempo de simulação e podem durar mais em tempo real sob travamentos prolongados. Pausar e retomar descarta o resíduo do acumulador.

O mapa original tem 8.400 pixels e 17 obstáculos. A suíte simula sua conclusão sem cristais e percorre a campanha inteira com compras e habilidades, incluindo o cenário de saldo zero na chegada à fase 2. Cada fase possui comprimento próprio, utilizado pela bandeira, câmera, HUD e regra de vitória. A colisão usa as formas Java2D dos obstáculos e é verificada a cada passo; não é uma detecção contínua para velocidades arbitrárias.

A renderização reutiliza fontes e cores, desenha obstáculos com uma transformação de câmera e ignora os que estão fora da tela. Todos os acessos ao modelo na aplicação acontecem na EDT; os testes de regras não precisam criar componentes gráficos.

## Testes

```sh
mvn test
```

JUnit 5 e Surefire são dependências apenas do build/teste. A suíte cobre salto e pouso, salto duplo, coleta e renovação, expiração, pausa, reinício, colisões das quatro formas, vitória, repetição de teclas e equivalência da física com diferentes intervalos de callbacks.

## Áudio

O jogo inclui efeitos suaves de salto, coleta de argola/cristal, dash, início de planar, compra, compra recusada, derrota e vitória. Comandos recusados de movimento e repetição de teclas não disparam efeitos; a loja informa compras duplicadas ou sem saldo com texto e um som curto. A música de fundo é uma composição procedural original de 16 segundos, com quatro acordes lentos e arpejos, reproduzida em loop e em volume baixo. O áudio não depende de arquivos externos, downloads nem bibliotecas adicionais: a síntese e a reprodução usam Java Sound, incluído no JDK.

A música começa ao abrir o painel e continua nas telas inicial, de derrota e de vitória. Pausar ou tirar o foco interrompe a música e os efeitos; ao continuar, a música retoma da posição anterior. A tecla **M** silencia música e efeitos juntos, com indicação no rodapé. Essa preferência é mantida ao reiniciar a fase, apenas durante a execução atual.

A criação dos sons e todas as operações do dispositivo são executadas em uma thread dedicada, sem bloquear a EDT. Ao remover o painel ou fechar a janela, os clips são liberados. Se o dispositivo estiver indisponível, o jogo continua sem som e registra o motivo no log. Os testes usam uma saída substituta, sem exigir alto-falantes, e verificam os eventos, mute, foco, encerramento e amostras PCM; não substituem uma avaliação auditiva em dispositivo real.
