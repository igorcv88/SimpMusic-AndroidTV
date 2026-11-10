# SimpMusic Android TV — implementação incremental

Este repositório é um fork do SimpMusic. O objetivo é um aplicativo nativo para
Android TV / Google TV, mantendo o motor de reprodução original (incluindo
AutoMix/crossfade), capas animadas e superfícies Liquid Glass.

## Princípios

- Reutilizar os módulos `core` e `composeApp` sempre que possível; evitar uma
  segunda implementação de fila, download, autenticação ou áudio.
- Separar a entrada/interface de TV da interface de celular. Uma UI que apenas
  abre em landscape não é considerada adaptação para controle remoto.
- Preservar o código e os avisos de licença GPL-3.0 do upstream.
- Manter um nome de pacote e assinatura próprios no APK de TV. Antes do primeiro
  release, adaptar a verificação de identidade que o upstream aplica a APKs não
  oficiais, sem depender de sua chave de assinatura.
- Cada etapa funcional exige build e teste em Android TV real antes do merge.
  Uma compilação ou captura de tela isolada não comprova reprodução ou fluidez.
- Sincronizar o submódulo Git `core` explicitamente: uma atualização do fork
  principal não atualiza o commit de `core` automaticamente.

## Etapas e critérios de aceite

### P0 — Descoberta e instalação na TV (Work / GPT-6.1 Sol, medium)

- Declarar `LEANBACK_LAUNCHER`, banner de TV e touchscreen opcional.
- Preservar a entrada de celular e os filtros de links existentes.
- Gerar APK debug e instalar via ADB na TV.
- Confirmar que o app aparece no launcher, abre sem crash e que o serviço de
  mídia inicializa. **A navegação por D-pad não faz parte de P0.**

### P1 — Aplicação e navegação nativas de TV (Work / GPT-6.1 Sol, high)

- Isolar `tvAndroidApp`/entrada de TV do aplicativo de celular, compartilhando
  dados, ViewModels e serviços de reprodução.
- Dar ao APK TV pacote próprio, banner final, nome e ciclo de releases/CI.
- Criar Home, Busca, Biblioteca, Fila e Player com foco visível, D-pad,
  Back, teclado virtual e estados vazios/erro.
- Testar uma sessão completa usando somente controle remoto, sem mouse.

### P2 — Compatibilidade de áudio (Work / GPT-6.1 Terra, medium-high)

- Reutilizar `CrossfadeExoPlayerAdapter` e o processamento de filtros.
- Testar transições automáticas, faixas curtas, fila editada, pausa/pulo durante
  crossfade, saída HDMI e retomada após suspensão.
- Não confundir reprodução nativa com Google Cast: o próprio SimpMusic desativa
  crossfade/DJ/EQ ao enviar áudio a um receptor Cast.
- Validar que CPU, áudio e memória da TV toleram preparação simultânea de faixas.

### P3 — Player Liquid Glass e capas animadas (Cloud Code / High Code 5.5, high)

- Preservar vídeos de capas, Canvas e paleta adaptativa no player de TV.
- Construir superfícies de vidro focáveis, com bordas e refração consistentes.
- Medir frames, GPU, memória e logs na própria TV; ajustar resolução de blur,
  quantidade de camadas e invalidações sem criar loops de renderização.
- Testar 1080p/4K, telas de pausa, mudança de capa, letras e menus sobrepostos.

### P4 — Pareamento opcional com o celular (Work / GPT-6.1 Terra, high)

- Pareamento local por QR Code, código efêmero e confirmação na TV.
- O telefone envia controles, busca e edição de fila; a TV continua a executar
  reprodução e AutoMix localmente.
- Autenticar a sessão, expirar chaves, validar origem e evitar transportar
  cookies/credenciais de YouTube Music pelo canal de controle.
- Testar perda de Wi-Fi, reconexão, múltiplos clientes e revogação do vínculo.

### P5 — Validação integrada e acabamento (Cloud Code / High Code 5.5, medium-high)

- Testes longos (fila, standby, retomada, troca de saída, letras, álbuns) com ADB.
- Corrigir regressões de foco, visual e sincronismo áudio/interface.
- Publicar APK assinado somente após CI e teste físico; documentar diferenças
  em relação ao SimpMusic de celular.

## Verificações de cada PR

1. Mudanças restritas ao escopo do PR; nada de refatorar o motor de áudio por
   causa de layout, ou de editar todas as telas de celular para adaptar uma TV.
2. `git diff --check`, recursos Android válidos, build Gradle e CI.
3. Teste de ADB em aparelho real quando a mudança afeta interface, foco,
   reprodução ou serviço; anexar evidência e limitações ao PR.
4. Não fazer merge de uma etapa que depende de teste físico ainda pendente.

**Status inicial:** P0 implementado no manifesto e banner provisório, mas ainda
pendente de compilação e validação física. Os trabalhos seguintes não foram
implementados.
