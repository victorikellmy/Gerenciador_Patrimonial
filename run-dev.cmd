@echo off
rem Sobe o Gerenciador Patrimonial localmente (perfil dev: H2 em memoria + seed de
rem demonstracao da frota). Usa a pasta temporaria curta e o JDK 21, sem os quais o
rem Gradle falha nesta maquina ("Unable to establish loopback connection").
if not exist C:\gtmp mkdir C:\gtmp
set "TEMP=C:\gtmp"
set "TMP=C:\gtmp"
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
cd /d "C:\Users\victorikellmy\IdeaProjects\Gerenciador_Patrimonial"
call "C:\Users\victorikellmy\IdeaProjects\Gerenciador_Patrimonial\gradlew.bat" bootRun --no-daemon --console=plain
