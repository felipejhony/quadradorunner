# Quadrado Runner

Este jogo foi criado com inteligência artificial (IA) para fins educacionais, como exemplo prático de desenvolvimento de jogos 2D em Java com Swing e Java2D.

Jogo 2D de corrida lateral feito com **Java 17, Maven, Swing e Java2D**, sem dependências externas em tempo de execução. Todos os elementos são desenhados por código.

O quadrado corre automaticamente da esquerda para a direita. Pule os 17 obstáculos (retângulos, triângulos, círculos e losangos) e alcance a bandeira para ver a mensagem de sucesso. As colisões acompanham o formato de cada obstáculo. Qualquer contato encerra a tentativa.

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
javac --release 17 -encoding UTF-8 -d target/classes src/main/java/br/com/quadradorunner/Main.java src/main/java/br/com/quadradorunner/GamePanel.java
java -cp target/classes br.com.quadradorunner.Main
```

## Controles

| Tecla | Ação |
|---|---|
| Espaço ou seta para cima | Começar / pular |
| P ou Esc | Pausar / continuar |
| R | Reiniciar a fase |

Solte e pressione novamente a tecla para cada pulo. O pulo está um pouco mais alto para facilitar a corrida. Colete os cristais azuis com o número **2** para liberar um segundo salto no ar por **10 segundos**. Cada pouso renova os dois saltos enquanto o efeito estiver ativo. Um novo cristal renova a duração para 10 segundos; a pausa congela esse tempo. O indicador superior mostra a duração restante e os saltos disponíveis. Reiniciar restaura os cristais e remove o efeito.

A câmera acompanha o jogador e o indicador superior mostra o progresso. A fase tem o dobro do comprimento original e pode ser concluída mesmo sem coletar os power-ups.

## Organização

- `Main.java`: cria a janela na thread de eventos do Swing.
- `GamePanel.java`: estados da partida, entrada por key bindings, física, colisões, câmera e renderização.
- `pom.xml`: compilação Java 17 e JAR executável.

A atualização usa um `javax.swing.Timer`, tempo decorrido e pequenos passos de física para manter as colisões consistentes. O mapa tem 8.400 pixels; velocidade, gravidade, pulo, cristais e obstáculos podem ser ajustados em `GamePanel`.
