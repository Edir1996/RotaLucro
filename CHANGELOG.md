# Changelog

## 0.11.2 — BR99 Reader Background Trigger

- Corrige leitura que só iniciava quando a 99 já estava em primeiro plano.
- Remove o filtro XML exclusivo `android:packageNames="com.app99.driver"` para conseguir perceber a transição de janela originada por Maps/SystemUI/launcher.
- Eventos de outros apps são usados somente como gatilho de transição; o leitor não armazena nem interpreta seu conteúdo.
- Adiciona `TYPE_NOTIFICATION_STATE_CHANGED` para usar a notificação da 99 como sinal antecipado.
- Novo watcher de 5,5 s após sinal da 99 para localizar o card/janela da oferta quando ela surge sobre outro app.
- Detecta qualquer janela visível da `com.app99.driver`, não apenas `rootInActiveWindow`.
- Se a notificação da 99 já trouxer valor + coleta + viagem, o parser pode reconhecê-la diretamente.
- Mantém debounce/latch da v0.11.1 para evitar OCR repetido e bolha piscando.
- Mantém screenshot pontual via AccessibilityService, ML Kit, parser BR99, Cloud e demanda inteligente.

## 0.11.1 — BR99 Reader Stable

- Bolha não alterna mais entre verde e laranja durante OCR.
- Verde significa leitor ativo + 99 visível; azul aguardando; cinza pausado.
- Eventos `WINDOW_CONTENT_CHANGED` passam por debounce.
- Rajadas de eventos de janela são coalescidas.
- Oferta reconhecida entra em latch curto para evitar OCR repetido.

## 0.11.0 — BR99 Reader

- Leitor orientado a eventos da Acessibilidade.
- Árvore de acessibilidade primeiro; OCR local como fallback.
- Screenshot pontual via AccessibilityService.
