# -*- coding: utf-8 -*-
"""
Gera o PDF do plano de implementacao da topologia de 2 servidores
Projeto: Gerenciador Patrimonial
"""
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import cm, mm
from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER, TA_LEFT, TA_JUSTIFY
from reportlab.platypus import (
    SimpleDocTemplate, Paragraph, Spacer, PageBreak, Table, TableStyle,
    Preformatted, KeepTogether, Image, HRFlowable, ListFlowable, ListItem
)
from reportlab.pdfgen import canvas
from reportlab.platypus.tableofcontents import TableOfContents
from datetime import datetime

OUTPUT = r"C:\Users\victor\IdeaProjects\Gerenciador_Patrimonial\plano-implementacao-patrimonial.pdf"

# Paleta de cores
NAVY = colors.HexColor("#1E3A5F")
NAVY_LIGHT = colors.HexColor("#2C5282")
ORANGE = colors.HexColor("#D97706")
RED = colors.HexColor("#B91C1C")
GREEN = colors.HexColor("#15803D")
GRAY_BG = colors.HexColor("#F3F4F6")
GRAY_BORDER = colors.HexColor("#D1D5DB")
CODE_BG = colors.HexColor("#1F2937")
CODE_FG = colors.HexColor("#F9FAFB")

# ===== Estilos =====
styles = getSampleStyleSheet()

styles.add(ParagraphStyle(
    name='CoverTitle', fontName='Helvetica-Bold', fontSize=28,
    textColor=NAVY, alignment=TA_CENTER, spaceAfter=12, leading=34
))
styles.add(ParagraphStyle(
    name='CoverSubtitle', fontName='Helvetica', fontSize=16,
    textColor=NAVY_LIGHT, alignment=TA_CENTER, spaceAfter=24
))
styles.add(ParagraphStyle(
    name='CoverInfo', fontName='Helvetica', fontSize=11,
    textColor=colors.black, alignment=TA_CENTER, spaceAfter=6
))
styles.add(ParagraphStyle(
    name='H1', fontName='Helvetica-Bold', fontSize=18,
    textColor=NAVY, spaceBefore=18, spaceAfter=10, leading=22,
    borderPadding=(0,0,4,0), borderColor=NAVY, borderWidth=0
))
styles.add(ParagraphStyle(
    name='H2', fontName='Helvetica-Bold', fontSize=14,
    textColor=NAVY_LIGHT, spaceBefore=12, spaceAfter=6, leading=18
))
styles.add(ParagraphStyle(
    name='H3', fontName='Helvetica-Bold', fontSize=11,
    textColor=colors.black, spaceBefore=8, spaceAfter=4, leading=14
))
styles.add(ParagraphStyle(
    name='Body', fontName='Helvetica', fontSize=10,
    textColor=colors.black, alignment=TA_JUSTIFY, leading=14, spaceAfter=6
))
styles.add(ParagraphStyle(
    name='BodySmall', fontName='Helvetica', fontSize=9,
    textColor=colors.black, alignment=TA_LEFT, leading=12, spaceAfter=4
))
styles.add(ParagraphStyle(
    name='CodeBlock', fontName='Courier', fontSize=8,
    textColor=CODE_FG, backColor=CODE_BG, leading=10,
    leftIndent=8, rightIndent=8, spaceBefore=4, spaceAfter=8,
    borderPadding=(6,8,6,8)
))
styles.add(ParagraphStyle(
    name='Inline', fontName='Courier', fontSize=9,
    textColor=colors.black, backColor=GRAY_BG
))
styles.add(ParagraphStyle(
    name='Warning', fontName='Helvetica-Bold', fontSize=10,
    textColor=RED, leading=14, spaceAfter=6,
    backColor=colors.HexColor("#FEF2F2"),
    borderColor=RED, borderWidth=1, borderPadding=8
))
styles.add(ParagraphStyle(
    name='Info', fontName='Helvetica', fontSize=10,
    textColor=NAVY, leading=14, spaceAfter=6,
    backColor=colors.HexColor("#EFF6FF"),
    borderColor=NAVY_LIGHT, borderWidth=1, borderPadding=8
))
styles.add(ParagraphStyle(
    name='Success', fontName='Helvetica', fontSize=10,
    textColor=GREEN, leading=14, spaceAfter=6,
    backColor=colors.HexColor("#F0FDF4"),
    borderColor=GREEN, borderWidth=1, borderPadding=8
))
styles.add(ParagraphStyle(
    name='Footer', fontName='Helvetica', fontSize=8,
    textColor=colors.HexColor("#6B7280"), alignment=TA_CENTER
))


def code_block(text):
    """Bloco de codigo com fundo escuro - cada linha um Paragraph nao quebra colorido."""
    safe = (text.replace('&', '&amp;')
                .replace('<', '&lt;')
                .replace('>', '&gt;'))
    return Preformatted(safe, ParagraphStyle(
        name='_code', fontName='Courier', fontSize=8,
        textColor=CODE_FG, backColor=CODE_BG, leading=10,
        leftIndent=0, rightIndent=0, spaceBefore=4, spaceAfter=8,
        borderPadding=(6,8,6,8)
    ))


def info_box(text, kind='info'):
    style_map = {
        'info': 'Info',
        'warning': 'Warning',
        'success': 'Success',
    }
    return Paragraph(text, styles[style_map[kind]])


def hr():
    return HRFlowable(width="100%", thickness=0.5, color=GRAY_BORDER,
                      spaceBefore=4, spaceAfter=8)


def page_break():
    return PageBreak()


def make_table(data, col_widths=None, header=True):
    t = Table(data, colWidths=col_widths, repeatRows=1 if header else 0)
    style = [
        ('FONTNAME', (0,0), (-1,-1), 'Helvetica'),
        ('FONTSIZE', (0,0), (-1,-1), 9),
        ('VALIGN', (0,0), (-1,-1), 'TOP'),
        ('TEXTCOLOR', (0,0), (-1,-1), colors.black),
        ('GRID', (0,0), (-1,-1), 0.5, GRAY_BORDER),
        ('LEFTPADDING', (0,0), (-1,-1), 6),
        ('RIGHTPADDING', (0,0), (-1,-1), 6),
        ('TOPPADDING', (0,0), (-1,-1), 4),
        ('BOTTOMPADDING', (0,0), (-1,-1), 4),
    ]
    if header:
        style.extend([
            ('BACKGROUND', (0,0), (-1,0), NAVY),
            ('TEXTCOLOR', (0,0), (-1,0), colors.white),
            ('FONTNAME', (0,0), (-1,0), 'Helvetica-Bold'),
        ])
        style.append(('ROWBACKGROUNDS', (0,1), (-1,-1), [colors.white, GRAY_BG]))
    t.setStyle(TableStyle(style))
    return t


