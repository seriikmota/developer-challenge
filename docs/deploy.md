# Deploy contínuo no servidor (OCI + Cloudflare)

Cada push na branch `teste-pleno` que passa em todas as verificações vai para produção sem passo manual:

```
push em teste-pleno -> CI: backend, frontend, compose
                    -> release: imagens linux/arm64 no GHCR, com a tag igual ao SHA do commit
                    -> deploy: runner do GitHub -> SSH na VM -> deploy.sh
                       (baixa as imagens, faz backup, sobe, confere a saúde; se falhar, volta a versão anterior)
```

| Peça | Arquivo |
|---|---|
| Encadeamento e condições | jobs `release` e `deploy` no fim do [`ci.yml`](../.github/workflows/ci.yml) |
| Imagens arm64 | [`.github/workflows/release.yml`](../.github/workflows/release.yml) (workflow reutilizável) |
| Deploy por SSH | [`.github/workflows/deploy.yml`](../.github/workflows/deploy.yml) (reutilizável e manual) |
| Script executado na VM | [`deploy.sh`](../deploy.sh) |
| Pilha de produção | [`docker-compose.prod.yml`](../docker-compose.prod.yml) |
| Variáveis do servidor | seção "Production server only" do [`.env.example`](../.env.example) |

## Como cada parte se protege

- **Só chega ao servidor o que passou no CI.** Os jobs `release` e `deploy` dependem dos três jobs de verificação e só rodam quando estas quatro condições valem juntas:
  1. o evento é um `push`;
  2. a branch é `teste-pleno`;
  3. o repositório é este;
  4. a variável `DEPLOY_ENABLED` vale `true`.

  Pull requests, inclusive de forks, nunca chegam a esses jobs. Sem a variável, os jobs aparecem como pulados e o CI continua verde.
- **Nada na VM executa código de workflow.** O deploy roda num runner do GitHub e só entra na VM por SSH para rodar o `deploy.sh` da versão aprovada. Por isso o repositório pode ser público: um runner próprio instalado na VM permitiria executar comandos no servidor por meio de um pull request.
- **A chave SSH fica protegida.** Ela fica num environment `production`, cujos segredos nunca são entregues a runs de pull request. A chave é exclusiva do deploy e serve para um usuário sem `sudo`. O host é fixado em `known_hosts`, e a chave é apagada do runner ao final.
- **SSH sem porta aberta (opcional).** Com um service token do Cloudflare Access, o SSH passa pelo Cloudflare Tunnel (`cloudflared access ssh`), e a porta 22 pode ficar fechada para a internet.
- **Rollback automático.**
  - Antes de trocar a versão, o `deploy.sh` grava a tag atual e faz um `pg_dump`, mantendo os 5 mais recentes em `backup/`.
  - Se algum serviço não ficar saudável em 300 segundos, ele volta a tag anterior, sobe de novo e deixa o workflow vermelho.
- **As migrations só avançam.** O Flyway roda quando o back-end sobe, e o rollback das imagens **não desfaz o banco**. Por isso, toda migration nova precisa funcionar também com a versão anterior do código. Se for preciso voltar o banco, use o dump de `backup/`.
- **Um deploy por vez.** O próximo espera na fila, e um push novo não cancela um deploy em andamento.

## Preparar o servidor (uma vez)

A VM já tem Docker e roda outros projetos atrás do Cloudflare Tunnel. Para este projeto:

### 1. Usuário e chave do deploy

```bash
# na VM
sudo useradd -m -s /bin/bash purchase
sudo usermod -aG docker purchase
sudo install -d -o purchase -g purchase -m 750 /opt/purchase

# no seu computador: uma chave só para este deploy, sem senha
ssh-keygen -t ed25519 -N '' -C purchase-deploy -f purchase_deploy_key

# na VM: autoriza a chave com "restrict" (sem encaminhamento de portas, sem agente, sem X11)
sudo install -d -o purchase -g purchase -m 700 /home/purchase/.ssh
echo "restrict $(cat purchase_deploy_key.pub)" | sudo tee -a /home/purchase/.ssh/authorized_keys
sudo chown purchase:purchase /home/purchase/.ssh/authorized_keys && sudo chmod 600 /home/purchase/.ssh/authorized_keys
```

O grupo `docker` dá poder equivalente ao de root. Por isso a chave fica só no environment protegido do GitHub e não é usada para mais nada.

### 2. Segredos do servidor

Copie a seção "Production server only" do `.env.example` para `/opt/purchase/.env`, com `chmod 600`, e preencha:

