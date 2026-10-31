# Quadrado Runner

Este jogo foi criado com inteligência artificial (IA) para fins educacionais, como exemplo prático de desenvolvimento de jogos 2D em Java com Swing e Java2D.

Jogo 2D de corrida lateral feito com **Java 17, Maven, Swing e Java2D**, sem dependências externas em tempo de execução. Todos os elementos são desenhados por código.

O quadrado corre automaticamente da esquerda para a direita. Pule os 17 obstáculos (retângulos, triângulos, círculos e losangos) e alcance a bandeira para ver a mensagem de sucesso. As colisões acompanham o formato de cada obstáculo. A interseção das hitboxes encerra a tentativa; tangência exata não é considerada colisão. O jogador usa uma caixa retangular, inclusive nos cantos arredondados do desenho. Cristais também usam uma caixa retangular de coleta, maior que o losango desenhado.

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
java -cp target/classes br.com.quadradorunner.Main
```

## Controles

| Tecla | Ação |
|---|---|
| Espaço ou seta para cima | Começar / pular |
| P ou Esc | Pausar / continuar |
| R | Reiniciar a fase |
| M | Silenciar / reativar música e efeitos |

Solte todas as teclas de pulo antes de pressionar novamente para cada salto. Comandos ignoram a repetição automática do teclado. Ao perder o foco da janela, a partida pausa e limpa as teclas pressionadas; pressione P ou Esc para continuar. O pulo está um pouco mais alto para facilitar a corrida. Colete os cristais azuis com o número **2** para liberar um segundo salto no ar por **10 segundos**. Cada pouso renova os dois saltos enquanto o efeito estiver ativo. Um novo cristal renova a duração para 10 segundos; a pausa congela esse tempo. O indicador superior mostra a duração restante e os saltos disponíveis. Reiniciar restaura os cristais e remove o efeito.

A câmera acompanha o jogador e o indicador superior mostra o progresso. A fase tem o dobro do comprimento original e pode ser concluída mesmo sem coletar os power-ups.

## Organização

- `Main.java`: cria a janela na EDT e encaminha perda de foco.
- `GamePanel.java`: adapta entrada e timer do Swing; desenha mundo, câmera e HUD.
- `GameModel.java`: estado, movimentação, saltos, colisões e power-ups, sem dependência de Swing.
- `Level.java`: geometria e configuração da fase.
- `GameInput.java`: transições das teclas e bloqueio de repetição.
- `FixedStepLoop.java`: acumulador de tempo com passo fixo de 1/120 s.
- `GameAudio.java`: coordena áudio fora da EDT e trata indisponibilidade do dispositivo.
- `AudioSamples.java`: gera a composição original e os efeitos em PCM.
- `SynthAudioOutput.java`: reproduz os sons com clips do Java Sound e libera os recursos.
- `src/test`: testes JUnit 5 das regras, entrada, loop e integração Swing sem janela.

O timer Swing solicita atualizações aproximadamente a cada 16 ms apenas durante a corrida. Cada callback executa até 30 passos fixos (250 ms de simulação); atrasos maiores descartam o excedente para evitar bloquear a EDT. Assim, os 10 segundos do cristal são medidos em tempo de simulação e podem durar mais em tempo real sob travamentos prolongados. Pausar e retomar descarta o resíduo do acumulador.

O mapa tem 8.400 pixels e 17 obstáculos. A suíte simula uma vitória sem cristais, saltando quando a distância até o próximo obstáculo fica abaixo de 55 pixels. A colisão usa as formas Java2D dos obstáculos e é verificada a cada passo; não é uma detecção contínua para velocidades arbitrárias.

A renderização reutiliza fontes e cores, desenha obstáculos com uma transformação de câmera e ignora os que estão fora da tela. Todos os acessos ao modelo na aplicação acontecem na EDT; os testes de regras não precisam criar componentes gráficos.

## Testes

```sh
mvn test
```

JUnit 5 e Surefire são dependências apenas do build/teste. A suíte cobre salto e pouso, salto duplo, coleta e renovação, expiração, pausa, reinício, colisões das quatro formas, vitória, repetição de teclas e equivalência da física com diferentes intervalos de callbacks.

## Áudio

O jogo inclui efeitos suaves de salto, coleta de cristal, derrota e vitória. Saltos recusados e repetição de teclas não disparam efeitos. A música de fundo é uma composição procedural original de 16 segundos, com quatro acordes lentos e arpejos, reproduzida em loop e em volume baixo. Não há arquivos externos, downloads nem bibliotecas de áudio adicionais: a síntese e a reprodução usam Java Sound, incluído no JDK.

A música começa ao abrir o painel e continua nas telas inicial, de derrota e de vitória. Pausar ou tirar o foco interrompe a música e os efeitos; ao continuar, a música retoma da posição anterior. A tecla **M** silencia música e efeitos juntos, com indicação no rodapé. Essa preferência é mantida ao reiniciar a fase, apenas durante a execução atual.

A criação dos sons e todas as operações do dispositivo são executadas em uma thread dedicada, sem bloquear a EDT. Ao remover o painel ou fechar a janela, os clips são liberados. Se o dispositivo estiver indisponível, o jogo continua sem som e registra o motivo no log. Os testes usam uma saída substituta, sem exigir alto-falantes, e verificam os eventos, mute, foco, encerramento e amostras PCM; não substituem uma avaliação auditiva em dispositivo real.