# ===== Header / Footer =====
def on_page(canvas_obj, doc):
    canvas_obj.saveState()
    # Footer
    canvas_obj.setFont('Helvetica', 8)
    canvas_obj.setFillColor(colors.HexColor("#6B7280"))
    canvas_obj.drawString(2*cm, 1.2*cm,
        "Gerenciador Patrimonial - Plano de Implementacao")
    canvas_obj.drawRightString(A4[0]-2*cm, 1.2*cm, f"Pagina {doc.page}")
    # Linha do header
    if doc.page > 1:
        canvas_obj.setStrokeColor(NAVY)
        canvas_obj.setLineWidth(2)
        canvas_obj.line(2*cm, A4[1]-1.5*cm, A4[0]-2*cm, A4[1]-1.5*cm)
        canvas_obj.setFont('Helvetica-Bold', 9)
        canvas_obj.setFillColor(NAVY)
        canvas_obj.drawString(2*cm, A4[1]-1.3*cm, "GERENCIADOR PATRIMONIAL")
        canvas_obj.setFont('Helvetica', 9)
        canvas_obj.setFillColor(colors.HexColor("#6B7280"))
        canvas_obj.drawRightString(A4[0]-2*cm, A4[1]-1.3*cm,
            "Topologia 2 Servidores - LAN Corporativa")
    canvas_obj.restoreState()


# ===== Construcao do conteudo =====
story = []

# ===== CAPA =====
story.append(Spacer(1, 4*cm))
story.append(Paragraph("PLANO DE IMPLEMENTACAO", styles['CoverTitle']))
story.append(Paragraph("Topologia de 2 Servidores em LAN Corporativa", styles['CoverSubtitle']))
story.append(Spacer(1, 1*cm))

# Caixa de info na capa
info_data = [
    ['Projeto', 'Gerenciador Patrimonial'],
    ['Stack', 'Java 21 + Spring Boot 3.3.5 + PostgreSQL 16'],
    ['Servidor BD', '192.168.1.35  (PostgreSQL + NFS)'],
    ['Servidor APP', '192.168.1.25  (Spring Boot + Nginx)'],
    ['Rede', '192.168.1.0/24  (LAN Interna)'],
    ['Data', datetime.now().strftime('%d/%m/%Y')],
]
t = Table(info_data, colWidths=[5*cm, 9*cm])
t.setStyle(TableStyle([
    ('FONTNAME', (0,0), (0,-1), 'Helvetica-Bold'),
    ('FONTNAME', (1,0), (1,-1), 'Helvetica'),
    ('FONTSIZE', (0,0), (-1,-1), 11),
    ('TEXTCOLOR', (0,0), (0,-1), NAVY),
    ('TEXTCOLOR', (1,0), (1,-1), colors.black),
    ('LEFTPADDING', (0,0), (-1,-1), 12),
    ('RIGHTPADDING', (0,0), (-1,-1), 12),
    ('TOPPADDING', (0,0), (-1,-1), 8),
    ('BOTTOMPADDING', (0,0), (-1,-1), 8),
    ('LINEABOVE', (0,0), (-1,0), 1, NAVY),
    ('LINEBELOW', (0,-1), (-1,-1), 1, NAVY),
    ('VALIGN', (0,0), (-1,-1), 'MIDDLE'),
]))
story.append(t)

story.append(Spacer(1, 3*cm))
story.append(Paragraph("Documento de referencia para execucao em campo", styles['CoverInfo']))
story.append(Paragraph("Tempo estimado: 1h30 a 2h", styles['CoverInfo']))
story.append(page_break())


# ===== INDICE =====
story.append(Paragraph("Indice", styles['H1']))
story.append(hr())
toc_data = [
    ['1.', 'Topologia Final', '3'],
    ['2.', 'Pre-requisitos (Confirmar antes)', '4'],
    ['3.', 'Etapa 1 - Servidor BD (192.168.1.35)', '5'],
    ['4.', 'Etapa 2 - Servidor App (192.168.1.25)', '8'],
    ['5.', 'Etapa 3 - Validacao Final', '12'],
    ['6.', 'Etapa 4 - Hardening Adicional', '13'],
    ['7.', 'Scripts Prontos (Anexo)', '14'],
    ['8.', 'Plano B - Problemas Comuns', '17'],
    ['9.', 'Cartao de Referencia Rapida', '18'],
]
t = Table(toc_data, colWidths=[1*cm, 13*cm, 2*cm])
t.setStyle(TableStyle([
    ('FONTNAME', (0,0), (-1,-1), 'Helvetica'),
    ('FONTSIZE', (0,0), (-1,-1), 11),
    ('TEXTCOLOR', (0,0), (-1,-1), colors.black),
    ('FONTNAME', (0,0), (0,-1), 'Helvetica-Bold'),
    ('TEXTCOLOR', (0,0), (0,-1), NAVY),
    ('ALIGN', (2,0), (2,-1), 'RIGHT'),
    ('TEXTCOLOR', (2,0), (2,-1), colors.HexColor("#6B7280")),
    ('TOPPADDING', (0,0), (-1,-1), 6),
    ('BOTTOMPADDING', (0,0), (-1,-1), 6),
    ('LINEBELOW', (0,0), (-1,-1), 0.25, GRAY_BORDER),
]))
story.append(t)
story.append(page_break())


# ===== 1. TOPOLOGIA =====
story.append(Paragraph("1. Topologia Final", styles['H1']))
story.append(hr())

story.append(Paragraph(
    "A arquitetura concentra <b>todo o estado</b> (banco de dados + arquivos de upload) "
    "no servidor 192.168.1.35, deixando o servidor de aplicacao 192.168.1.25 totalmente "
    "<b>stateless</b> - facil de recriar em minutos se houver problema.",
    styles['Body']))

story.append(Spacer(1, 6))

