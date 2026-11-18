# Modo Cinza

App Android que liga a **escala de cinza da tela** quando você abre os apps que escolheu
(Instagram, TikTok, jogos…) e volta às cores normais quando você sai deles.

## Como funciona

Um **serviço de acessibilidade** percebe qual app está em primeiro plano. Ele só recebe o nome do
app e **não lê** o conteúdo da tela. Para deixar a tela cinza, o app usa um destes três métodos e
escolhe sozinho o mais simples que o celular suporta (dá para trocar na tela do app):

| Método | Android | O que o usuário precisa fazer (uma vez) |
|---|---|---|
| **Modos do Android** (recomendado) | 15+ | Ligar duas chaves: o serviço de acessibilidade e o acesso ao "Não perturbe" |
| **Atalho de acessibilidade** | 12+ | Ligar o serviço e configurar o atalho das teclas de volume para a correção de cor em "Escala de cinza" |
| **Permissão via ADB** | 8+ | Rodar um comando `adb` uma vez (troca mais rápida e precisa) |

### Modos do Android (Android 15+)
O app cria um Modo chamado "Modo Cinza" que **não silencia nada** (filtro "todas as
notificações") e só tem o efeito *escala de cinza* (`ZenDeviceEffects`). Ele liga esse Modo ao
abrir um app escolhido e desliga ao sair. Para isso, o app só precisa do acesso ao "Não perturbe",
que é uma chave comum nas Configurações. O Modo aparece em *Configurações → Modos*.

### Atalho de acessibilidade (Android 12–14)
Em *Configurações → Acessibilidade → Correção de cor*:
1. escolha **Escala de cinza** (não precisa ligar a correção);
2. ligue o **atalho** e marque **Manter as teclas de volume pressionadas**.

Deixe só a correção de cor nesse atalho. O app aciona o atalho (`GLOBAL_ACTION_ACCESSIBILITY_SHORTCUT`)
quando precisa. Em alguns celulares pode aparecer um aviso rápido ou uma vibração a cada troca.

### Permissão via ADB (opcional)
```
adb shell pm grant br.app.modocinza android.permission.WRITE_SECURE_SETTINGS
```
Com essa permissão, o app também liga o próprio serviço de acessibilidade sozinho.

## Compilar e instalar

**Android Studio:** *File → Open* nesta pasta, espere o Gradle sincronizar e clique em *Run ▶*
com o celular conectado (Depuração USB ativada).

**GitHub Actions (sem Android Studio):** suba a pasta para um repositório no GitHub. O workflow
`.github/workflows/build.yml` compila o APK; baixe em *Actions → último build → Artifacts*.

> **Android 13+ e APK baixado:** ao ativar o serviço, o Android pode mostrar "Configuração
> restrita". Abra *Configurações → Apps → Modo Cinza → ⋮ → Permitir configurações restritas* e
> tente de novo. O app tem um botão que abre essa tela. Instalando pelo Android Studio, isso não
> acontece.

## Limitações conhecidas

- Alguns fabricantes mexem nessas partes do Android. Se um método não funcionar no seu celular,
  troque de método na tela do app e use o botão **Testar por 3 segundos**.
- A barra de notificações e o teclado ficam cinza junto com o app escolhido (é a tela inteira).
- Para desativar tudo: desligue a chave **Ativado** no app ou o serviço em Acessibilidade.

## Estrutura

```
app/src/main/java/br/app/modocinza/
├── MainActivity.kt      tela: método, configuração guiada, lista de apps (Compose)
├── GrayscaleService.kt  detecta o app em primeiro plano; aciona o atalho (método 2)
├── Method.kt            os três métodos + GrayController (escolhe e aplica)
├── ModesGray.kt         método 1: Modo com efeito de escala de cinza (Android 15+)
├── ShortcutGray.kt      método 2: verifica a configuração do atalho
├── AdbGray.kt           método 3: escreve na correção de cor (precisa do ADB)
├── Daltonizer.kt        lê o estado da correção de cor
└── AppPrefs.kt          apps escolhidos, liga/desliga, método
```

Versões: AGP 8.7.3, Kotlin 2.1.0, Compose BOM 2024.12.01, minSdk 26, targetSdk 35.
