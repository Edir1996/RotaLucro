# RotaLucro 0.8.0 — Smart Demand

Aplicativo Android em Kotlin/Jetpack Compose que usa captura de tela autorizada pelo Android + OCR local (ML Kit) para analisar ofertas da 99 e mostrar um box flutuante com rentabilidade.

## Novidades 0.8.0

### Demanda inteligente
O app não depende apenas do R$/km bruto. Ele pode reconhecer o texto do destino, geocodificar o endereço e comparar o ponto de chegada com regiões de demanda cadastradas pelo usuário.

Classificação padrão da distância do destino até a demanda:
- 0–3 km: Excelente
- 3–5 km: Boa
- 5–7 km: Atenção
- 7–9 km: Afastada
- 9–12 km: Ruim
- 12 km+: Muito ruim

O mínimo por km pode subir automaticamente para destinos afastados. Valores padrão:
- 5–7 km: R$ 1,40/km
- 7–9 km: R$ 1,60/km
- 9 km+: R$ 1,80/km

O app calcula `km_retorno_estimado` usando a distância até a região de demanda. Assim, uma oferta aparentemente boa pode ser reclassificada se houver risco de volta vazia.

### Score 0–100
O score usa rentabilidade efetiva, R$/hora, distância da demanda, nível de demanda e deslocamento até o passageiro. A saída é ACEITAR, ATENÇÃO ou RECUSAR.

### Aprendizado local
Ao salvar uma corrida como aceita, o RotaLucro guarda o destino. Se uma nova oferta aparecer perto do horário estimado de término, essa área ganha confiança de demanda. O aprendizado fica apenas no aparelho.

## Limitação importante
O RotaLucro não tem acesso ao mapa de calor interno nem a uma API de demanda da 99. “Demanda inteligente” significa regiões configuradas pelo usuário + sinais aprendidos do próprio histórico, não demanda oficial em tempo real.

## Build no GitHub
Abra Actions → Android Build → Run workflow. O artefato é `RotaLucro-v0.8.0-SmartDemand-debug-apk`.