# Diagrama ASCII em code block
diagrama = """
                    LAN 192.168.1.0/24
                            |
       +--------------------+--------------------+
       |                                         |
       v                                         v
+----------------------+              +------------------------+
|  192.168.1.25  APP   |              |  192.168.1.35  BD      |
|  ------------------  |              |  --------------------  |
|  Nginx (80/443)      |  <-- LAN --> |  PostgreSQL 16 (5432)  |
|  Spring Boot (8080)  |   privada    |  - escuta SO em .35    |
|  - bind 127.0.0.1    |              |  - SCRAM-SHA-256       |
|  Java 21 / systemd   |  <-- NFS --> |  NFS export uploads/   |
|  Mount /uploads      |   2049,111   |  Backup diario 02:00   |
|  UFW: 22, 80, 443    |              |  UFW: 22, 5432, 2049   |
+----------------------+              +------------------------+
       ^                                         ^
       |                                         |
       +---- Apenas IP do admin via SSH ---------+
"""
story.append(code_block(diagrama))

story.append(Paragraph("Principios da arquitetura", styles['H2']))
principios = [
    ['Isolamento', 'Banco sem IP publico; so aceita conexao da aplicacao via LAN'],
    ['Stateless', 'Servidor de app pode ser recriado sem perda de dados'],
    ['Single source of truth', 'Estado (DB + arquivos) concentrado em um servidor'],
    ['Defense in depth', 'UFW + pg_hba.conf + bind interface + senha forte'],
    ['Backup separado', 'Dump diario + copia externa (NAO no mesmo servidor apenas)'],
]
story.append(make_table(
    [['Principio', 'Como se aplica']] + principios,
    col_widths=[5*cm, 11*cm]
))
story.append(page_break())


# ===== 2. PRE-REQUISITOS =====
story.append(Paragraph("2. Pre-requisitos - Confirmar HOJE antes de ir", styles['H1']))
story.append(hr())

story.append(info_box(
    "<b>IMPORTANTE:</b> Nao saia de casa amanha sem todos esses itens confirmados. "
    "Faltar um pode te travar la dentro.", 'warning'))

prereq = [
    ['[  ]', 'SO de cada servidor (Ubuntu 22.04 ou 24.04 LTS recomendado)'],
    ['[  ]', 'Acesso SSH com sudo em ambos os servidores'],
    ['[  ]', 'JAR compilado: ./gradlew bootJar (gera build/libs/*.jar)'],
    ['[  ]', 'Senha forte gerada: openssl rand -base64 32 (anote em local seguro)'],
    ['[  ]', 'Confirmar servico de arquivos atual em 192.168.1.35 (Samba? NFS?)'],
    ['[  ]', 'Backup do servidor de arquivos antes de mexer'],
    ['[  ]', 'IP fixo do PC de admin (para liberar so ele no SSH)'],
    ['[  ]', 'Pendrive ou rede para transferir o JAR (~50-80 MB)'],
    ['[  ]', 'Acesso fisico ou KVM caso o SSH quebre durante config de UFW'],
    ['[  ]', 'Janela de manutencao acordada com a empresa (avisar usuarios)'],
]
story.append(make_table(
    [['', 'Item a confirmar']] + prereq,
    col_widths=[1.5*cm, 14.5*cm]
))

story.append(Paragraph("Comandos uteis na preparacao", styles['H2']))
story.append(Paragraph("Gerar a senha do banco (no seu PC):", styles['Body']))
story.append(code_block("openssl rand -base64 32\n# Exemplo de saida: kZ8mP3xQ9vL2nR7tY4wB6jH1uF5sA0eD"))

story.append(Paragraph("Compilar o JAR no projeto Spring Boot:", styles['Body']))
story.append(code_block("cd C:\\Users\\victor\\IdeaProjects\\Gerenciador_Patrimonial\n./gradlew bootJar\n# JAR gerado em: build/libs/Gerenciador_Patrimonial-0.0.1-SNAPSHOT.jar"))

story.append(Paragraph("Descobrir seu IP fixo no Windows:", styles['Body']))
story.append(code_block("ipconfig | findstr IPv4"))

story.append(page_break())


# ===== 3. ETAPA 1 - SERVIDOR BD =====
story.append(Paragraph("3. Etapa 1 - Servidor BD (192.168.1.35)", styles['H1']))
story.append(hr())

story.append(Paragraph("3.1 Atualizar e instalar PostgreSQL 16", styles['H2']))
story.append(code_block("""sudo apt update && sudo apt upgrade -y
sudo apt install -y curl ca-certificates gnupg lsb-release

# Repositorio oficial PostgreSQL (Ubuntu padrao pode estar desatualizado)
sudo install -d /usr/share/postgresql-common/pgdg
sudo curl -o /usr/share/postgresql-common/pgdg/apt.postgresql.org.asc \\
  https://www.postgresql.org/media/keys/ACCC4CF8.asc
echo "deb [signed-by=/usr/share/postgresql-common/pgdg/apt.postgresql.org.asc] \\
  https://apt.postgresql.org/pub/repos/apt $(lsb_release -cs)-pgdg main" | \\
  sudo tee /etc/apt/sources.list.d/pgdg.list

sudo apt update
sudo apt install -y postgresql-16 postgresql-contrib-16
sudo systemctl enable --now postgresql"""))

story.append(Paragraph("3.2 Criar banco e usuario da aplicacao", styles['H2']))
story.append(code_block("""sudo -u postgres psql <<'EOF'
CREATE USER patrimonial WITH PASSWORD 'COLE_AQUI_A_SENHA_GERADA';
CREATE DATABASE patrimonial OWNER patrimonial
  ENCODING 'UTF8'
  LC_COLLATE='pt_BR.UTF-8'
  LC_CTYPE='pt_BR.UTF-8'
  TEMPLATE template0;
\\c patrimonial
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT ALL ON SCHEMA public TO patrimonial;
EOF"""))

story.append(Paragraph("3.3 Hardening - postgresql.conf", styles['H2']))
story.append(Paragraph(
    "Edite <font face='Courier' size='9'>/etc/postgresql/16/main/postgresql.conf</font> "
    "(ajuste valores de RAM conforme servidor):",
    styles['Body']))
story.append(code_block("""# Escuta SO na interface da LAN, NUNCA 0.0.0.0
listen_addresses = '192.168.1.35'
port = 5432

# Tuning - referencia para 16 GB de RAM
shared_buffers = 4GB
effective_cache_size = 12GB
work_mem = 32MB
maintenance_work_mem = 1GB
random_page_cost = 1.1
effective_io_concurrency = 200
max_connections = 50
wal_compression = on
checkpoint_completion_target = 0.9

# Auditoria
password_encryption = scram-sha-256
log_connections = on
log_disconnections = on
log_min_duration_statement = 500
log_line_prefix = '%t [%p] %q%u@%d '"""))

