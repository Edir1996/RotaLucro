# RotaLucro — MVP Android

Aplicativo Android para motoristas avaliarem ofertas da 99 por:

- valor bruto por quilômetro;
- valor bruto por hora;
- combustível estimado;
- manutenção por quilômetro;
- lucro estimado;
- classificação: boa, atenção ou fraca.

## O que já funciona

1. Calculadora manual completa.
2. Configuração das metas e dos custos do veículo.
3. Serviço de Acessibilidade limitado ao pacote `com.app99.driver`.
4. Leitura inicial de valores, distâncias e tempos exibidos na tela.
5. Faixa de resultado sobre a tela da 99.
6. Testes unitários.
7. Build automático de APK pelo GitHub Actions.

## Como gerar o APK no GitHub

1. Crie um repositório no GitHub.
2. Envie todos os arquivos deste projeto para o repositório.
3. Abra a aba **Actions**.
4. Execute o workflow **Android Build** ou faça um push na branch `main`.
5. Ao finalizar, abra a execução e baixe o artefato `RotaLucro-debug-apk`.

## Como testar no celular

1. Instale o APK de debug.
2. Abra o RotaLucro e salve suas metas e custos.
3. Toque em **Ativar nas configurações de acessibilidade**.
4. Habilite **RotaLucro — leitura de ofertas**.
5. Abra o app 99 Motorista e aguarde uma oferta.

## Ajuste necessário para ficar preciso

A 99 pode mudar os textos e a ordem dos elementos da tela conforme versão, cidade ou categoria. O parser atual usa uma heurística inicial. Para calibrar, registre os textos reais da tela ou use uma captura de oferta sem dados pessoais.

## Segurança e publicação

O serviço observa apenas o app `com.app99.driver`, não toca em botões e não aceita ou recusa corridas. Antes de publicar na Google Play, será necessário preencher a declaração de uso da API de Acessibilidade, mostrar divulgação destacada e revisar a política vigente.

## Próximas etapas sugeridas

- histórico de ofertas;
- voz anunciando R$/km e R$/hora;
- perfis para carro, moto e entrega;
- custos fixos mensais rateados;
- mapa e alerta de região;
- assinatura e painel administrativo;
- build release assinado com GitHub Secrets.
