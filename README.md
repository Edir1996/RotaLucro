# RotaLucro Android 0.9.3 — Cloud + Demanda Inteligente

Aplicativo Android para análise de ofertas da 99 com OCR local, regras por horário, demanda inteligente, retorno vazio, histórico e integração com o painel PHP/MySQL RotaLucro Cloud.

## Novidades da 0.9.3

- Login obrigatório usando a mesma conta criada pelo administrador do painel web.
- Servidor Cloud embutido no APK; o motorista vê apenas os campos **Usuário** e **Senha**.
- URL temporária configurada em `cloud/ServerConfig.kt`.
- Sincronização de corridas aceitas, hotspots aprendidos, regiões de demanda e configurações.
- Cada instalação possui um `device_id` próprio.
- A bolha flutuante continua com a logomarca RotaLucro.
- Ao tocar na bolha, o menu agora abre **no topo da tela, horizontalmente**.
- Ações rápidas: ativar/pausar OCR, ler agora, salvar corrida aceita, abrir app e ocultar bolha.
- A tela **Ajustes** mostra a conta conectada e permite sincronizar ou sair.

## Primeiro uso

1. Instale o painel web no servidor configurado no APK e crie o usuário no administrador.
2. Abra o app.
3. Informe somente o **usuário** e a **senha** criados no painel.
4. Ative a Acessibilidade.
5. Autorize a captura de tela para o OCR.
6. Use a bolha flutuante enquanto estiver na 99.

## Alterar o servidor no futuro

Edite somente:

`app/src/main/java/com/rotalucro/app/cloud/ServerConfig.kt`

e troque `BASE_URL`. O endereço não é exibido ao motorista.

## O que vai para o servidor

O OCR continua sendo processado localmente. O app não envia screenshots. A nuvem recebe somente dados estruturados necessários aos relatórios e ao mapa: corridas salvas, coordenadas de destino quando disponíveis, hotspots, zonas e configurações.

## Build pelo GitHub Actions

O workflow `.github/workflows/android-build.yml` executa os testes e gera o artefato:

`RotaLucro-v0.9.3-DemandLearning-debug-apk`

## Requisitos

- Android 8.0+ (API 26)
- Acessibilidade RotaLucro ativada
- Autorização de captura MediaProjection
- Internet para login/sincronização
- Painel RotaLucro Cloud acessível por HTTPS