story.append(Paragraph("3.4 Hardening - pg_hba.conf", styles['H2']))
story.append(Paragraph(
    "Edite <font face='Courier' size='9'>/etc/postgresql/16/main/pg_hba.conf</font> "
    "- substitua TODO o conteudo por:",
    styles['Body']))
story.append(code_block("""# TYPE  DATABASE       USER          ADDRESS              METHOD
local   all            postgres                           peer
local   all            all                                scram-sha-256
host    patrimonial    patrimonial   192.168.1.25/32      scram-sha-256
host    all            all           0.0.0.0/0            reject"""))

story.append(Paragraph("Aplicar e validar:", styles['Body']))
story.append(code_block("""sudo systemctl restart postgresql
sudo systemctl status postgresql
sudo ss -tlnp | grep 5432
# DEVE mostrar: 192.168.1.35:5432 (NAO 0.0.0.0:5432)"""))

story.append(page_break())

story.append(Paragraph("3.5 Firewall UFW (Servidor BD)", styles['H2']))
story.append(info_box(
    "<b>ATENCAO:</b> Antes de ativar UFW, confirme que voce vai liberar o seu IP de admin. "
    "Se errar o IP e ativar, voce perde o SSH e precisa de acesso fisico.", 'warning'))
story.append(code_block("""sudo apt install -y ufw
sudo ufw default deny incoming
sudo ufw default allow outgoing

# SSH apenas do PC do admin (TROCAR IP)
sudo ufw allow from 192.168.1.<SEU_IP_ADMIN> to any port 22 proto tcp comment 'SSH admin'

# PostgreSQL apenas do servidor de aplicacao
sudo ufw allow from 192.168.1.25 to any port 5432 proto tcp comment 'PostgreSQL app'

# NFS apenas do servidor de aplicacao
sudo ufw allow from 192.168.1.25 to any port 2049 proto tcp comment 'NFS'
sudo ufw allow from 192.168.1.25 to any port 111 comment 'rpcbind'

sudo ufw enable
sudo ufw status verbose"""))

story.append(Paragraph("3.6 Backup automatico (pg_dump diario)", styles['H2']))
story.append(code_block("""sudo mkdir -p /var/backups/postgresql
sudo chown postgres:postgres /var/backups/postgresql
sudo chmod 700 /var/backups/postgresql

sudo tee /usr/local/bin/backup-patrimonial.sh > /dev/null <<'EOF'
#!/bin/bash
set -euo pipefail
DEST=/var/backups/postgresql
DATE=$(date +%Y%m%d_%H%M%S)
sudo -u postgres pg_dump -Fc patrimonial > "$DEST/patrimonial_${DATE}.dump"
gzip "$DEST/patrimonial_${DATE}.dump"
find "$DEST" -name "patrimonial_*.dump.gz" -mtime +14 -delete
EOF

sudo chmod +x /usr/local/bin/backup-patrimonial.sh

# Crontab - backup todo dia as 02h
(sudo crontab -l 2>/dev/null; echo "0 2 * * * /usr/local/bin/backup-patrimonial.sh \\
  >> /var/log/backup-patrimonial.log 2>&1") | sudo crontab -

# Teste imediato
sudo /usr/local/bin/backup-patrimonial.sh
ls -lh /var/backups/postgresql/"""))

story.append(info_box(
    "<b>Backup no mesmo servidor NAO e backup.</b> Configure copia para storage externo "
    "(NAS, outro servidor, Backblaze B2). Sem isso, raio cai e perde tudo.", 'warning'))

story.append(Paragraph("3.7 NFS Share para uploads", styles['H2']))
story.append(code_block("""sudo apt install -y nfs-kernel-server
sudo mkdir -p /srv/patrimonial/uploads

# UID/GID 1500 deve bater com o usuario que vamos criar no servidor de app
sudo groupadd -g 1500 patrimonial 2>/dev/null || true
sudo useradd -u 1500 -g 1500 patrimonial 2>/dev/null || true
sudo chown -R 1500:1500 /srv/patrimonial/uploads
sudo chmod 750 /srv/patrimonial/uploads

# Exporta SO para o servidor de aplicacao
echo "/srv/patrimonial/uploads 192.168.1.25(rw,sync,no_subtree_check,no_root_squash)" | \\
  sudo tee -a /etc/exports

sudo exportfs -ra
sudo systemctl enable --now nfs-kernel-server
sudo exportfs -v"""))

story.append(page_break())


# ===== 4. ETAPA 2 - SERVIDOR APP =====
story.append(Paragraph("4. Etapa 2 - Servidor App (192.168.1.25)", styles['H1']))
story.append(hr())

story.append(Paragraph("4.1 Atualizar e dependencias basicas", styles['H2']))
story.append(code_block("""sudo apt update && sudo apt upgrade -y
sudo apt install -y curl ca-certificates gnupg nfs-common ufw nginx \\
                    openjdk-21-jre-headless postgresql-client-16

java -version  # deve mostrar 21.x"""))

story.append(Paragraph("4.2 Usuario e diretorios", styles['H2']))
story.append(code_block("""# UID 1500 PRECISA bater com o do NFS export do servidor BD
sudo groupadd -g 1500 patrimonial
sudo useradd -u 1500 -g 1500 -m -s /bin/bash -d /opt/patrimonial patrimonial

sudo mkdir -p /opt/patrimonial/{app,logs,config,uploads}
sudo chown -R patrimonial:patrimonial /opt/patrimonial"""))

story.append(Paragraph("4.3 Mount NFS do servidor BD", styles['H2']))
story.append(code_block("""# Teste manual primeiro
sudo mount -t nfs 192.168.1.35:/srv/patrimonial/uploads /opt/patrimonial/uploads
ls -la /opt/patrimonial/uploads
sudo touch /opt/patrimonial/uploads/.teste && sudo rm /opt/patrimonial/uploads/.teste

# Persistir no fstab
echo "192.168.1.35:/srv/patrimonial/uploads /opt/patrimonial/uploads nfs \\
  rw,hard,intr,_netdev 0 0" | sudo tee -a /etc/fstab

sudo systemctl daemon-reload"""))

