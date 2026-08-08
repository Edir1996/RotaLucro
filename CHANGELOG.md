# Changelog

## 0.6.0

- Substitui leitura dos valores via árvore de acessibilidade por OCR local com ML Kit.
- Adiciona captura de tela via MediaProjection com foreground service `mediaProjection`.
- Modelo OCR latino agrupado no APK (`com.google.mlkit:text-recognition:16.0.1`).
- Não salva screenshots e não usa permissão de Internet.
- Serviço de acessibilidade passa a identificar apenas o app em primeiro plano e hospedar overlays.
- Nova bolha flutuante arrastável com menu: ativar/desativar OCR, ler agora, abrir app e ocultar.
- Novo box compacto de resultado com R$/km, R$/hora, km, minutos, valor, regra ativa e lucro estimado.
- Novo laboratório OCR com ofertas simuladas para testar sem ficar online na 99.
- Nova aba Leitor com diagnóstico detalhado do pipeline OCR.
- Parser OCR evita usar `Tarifa base dinâmica` como valor principal.
- Mantém regras padrão e quatro faixas configuráveis por horário.

## 0.5.0

- Diagnóstico do leitor por acessibilidade e simulador inicial.