- `POSTGRES_PASSWORD`: gere com `openssl rand -base64 24`;
- `JWT_SECRET`: gere com `openssl rand -base64 48`;
- `COMPOSE_PROFILES=tunnel`;
- `CLOUDFLARE_TUNNEL_TOKEN`;
- opcionalmente, `TELEGRAM_BOT_TOKEN` e `TELEGRAM_CHAT_ID`.

### 3. Cloudflare

1. No Zero Trust, abra Networks → Tunnels, crie o túnel `purchase` e copie o token para o `.env`.
2. Hostname público: `compras.erikmota.dev` apontando para `http://frontend:80`.
3. Opcional, SSH pelo túnel:
   - hostname `ssh-purchase.erikmota.dev` apontando para `ssh://host.docker.internal:22`;
   - em Access → Applications, uma aplicação self-hosted para esse hostname, com política **Service Auth**;
   - em Access → Service Auth, crie o service token e guarde o Client ID e o Secret.
4. Recomendado nas regras do domínio:
   - limite de tentativas em `POST /api/auth/login`;
   - sem cache em `/api/*`;
   - HTTPS forçado.

### 4. GitHub

1. **Environment `production`** (Settings → Environments). Em "Deployment branches and tags", limite à branch `teste-pleno`. Crie estes segredos:

   | Segredo | Valor |
   |---|---|
   | `SSH_HOST` | IP da VM (SSH direto) ou `ssh-purchase.erikmota.dev` (pelo Cloudflare) |
   | `SSH_USER` | `purchase` |
   | `SSH_PORT` | opcional, padrão `22` |
   | `SSH_KEY` | conteúdo de `purchase_deploy_key` (a chave **privada**) |
   | `SSH_KNOWN_HOSTS` | na VM: `ssh-keyscan -t ed25519 localhost \| sed "s/^localhost/<SSH_HOST>/"` |
   | `CF_ACCESS_CLIENT_ID` e `CF_ACCESS_CLIENT_SECRET` | só se o SSH for pelo Cloudflare. Sem eles, o SSH é direto. |

2. **Variáveis do repositório** (Settings → Secrets and variables → Actions → Variables):
   - `PUBLIC_URL=https://compras.erikmota.dev` (teste de saúde depois do deploy);
   - `DEPLOY_ENABLED=true` (crie por último);
   - opcional: `DEPLOY_DIR`, padrão `/opt/purchase`.

### 5. Primeiro deploy

1. Com `DEPLOY_ENABLED=true`, faça um push em `teste-pleno`.
2. Depois do primeiro `release`, em Packages, deixe `developer-challenge-backend` e `developer-challenge-frontend` **públicas**. A outra opção é rodar `docker login ghcr.io` como o usuário `purchase`, com um token `read:packages`.
3. Se o primeiro deploy falhou porque as imagens ainda eram privadas, reexecute o job `Deploy` daquele run ("Re-run failed jobs").

**Ovo e galinha do SSH pelo Cloudflare:** o túnel deste projeto só existe depois do primeiro deploy. Faça o primeiro deploy por SSH direto, ou por um túnel que já existe na VM. Depois troque `SSH_HOST` e adicione o service token.

## Operação

| Tarefa | Como |
|---|---|
| Deploy | Push em `teste-pleno` (acompanhe com `gh run watch`) |
| Reimplantar uma versão | Reexecute o job `Deploy` do run do CI daquele commit: `gh run rerun <run-id> --failed`, ou o botão "Re-run jobs" |
| Voltar para um commit anterior | `gh workflow run deploy.yml --ref teste-pleno -f sha=<SHA completo>`. As imagens desse SHA precisam existir no GHCR, e o GitHub só aceita o disparo manual quando o `deploy.yml` também está na branch padrão (`main`). |
| Logs | `cd /opt/purchase && docker compose logs -f backend` |
| Estado | `docker compose ps` (todos `healthy`) |
| Restaurar um dump | `gunzip -c backup/<arquivo>.sql.gz \| docker compose exec -T postgres psql -U purchase purchase` |
| Desligar o deploy automático | Variável `DEPLOY_ENABLED=false` |

## Demonstração pública

O seed cria usuários com senhas conhecidas (veja o `SOLUTION.md`), inclusive dois `ADMIN`. Para uma demonstração aberta, isso é intencional. Se alguém bagunçar os dados, recrie a base do zero:

```bash
cd /opt/purchase && docker compose down -v && docker compose up -d --wait
```

O comando apaga o volume do banco, e o seed roda de novo na subida. Pode ser agendado no `cron` para rodar toda noite.