story.append(Paragraph("4.4 Testar conexao com o banco ANTES do app", styles['H2']))
story.append(info_box(
    "Se este teste falhar, NAO continue. Volte e revise pg_hba.conf e UFW do servidor BD.",
    'warning'))
story.append(code_block("""PGPASSWORD='SENHA_GERADA' psql -h 192.168.1.35 -U patrimonial \\
  -d patrimonial -c '\\conninfo'

# Resposta esperada:
# You are connected to database "patrimonial" as user "patrimonial"
# on host "192.168.1.35" at port "5432"."""))

story.append(Paragraph("4.5 Deploy do JAR", styles['H2']))
story.append(code_block("""# Do seu PC ou servidor de build
# scp build/libs/Gerenciador_Patrimonial-0.0.1-SNAPSHOT.jar \\
#     admin@192.168.1.25:/tmp/

sudo mv /tmp/Gerenciador_Patrimonial-0.0.1-SNAPSHOT.jar /opt/patrimonial/app/app.jar
sudo chown patrimonial:patrimonial /opt/patrimonial/app/app.jar
sudo chmod 750 /opt/patrimonial/app/app.jar"""))

story.append(page_break())

story.append(Paragraph("4.6 Arquivo de variaveis de ambiente", styles['H2']))
story.append(Paragraph(
    "Crie <font face='Courier' size='9'>/opt/patrimonial/config/app.env</font> "
    "com permissao 600 (so o usuario le):", styles['Body']))
story.append(code_block("""sudo tee /opt/patrimonial/config/app.env > /dev/null <<'EOF'
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:postgresql://192.168.1.35:5432/patrimonial
DB_USER=patrimonial
DB_PASSWORD=COLE_AQUI_A_SENHA_GERADA
APP_STORAGE_PASTA_RAIZ=/opt/patrimonial/uploads
SERVER_PORT=8080
SERVER_ADDRESS=127.0.0.1
JAVA_OPTS=-Xms2g -Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 \\
          -XX:+HeapDumpOnOutOfMemoryError \\
          -XX:HeapDumpPath=/opt/patrimonial/logs/heapdump.hprof
EOF

sudo chown patrimonial:patrimonial /opt/patrimonial/config/app.env
sudo chmod 600 /opt/patrimonial/config/app.env"""))

story.append(info_box(
    "<b>Tuning de heap:</b> ajuste -Xmx para METADE da RAM do servidor. "
    "Servidor com 8GB -> -Xmx4g; com 16GB -> -Xmx6g; com 32GB -> -Xmx12g.", 'info'))

story.append(Paragraph("4.7 Servico systemd", styles['H2']))
story.append(code_block("""sudo tee /etc/systemd/system/patrimonial.service > /dev/null <<'EOF'
[Unit]
Description=Gerenciador Patrimonial - Spring Boot
After=network-online.target remote-fs.target
Wants=network-online.target remote-fs.target

[Service]
Type=simple
User=patrimonial
Group=patrimonial
WorkingDirectory=/opt/patrimonial/app
EnvironmentFile=/opt/patrimonial/config/app.env
ExecStart=/usr/bin/java $JAVA_OPTS -jar /opt/patrimonial/app/app.jar
SuccessExitStatus=143
Restart=on-failure
RestartSec=10
StandardOutput=append:/opt/patrimonial/logs/app.log
StandardError=append:/opt/patrimonial/logs/app.err.log

# Hardening
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/opt/patrimonial/logs /opt/patrimonial/uploads
ProtectKernelTunables=true
ProtectKernelModules=true
ProtectControlGroups=true

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable --now patrimonial
sudo systemctl status patrimonial
sudo journalctl -u patrimonial -f"""))

story.append(page_break())

story.append(Paragraph("4.8 Nginx como reverse proxy", styles['H2']))
story.append(code_block("""sudo tee /etc/nginx/sites-available/patrimonial > /dev/null <<'EOF'
upstream patrimonial_app {
    server 127.0.0.1:8080;
    keepalive 32;
}

server {
    listen 80 default_server;
    server_name patrimonial.local 192.168.1.25;

    client_max_body_size 30M;
    client_body_buffer_size 128k;

    gzip on;
    gzip_vary on;
    gzip_proxied any;
    gzip_types text/plain text/css text/javascript application/javascript
               application/json application/xml;

    access_log /var/log/nginx/patrimonial.access.log;
    error_log  /var/log/nginx/patrimonial.error.log warn;

    location / {
        proxy_pass http://patrimonial_app;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Connection "";
        proxy_buffering on;
        proxy_buffer_size 128k;
        proxy_buffers 4 256k;
        proxy_read_timeout 60s;
        proxy_send_timeout 60s;
    }

    location ~* \\.(?:css|js|jpg|jpeg|png|gif|ico|svg|woff2?)$ {
        proxy_pass http://patrimonial_app;
        proxy_cache_valid 200 30d;
        expires 30d;
        add_header Cache-Control "public, immutable";
    }
}
EOF

sudo ln -sf /etc/nginx/sites-available/patrimonial /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t
sudo systemctl reload nginx"""))

story.append(Paragraph("4.9 Firewall UFW (Servidor App)", styles['H2']))
story.append(code_block("""sudo ufw default deny incoming
sudo ufw default allow outgoing

# SSH so do admin
sudo ufw allow from 192.168.1.<SEU_IP_ADMIN> to any port 22 proto tcp \\
  comment 'SSH admin'

# HTTP/HTTPS para usuarios da rede interna
sudo ufw allow from 192.168.1.0/24 to any port 80 proto tcp comment 'HTTP LAN'
sudo ufw allow from 192.168.1.0/24 to any port 443 proto tcp comment 'HTTPS LAN'

# Spring Boot (8080) JAMAIS exposto - fica em 127.0.0.1 via SERVER_ADDRESS

sudo ufw enable
sudo ufw status verbose"""))

story.append(page_break())


# ===== 5. VALIDACAO =====
story.append(Paragraph("5. Etapa 3 - Validacao Final", styles['H1']))
story.append(hr())

story.append(Paragraph(
    "Execute os testes <b>na ordem</b>. Se algum falhar, NAO avance - corrija e teste de novo.",
    styles['Body']))

story.append(Paragraph("5.1 Banco respondendo do servidor de app", styles['H3']))
story.append(code_block("""PGPASSWORD='...' psql -h 192.168.1.35 -U patrimonial \\
  -d patrimonial -c 'SELECT 1;'"""))

