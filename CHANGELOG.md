# Changelog

## 0.7.0
- OCR passa a continuar processando enquanto a captura estiver ativa, sem depender de eventos isolados da Acessibilidade.
- Detecção da 99 estabilizada usando a janela ativa e ignorando eventos transitórios do SystemUI/overlays.
- Bolha flutuante usa a identidade visual/logo do RotaLucro e memoriza a posição.
- Menu da bolha ganhou “Salvar última como aceita”.
- Novo histórico local de corridas aceitas, com faturamento, km, R$/km, R$/hora e lucro estimado.
- Nova tela Box para configurar posição vertical, largura, tamanho, transparência, tempo e cores, com prévia na tela.
- Nova análise de retorno vazio: viagens acima do limite configurável consideram km/tempo de volta sem passageiro e classificam pela rentabilidade efetiva.
- Custos de combustível/manutenção também passam a considerar o retorno vazio quando ativado.

## 0.6.1
- Correção de compatibilidade do OverlayController legado.
