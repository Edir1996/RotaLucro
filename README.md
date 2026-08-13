# RotaLucro v0.11.0 — BR99 Reader

Aplicativo Android de apoio para análise das ofertas exibidas no app 99 Motorista.

## O que mudou no leitor

A v0.11.0 não usa MediaProjection e não mantém uma gravação de tela contínua.

Fluxo do leitor:

1. Acessibilidade recebe um evento da `com.app99.driver`.
2. O RotaLucro verifica a árvore da janela e os view IDs.
3. Se os dados da oferta estiverem acessíveis, eles são interpretados diretamente.
4. Se a interface Flutter não expuser os textos, o serviço faz uma captura pontual da janela/tela.
5. Google ML Kit Text Recognition processa a imagem localmente.
6. O parser valida valor + coleta + viagem.
7. O cálculo e o box são exibidos.

Quando o motorista estiver usando o Google Maps, o leitor permanece armado. Se a 99 voltar para a frente ao receber uma oferta, os eventos da janela disparam uma nova leitura.

## Permissões

- Internet: login/sincronização Cloud.
- Acessibilidade: detecção da 99, janela e overlay.
- `canTakeScreenshot`: captura pontual usada pelo serviço de Acessibilidade.

Não existe permissão de gravação/transmissão de tela do MediaProjection.

## Primeiro uso

1. Instale o APK.
2. Abra o RotaLucro.
3. Faça login com usuário e senha.
4. Ative a Acessibilidade do RotaLucro.
5. Se a Acessibilidade já estava ativa numa versão anterior, desative e ative novamente depois de instalar a v0.11.0.
6. Deixe o Leitor BR99 ativado.
7. Pode minimizar o RotaLucro e usar Maps/99 normalmente.

## Servidor Cloud

O servidor está fixo em:

`https://greenyellow-hippopotamus-200993.hostingersite.com`

O motorista vê somente usuário e senha.

## Build pelo GitHub Actions

Após enviar os arquivos ao repositório:

1. Commit to main
2. Push origin
3. GitHub > Actions > Android Build

Artefato esperado:

`RotaLucro-v0.11.0-BR99Reader-debug-apk`