story.append(Paragraph("5.2 NFS montado e gravavel", styles['H3']))
story.append(code_block("""sudo -u patrimonial touch /opt/patrimonial/uploads/.write-test && \\
sudo -u patrimonial rm /opt/patrimonial/uploads/.write-test"""))

story.append(Paragraph("5.3 Aplicacao rodando", styles['H3']))
story.append(code_block("""sudo systemctl is-active patrimonial
curl -I http://127.0.0.1:8080/
sudo journalctl -u patrimonial -n 100 --no-pager
# Procurar por: 'Started GerenciadorPatrimonialApplication'"""))

story.append(Paragraph("5.4 Nginx proxy funcionando", styles['H3']))
story.append(code_block("""curl -I http://192.168.1.25/
# Resposta esperada: 200 OK ou 302 (redirect para login)"""))

story.append(Paragraph("5.5 Migrations Flyway executadas", styles['H3']))
story.append(code_block("""PGPASSWORD='...' psql -h 192.168.1.35 -U patrimonial -d patrimonial \\
  -c 'SELECT version, description, success FROM flyway_schema_history \\
      ORDER BY installed_rank;'"""))

story.append(Paragraph("5.6 Acesso de outra maquina da LAN", styles['H3']))
story.append(Paragraph(
    "De um PC qualquer da rede, abra no navegador: <b>http://192.168.1.25/</b><br/>"
    "Tela de login do Spring Security deve aparecer.",
    styles['Body']))

story.append(Paragraph("5.7 Conexao DB BLOQUEADA do exterior (teste de seguranca)", styles['H3']))
story.append(code_block("""# De um PC qualquer da LAN, EXCETO o servidor de app:
nc -zv 192.168.1.35 5432
# Resposta esperada: 'Connection timed out' ou 'Connection refused'
# Se conectar -> firewall esta MAL configurado, corrigir antes de prosseguir"""))

story.append(info_box(
    "<b>So apos TODOS os 7 testes passarem</b>, considere o deploy concluido. "
    "Anote a data e a senha em local seguro (cofre de senhas, NAO em texto plano).",
    'success'))

story.append(page_break())


# ===== 6. HARDENING =====
story.append(Paragraph("6. Etapa 4 - Hardening Adicional", styles['H1']))
story.append(hr())

story.append(Paragraph(
    "Faca <b>depois</b> que o sistema esta rodando e validado. Nao tente fazer tudo no mesmo dia.",
    styles['Body']))

hardening = [
    ['SSH so por chave',
     'Editar /etc/ssh/sshd_config:\nPasswordAuthentication no\nPermitRootLogin no'],
    ['Fail2ban',
     'sudo apt install -y fail2ban\n(jails de SSH ja vem ativos por padrao)'],
    ['Updates automaticos',
     'sudo apt install -y unattended-upgrades\nsudo dpkg-reconfigure unattended-upgrades'],
    ['Logrotate dos logs do app',
     'Criar /etc/logrotate.d/patrimonial (ver anexo)'],
    ['HTTPS com cert interno',
     'mkcert para gerar certificado + adicionar bloco listen 443 ssl no Nginx'],
    ['Spring Actuator',
     'Adicionar spring-boot-starter-actuator e expor /actuator/health'],
    ['Monitoramento basico',
     'curl https://my-netdata.io/kickstart.sh | sh\n(gratuito, mostra tudo)'],
    ['Copia externa do backup',
     'rclone copy /var/backups/postgresql remote:patrimonial-backup\n(diaria via cron)'],
]
story.append(make_table(
    [['Item', 'Como aplicar']] + hardening,
    col_widths=[5*cm, 11*cm]
))

story.append(Paragraph("Logrotate - /etc/logrotate.d/patrimonial", styles['H2']))
story.append(code_block("""/opt/patrimonial/logs/*.log {
    daily
    rotate 14
    compress
    delaycompress
    missingok
    notifempty
    copytruncate
    su patrimonial patrimonial
}"""))

story.append(page_break())


# ===== 7. SCRIPTS PRONTOS =====
story.append(Paragraph("7. Scripts Prontos (Anexo)", styles['H1']))
story.append(hr())

story.append(info_box(
    "Esses scripts automatizam TODA a configuracao em cada servidor. "
    "Edite as 3 variaveis no topo, salve em /tmp/, e execute com sudo bash.",
    'info'))

story.append(Paragraph("7.1 Script para Servidor BD - setup-db.sh", styles['H2']))
story.append(code_block("""#!/bin/bash
set -euo pipefail

# === EDITE AQUI ===
DB_PASSWORD='COLE_SENHA_GERADA_AQUI'
APP_SERVER_IP='192.168.1.25'
ADMIN_IP='192.168.1.<SEU_IP>'
# ==================

echo "[1/6] Atualizando sistema..."
apt update && apt upgrade -y
apt install -y curl ca-certificates gnupg lsb-release ufw nfs-kernel-server

echo "[2/6] Instalando PostgreSQL 16..."
install -d /usr/share/postgresql-common/pgdg
curl -o /usr/share/postgresql-common/pgdg/apt.postgresql.org.asc \\
  https://www.postgresql.org/media/keys/ACCC4CF8.asc
echo "deb [signed-by=/usr/share/postgresql-common/pgdg/apt.postgresql.org.asc] \\
  https://apt.postgresql.org/pub/repos/apt $(lsb_release -cs)-pgdg main" \\
  > /etc/apt/sources.list.d/pgdg.list
apt update
apt install -y postgresql-16 postgresql-contrib-16 postgresql-client-16

echo "[3/6] Configurando banco..."
sudo -u postgres psql <<EOF
CREATE USER patrimonial WITH PASSWORD '${DB_PASSWORD}';
CREATE DATABASE patrimonial OWNER patrimonial ENCODING 'UTF8' TEMPLATE template0;
\\c patrimonial
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT ALL ON SCHEMA public TO patrimonial;
EOF

echo "[4/6] Hardening configs..."
PG_CONF=/etc/postgresql/16/main/postgresql.conf
PG_HBA=/etc/postgresql/16/main/pg_hba.conf

sed -i "s/^#\\?listen_addresses.*/listen_addresses = '192.168.1.35'/" $PG_CONF
sed -i "s/^#\\?password_encryption.*/password_encryption = scram-sha-256/" $PG_CONF
sed -i "s/^#\\?log_connections.*/log_connections = on/" $PG_CONF
sed -i "s/^#\\?log_min_duration_statement.*/log_min_duration_statement = 500/" $PG_CONF

cat > $PG_HBA <<EOF
local   all             postgres                              peer
local   all             all                                   scram-sha-256
host    patrimonial     patrimonial   ${APP_SERVER_IP}/32     scram-sha-256
host    all             all           0.0.0.0/0               reject
EOF

systemctl restart postgresql

echo "[5/6] NFS..."
mkdir -p /srv/patrimonial/uploads
groupadd -g 1500 patrimonial 2>/dev/null || true
useradd -u 1500 -g 1500 patrimonial 2>/dev/null || true
chown -R 1500:1500 /srv/patrimonial/uploads
chmod 750 /srv/patrimonial/uploads
echo "/srv/patrimonial/uploads ${APP_SERVER_IP}(rw,sync,no_subtree_check,no_root_squash)" \\
  >> /etc/exports
exportfs -ra
systemctl enable --now nfs-kernel-server

echo "[6/6] Firewall..."
ufw default deny incoming
ufw default allow outgoing
ufw allow from ${ADMIN_IP} to any port 22 proto tcp
ufw allow from ${APP_SERVER_IP} to any port 5432 proto tcp
ufw allow from ${APP_SERVER_IP} to any port 2049 proto tcp
ufw allow from ${APP_SERVER_IP} to any port 111
ufw --force enable

echo "============================================"
echo "OK - Servidor BD pronto."
echo "Proximo: setup-app.sh em 192.168.1.25"
echo "============================================" """))

