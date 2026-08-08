# RotaLucro 0.7.0

Aplicativo Android experimental para analisar ofertas da 99 Motorista usando captura de tela + OCR local.

## Recursos
- OCR local com ML Kit, sem envio de screenshots para servidor.
- Serviço de captura em primeiro plano para continuar lendo ofertas enquanto a sessão estiver ativa.
- Bolha flutuante com logo RotaLucro, arrastável e com atalhos.
- Regras de R$/km por horário: ruim, média e ótima.
- Box configurável: posição, largura, tamanho, transparência, cores e tempo em tela.
- Histórico local de corridas aceitas. Para evitar falsos positivos, depois de aceitar na 99 toque na bolha e escolha **Salvar última como aceita**.
- Análise opcional de possível retorno vazio em viagens longas. O usuário define a partir de quantos km da viagem considerar o retorno e qual fator da volta (1,0 = 100% da distância da viagem).
- Diagnóstico do OCR e laboratório de teste sem abrir a 99.

## Build no GitHub Actions
Faça push para `main` ou execute manualmente **Android Build** na aba Actions. O artefato será `RotaLucro-v0.7.0-OCR-debug-apk`.

## Observação sobre MediaProjection
O Android exige autorização do usuário para iniciar cada nova sessão de captura de tela. Se o sistema encerrar a sessão de captura, é necessário abrir o RotaLucro e autorizar novamente.
