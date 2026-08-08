# RotaLucro 0.6.0 — OCR + bolha flutuante

Aplicativo Android para analisar ofertas exibidas na 99 Motorista e calcular automaticamente o valor por km, valor por hora e lucro estimado.

## O que mudou nesta versão

A tela de oferta da 99 é renderizada em Flutter e os textos não aparecem na árvore do UIAutomator/Acessibilidade. Por isso esta versão não tenta mais extrair os valores pelos nós da interface.

O fluxo agora é:

1. O serviço de acessibilidade identifica quando `com.app99.driver` está em primeiro plano e hospeda a bolha/box de sobreposição.
2. O usuário autoriza uma sessão de captura de tela do Android.
3. `OcrCaptureService` usa MediaProjection para receber frames da tela.
4. A imagem é recortada para a região onde normalmente aparece a oferta.
5. ML Kit Text Recognition (modelo latino agrupado no APK) reconhece os textos localmente.
6. `OcrOfferParser` identifica valor principal, coleta e viagem e ignora linhas como `Tarifa base dinâmica`.
7. `RideCalculator` calcula R$/km, R$/hora, custos e lucro, aplica a regra de horário e publica o resultado.
8. O serviço de acessibilidade exibe um box vermelho/amarelo/verde sobre a 99.

Nenhuma captura de tela é salva em arquivo e o app não declara permissão de Internet.

## Bolha flutuante

Com a acessibilidade ativa aparece uma bolha `R`. Ela pode ser arrastada. Ao tocar nela abre um menu com:

- Ativar/desativar OCR
- Ler agora
- Abrir RotaLucro
- Ocultar bolha

A bolha usa `TYPE_ACCESSIBILITY_OVERLAY`, então não precisa da permissão "aparecer sobre outros apps".

## Regras

- Abaixo do mínimo configurado: vermelho / ruim
- Entre o mínimo e o ótimo: amarelo / média
- Igual ou acima do ótimo: verde / ótima

Há uma faixa padrão e quatro faixas opcionais por horário, inclusive horários que cruzam meia-noite.

## Teste sem entrar online na 99

1. Ative a acessibilidade do RotaLucro.
2. Abra o RotaLucro e toque em **Ativar OCR**.
3. Autorize a captura de tela inteira.
4. Toque em **Abrir laboratório OCR**.
5. A tela simulada mostra ofertas com o mesmo formato textual necessário ao parser.
6. O OCR deve reconhecer a oferta e exibir o box flutuante.
7. Na aba **Leitor** é possível acompanhar textos úteis, valor, coleta, viagem, R$/km, R$/hora e status do box.

## Primeira execução

- Em Android recente, a captura de tela precisa ser autorizada pelo usuário a cada nova sessão de MediaProjection.
- Se o aparelho bloquear a acessibilidade de APK instalado manualmente, abra a tela de informações do RotaLucro e habilite "Permitir configurações restritas" quando essa opção existir.
- Se a sessão de captura for encerrada pelo Android, pela tela bloqueada ou pelo usuário, toque novamente em **Ativar OCR**.

## GitHub Actions

O workflow `.github/workflows/android-build.yml` usa Java 17, Android API 36 e Gradle 8.13.

Depois de enviar os arquivos para o GitHub:

1. Abra **Actions**.
2. Execute **Android Build** (ou faça um push para `main`).
3. Ao finalizar, baixe o artefato `RotaLucro-v0.6.0-OCR-debug-apk`.

## Segurança funcional

O RotaLucro não toca no botão Aceitar, não aceita nem rejeita corridas e não automatiza ações na 99. Ele apenas lê a tela autorizada pelo usuário, calcula e mostra informações auxiliares.