story.append(page_break())

story.append(Paragraph("7.2 Script para Servidor APP - setup-app.sh", styles['H2']))
story.append(Paragraph(
    "Coloque o JAR em <font face='Courier' size='9'>/tmp/app.jar</font> antes de rodar.",
    styles['Body']))
story.append(code_block("""#!/bin/bash
set -euo pipefail

# === EDITE AQUI ===
DB_PASSWORD='COLE_MESMA_SENHA'
DB_SERVER_IP='192.168.1.35'
ADMIN_IP='192.168.1.<SEU_IP>'
JAR_SOURCE='/tmp/app.jar'
# ==================

echo "[1/7] Atualizando sistema..."
apt update && apt upgrade -y
apt install -y curl ca-certificates gnupg openjdk-21-jre-headless \\
               nginx nfs-common ufw postgresql-client-16

echo "[2/7] Usuario e diretorios..."
groupadd -g 1500 patrimonial 2>/dev/null || true
useradd -u 1500 -g 1500 -m -s /bin/bash -d /opt/patrimonial patrimonial 2>/dev/null || true
mkdir -p /opt/patrimonial/{app,logs,config,uploads}
chown -R patrimonial:patrimonial /opt/patrimonial

echo "[3/7] Mount NFS..."
mount -t nfs ${DB_SERVER_IP}:/srv/patrimonial/uploads /opt/patrimonial/uploads
echo "${DB_SERVER_IP}:/srv/patrimonial/uploads /opt/patrimonial/uploads nfs \\
  rw,hard,intr,_netdev 0 0" >> /etc/fstab

echo "[4/7] Testando conexao com banco..."
PGPASSWORD="${DB_PASSWORD}" psql -h ${DB_SERVER_IP} -U patrimonial \\
  -d patrimonial -c '\\conninfo' || {
  echo "FALHA na conexao com banco. Revisar pg_hba.conf e UFW."
  exit 1
}

echo "[5/7] Deploy do JAR..."
cp ${JAR_SOURCE} /opt/patrimonial/app/app.jar
chown patrimonial:patrimonial /opt/patrimonial/app/app.jar
chmod 750 /opt/patrimonial/app/app.jar

cat > /opt/patrimonial/config/app.env <<EOF
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:postgresql://${DB_SERVER_IP}:5432/patrimonial
DB_USER=patrimonial
DB_PASSWORD=${DB_PASSWORD}
APP_STORAGE_PASTA_RAIZ=/opt/patrimonial/uploads
SERVER_PORT=8080
SERVER_ADDRESS=127.0.0.1
JAVA_OPTS=-Xms2g -Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=200
EOF
chown patrimonial:patrimonial /opt/patrimonial/config/app.env
chmod 600 /opt/patrimonial/config/app.env

echo "[6/7] systemd service..."
cat > /etc/systemd/system/patrimonial.service <<'EOF'
[Unit]
Description=Gerenciador Patrimonial
After=network-online.target remote-fs.target
Wants=network-online.target remote-fs.target

[Service]
Type=simple
User=patrimonial
Group=patrimonial
WorkingDirectory=/opt/patrimonial/app
EnvironmentFile=/opt/patrimonial/config/app.env
ExecStart=/usr/bin/java $JAVA_OPTS -jar /opt/patrimonial/app/app.jar
Restart=on-failure
RestartSec=10
StandardOutput=append:/opt/patrimonial/logs/app.log
StandardError=append:/opt/patrimonial/logs/app.err.log
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ReadWritePaths=/opt/patrimonial/logs /opt/patrimonial/uploads

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable --now patrimonial

echo "[7/7] Nginx + UFW..."
cat > /etc/nginx/sites-available/patrimonial <<'EOF'
upstream patrimonial_app { server 127.0.0.1:8080; keepalive 32; }
server {
    listen 80 default_server;
    server_name _;
    client_max_body_size 30M;
    gzip on;
    gzip_types text/css application/javascript application/json;
    location / {
        proxy_pass http://patrimonial_app;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
EOF
ln -sf /etc/nginx/sites-available/patrimonial /etc/nginx/sites-enabled/
rm -f /etc/nginx/sites-enabled/default
nginx -t && systemctl reload nginx

ufw default deny incoming
ufw default allow outgoing
ufw allow from ${ADMIN_IP} to any port 22 proto tcp
ufw allow from 192.168.1.0/24 to any port 80 proto tcp
ufw allow from 192.168.1.0/24 to any port 443 proto tcp
ufw --force enable

echo "============================================"
echo "OK - Aguardando 30s para Spring subir..."
sleep 30
systemctl status patrimonial --no-pager
echo "Acesse: http://192.168.1.25/"
echo "Logs:   sudo journalctl -u patrimonial -f"
echo "============================================" """))

story.append(page_break())


# ===== 8. PLANO B =====
story.append(Paragraph("8. Plano B - Problemas Comuns", styles['H1']))
story.append(hr())

