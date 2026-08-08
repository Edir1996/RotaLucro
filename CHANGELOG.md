# Changelog

## 0.8.0 — Demanda Inteligente
- Analisa o destino reconhecido pelo OCR e tenta geocodificá-lo.
- Regiões de demanda configuráveis por nome, endereço de referência, palavras-chave, raio e força de demanda.
- Distância do destino até a região de demanda: Excelente, Boa, Atenção, Afastada, Ruim e Muito ruim.
- Retorno vazio estimado passa a usar a distância real até uma região de demanda quando disponível.
- Prêmio mínimo de R$/km configurável para destinos a 5–7 km, 7–9 km e 9 km+ da demanda.
- Score inteligente 0–100 e recomendação ACEITAR / ATENÇÃO / RECUSAR.
- Áreas de alta demanda reduzem a penalização de afastamento.
- Aprendizado local: quando nova oferta aparece pouco depois do fim estimado de corrida aceita, a região ganha confiança de demanda.
- Histórico salva destino OCR, distância até demanda, região e score.
- Box flutuante mostra score, distância da demanda, possível retorno vazio e recomendação.
- Mantém OCR contínuo, bolha flutuante com logo, histórico e personalização do box da 0.7.x.

> A demanda não é o mapa de calor da 99. Ela é estimada a partir das regiões configuradas e do histórico do próprio usuário.
