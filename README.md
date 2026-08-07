# RotaLucro — MVP Android 0.3.1

Aplicativo Android para analisar ofertas exibidas no 99 Motorista.

## Correção desta versão

A versão anterior apontava para o Compose BOM `2026.04.00`, que não estava disponível nos repositórios do Android. Nesta versão a dependência foi corrigida para `2026.06.01`.

## Classificação do box

A cor agora é definida pela faixa de valor por quilômetro que estiver ativa no horário da oferta:

- abaixo do mínimo: vermelho, corrida ruim;
- entre o mínimo e o máximo/ótimo: amarelo, corrida média;
- a partir do máximo/ótimo: verde, corrida ótima.

O aplicativo continua mostrando o valor por hora, mas ele é apenas informativo e não muda a cor do box.

## Faixas por horário

O motorista configura:

1. uma faixa padrão para os horários fora da dinâmica;
2. uma faixa chamada `Dinâmica 1`;
3. uma segunda faixa chamada `Dinâmica 2`.

Cada faixa dinâmica tem:

- botão para ativar ou desativar;
- horário de início;
- horário de término;
- mínimo por km;
- máximo/ótimo por km.

Exemplo:

- fora da dinâmica: mínimo R$ 1,20/km e ótimo R$ 1,80/km;
- das 18:00 às 22:00: mínimo R$ 1,40/km e ótimo R$ 2,00/km.

Nesse exemplo, uma oferta de R$ 1,30/km fica amarela fora da dinâmica, mas fica vermelha entre 18:00 e 22:00.

Horários que atravessam a meia-noite também funcionam, como `22:00–02:00`. Se as duas faixas dinâmicas se cruzarem, `Dinâmica 1` tem prioridade.

## Dados exibidos no box

- valor bruto por quilômetro;
- valor bruto por hora;
- lucro estimado;
- valor total da oferta;
- distância e tempo totais;
- custos estimados;
- nome e limites da faixa usada naquele horário.

## Como atualizar no GitHub

1. Extraia o ZIP.
2. Copie todos os arquivos de dentro da pasta `RotaLucro` para a pasta do repositório atual.
3. Confirme a substituição dos arquivos antigos.
4. No GitHub Desktop, escreva `Corrige build e adiciona faixas por horário`.
5. Clique em **Commit to main**.
6. Clique em **Push origin**.
7. Abra a aba **Actions** no GitHub.
8. Aguarde o workflow **Android Build**.
9. Baixe o artefato `RotaLucro-debug-apk`.

## Como testar

1. Instale o APK.
2. Configure a faixa padrão.
3. Ative e configure os horários de dinâmica desejados.
4. Salve.
5. Ative `RotaLucro — leitura de ofertas` nas configurações de acessibilidade.
6. Abra o 99 Motorista e aguarde uma oferta.

O RotaLucro não toca em botões e não aceita nem recusa corridas.

## Correção 0.3.1

- Corrigido o import de `KeyboardOptions` para `androidx.compose.foundation.text.KeyboardOptions`.
- Mantidas as faixas de R$/km configuráveis por horário.