planB = [
    ['PostgreSQL 16 nao disponivel no apt',
     'Cair para postgresql-15 ou postgresql padrao do Ubuntu. Funciona igual.'],
    ['NFS nao monta',
     'Usar storage local em /opt/patrimonial/uploads e migrar depois. NAO bloqueia deploy.'],
    ['Flyway falha em alguma migration',
     'sudo journalctl -u patrimonial -n 200\nIdentifique a migration, corrija e reinicie.\nNAO use flyway repair sem entender.'],
    ['Porta 5432 bloqueada por firewall corporativo',
     'nc -zv 192.168.1.35 5432 do app server.\nSe falhar, falar com TI ANTES de tudo.'],
    ['Senha quebra o bash (caracteres especiais)',
     'openssl rand -base64 32 pode gerar / e + (problema em URL JDBC).\nGerar nova senha so alfanumerica + _ -'],
    ['UFW ativado errado e perdi SSH',
     'Acesso fisico ou KVM remoto.\nNo console: sudo ufw disable, depois reconfigurar.'],
    ['App nao sobe - OutOfMemoryError',
     'Servidor com pouca RAM.\nReduzir -Xmx2g no app.env e reiniciar.'],
    ['Login do Spring Security nao aparece',
     'Verificar se e 302 ou 200 com curl.\nVer logs do Nginx em /var/log/nginx/patrimonial.error.log'],
    ['NFS lento ou trava ao abrir arquivos',
     'Adicionar opcoes de mount: rsize=32768,wsize=32768,timeo=600 no fstab'],
    ['Servidor reinicia e app nao sobe sozinho',
     'Verificar se NFS montou antes:\nsudo systemctl list-units --failed\nsudo systemctl daemon-reload'],
]
story.append(make_table(
    [['Sintoma', 'Solucao']] + planB,
    col_widths=[6*cm, 10*cm]
))

story.append(page_break())


# ===== 9. CARTAO DE REFERENCIA =====
story.append(Paragraph("9. Cartao de Referencia Rapida", styles['H1']))
story.append(hr())

story.append(Paragraph("Comandos do dia-a-dia", styles['H2']))

cmds = [
    ['Status do app',         'sudo systemctl status patrimonial'],
    ['Reiniciar app',         'sudo systemctl restart patrimonial'],
    ['Ver logs em tempo real', 'sudo journalctl -u patrimonial -f'],
    ['Ver ultimos 200 logs',  'sudo journalctl -u patrimonial -n 200 --no-pager'],
    ['Status PostgreSQL',     'sudo systemctl status postgresql'],
    ['Status Nginx',          'sudo systemctl status nginx'],
    ['Reload Nginx',          'sudo nginx -t && sudo systemctl reload nginx'],
    ['Backup manual',         'sudo /usr/local/bin/backup-patrimonial.sh'],
    ['Listar backups',        'ls -lh /var/backups/postgresql/'],
    ['Conectar no banco',     'sudo -u postgres psql patrimonial'],
    ['Status UFW',            'sudo ufw status verbose'],
    ['Status NFS (BD)',       'sudo exportfs -v'],
    ['Verificar mount NFS',   'mount | grep patrimonial'],
    ['Espaco em disco',       'df -h /opt/patrimonial /var/backups'],
    ['Uso de RAM',            'free -h'],
    ['Top processos',         'top -u patrimonial'],
]
story.append(make_table(
    [['Acao', 'Comando']] + cmds,
    col_widths=[5*cm, 11*cm]
))

story.append(Paragraph("Portas usadas", styles['H2']))
ports = [
    ['22',   'SSH',         'Ambos servidores',     'So IP de admin'],
    ['80',   'HTTP',        '192.168.1.25',         'Toda LAN 192.168.1.0/24'],
    ['443',  'HTTPS',       '192.168.1.25',         'Toda LAN (apos cert TLS)'],
    ['5432', 'PostgreSQL',  '192.168.1.35',         'So 192.168.1.25'],
    ['8080', 'Spring Boot', '192.168.1.25',         'So localhost (127.0.0.1)'],
    ['2049', 'NFS',         '192.168.1.35',         'So 192.168.1.25'],
    ['111',  'rpcbind/NFS', '192.168.1.35',         'So 192.168.1.25'],
]
story.append(make_table(
    [['Porta', 'Servico', 'Servidor', 'Quem pode acessar']] + ports,
    col_widths=[1.8*cm, 3*cm, 4.5*cm, 6.7*cm]
))

story.append(Paragraph("Caminhos importantes", styles['H2']))
paths = [
    ['JAR da aplicacao',      '/opt/patrimonial/app/app.jar'],
    ['Variaveis de ambiente', '/opt/patrimonial/config/app.env'],
    ['Logs da aplicacao',     '/opt/patrimonial/logs/'],
    ['Uploads (NFS)',         '/opt/patrimonial/uploads/  ->  192.168.1.35:/srv/patrimonial/uploads'],
    ['Servico systemd',       '/etc/systemd/system/patrimonial.service'],
    ['Config Nginx',          '/etc/nginx/sites-available/patrimonial'],
    ['Logs Nginx',            '/var/log/nginx/patrimonial.access.log e .error.log'],
    ['Config PostgreSQL',     '/etc/postgresql/16/main/postgresql.conf'],
    ['Hosts permitidos PG',   '/etc/postgresql/16/main/pg_hba.conf'],
    ['Backups PostgreSQL',    '/var/backups/postgresql/'],
    ['Storage NFS (origem)',  '/srv/patrimonial/uploads/  no servidor 192.168.1.35'],
]
story.append(make_table(
    [['O que', 'Onde']] + paths,
    col_widths=[5*cm, 11*cm]
))

story.append(Spacer(1, 1*cm))
story.append(info_box(
    "<b>Boa sorte amanha!</b> Lembre-se: faca um passo de cada vez, valide antes de avancar, "
    "e nunca ative o UFW sem ter certeza do IP de admin liberado.",
    'success'))


# ===== Build do PDF =====
doc = SimpleDocTemplate(
    OUTPUT,
    pagesize=A4,
    leftMargin=2*cm,
    rightMargin=2*cm,
    topMargin=2*cm,
    bottomMargin=2*cm,
    title="Plano de Implementacao - Gerenciador Patrimonial",
    author="Arquitetura Cloud/DevOps",
    subject="Topologia 2 servidores LAN"
)

doc.build(story, onFirstPage=on_page, onLaterPages=on_page)

print(f"PDF gerado: {OUTPUT}")
