# Wallpapers FOSS - Cloudflare Worker CDN

CDN e Proxy de mídia com Edge Caching para o aplicativo Wallpapers FOSS.

## Recursos de Segurança e Performance
- **100% Seguro:** O `BOT_TOKEN` nunca é exposto ao aplicativo nem gravado em repositórios públicos. Fica encriptado como Secret da Cloudflare.
- **Edge Caching Global:** O Cloudflare armazena os wallpapers em cache por 1 ano (`Cache-Control: immutable`). Do segundo download em diante, a entrega é instantânea e o Telegram não é mais consultado.
- **Suporte a Range (Vídeo 206):** Compatível com reprodução fluida de live wallpapers (vídeos MP4).
- **Sem Limites de Repositório Git:** Os arquivos pesados (imagens originais e vídeos) ficam salvos no Telegram e servidos pelo CDN, sem consumir espaço do repositório Git.

---

## Como Publicar (Deploy em 2 minutos)

### Pré-requisitos
1. Uma conta gratuita no [Cloudflare](https://dash.cloudflare.com/).
2. Node.js instalado (para executar `npx wrangler`).
3. Um Bot criado no Telegram via [@BotFather](https://t.me/BotFather) (guarde o Token gerado).

---

### Passo 1: Fazer o Login no Cloudflare
No terminal, dentro desta pasta:
```bash
npx wrangler login
```

### Passo 2: Configurar o Token Secreto do Bot
Execute o comando abaixo e cole o Token do seu Bot quando solicitado:
```bash
npx wrangler secret put BOT_TOKEN
```

### Passo 3: Publicar o Worker
```bash
npx wrangler deploy
```

O Wrangler exibirá a URL do seu Worker, por exemplo:
`https://wallpapers-foss-cdn.<seu-subdominio>.workers.dev`

---

## Endpoints Disponíveis

- `GET /health`: Verifica se o CDN está operacional.
- `GET /file/<file_id>`: Baixa a imagem ou vídeo original em alta qualidade do Telegram com Edge Cache.
- `GET /raw/<path>`: Proxy de fallback com cache para papéis de parede existentes no repositório.
