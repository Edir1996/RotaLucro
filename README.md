# RotaLucro 0.4.0

Aplicativo Android em Kotlin + Jetpack Compose para analisar ofertas exibidas no app 99 Motorista.

A versão 0.4.0 foi redesenhada com navegação profissional, regras de R$/km por horário, simulador, custos da moto, diagnóstico da leitura e um novo box sobreposto à oferta.

## O que o aplicativo mostra no box

- Classificação: **RUIM**, **MÉDIA** ou **ÓTIMA**
- Valor recebido por quilômetro
- Valor recebido por hora
- Lucro estimado
- Valor total da oferta
- Distância total, incluindo o deslocamento até o passageiro
- Tempo total, incluindo o deslocamento até o passageiro
- Faixa de R$/km ativa naquele horário

## Regra das cores

Exemplo com mínimo de R$ 1,20/km e ótimo de R$ 1,80/km:

- Abaixo de R$ 1,20/km: vermelho
- De R$ 1,20 até menos de R$ 1,80/km: amarelo
- A partir de R$ 1,80/km: verde

A referência média exibida no app é a média entre os dois limites: R$ 1,50/km.

Você pode configurar quatro faixas de horário. Por exemplo:

- Padrão: mínimo R$ 1,20 e ótimo R$ 1,80
- 18:00–20:00: mínimo R$ 1,40 e ótimo R$ 2,00
- 20:00–23:00: mínimo R$ 1,50 e ótimo R$ 2,10

Horários que atravessam a meia-noite, como 23:00–02:00, também são aceitos.

## Leitura adaptada ao cartão atual da 99

O parser foi testado com este formato de oferta:

- Total: R$ 8,40
- Dinâmica: 1,6x
- Tarifa base dinâmica: R$ 1,27
- Até o passageiro: 6 min e 2 km
- Corrida: 5 min e 2,3 km

Resultado bruto:

- Distância total: 4,3 km
- Tempo total: 11 min
- R$/km: aproximadamente R$ 1,95
- R$/hora: aproximadamente R$ 45,82

A tarifa base dinâmica não é confundida com o valor total da oferta.

## Telas

- **Início:** regra ativa, status do leitor, teste do box e diagnóstico
- **Regras:** faixa padrão e quatro horários configuráveis
- **Simular:** cálculo manual e prévia realista do box
- **Ajustes:** combustível, consumo, manutenção e tempo de exibição

## Privacidade

O serviço de acessibilidade processa apenas textos da tela da 99 e usa somente valor, tempo e distância para os cálculos. O projeto não automatiza o botão Aceitar, não recusa corridas e não armazena endereços, nomes ou avaliações.

## Assinatura estável da versão de testes

O projeto inclui uma chave **somente de desenvolvimento** para que os APKs gerados em execuções diferentes do GitHub possam ser instalados como atualização, sem precisar desinstalar a versão anterior. Essa chave não deve ser usada em uma publicação definitiva.

## Gerar o APK no GitHub

1. Copie os arquivos deste projeto para o repositório `RotaLucro`.
2. Faça **Commit to main** e depois **Push origin** no GitHub Desktop.
3. Abra a aba **Actions** no GitHub.
4. Execute **Android Build** ou aguarde a execução automática.
5. Quando o processo ficar verde, abra-o e baixe o artefato `RotaLucro-debug-apk`.
6. Extraia o ZIP do artefato e instale `app-debug.apk`.

## Primeiro uso

1. Abra o RotaLucro.
2. Toque em **Acessibilidade**.
3. Ative `RotaLucro — leitor de ofertas`.
4. Volte ao app e toque em **Testar box**.
5. Abra a 99 Motorista e aguarde uma oferta.
6. Caso o box não apareça, volte ao painel inicial e consulte **Última leitura da 99**.

## Tecnologias

- Kotlin 2.2.10
- Android Gradle Plugin 8.13.2
- Gradle 8.13 no GitHub Actions
- Android API 36
- Jetpack Compose BOM 2026.06.01
- Material 3
