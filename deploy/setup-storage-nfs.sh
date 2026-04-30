#!/usr/bin/env bash
# =============================================================================
# Configura o servidor de armazenamento 192.168.1.35 como exportador NFS para
# os anexos do sistema. Roda UMA VEZ no servidor de armazenamento (não no app).
#
# Uso (no 192.168.1.35, como root):
#   sudo ./setup-storage-nfs.sh
# =============================================================================
set -euo pipefail

EXPORT_DIR="/srv/patrimonio/uploads"
APP_HOST="192.168.1.25"

if [[ $EUID -ne 0 ]]; then
  echo "ERRO: rode como root (sudo)."
  exit 1
fi

# 1) Pacotes
if   command -v apt-get >/dev/null; then
  apt-get update -y
  apt-get install -y nfs-kernel-server
elif command -v dnf >/dev/null; then
  dnf install -y nfs-utils
  systemctl enable --now nfs-server
else
  echo "ERRO: gerenciador de pacotes não suportado."
  exit 1
fi

# 2) Diretório com perms compatíveis com o usuário 'app' (UID 1000) do container
mkdir -p "$EXPORT_DIR"
chown -R 1000:1000 "$EXPORT_DIR"
chmod 750 "$EXPORT_DIR"

# 3) /etc/exports — só permite leitura/escrita do servidor de aplicação
EXPORT_LINE="$EXPORT_DIR $APP_HOST(rw,sync,no_subtree_check,no_root_squash)"
if ! grep -qF "$EXPORT_LINE" /etc/exports 2>/dev/null; then
  echo "$EXPORT_LINE" >> /etc/exports
  echo ">> Linha adicionada em /etc/exports:"
  echo "   $EXPORT_LINE"
fi

# 4) Aplica + ativa o serviço
exportfs -ra
systemctl enable --now nfs-server || systemctl restart nfs-kernel-server

# 5) Verifica
echo ">> Exports ativos:"
exportfs -v

cat <<EOF

>> Pronto. Teste do servidor de aplicação (192.168.1.25):
   showmount -e 192.168.1.35
   sudo mount -t nfs 192.168.1.35:$EXPORT_DIR /mnt && ls /mnt && sudo umount /mnt

>> Firewall: libere as portas NFS (TCP/UDP 2049) entre os dois hosts.
EOF
