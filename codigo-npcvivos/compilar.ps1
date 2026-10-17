# Recompila o plugin NpcVivos e coloca o .jar na pasta plugins do servidor.
# Requer o JDK 25 (com javac): https://adoptium.net/temurin/releases/?version=25
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$javac = 'javac'
$jar = 'jar'
if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\javac.exe")) {
    $javac = "$env:JAVA_HOME\bin\javac.exe"
    $jar = "$env:JAVA_HOME\bin\jar.exe"
}
if (-not (Get-Command $javac -ErrorAction SilentlyContinue)) {
    Write-Host 'javac nao encontrado. Instale o JDK 25 e tente de novo.' -ForegroundColor Red
    exit 1
}
if (-not (Test-Path '..\libraries')) {
    Write-Host 'A pasta libraries ainda nao existe. Inicie o servidor uma vez antes de compilar.' -ForegroundColor Red
    exit 1
}

$classpath = @(Get-ChildItem -Recurse -Filter *.jar '..\libraries' | ForEach-Object { $_.FullName })
$classpath += @(Get-ChildItem '..\plugins\VaultUnlocked-*.jar', '..\plugins\PlaceholderAPI-*.jar' | ForEach-Object { $_.FullName })

$saida = Join-Path $PSScriptRoot 'build'
if (Test-Path $saida) { Remove-Item -Recurse -Force $saida }
New-Item -ItemType Directory $saida | Out-Null

# Arquivo de argumentos (evita o limite de tamanho da linha de comando do Windows)
$argumentos = Join-Path $saida 'argumentos.txt'
$linhas = @('-encoding', 'UTF-8', '--release', '21', '-d', "`"$($saida -replace '\\', '/')`"", '-cp', "`"$(($classpath -join ';') -replace '\\', '/')`"")
$linhas += Get-ChildItem -Recurse -Filter *.java 'src' | ForEach-Object { "`"$($_.FullName -replace '\\', '/')`"" }
[System.IO.File]::WriteAllLines($argumentos, $linhas)

& $javac "@$argumentos"
if ($LASTEXITCODE -ne 0) { Write-Host 'ERRO na compilacao.' -ForegroundColor Red; exit 1 }
Remove-Item $argumentos
# Recursos com as subpastas (dialogos, missoes, faccoes)
Copy-Item 'recursos\*' $saida -Recurse
& $jar --create --file '..\plugins\NpcVivos.jar' -C $saida .
Remove-Item -Recurse -Force $saida
Write-Host 'Plugin compilado: plugins\NpcVivos.jar (reinicie o servidor para aplicar)' -ForegroundColor Green
