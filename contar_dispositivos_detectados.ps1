# Script PowerShell para contar dispositivos detectados
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "CONTADOR DE DISPOSITIVOS DETECTADOS" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Este script muestra los dispositivos detectados" -ForegroundColor Yellow
Write-Host "y cuenta cuantos HR y CAD se conectan." -ForegroundColor Yellow
Write-Host ""
Write-Host "INSTRUCCIONES:" -ForegroundColor Green
Write-Host "  1. Ejecuta este script" -ForegroundColor White
Write-Host "  2. Inicia la app y comienza un entrenamiento" -ForegroundColor White
Write-Host "  3. Observa los dispositivos que aparecen" -ForegroundColor White
Write-Host "  4. Presiona Ctrl+C cuando quieras ver el resumen" -ForegroundColor White
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Buscar ADB
$adbPath = $null
$possiblePaths = @(
    "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    "$env:USERPROFILE\AppData\Local\Android\Sdk\platform-tools\adb.exe",
    "$env:ANDROID_HOME\platform-tools\adb.exe"
)

foreach ($path in $possiblePaths) {
    if (Test-Path $path) {
        $adbPath = $path
        break
    }
}

if ($null -eq $adbPath) {
    $adbPath = "adb"
}

Write-Host "Limpiando logs anteriores..." -ForegroundColor Yellow
& $adbPath logcat -c
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "DETECTANDO DISPOSITIVOS..." -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Contadores
$hrAnt = 0
$hrBle = 0
$cadAnt = 0
$cadBle = 0
$dispositivos = @()

# Archivo temporal
$tempFile = [System.IO.Path]::GetTempFileName()

try {
    # Iniciar logcat en segundo plano
    $logcatProcess = Start-Process -FilePath $adbPath -ArgumentList "logcat","-s","com.tuapp.tabatatrainer:*" -RedirectStandardOutput $tempFile -NoNewWindow -PassThru
    
    Write-Host "Capturando logs... (Presiona Ctrl+C para detener y ver resumen)" -ForegroundColor Yellow
    Write-Host ""
    
    # Leer archivo en tiempo real
    $reader = [System.IO.StreamReader]::new($tempFile)
    $buffer = New-Object System.Text.StringBuilder
    
    while ($true) {
        $line = $reader.ReadLine()
        if ($null -ne $line) {
            $buffer.AppendLine($line) | Out-Null
            
            # Mostrar lineas relevantes
            if ($line -match "ANT\+ HR|ANT\+ HR2|ANT\+ Cadence|ANT\+ Cad|BLE HR|BLE HR2|BLE Cadence|BLE Cad|Dispositivo conectado|SUCCESS|deviceName|DeviceNumber|Connected|Conectado") {
                Write-Host $line
                
                # Contar HR ANT+
                if ($line -match "ANT\+ HR" -and ($line -match "SUCCESS|Dispositivo conectado")) {
                    $hrAnt++
                    if ($line -match "deviceName.*?(\S+)" -or $line -match "DeviceNumber.*?(\d+)") {
                        $dispositivos += "HR ANT+: $($matches[1])"
                    }
                }
                
                # Contar HR BLE
                if ($line -match "BLE HR" -and ($line -match "Connected|Conectado")) {
                    $hrBle++
                    if ($line -match "deviceName.*?(\S+)" -or $line -match "macAddress.*?([0-9A-F:]+)") {
                        $dispositivos += "HR BLE: $($matches[1])"
                    }
                }
                
                # Contar CAD ANT+
                if ($line -match "ANT\+ Cad" -and ($line -match "SUCCESS|Dispositivo conectado")) {
                    $cadAnt++
                    if ($line -match "deviceName.*?(\S+)" -or $line -match "DeviceNumber.*?(\d+)") {
                        $dispositivos += "CAD ANT+: $($matches[1])"
                    }
                }
                
                # Contar CAD BLE
                if ($line -match "BLE Cad" -and ($line -match "Connected|Conectado")) {
                    $cadBle++
                    if ($line -match "deviceName.*?(\S+)" -or $line -match "macAddress.*?([0-9A-F:]+)") {
                        $dispositivos += "CAD BLE: $($matches[1])"
                    }
                }
            }
        }
        Start-Sleep -Milliseconds 100
    }
} catch {
    # Ctrl+C o error
} finally {
    # Detener procesos
    if ($null -ne $logcatProcess -and -not $logcatProcess.HasExited) {
        Stop-Process -Id $logcatProcess.Id -Force -ErrorAction SilentlyContinue
    }
    $reader?.Close()
    
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host "RESUMEN DE DISPOSITIVOS DETECTADOS" -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "HR (Frecuencia Cardiaca):" -ForegroundColor Yellow
    Write-Host "  - ANT+: $hrAnt dispositivo(s)" -ForegroundColor White
    Write-Host "  - BLE: $hrBle dispositivo(s)" -ForegroundColor White
    $totalHr = $hrAnt + $hrBle
    Write-Host "  - TOTAL HR: $totalHr dispositivo(s)" -ForegroundColor Green
    Write-Host ""
    Write-Host "CAD (Cadencia):" -ForegroundColor Yellow
    Write-Host "  - ANT+: $cadAnt dispositivo(s)" -ForegroundColor White
    Write-Host "  - BLE: $cadBle dispositivo(s)" -ForegroundColor White
    $totalCad = $cadAnt + $cadBle
    Write-Host "  - TOTAL CAD: $totalCad dispositivo(s)" -ForegroundColor Green
    Write-Host ""
    
    if ($dispositivos.Count -gt 0) {
        Write-Host "Dispositivos unicos detectados:" -ForegroundColor Yellow
        $dispositivos | Select-Object -Unique | ForEach-Object {
            Write-Host "  - $_" -ForegroundColor Cyan
        }
        Write-Host ""
    }
    
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host ""
    
    # Limpiar archivo temporal
    Remove-Item $tempFile -ErrorAction SilentlyContinue
}
