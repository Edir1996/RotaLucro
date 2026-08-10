# Changelog

## 0.10.0

- Remove MediaProjection e a permissão de gravação/transmissão de tela.
- OCR passa a usar screenshots pontuais do AccessibilityService.
- `android:canTakeScreenshot="true"` habilitado na configuração do serviço.
- Android 14+ usa `takeScreenshotOfWindow()` quando a janela da 99 pode ser localizada.
- Android 11–13 usa screenshot do display e mascara os overlays do próprio RotaLucro antes do OCR.
- Leitor permanece armado quando o RotaLucro e a 99 estão minimizados.
- Quando o motorista está no Maps e a 99 volta para frente com nova oferta, uma rajada controlada de leituras é disparada automaticamente.
- Loop de watchdog evita que uma leitura travada bloqueie as ofertas seguintes.
- OCR só roda continuamente quando a 99 ou o laboratório está visível, reduzindo bateria e falsos positivos.
- Bolha: azul = leitor armado, verde = 99 detectada, amarelo = OCR processando, cinza = pausado.
- Menu flutuante continua horizontal no topo.
- Mantidos login Cloud, histórico, demanda inteligente, aprendizado, box configurável e servidor temporário fixo.

## 0.9.3

- Aprendizado de demanda corrigido: destinos aceitos aparecem em aprendizado e confirmações posteriores aumentam a confiança.
