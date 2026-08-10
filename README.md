# RotaLucro 0.10.0 — Leitor visual por Acessibilidade

Aplicativo Android para analisar ofertas da 99, calcular rentabilidade e sincronizar corridas/regiões com o RotaLucro Cloud.

## Mudança principal da v0.10.0

O MediaProjection foi removido. O RotaLucro não solicita mais autorização de gravação/transmissão da tela.

O leitor agora funciona dentro do `AccessibilityService`:

1. fica armado mesmo com o RotaLucro minimizado;
2. detecta o aplicativo em primeiro plano;
3. enquanto o motorista usa Maps, não roda OCR desnecessariamente;
4. quando a 99 volta para frente com uma oferta, solicita screenshots pontuais com `AccessibilityService.takeScreenshot()`;
5. no Android 14+, prefere `takeScreenshotOfWindow()` para capturar diretamente a janela da 99 sem os overlays do RotaLucro;
6. executa o ML Kit Text Recognition localmente;
7. descarta a imagem após o OCR;
8. calcula R$/km, R$/hora, lucro, retorno provável, demanda e score;
9. exibe o box configurável.

## Requisitos do leitor automático

- Android 11 (API 30) ou superior para screenshot por Acessibilidade.
- Serviço de Acessibilidade do RotaLucro ativado.
- A 99 precisa estar visível para seus dados poderem ser lidos visualmente. Se o motorista estiver no Maps e a 99 abrir automaticamente quando chegar uma oferta, o RotaLucro detecta essa troca e inicia a leitura.

## Privacidade

- screenshots não são salvos;
- imagens não são enviadas ao painel;
- OCR é local;
- o serviço não aceita, recusa ou toca em corridas;
- somente dados estruturados e itens escolhidos pelo usuário são sincronizados com o servidor.

## Cloud

Servidor temporário fixo no APK:

`https://greenyellow-hippopotamus-200993.hostingersite.com`

O usuário informa somente usuário e senha.

## Build

O GitHub Actions executa testes e gera o artefato:

`RotaLucro-v0.10.0-A11yScreenshot-debug-apk`

## Após instalar esta atualização

Como a capacidade `canTakeScreenshot` pertence à configuração estática do serviço de Acessibilidade, desligue e ligue novamente o RotaLucro em **Configurações > Acessibilidade** uma vez após instalar a v0.10.0.
