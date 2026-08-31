#!/usr/bin/env bash
# =============================================================================
# Deploy do Gerenciador Patrimonial no servidor de aplicação 192.168.1.25.
#
# Uso (na raiz do projeto, no servidor 192.168.1.25):
#   ./deploy/deploy.sh           # build + up
#   ./deploy/deploy.sh --rebuild # força reconstrução da imagem
#   ./deploy/deploy.sh --logs    # apenas tail nos logs
#
# Idempotente: pode rodar várias vezes; só age quando há mudança.
# =============================================================================
set -euo pipefail

cd "$(dirname "$0")/.."

REQUIRED_BINARIES=(docker)
for bin in "${REQUIRED_BINARIES[@]}"; do
  command -v "$bin" >/dev/null || { echo "ERRO: '$bin' não está instalado."; exit 1; }
done

# Garante que o cliente NFS está instalado (driver do compose precisa do mount.nfs).
if ! command -v mount.nfs >/dev/null && ! command -v mount.nfs4 >/dev/null; then
  cat <<EOF
ERRO: cliente NFS não encontrado.

  Debian/Ubuntu: sudo apt-get install -y nfs-common
  RHEL/Alma:     sudo dnf  install -y nfs-utils

EOF
  exit 1
fi

# .env obrigatório — protege contra subir sem senha de banco.
if [[ ! -f .env ]]; then
  echo "ERRO: arquivo .env ausente. Copie .env.example para .env e edite os valores."
  exit 1
fi

# Sanity check: NFS server alcançável.
if ! ping -c 1 -W 2 192.168.1.35 >/dev/null 2>&1; then
  echo "AVISO: 192.168.1.35 não respondeu ao ping. Verifique a rede antes de prosseguir."
  read -rp "Continuar mesmo assim? [y/N] " resp
  [[ "${resp:-N}" =~ ^[Yy]$ ]] || exit 1
fi

case "${1:-}" in
  --logs)
    docker compose logs -f --tail=200 app
    exit 0
    ;;
  --rebuild)
    docker compose build --no-cache app
    ;;
esac

echo ">> Subindo stack (db + app)..."
docker compose up -d --build

echo ">> Aguardando o app iniciar..."
for i in $(seq 1 30); do
  if curl -fsS http://localhost/actuator/health >/dev/null 2>&1 \
     || curl -fsS http://localhost/login            >/dev/null 2>&1; then
    echo ">> Aplicação respondendo."
    exit 0
  fi
  sleep 2
done

echo "AVISO: app ainda não respondeu. Logs recentes:"
docker compose logs --tail=80 app
exit 1
