# rollback-dev.ps1
Write-Host "=== ROLLBACK DE RAMA DEV ===" -ForegroundColor Cyan
$rollbackCommit = Read-Host "Escribe el hash del commit al que quieres volver"

# Validar si el commit existe
$commitExists = git cat-file -e "$rollbackCommit^{commit}" 2>$null

if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: El commit '$rollbackCommit' no existe." -ForegroundColor Red
    exit 1
}

# Paso 2: Guardar estado actual en un commit temporal y tag
$fecha = Get-Date -Format "yyyyMMdd_HHmmss"
$tag = "rollback_no_funcional_$fecha"

Write-Host "Haciendo commit de todo lo actual..."
git add -A
git commit -m "Versión no funcional - rollback temporal" | Out-Null
$commitTemporal = git rev-parse HEAD
git tag $tag

# Paso 3: Intentar hacer checkout al commit dado
Write-Host "Intentando hacer checkout a $rollbackCommit..."
git checkout $rollbackCommit 2>$null

if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: No se pudo hacer checkout al commit '$rollbackCommit'." -ForegroundColor Red
    Write-Host "Revirtiendo commit temporal y eliminando el tag..."

    git reset --hard HEAD~1
    git tag -d $tag | Out-Null
    exit 1
}

# Paso 4: Mover la rama dev a ese commit
Write-Host "Moviendo rama 'dev' al commit $rollbackCommit..."
git branch -f dev $rollbackCommit
git checkout dev

Write-Host "Rollback completo. La rama 'dev' ahora apunta a $rollbackCommit." -ForegroundColor Green
