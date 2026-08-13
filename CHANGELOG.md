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

## 0.11.0 — BR99 Reader
- Leitor refeito para ser orientado a eventos da Acessibilidade, em vez de depender de uma sequência contínua de OCR.
- A árvore de acessibilidade é verificada primeiro; OCR local é usado como fallback quando a 99/Flutter não expõe os textos.
- Adicionadas flags de view IDs, janelas interativas e views não importantes para melhorar a detecção da tela da 99.
- Captura pontual via AccessibilityService.takeScreenshot; Android 14+ prioriza takeScreenshotOfWindow e faz fallback para o display se a janela mudar.
- Nova fila de leitura pendente: um evento que chega durante o OCR não é perdido.
- Watchdog e retry automático depois de falhas da captura.
- Detecção da 99 mais robusta quando o motorista está no Maps e a 99 abre sozinha com uma nova oferta.
- Recorte OCR mais tolerante e linhas ordenadas geometricamente antes do parser.
- Servidor Cloud temporário continua fixo no APK; login permanece apenas usuário + senha.
