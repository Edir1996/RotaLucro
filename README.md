# RotaLucro v0.11.2 — BR99 Reader Background Trigger

Aplicativo Android de apoio para análise das ofertas exibidas no app 99 Motorista.

## Correção v0.11.2

Esta versão corrige o caso em que a leitura funcionava somente quando a 99 já estava em primeiro plano.

O serviço agora observa transições globais de janela apenas para detectar quando a 99 surge sobre outro app. O conteúdo de outros aplicativos não é armazenado nem processado pelo parser.

Fluxo quando o motorista está no Maps, tela inicial ou outro app:

1. a 99 gera uma notificação/sinal em segundo plano;
2. o RotaLucro arma um watcher curto de 5,5 segundos;
3. o serviço acompanha as janelas do Android até detectar uma janela da `com.app99.driver`;
4. quando o card/atividade da 99 surge, o leitor BR99 é disparado;
5. primeiro tenta a árvore de Acessibilidade;
6. se necessário, faz screenshot pontual + ML Kit OCR;
7. o parser valida valor + coleta + viagem e mostra o box.

Também existe leitura do texto da própria notificação da 99: se excepcionalmente ela contiver todos os dados necessários, a oferta pode ser analisada antes mesmo do card completo aparecer.

## Bolha

- Verde: leitor ativo + janela da 99 detectada.
- Azul: leitor ativo aguardando a 99.
- Cinza: leitor pausado/indisponível.

O OCR não altera a cor da bolha, evitando piscadas.

## Permissões

- Internet: login/sincronização Cloud.
- Acessibilidade: detectar mudanças de janela, janela da 99 e overlay.
- `canTakeScreenshot`: captura pontual usada pelo serviço de Acessibilidade.

Não existe sessão contínua de MediaProjection.

## Primeiro uso após atualizar

1. Instale o novo APK.
2. Vá em Configurações > Acessibilidade > RotaLucro.
3. Desative o serviço.
4. Ative novamente.
5. Abra o RotaLucro e deixe o Leitor BR99 ativado.
6. Pode voltar ao Maps ou outro app e aguardar a próxima oferta.

A reativação é importante porque a v0.11.2 alterou os tipos de eventos que o serviço de Acessibilidade solicita.

## Servidor Cloud

`https://greenyellow-hippopotamus-200993.hostingersite.com`

## Build pelo GitHub Actions

Artefato esperado:

`RotaLucro-v0.11.2-BR99Reader-BackgroundTrigger-debug-apk`
